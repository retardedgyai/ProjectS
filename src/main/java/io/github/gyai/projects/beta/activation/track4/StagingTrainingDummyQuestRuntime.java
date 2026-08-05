package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.participation.EncounterId;
import io.github.gyai.projects.participation.ParticipationEvent;
import io.github.gyai.projects.participation.ParticipationKey;
import io.github.gyai.projects.participation.ParticipationLedger;
import io.github.gyai.projects.participation.ParticipationResult;
import io.github.gyai.projects.quest.QuestDefinitionRef;
import io.github.gyai.projects.quest.QuestProgressCommand;
import io.github.gyai.projects.quest.QuestProgressResult;
import io.github.gyai.projects.quest.QuestProgressService;
import io.github.gyai.projects.quest.QuestProgressSnapshot;
import io.github.gyai.projects.reward.RewardClaimKey;
import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardClaimResult;
import io.github.gyai.projects.reward.RewardClaimService;
import io.github.gyai.projects.reward.RewardClaimStore;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StagingTrainingDummyQuestRuntime
        implements TrainingDummyParticipationPort, AutoCloseable {
    public static final int MAXIMUM_PLAYERS = 512;
    public static final int MAXIMUM_HIT_IDENTITIES = 4096;
    private static final QuestDefinitionRef QUEST =
            new QuestDefinitionRef(Track4StagingIds.QUEST, 1);

    private final BetaActivationPolicy policy;
    private final Clock clock;
    private final StagingPlayerProgressPort progressPort;
    private final RewardClaimStore claimStore;
    private final StagingItemDeliveryPort deliveryPort;
    private final ParticipationLedger ledger;
    private final QuestProgressService questService =
            new QuestProgressService(definition -> QUEST.equals(definition));
    private final LinkedHashMap<UUID, QuestProgressSnapshot> progress =
            new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<HitIdentity, Boolean> hitIdentities =
            new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public StagingTrainingDummyQuestRuntime(
            BetaActivationPolicy policy,
            Clock clock,
            StagingPlayerProgressPort progressPort,
            RewardClaimStore claimStore,
            StagingItemDeliveryPort deliveryPort
    ) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.progressPort = progressPort;
        this.claimStore = claimStore;
        this.deliveryPort = deliveryPort;
        ledger = new ParticipationLedger(128, 2048,
                event -> new io.github.gyai.projects.participation.ParticipationPolicy.Decision(
                        event.reportedContribution() == 1.0,
                        event.reportedContribution() == 1.0 ? 1.0 : 0.0,
                        event.reportedContribution() == 1.0 ? "" : "invalid-hit-value"),
                clock);
    }

    @Override
    public synchronized HitResult record(DirectHit hit) {
        if (closed) return HitResult.failure(HitStatus.CLOSED, "runtime-closed");
        if (hit == null || !hit.trainingDummy() || !hit.directDamage()) {
            return HitResult.failure(HitStatus.INELIGIBLE, "direct-training-dummy-hit-required");
        }
        StagingAdmission.Decision admission = StagingAdmission.read(
                policy, hit.playerId(), hit.worldName(), hit.projectsDev(),
                hit.compatibleClient());
        if (!admission.allowed()) {
            return HitResult.failure(HitStatus.INELIGIBLE, admission.reason());
        }
        HitIdentity identity = new HitIdentity(
                hit.encounterId(), hit.hitSessionId(), hit.playerId(), hit.targetId());
        if (hitIdentities.containsKey(identity)) {
            return result(HitStatus.DUPLICATE, current(hit.playerId()), "duplicate-hit");
        }
        ParticipationResult participation = ledger.record(new ParticipationEvent(
                new ParticipationKey(new EncounterId(hit.encounterId()), hit.playerId(),
                        Track4StagingIds.ENCOUNTER, hit.contributionRevision()),
                1.0, ParticipationEvent.ContributionSemantics.DELTA, hit.occurredAt()));
        if (participation.status() == ParticipationResult.Status.DUPLICATE
                || participation.status() == ParticipationResult.Status.STALE) {
            return result(HitStatus.DUPLICATE, current(hit.playerId()), participation.reason());
        }
        if (participation.status() != ParticipationResult.Status.RECORDED) {
            return result(HitStatus.INELIGIBLE, current(hit.playerId()), participation.reason());
        }
        remember(identity);
        QuestProgressSnapshot before = current(hit.playerId()).orElse(null);
        QuestProgressResult updated = advance(hit, before);
        if (updated.proposal().isEmpty()) {
            return result(HitStatus.REJECTED, Optional.ofNullable(before), updated.reason());
        }
        QuestProgressSnapshot proposal = updated.proposal().orElseThrow();
        try {
            QuestProgressSnapshot persisted = persist(proposal,
                    before == null ? 0 : before.progressRevision());
            put(persisted);
            HitStatus status = persisted.completionMarked()
                    ? HitStatus.COMPLETED
                    : (progressPort != null && progressPort.available()
                    ? HitStatus.RECORDED : HitStatus.MEMORY_ONLY);
            return result(status, Optional.of(persisted), "");
        } catch (RuntimeException failure) {
            return result(HitStatus.PERSISTENCE_FAILED, Optional.ofNullable(before),
                    bounded(failure));
        }
    }

    public synchronized ClaimResult claim(ClaimRequest request) {
        if (closed) return ClaimResult.failure(ClaimStatus.CLOSED, "runtime-closed");
        if (request == null) return ClaimResult.failure(ClaimStatus.REJECTED, "request-required");
        StagingAdmission.Decision admission = StagingAdmission.stagingWrite(
                policy, request.playerId(), request.worldName(), request.projectsDev(),
                request.compatibleClient());
        if (!admission.allowed()) return ClaimResult.failure(
                ClaimStatus.REJECTED, admission.reason());
        Optional<QuestProgressSnapshot> current = current(request.playerId());
        if (current.isEmpty() || !current.orElseThrow().completionMarked()) {
            return ClaimResult.failure(ClaimStatus.REJECTED, "quest-not-complete");
        }
        if (claimStore == null || deliveryPort == null || !deliveryPort.available()) {
            return ClaimResult.failure(ClaimStatus.BLOCKED, "reward-delivery-port-unavailable");
        }
        RewardClaimKey key = new RewardClaimKey(
                request.playerId(), "projects:staging/quest",
                request.encounterId(), Track4StagingIds.REWARD, 1);
        UUID requestId = stableUuid(key.stableIdentity());
        RewardClaimService service = new RewardClaimService(
                claimStore,
                value -> deliveryPort.deliver(value, Track4StagingIds.TOKEN, 1,
                        new StagingItemDeliveryPort.DeliveryContext(
                                request.worldName(), request.projectsDev(),
                                request.compatibleClient())),
                receipt -> receipt.status()
                        == io.github.gyai.projects.reward.RewardDeliveryReceipt.Status.REJECTED,
                clock);
        RewardClaimResult claim = service.claim(new RewardClaimRequest(
                requestId, key, clock.instant()));
        if (claim.status() != RewardClaimResult.Status.DELIVERED) {
            return new ClaimResult(map(claim.status()), claim, claim.reason());
        }
        QuestProgressSnapshot before = current.orElseThrow();
        if (!before.claimedMarked()) {
            QuestProgressResult marked = questService.propose(Optional.of(before),
                    command(requestId, request.playerId(), QuestProgressCommand.Type.MARK_CLAIMED,
                            before.progressRevision(), Optional.empty(), 0));
            if (marked.proposal().isPresent()) {
                try {
                    QuestProgressSnapshot saved = persist(
                            marked.proposal().orElseThrow(), before.progressRevision());
                    put(saved);
                } catch (RuntimeException failure) {
                    return new ClaimResult(ClaimStatus.DELIVERED_PROGRESS_PENDING,
                            claim, bounded(failure));
                }
            }
        }
        return new ClaimResult(claim.replayed()
                ? ClaimStatus.REPLAYED : ClaimStatus.DELIVERED, claim, "");
    }

    public synchronized Optional<QuestProgressSnapshot> snapshot(UUID playerId) {
        return current(playerId);
    }

    public synchronized int trackedPlayerCount() {
        return progress.size();
    }

    public synchronized int trackedHitCount() {
        return hitIdentities.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        ledger.close();
        progress.clear();
        hitIdentities.clear();
    }

    private QuestProgressResult advance(DirectHit hit, QuestProgressSnapshot before) {
        QuestProgressSnapshot working = before;
        if (working == null) {
            QuestProgressResult started = questService.propose(Optional.empty(),
                    command(stableUuid(hit.hitSessionId() + ":start"), hit.playerId(),
                            QuestProgressCommand.Type.START, 0, Optional.empty(), 0));
            if (started.proposal().isEmpty()) return started;
            working = started.proposal().orElseThrow();
        }
        QuestProgressResult incremented = questService.propose(Optional.of(working),
                command(stableUuid(hit.hitSessionId() + ":increment"), hit.playerId(),
                        QuestProgressCommand.Type.INCREMENT_COUNTER,
                        working.progressRevision(),
                        Optional.of(Track4StagingIds.QUEST_HIT_COUNTER), 1));
        if (incremented.proposal().isEmpty()) return incremented;
        QuestProgressSnapshot after = incremented.proposal().orElseThrow();
        if (after.counters().getOrDefault(Track4StagingIds.QUEST_HIT_COUNTER, 0L)
                < Track4StagingIds.REQUIRED_HITS) return incremented;
        return questService.propose(Optional.of(after),
                command(stableUuid(hit.hitSessionId() + ":complete"), hit.playerId(),
                        QuestProgressCommand.Type.COMPLETE,
                        after.progressRevision(), Optional.empty(), 0));
    }

    private Optional<QuestProgressSnapshot> current(UUID playerId) {
        QuestProgressSnapshot memory = progress.get(playerId);
        if (memory != null) return Optional.of(memory);
        if (progressPort == null || !progressPort.available()) return Optional.empty();
        Optional<QuestProgressSnapshot> loaded = progressPort.load(playerId, QUEST);
        loaded.ifPresent(this::put);
        return loaded;
    }

    private QuestProgressSnapshot persist(QuestProgressSnapshot proposal, long expected) {
        if (progressPort == null || !progressPort.available()) return proposal;
        return java.util.Objects.requireNonNull(progressPort.save(proposal, expected));
    }

    private void put(QuestProgressSnapshot value) {
        if (!progress.containsKey(value.playerId()) && progress.size() >= MAXIMUM_PLAYERS) {
            UUID eldest = progress.keySet().iterator().next();
            progress.remove(eldest);
        }
        progress.put(value.playerId(), value);
    }

    private void remember(HitIdentity value) {
        if (hitIdentities.size() >= MAXIMUM_HIT_IDENTITIES
                && !hitIdentities.containsKey(value)) {
            hitIdentities.remove(hitIdentities.keySet().iterator().next());
        }
        hitIdentities.put(value, Boolean.TRUE);
    }

    private static QuestProgressCommand command(
            UUID id, UUID playerId, QuestProgressCommand.Type type,
            long expected, Optional<String> target, long amount) {
        return new QuestProgressCommand(id, playerId, QUEST, type,
                expected, target, amount);
    }

    private static HitResult result(
            HitStatus status, Optional<QuestProgressSnapshot> snapshot, String detail) {
        return new HitResult(status, snapshot, detail);
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String bounded(RuntimeException failure) {
        String detail = failure.getMessage();
        if (detail == null || detail.isBlank()) detail = failure.getClass().getSimpleName();
        return detail.length() <= 256 ? detail : detail.substring(0, 256);
    }

    private static ClaimStatus map(RewardClaimResult.Status status) {
        return switch (status) {
            case DELIVERED -> ClaimStatus.DELIVERED;
            case FULL_INVENTORY -> ClaimStatus.FULL_INVENTORY;
            case PERSIST_FAILURE -> ClaimStatus.PERSISTENCE_FAILED;
            case COMMIT_UNCERTAIN -> ClaimStatus.COMMIT_UNCERTAIN;
            case REJECTED, CLAIM_STORE_FAILURE -> ClaimStatus.REJECTED;
        };
    }

    private record HitIdentity(UUID encounterId, UUID hitSessionId,
                               UUID playerId, UUID targetId) {
    }

    public record DirectHit(
            UUID encounterId,
            UUID hitSessionId,
            UUID playerId,
            UUID targetId,
            String worldName,
            long contributionRevision,
            Instant occurredAt,
            boolean directDamage,
            boolean trainingDummy,
            boolean projectsDev,
            boolean compatibleClient
    ) {
        public DirectHit {
            java.util.Objects.requireNonNull(encounterId);
            java.util.Objects.requireNonNull(hitSessionId);
            java.util.Objects.requireNonNull(playerId);
            java.util.Objects.requireNonNull(targetId);
            java.util.Objects.requireNonNull(occurredAt);
            if (contributionRevision < 0) throw new IllegalArgumentException("revision");
        }
    }

    public record ClaimRequest(
            UUID encounterId,
            UUID playerId,
            String worldName,
            boolean projectsDev,
            boolean compatibleClient
    ) {
    }

    public record HitResult(
            HitStatus status,
            Optional<QuestProgressSnapshot> progress,
            String detail
    ) {
        public HitResult {
            progress = progress == null ? Optional.empty() : progress;
            detail = detail == null ? "" : detail;
        }

        static HitResult failure(HitStatus status, String detail) {
            return new HitResult(status, Optional.empty(), detail);
        }
    }

    public enum HitStatus {
        RECORDED, MEMORY_ONLY, COMPLETED, DUPLICATE, INELIGIBLE,
        REJECTED, PERSISTENCE_FAILED, CLOSED
    }

    public record ClaimResult(
            ClaimStatus status,
            RewardClaimResult claim,
            String detail
    ) {
        public ClaimResult {
            detail = detail == null ? "" : detail;
        }

        static ClaimResult failure(ClaimStatus status, String detail) {
            return new ClaimResult(status, null, detail);
        }
    }

    public enum ClaimStatus {
        DELIVERED, REPLAYED, DELIVERED_PROGRESS_PENDING, FULL_INVENTORY,
        PERSISTENCE_FAILED, COMMIT_UNCERTAIN, BLOCKED, REJECTED, CLOSED
    }
}
