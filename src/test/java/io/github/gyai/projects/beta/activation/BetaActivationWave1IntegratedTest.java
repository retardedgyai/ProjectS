package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.beta.activation.track2.StagingElementProfile;
import io.github.gyai.projects.beta.activation.track3.StagingTransactionJournalRepository;
import io.github.gyai.projects.beta.activation.track3.StagingTransactionRecoveryService;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingOperationJournal;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolAdapter;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilitySnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BetaActivationWave1IntegratedTest {
    private BetaActivationWave1IntegratedTest() { }

    public static void main(String[] arguments) throws Exception {
        centralPlanIsRegisteredButCompletelyDisabled();
        operatorRegistryIsBoundedAndRestartOnly();
        durableRecoveryNeverRetriesUncertainWork();
        fireSnapshotIsRevisionGatedAndProtocolCompatible();
        sourceBoundariesPreserveSingleApplicationAndVanillaFireBan();
    }

    private static void centralPlanIsRegisteredButCompletelyDisabled() {
        var registry = new BetaActivationWave1ModuleRegistry(testModules());
        assert registry.size() == 8;
        BetaRuntime runtime = BetaRuntimeFactory.create(BetaActivationPolicy.defaults(),
                FeatureFlagSnapshot.allDisabled(), registry.modules(), Set.of(),
                Clock.systemUTC(), (message, failure) -> { throw failure; });
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert health.moduleStates().size() == 8;
        assert health.moduleStates().values().stream()
                .allMatch(value -> value == BetaRuntimeModuleState.DISABLED);
        assert health.moduleStates().values().stream()
                .noneMatch(value -> value == BetaRuntimeModuleState.RUNNING);
        assert runtime.policy().audience() == BetaActivationAudience.OFF;
        assert runtime.policy().targetScope() == BetaActivationTargetScope.TRAINING_DUMMY_ONLY;
        assert runtime.policy().mutationPolicy() == BetaMutationPolicy.READ_ONLY;
        for (var key : io.github.gyai.projects.feature.FeatureKey.values()) {
            assert !runtime.featureFlags().isEnabled(key);
        }
        runtime.close();
    }

    private static void operatorRegistryIsBoundedAndRestartOnly() {
        BetaRuntime runtime = BetaRuntimeFactory.create(BetaActivationPolicy.defaults(),
                FeatureFlagSnapshot.allDisabled(),
                new BetaActivationWave1ModuleRegistry(testModules()).modules(), Set.of(),
                Clock.systemUTC(), (message, failure) -> { });
        runtime.start();
        BetaOperatorContributorRegistry registry =
                BetaOperatorContributorRegistry.disabledDefaults(runtime);
        assert registry.size() == 8;
        var response = new BetaRuntimeCommandService(runtime, registry)
                .execute(List.of("staging", "economy", "give"), true);
        assert !response.success();
        assert response.messages().equals(List.of(
                BetaOperatorContributorRegistry.DISABLED_MESSAGE));
        assert response.messages().size() <= BetaRuntimeCommandService.MAXIMUM_RESPONSE_LINES;
    }

    private static void durableRecoveryNeverRetriesUncertainWork() throws Exception {
        Path base = Files.createTempDirectory("projects-wave1-recovery");
        Path root = base.resolve("beta-staging").resolve("transactions");
        UUID player = UUID.randomUUID();
        UUID committed = UUID.randomUUID();
        UUID rolledBack = UUID.randomUUID();
        UUID incomplete = UUID.randomUUID();
        UUID uncertain = UUID.randomUUID();
        try (var repository = new StagingTransactionJournalRepository(root)) {
            repository.save(entry(committed, player,
                    StagingTransactionJournalRepository.Stage.COMMITTED,
                    StagingTransactionJournalRepository.TerminalOutcome.COMMITTED));
            repository.save(entry(rolledBack, player,
                    StagingTransactionJournalRepository.Stage.ROLLED_BACK,
                    StagingTransactionJournalRepository.TerminalOutcome.ROLLED_BACK));
            repository.save(entry(incomplete, player,
                    StagingTransactionJournalRepository.Stage.CONSUMED,
                    StagingTransactionJournalRepository.TerminalOutcome.NONE));
            repository.save(entry(uncertain, player,
                    StagingTransactionJournalRepository.Stage.COMMIT_UNCERTAIN,
                    StagingTransactionJournalRepository.TerminalOutcome.COMMIT_UNCERTAIN));
        }
        var terminalJournal = new BoundedStagingOperationJournal(32);
        try (var repository = new StagingTransactionJournalRepository(root);
             var recovery = new StagingTransactionRecoveryService(repository, terminalJournal)) {
            var result = recovery.recover();
            assert result.terminalReplayed() == 2;
            assert result.recoveryRequired() == 1;
            assert result.quarantined() == 1;
            assert result.blockedRequestIds().containsAll(Set.of(incomplete, uncertain));
            assert recovery.blocked(uncertain, null);
            assert repository.load(committed).isPresent();
            assert repository.load(rolledBack).isPresent();
            assert repository.load(uncertain).isEmpty();
            assert terminalJournal.findTerminal(committed).orElseThrow().replayed();
            assert terminalJournal.findTerminal(rolledBack).orElseThrow().replayed();
            var again = recovery.recover();
            assert again.terminalReplayed() == 2;
            assert again.recoveryRequired() == 1;
            assert again.quarantined() == 0;
        }
        terminalJournal.close();
    }

    private static StagingTransactionJournalRepository.Entry entry(
            UUID requestId, UUID playerId,
            StagingTransactionJournalRepository.Stage stage,
            StagingTransactionJournalRepository.TerminalOutcome outcome
    ) {
        return new StagingTransactionJournalRepository.Entry(requestId, stage, playerId,
                "projects:craft", List.of("projects:item@2"),
                StagingTransactionJournalRepository.ReservationState.HELD,
                "projects:output:1", outcome, "projects:recipe", 0, 1,
                List.of("VALIDATE", "RESERVE", "CONSUME", "PRODUCE", "PERSIST", "COMMIT"),
                false, "", 1_000L);
    }

    private static void fireSnapshotIsRevisionGatedAndProtocolCompatible() {
        UUID target = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        var snapshot = new ElementRuntimeSnapshotPort.TargetSnapshot(
                target, 42, 7, 3, 25.0, 100.0, .25,
                true, 500, 2, 10_000, 40.0,
                IceElementEngine.Stage.COLD_I, false, 0, 1_000, 1);
        ElementRuntimeSnapshotPort port = new ElementRuntimeSnapshotPort() {
            @Override public Optional<TargetSnapshot> target(UUID id) {
                return target.equals(id) ? Optional.of(snapshot) : Optional.empty();
            }
            @Override public Map<UUID, TargetSnapshot> targets() { return Map.of(target, snapshot); }
            @Override public StagingElementProfile playerProfile(UUID id) { return StagingElementProfile.NONE; }
        };
        var capability = new BetaCapabilitySnapshot(viewer, UUID.randomUUID(), 1,
                Map.of(BetaCapabilityId.ELEMENTS, 1), Instant.ofEpochMilli(20_000), false);
        var adapter = new ElementSnapshotProtocolAdapter(port,
                () -> BetaRuntimeModuleState.RUNNING,
                () -> BetaRuntimeModuleState.RUNNING,
                ignored -> capability, ignored -> true);
        var mapped = adapter.next(viewer, target, 2_000).orElseThrow();
        assert mapped.targetNetworkId() == 42;
        assert mapped.stateRevision() == 7;
        assert mapped.fireStacks() == 3;
        assert mapped.fireFractionalProgress() == .25;
        assert mapped.detonationPulseRevision() == 2;
        assert adapter.next(viewer, target, 2_001).isEmpty();
        var disabled = new ElementSnapshotProtocolAdapter(port,
                () -> BetaRuntimeModuleState.DISABLED,
                () -> BetaRuntimeModuleState.RUNNING,
                ignored -> capability, ignored -> true);
        assert disabled.next(viewer, target, 2_000).isEmpty();
    }

    private static void sourceBoundariesPreserveSingleApplicationAndVanillaFireBan()
            throws Exception {
        String listener = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/listener/CombatListener.java"));
        String support = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/skill/warrior/WarriorSkillSupport.java"));
        StringBuilder track2 = new StringBuilder();
        try (var files = Files.walk(Path.of(
                "src/main/java/io/github/gyai/projects/beta/activation/track2"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                track2.append(Files.readString(file));
            }
        }
        assert count(listener, "starterSwordDamageRouter.apply(request)") == 1;
        assert count(support, "damageApplier.apply(request)") == 1;
        assert listener.contains("confirmedHitObserver.confirmed");
        assert support.contains("confirmedHitObserver.confirmed");
        assert track2.indexOf("setFireTicks") < 0;
        assert track2.indexOf("setVisualFire") < 0;
    }

    private static int count(String source, String needle) {
        int count = 0, index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) { count++; index += needle.length(); }
        return count;
    }

    private static List<BetaRuntimeModule> testModules() {
        return java.util.Arrays.stream(BetaRuntimeModuleId.values())
                .map(TestModule::new).map(BetaRuntimeModule.class::cast).toList();
    }

    private static final class TestModule implements BetaRuntimeModule {
        private final BetaRuntimeModuleId id;
        private TestModule(BetaRuntimeModuleId id) { this.id = id; }
        @Override public BetaRuntimeModuleId id() { return id; }
        @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
        @Override public BetaRuntimeModuleDescriptor descriptor() {
            return new BetaRuntimeModuleDescriptor(id, Set.of(), Set.of(),
                    BetaMutationPolicy.READ_ONLY, true, Set.of());
        }
        @Override public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
            return BetaRuntimeModuleResult.ready();
        }
        @Override public BetaRuntimeModuleResult start() { return BetaRuntimeModuleResult.running(); }
        @Override public BetaRuntimeModuleResult stop() { return BetaRuntimeModuleResult.stopped(); }
        @Override public BetaRuntimeModuleState state() { return BetaRuntimeModuleState.NOT_INSTALLED; }
    }
}
