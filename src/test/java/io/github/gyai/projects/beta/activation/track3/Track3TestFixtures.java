package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class Track3TestFixtures {
    static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    private Track3TestFixtures() {
    }

    static StagingOperationAccess access(UUID playerId) {
        return new StagingOperationAccess(playerId, "staging_world", true,
                new BetaActivationPolicy(
                        BetaActivationAudience.ALLOWLIST,
                        BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                        BetaMutationPolicy.STAGING_WRITE,
                        Set.of(playerId), Set.of("staging_world"), true, false));
    }

    static Fixture fixture(int equipmentCapacity) {
        BoundedStagingInventory inventory = new BoundedStagingInventory(equipmentCapacity);
        BoundedStagingOperationJournal journal = new BoundedStagingOperationJournal(256);
        AtomicLong uuids = new AtomicLong(10_000);
        StagingInventoryTransactionAdapter transactions =
                new StagingInventoryTransactionAdapter(
                        inventory, journal, CLOCK,
                        () -> new UUID(1, uuids.incrementAndGet()));
        StagingEnhancementOutcomeRegistry outcomes = new StagingEnhancementOutcomeRegistry();
        StagingEconomyService service = new StagingEconomyService(
                inventory, journal, transactions, outcomes);
        service.setGroupRunning(
                StagingEconomyService.OperationGroup.GATHERING_CRAFTING, true);
        service.setGroupRunning(
                StagingEconomyService.OperationGroup.ENHANCEMENT_REPAIR, true);
        return new Fixture(inventory, journal, outcomes, service);
    }

    record Fixture(
            BoundedStagingInventory inventory,
            BoundedStagingOperationJournal journal,
            StagingEnhancementOutcomeRegistry outcomes,
            StagingEconomyService service
    ) implements AutoCloseable {
        @Override
        public void close() {
            service.close();
        }
    }
}
