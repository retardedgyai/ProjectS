package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.crafting.OutputProposal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;
import io.github.gyai.projects.equipment.EquipmentItemV1;

/** Atomic, bounded audit export confined to beta-staging/transactions. */
public final class FileStagingTransactionAuditSink implements StagingTransactionAuditSink {
    private static final int MAXIMUM_AUDIT_BYTES = 65_536;
    private final Path directory;
    private final StagingEquipmentCodec codec = new StagingEquipmentCodec();
    private final StagingTransactionJournalRepository recoveryJournal;

    public FileStagingTransactionAuditSink(StagingEconomyPaths paths) {
        this(paths, new StagingTransactionJournalRepository(
                java.util.Objects.requireNonNull(paths, "staging paths are required")
                        .transactionsDirectory()));
    }

    public FileStagingTransactionAuditSink(
            StagingEconomyPaths paths,
            StagingTransactionJournalRepository recoveryJournal
    ) {
        if (paths == null) throw new IllegalArgumentException("staging paths are required");
        directory = paths.transactionsDirectory();
        this.recoveryJournal = java.util.Objects.requireNonNull(
                recoveryJournal, "recovery journal is required");
    }

    @Override
    public void resolved(EquipmentMutationProposal proposal) {
        StagingEquipmentDocument document = codec.encode(
                proposal.proposedItem(), proposal.expectedRevision() + 1);
        String body = "state: RESOLVED\n"
                + "request-id: " + proposal.requestId() + "\n"
                + "player-id: " + proposal.playerId() + "\n"
                + "operation-id: " + safe(proposal.operationId()) + "\n"
                + "recipe-id: " + safe(proposal.recipeId()) + "\n"
                + "family-id: " + safe(proposal.canonicalFamilyId()) + "\n"
                + "expected-revision: " + proposal.expectedRevision() + "\n"
                + "item-payload-base64: " + Base64.getEncoder()
                .encodeToString(document.payload()) + "\n";
        write(proposal.requestId(), "resolved", body);
        recoveryJournal.save(
                new StagingTransactionJournalRepository.Entry(
                        proposal.requestId(),
                        StagingTransactionJournalRepository.Stage.PRODUCED,
                        proposal.playerId(), proposal.operationId(),
                        proposal.inputs().stream().map(input ->
                                input.inputId() + "@" + input.revision()).toList(),
                        StagingTransactionJournalRepository.ReservationState.HELD,
                        proposal.proposedItem().instanceId().map(UUID::toString)
                                .orElse(proposal.canonicalFamilyId()),
                        StagingTransactionJournalRepository.TerminalOutcome.NONE,
                        proposal.recipeId(), proposal.expectedRevision(), 1,
                        java.util.List.of("VALIDATE", "RESERVE", "CONSUME", "PRODUCE"),
                        true, "",
                        System.currentTimeMillis()));
    }

    @Override
    public void terminal(TransactionAuditResult result) {
        String body = "state: TERMINAL\n"
                + "request-id: " + result.requestId() + "\n"
                + "player-id: " + result.playerId() + "\n"
                + "operation-id: " + safe(result.operationId()) + "\n"
                + "recipe-id: " + safe(result.recipeId()) + "\n"
                + "outcome: " + result.outcome().name() + "\n"
                + "replayed: " + result.replayed() + "\n"
                + "completed-at: " + result.completedAt() + "\n"
                + "reason: " + safe(result.reason()) + "\n";
        write(result.requestId(), "terminal", body);
        boolean retainFinalized = result.outcome() == TransactionAuditResult.Outcome.COMMITTED
                || result.outcome() == TransactionAuditResult.Outcome.COMMIT_UNCERTAIN;
        String retained = retainFinalized ? recoveryJournal.load(result.requestId())
                .map(StagingTransactionJournalRepository.Entry::proposedOutputIdentity)
                .filter(value -> value.startsWith("equipment:")).orElse("") : "";
        recoveryJournal.save(terminalEntry(result, retained));
    }

    @Override
    public void resourceIntent(TransactionRequest request, OutputProposal output) {
        if (request == null || output == null || output.equipmentBase()) {
            throw new IllegalArgumentException("invalid resource transaction intent");
        }
        String outputIdentity = output.outputId() + ":" + output.quantity();
        recoveryJournal.save(new StagingTransactionJournalRepository.Entry(
                request.requestId(), StagingTransactionJournalRepository.Stage.RESERVED,
                request.playerId(), request.operationId(), request.inputs().stream()
                        .map(input -> input.inputId() + "@" + input.revision()).toList(),
                StagingTransactionJournalRepository.ReservationState.HELD,
                outputIdentity, StagingTransactionJournalRepository.TerminalOutcome.NONE,
                request.recipeId(), request.expectedRevision(), request.expectedOutputUnits(),
                java.util.List.of("VALIDATE", "RESERVE"), false, "",
                System.currentTimeMillis()));
    }

