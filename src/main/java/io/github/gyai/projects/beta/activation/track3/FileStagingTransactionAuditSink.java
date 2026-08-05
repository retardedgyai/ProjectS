package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.transaction.TransactionAuditResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

/** Atomic, bounded audit export confined to beta-staging/transactions. */
public final class FileStagingTransactionAuditSink implements StagingTransactionAuditSink {
    private static final int MAXIMUM_AUDIT_BYTES = 65_536;
    private final Path directory;
    private final StagingEquipmentCodec codec = new StagingEquipmentCodec();

    public FileStagingTransactionAuditSink(StagingEconomyPaths paths) {
        if (paths == null) throw new IllegalArgumentException("staging paths are required");
        directory = paths.transactionsDirectory();
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
