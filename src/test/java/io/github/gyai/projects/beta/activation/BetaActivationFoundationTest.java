package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class BetaActivationFoundationTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    private BetaActivationFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        policyDefaultsAndInvalidInputFailClosed();
        policyAllowlistWorldAndTargetBoundaries();
        policyCollectionsAreImmutable();
        duplicateAndCycleAreRejected();
        topologicalStartAndReverseStop();
        partialStartRollbackOnlyStopsStartedModules();
        startFailureIsIsolatedFromCaller();
        stopFailureDoesNotPreventRemainingStops();
        startAndCloseAreIdempotent();
        healthReadIsSideEffectFreeAndImmutable();
        diagnosticsAreBounded();
        featureFlagsAndAudiencePreventStart();
        missingDependencyIsBlocked();
        readOnlyFallbackAndMutationBlockAreExplicit();
        commandSurfaceIsReadOnlyBoundedAndPermissionChecked();
        pluginBoundaryHasNoNewChannelsListenersOrSchedulers();
        configDefaultsRemainClosed();
    }

    private static void policyDefaultsAndInvalidInputFailClosed() {
        BetaActivationPolicy defaults = BetaActivationPolicy.defaults();
        assert defaults.audience() == BetaActivationAudience.OFF;
        assert defaults.targetScope() == BetaActivationTargetScope.TRAINING_DUMMY_ONLY;
        assert defaults.mutationPolicy() == BetaMutationPolicy.READ_ONLY;
        assert defaults.failClosed();
        assert !defaults.requireCompatibleClient();
        assert defaults.restartRequired();
        assert !defaults.allowsAudience(null, true);
        assert !defaults.allowsWorld("world");

        List<String> warnings = new ArrayList<>();
        BetaActivationPolicy parsed = BetaActivationPolicy.parse(Map.of(
                "audience", "invalid",
                "target-scope", "invalid",
                "mutation-policy", "invalid",
                "allowlisted-player-uuids", List.of("not-a-uuid"),
                "fail-closed", "not-a-boolean"), warnings::add);
        assert parsed.audience() == BetaActivationAudience.OFF;
        assert parsed.targetScope() == BetaActivationTargetScope.TRAINING_DUMMY_ONLY;
        assert parsed.mutationPolicy() == BetaMutationPolicy.READ_ONLY;
        assert parsed.allowlistedPlayerUuids().isEmpty();
        assert parsed.failClosed();
        assert warnings.size() == 5;
    }

    private static void policyAllowlistWorldAndTargetBoundaries() {
        UUID allowed = UUID.fromString("00000000-0000-0000-0000-000000000001");
        BetaActivationPolicy policy = new BetaActivationPolicy(
                BetaActivationAudience.ALLOWLIST,
                BetaActivationTargetScope.NON_PLAYER_PVE,
                BetaMutationPolicy.STAGING_WRITE,
                Set.of(allowed), Set.of("beta_world"), true, true);
        assert policy.allowsAudience(allowed, true);
        assert !policy.allowsAudience(allowed, false);
        assert !policy.allowsAudience(UUID.randomUUID(), true);
        assert policy.allowsWorld("beta_world");
        assert !policy.allowsWorld("production_world");
        assert policy.allowsTarget(BetaActivationTarget.TRAINING_DUMMY);
        assert policy.allowsTarget(BetaActivationTarget.NON_PLAYER_PVE);
        assert !policy.allowsTarget(BetaActivationTarget.OTHER_PVE);
        assert !policy.allowsTarget(BetaActivationTarget.PLAYER_PVP);
        assert policy.allowsMutation(BetaMutationPolicy.READ_ONLY);
        assert policy.allowsMutation(BetaMutationPolicy.STAGING_WRITE);
        assert !policy.allowsMutation(BetaMutationPolicy.PRODUCTION_WRITE);

        BetaActivationPolicy allPve = new BetaActivationPolicy(
                BetaActivationAudience.GLOBAL, BetaActivationTargetScope.ALL_PVE,
                BetaMutationPolicy.READ_ONLY, Set.of(), Set.of(), true, false);
        assert allPve.allowsTarget(BetaActivationTarget.OTHER_PVE);
        assert !allPve.allowsTarget(BetaActivationTarget.PLAYER_PVP);
    }

    private static void policyCollectionsAreImmutable() {
        BetaActivationPolicy policy = BetaActivationPolicy.defaults();
        expectUnsupported(() -> policy.allowedWorlds().add("world"));
        expectUnsupported(() -> policy.allowlistedPlayerUuids().add(UUID.randomUUID()));
    }

    private static void duplicateAndCycleAreRejected() {
        FakeModule first = module(BetaRuntimeModuleId.EQUIPMENT, Set.of(), List.of());
        FakeModule duplicate = module(BetaRuntimeModuleId.EQUIPMENT, Set.of(), List.of());
        expectIllegal(() -> new BetaRuntimeDependencyResolver().resolve(
                List.of(first, duplicate)));

        FakeModule a = module(BetaRuntimeModuleId.EQUIPMENT,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE), List.of());
        FakeModule b = module(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                Set.of(BetaRuntimeModuleId.EQUIPMENT), List.of());
        expectIllegal(() -> new BetaRuntimeDependencyResolver().resolve(List.of(a, b)));
    }

    private static void topologicalStartAndReverseStop() {
        List<String> events = new ArrayList<>();
        FakeModule persistence = module(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                Set.of(), events);
        FakeModule equipment = module(BetaRuntimeModuleId.EQUIPMENT,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE), events);
        BetaRuntime runtime = runtime(global(), disabledFlags(),
                List.of(equipment, persistence), Set.of());
        runtime.start();
        assert events.equals(List.of(
                "prepare:PLAYER_PERSISTENCE", "start:PLAYER_PERSISTENCE",
                "prepare:EQUIPMENT", "start:EQUIPMENT"));
        assert runtime.startupPlan().orderedModules().stream().map(BetaRuntimeModule::id)
                .toList().equals(List.of(
                        BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                        BetaRuntimeModuleId.EQUIPMENT));
        runtime.close();
        assert events.subList(4, 6).equals(List.of(
                "stop:EQUIPMENT", "stop:PLAYER_PERSISTENCE"));
    }

    private static void partialStartRollbackOnlyStopsStartedModules() {
        List<String> events = new ArrayList<>();
        FakeModule persistence = module(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                Set.of(), events);
        FakeModule equipment = module(BetaRuntimeModuleId.EQUIPMENT,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE), events);
        equipment.failStart = true;
        FakeModule combat = module(BetaRuntimeModuleId.COMBAT_ELEMENTS,
                Set.of(), events);
        BetaRuntime runtime = runtime(global(), disabledFlags(),
                List.of(persistence, equipment, combat), Set.of());
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert events.contains("stop:PLAYER_PERSISTENCE");
        assert !events.contains("stop:EQUIPMENT");
        assert !events.contains("prepare:COMBAT_ELEMENTS");
        assert health.moduleStates().get(BetaRuntimeModuleId.PLAYER_PERSISTENCE)
                == BetaRuntimeModuleState.STOPPED;
        assert health.moduleStates().get(BetaRuntimeModuleId.EQUIPMENT)
                == BetaRuntimeModuleState.FAILED;
        assert health.status() == BetaRuntimeHealthStatus.FAILED;
    }

    private static void startFailureIsIsolatedFromCaller() {
        FakeModule module = module(BetaRuntimeModuleId.COMBAT_ELEMENTS, Set.of(), List.of());
        module.throwStart = true;
        AtomicInteger logged = new AtomicInteger();
        BetaRuntime runtime = BetaRuntimeFactory.create(global(), disabledFlags(),
                List.of(module), Set.of(), CLOCK,
                (message, exception) -> logged.incrementAndGet());
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert health.status() == BetaRuntimeHealthStatus.FAILED;
        assert logged.get() == 1;
        assert health.lastFailure().contains("IllegalStateException") : health.lastFailure();
        assert health.lastFailure().length() <= BetaRuntimeModuleResult.MAXIMUM_DETAIL_LENGTH;
    }

    private static void stopFailureDoesNotPreventRemainingStops() {
        List<String> events = new ArrayList<>();
        FakeModule persistence = module(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                Set.of(), events);
        FakeModule equipment = module(BetaRuntimeModuleId.EQUIPMENT,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE), events);
        equipment.throwStop = true;
        BetaRuntime runtime = runtime(global(), disabledFlags(),
                List.of(persistence, equipment), Set.of());
        runtime.start();
        runtime.close();
        assert events.subList(events.size() - 2, events.size()).equals(List.of(
                "stop:EQUIPMENT", "stop:PLAYER_PERSISTENCE"));
        assert runtime.healthSnapshot().status() == BetaRuntimeHealthStatus.STOPPED;
        assert runtime.healthSnapshot().moduleStates().get(BetaRuntimeModuleId.EQUIPMENT)
                == BetaRuntimeModuleState.FAILED;
    }

    private static void startAndCloseAreIdempotent() {
        FakeModule module = module(BetaRuntimeModuleId.COMBAT_ELEMENTS, Set.of(), List.of());
        BetaRuntime runtime = runtime(global(), disabledFlags(), List.of(module), Set.of());
        runtime.start();
        runtime.start();
        runtime.close();
        runtime.close();
        assert module.prepareCalls == 1;
        assert module.startCalls == 1;
        assert module.stopCalls == 1;
        assert runtime.healthSnapshot().startCount() == 1;
        assert runtime.healthSnapshot().stopCount() == 1;

        BetaRuntime closedBeforeStart = runtime(global(), disabledFlags(),
                List.of(module(BetaRuntimeModuleId.EQUIPMENT, Set.of(), List.of())), Set.of());
        closedBeforeStart.close();
        BetaRuntimeHealthSnapshot rejected = closedBeforeStart.start();
        assert rejected.status() == BetaRuntimeHealthStatus.STOPPED;
        assert rejected.diagnostics().stream().anyMatch(value ->
                value.code() == BetaRuntimeDiagnosticCode.START_REJECTED_CLOSED);
    }

    private static void healthReadIsSideEffectFreeAndImmutable() {
        BetaRuntime runtime = runtime(global(), disabledFlags(), List.of(), Set.of());
        runtime.start();
        BetaRuntimeHealthSnapshot first = runtime.healthSnapshot();
        BetaRuntimeHealthSnapshot second = runtime.healthSnapshot();
        assert first.moduleStates().equals(second.moduleStates());
        assert first.startCount() == second.startCount();
        assert first.stopCount() == second.stopCount();
        expectUnsupported(() -> first.moduleStates().put(
                BetaRuntimeModuleId.EQUIPMENT, BetaRuntimeModuleState.RUNNING));
        expectUnsupported(() -> first.diagnostics().add(new BetaRuntimeDiagnostic(
                Instant.now(CLOCK), null, BetaRuntimeDiagnosticCode.POLICY_DISABLED, "x")));
    }

    private static void diagnosticsAreBounded() {
        BetaRuntimeHealthService service = new BetaRuntimeHealthService(CLOCK);
        for (int i = 0; i < 100; i++) {
            service.diagnostic(null, BetaRuntimeDiagnosticCode.START_FAILED,
                    "failure-" + i, true);
            service.status(i % 2 == 0
                    ? BetaRuntimeHealthStatus.DEGRADED
                    : BetaRuntimeHealthStatus.FAILED);
        }
        BetaRuntimeHealthSnapshot snapshot = service.snapshot(true);
        assert snapshot.diagnostics().size() == BetaRuntimeHealthService.MAXIMUM_DIAGNOSTICS;
        assert snapshot.diagnostics().get(0).detail().equals("failure-36");
        assert snapshot.lastFailure().equals("failure-99");
        assert service.history().size() == BetaRuntimeHealthService.MAXIMUM_HEALTH_HISTORY;
        expectUnsupported(() -> service.history().clear());
    }

    private static void featureFlagsAndAudiencePreventStart() {
        List<String> events = new ArrayList<>();
        FakeModule flagged = new FakeModule(BetaRuntimeModuleId.EQUIPMENT, Set.of(),
                Set.of(FeatureKey.EQUIPMENT_V2), BetaMutationPolicy.READ_ONLY,
                true, Set.of(), events);
        BetaRuntime flagsOff = runtime(global(), disabledFlags(), List.of(flagged), Set.of());
        BetaRuntimeHealthSnapshot offHealth = flagsOff.start();
        assert flagged.startCalls == 0;
        assert offHealth.moduleStates().get(flagged.id()) == BetaRuntimeModuleState.DISABLED;

        EnumMap<FeatureKey, Boolean> enabled = new EnumMap<>(FeatureKey.class);
        enabled.put(FeatureKey.EQUIPMENT_V2, true);
        FakeModule audienceModule = new FakeModule(BetaRuntimeModuleId.EQUIPMENT, Set.of(),
                Set.of(FeatureKey.EQUIPMENT_V2), BetaMutationPolicy.READ_ONLY,
                true, Set.of(), events);
        BetaRuntime audienceOff = runtime(BetaActivationPolicy.defaults(),
                FeatureFlagSnapshot.of(enabled), List.of(audienceModule), Set.of());
        audienceOff.start();
        assert audienceModule.startCalls == 0;
        assert audienceOff.healthSnapshot().status() == BetaRuntimeHealthStatus.DISABLED;
    }

    private static void missingDependencyIsBlocked() {
        FakeModule equipment = module(BetaRuntimeModuleId.EQUIPMENT,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE), List.of());
        BetaRuntime runtime = runtime(global(), disabledFlags(), List.of(equipment), Set.of());
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert equipment.startCalls == 0;
        assert health.moduleStates().get(equipment.id()) == BetaRuntimeModuleState.BLOCKED;
        assert health.blockedDependencies().get(equipment.id())
                .equals(Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE));
    }

    private static void readOnlyFallbackAndMutationBlockAreExplicit() {
        FakeModule preview = new FakeModule(BetaRuntimeModuleId.MOB_EDITOR_V2, Set.of(),
                Set.of(), BetaMutationPolicy.STAGING_WRITE, true, Set.of(), List.of());
        FakeModule writer = new FakeModule(BetaRuntimeModuleId.ENHANCEMENT_REPAIR, Set.of(),
                Set.of(), BetaMutationPolicy.STAGING_WRITE, false, Set.of(), List.of());
        FakeModule readOnlyBase = new FakeModule(BetaRuntimeModuleId.COMBAT_ELEMENTS, Set.of(),
                Set.of(), BetaMutationPolicy.READ_ONLY, true, Set.of(), List.of());
        BetaRuntime runtime = runtime(global(), disabledFlags(),
                List.of(preview, writer, readOnlyBase), Set.of());
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert preview.lastContext.readOnlyMode();
        assert preview.startCalls == 1;
        assert readOnlyBase.lastContext.readOnlyMode();
        assert readOnlyBase.startCalls == 1;
        assert writer.startCalls == 0;
        assert health.moduleStates().get(writer.id()) == BetaRuntimeModuleState.BLOCKED;
    }

    private static void commandSurfaceIsReadOnlyBoundedAndPermissionChecked() {
        BetaRuntime runtime = runtime(BetaActivationPolicy.defaults(), disabledFlags(),
                List.of(), Set.of());
        runtime.start();
        BetaRuntimeCommandService commands = new BetaRuntimeCommandService(runtime);
        assert !commands.execute("status", false).success();
        assert commands.execute("status", true).success();
        assert commands.execute("modules", true).messages().size() <= 32;
        assert commands.execute("modules", true).messages().stream()
                .anyMatch(value -> value.equals("feature.equipment-v2=false"));
        assert commands.execute("policy", true).messages().stream()
                .anyMatch(value -> value.equals("audience=OFF"));
        assert commands.execute("health", true).messages().stream()
                .anyMatch(value -> value.contains("timestamp="));
        for (String forbidden : List.of("enable", "disable", "reload", "set",
                "migrate", "activate")) {
            BetaRuntimeCommandService.Response response = commands.execute(forbidden, true);
            assert !response.success();
            assert response.messages().stream().noneMatch(value -> value.equals(forbidden));
        }
        assert runtime.healthSnapshot().startCount() == 1;
        assert runtime.healthSnapshot().stopCount() == 0;
    }

    private static void pluginBoundaryHasNoNewChannelsListenersOrSchedulers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java"));
        assert occurrences(source, ".registerIncomingPluginChannel(") == 8;
        assert occurrences(source, ".registerOutgoingPluginChannel(") == 7;
        assert occurrences(source, ".registerEvents(") == 17;
        assert occurrences(source, ".runTask(") == 0;
        assert occurrences(source, ".runTaskLater(") == 0;
        assert occurrences(source, ".runTaskTimer(") == 0;
        assert !source.contains("projects:beta_");
        assert source.indexOf("initializeBetaRuntime();")
                < source.indexOf("playerManager = new PlayerManager();");
        assert source.indexOf("betaRuntime, BetaRuntime::close")
                < source.indexOf("monsterManager, MonsterManager::stop");

        String command = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/command/ProjectCommand.java"));
        assert command.indexOf("args[0].equalsIgnoreCase(\"beta\")")
                < command.indexOf("if (!(sender instanceof Player player))");
        assert command.contains("sender.hasPermission(\"projects.dev\")");
    }

    private static void configDefaultsRemainClosed() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assert config.contains("audience: OFF");
        assert config.contains("target-scope: TRAINING_DUMMY_ONLY");
        assert config.contains("mutation-policy: READ_ONLY");
        assert config.contains("fail-closed: true");
        assert config.contains("require-compatible-client: false");
        for (FeatureKey key : FeatureKey.values()) {
            assert config.contains("  " + key.id() + ": false");
        }
    }

    private static BetaRuntime runtime(
            BetaActivationPolicy policy,
            FeatureFlagSnapshot flags,
            List<FakeModule> modules,
            Set<String> infrastructure
    ) {
        return BetaRuntimeFactory.create(policy, flags, modules, infrastructure,
                CLOCK, (message, exception) -> { });
    }

    private static FakeModule module(
            BetaRuntimeModuleId id,
            Set<BetaRuntimeModuleId> dependencies,
            List<String> events
    ) {
        return new FakeModule(id, dependencies, Set.of(), BetaMutationPolicy.READ_ONLY,
                true, Set.of(), events);
    }

    private static BetaActivationPolicy global() {
        return new BetaActivationPolicy(BetaActivationAudience.GLOBAL,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.READ_ONLY, Set.of(), Set.of(), true, false);
    }

    private static FeatureFlagSnapshot disabledFlags() {
        return FeatureFlagSnapshot.allDisabled();
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static final class FakeModule implements BetaRuntimeModule {
        private final BetaRuntimeModuleDescriptor descriptor;
        private final List<String> events;
        private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
        private boolean failStart;
        private boolean throwStart;
        private boolean throwStop;
        private int prepareCalls;
        private int startCalls;
        private int stopCalls;
        private BetaRuntimeModuleContext lastContext;

        private FakeModule(
                BetaRuntimeModuleId id,
                Set<BetaRuntimeModuleId> dependencies,
                Set<FeatureKey> features,
                BetaMutationPolicy mutation,
                boolean readOnlyCapable,
                Set<String> infrastructure,
                List<String> events
        ) {
            descriptor = new BetaRuntimeModuleDescriptor(id, dependencies, features,
                    mutation, readOnlyCapable, infrastructure);
            this.events = events;
        }

        @Override
        public BetaRuntimeModuleId id() {
            return descriptor.id();
        }

        @Override
        public Set<BetaRuntimeModuleId> dependencies() {
            return descriptor.dependencies();
        }

        @Override
        public BetaRuntimeModuleDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
            prepareCalls++;
            lastContext = context;
            event("prepare:" + id());
            state = BetaRuntimeModuleState.READY;
            return BetaRuntimeModuleResult.ready();
        }

        @Override
        public BetaRuntimeModuleResult start() {
            startCalls++;
            event("start:" + id());
            if (throwStart) throw new IllegalStateException("start failed");
            if (failStart) {
                state = BetaRuntimeModuleState.FAILED;
                return BetaRuntimeModuleResult.failure("start refused");
            }
            state = BetaRuntimeModuleState.RUNNING;
            return BetaRuntimeModuleResult.running();
        }

        @Override
        public BetaRuntimeModuleResult stop() {
            stopCalls++;
            event("stop:" + id());
            if (throwStop) throw new IllegalStateException("stop failed");
            state = BetaRuntimeModuleState.STOPPED;
            return BetaRuntimeModuleResult.stopped();
        }

        @Override
        public BetaRuntimeModuleState state() {
            return state;
        }

        private void event(String value) {
            try {
                events.add(value);
            } catch (UnsupportedOperationException ignored) {
                // Some tests do not need an event sink.
            }
        }
    }
}