    @Override
    public void finalized(UUID requestId, EquipmentItemV1 item) {
        StagingEquipmentDocument document = codec.encode(item, 0);
        String retained = "equipment:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(document.payload());
        if (retained.length() > 512) throw new IllegalStateException("finalized staging item is oversized");
        StagingTransactionJournalRepository.Entry previous = recoveryJournal.load(requestId)
                .orElseThrow(() -> new IllegalStateException("resolved staging journal is missing"));
        recoveryJournal.save(new StagingTransactionJournalRepository.Entry(requestId,
                StagingTransactionJournalRepository.Stage.PERSISTED, previous.playerId(),
                previous.operationType(), previous.inputIdentities(),
                previous.reservationState(), retained,
                StagingTransactionJournalRepository.TerminalOutcome.NONE,
                previous.recipeId(), previous.expectedRevision(), previous.expectedOutputUnits(),
                previous.completedStages(), true, previous.reason(), System.currentTimeMillis()));
    }

    public boolean usesRecoveryJournal(StagingTransactionJournalRepository repository) {
        return recoveryJournal == repository;
    }

    private static StagingTransactionJournalRepository.Entry terminalEntry(
            TransactionAuditResult result, String retainedOutput
    ) {
        StagingTransactionJournalRepository.Stage stage;
        StagingTransactionJournalRepository.TerminalOutcome outcome;
        switch (result.outcome()) {
            case COMMITTED -> {
                stage = StagingTransactionJournalRepository.Stage.COMMITTED;
                outcome = StagingTransactionJournalRepository.TerminalOutcome.COMMITTED;
            }
            case ROLLED_BACK, REJECTED, INPUT_CONFLICT, TERMINAL_LIMIT,
                    REPLAY_CONFLICT, DUPLICATE_ACTIVE, ACTIVE_LIMIT, CLOSED -> {
                stage = StagingTransactionJournalRepository.Stage.ROLLED_BACK;
                outcome = StagingTransactionJournalRepository.TerminalOutcome.ROLLED_BACK;
            }
            case ROLLBACK_FAILED, COMMIT_UNCERTAIN -> {
                stage = StagingTransactionJournalRepository.Stage.COMMIT_UNCERTAIN;
                outcome = StagingTransactionJournalRepository.TerminalOutcome.COMMIT_UNCERTAIN;
            }
            default -> throw new IllegalStateException("unknown transaction outcome");
        }
        return new StagingTransactionJournalRepository.Entry(
                result.requestId(), stage, result.playerId(), result.operationId(),
                result.inputs().stream().map(input ->
                        input.inputId() + "@" + input.revision()).toList(),
                stage == StagingTransactionJournalRepository.Stage.COMMITTED
                        ? StagingTransactionJournalRepository.ReservationState.CONSUMED
                        : StagingTransactionJournalRepository.ReservationState.RELEASED,
                retainedOutput.isBlank() ? result.output().map(output -> output.outputId()
                        + ":" + output.quantity()).orElse("") : retainedOutput,
                outcome, result.recipeId(), result.expectedRevision(),
                result.expectedOutputUnits(), result.completedStages().stream()
                        .map(Enum::name).toList(),
                result.output().map(io.github.gyai.projects.crafting.OutputProposal::equipmentBase)
                        .orElse(false), result.reason(), result.completedAt().toEpochMilli());
    }

    private void write(UUID requestId, String kind, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_AUDIT_BYTES) {
            throw new IllegalStateException("staging transaction audit is oversized");
        }
        try {
            Files.createDirectories(directory);
            Path target = checked(directory.resolve(requestId + "." + kind + ".yml"));
            Path temporary = checked(directory.resolve(requestId + "." + kind + ".tmp"));
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("staging transaction audit write failed", failure);
        }
    }

    private Path checked(Path value) {
        Path normalized = value.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory)) {
            throw new IllegalArgumentException("staging transaction path escaped root");
        }
        return normalized;
    }

    private static String safe(String value) {
        if (value == null) return "";
        String result = value.replace('\n', ' ').replace('\r', ' ');
        return result.length() <= 512 ? result : result.substring(0, 512);
    }
}
