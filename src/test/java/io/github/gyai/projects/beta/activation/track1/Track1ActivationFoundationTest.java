package io.github.gyai.projects.beta.activation.track1;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleResult;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.track1.bukkit.Track1ListenerRegistrar;
import io.github.gyai.projects.beta.activation.track1.command.Track1OperatorCommandContributor;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentScanEntry;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.beta.activation.track1.module.EquipmentRuntimeModule;
import io.github.gyai.projects.beta.activation.track1.module.PlayerPersistenceRuntimeModule;
import io.github.gyai.projects.beta.activation.track1.module.Track1RuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track1.player.PlayerProgressObservationStatus;
import io.github.gyai.projects.beta.activation.track1.player.PlayerProgressSaveObservation;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressFileStore;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressService;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressStore;
import io.github.gyai.projects.beta.activation.track1.spi.BetaOperatorSubject;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;
import io.github.gyai.projects.mod.UnknownModEntry;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import org.bukkit.event.Listener;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class Track1ActivationFoundationTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String WORLD = "beta_world";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    private Track1ActivationFoundationTest() { }

    public static void main(String[] args) throws Exception {
        readOnlyCreatesNoFiles();
        stagingWriteIsIsolatedAndReconnects();
        staleSaveAndDisableDrain();
        malformedSourceIsQuarantinedWithoutMutation();
        repositoryExceptionsFailSafe();
        comparisonDetectsLegacyDifferences();
        allowlistAndWorldDenials();
        equipmentInspectionIsReadOnly();
        unknownModsAreIsolatedAndNoUuidIsGenerated();
        unstartedReadOnlyInspectionIsSafeWithDefaultFeatures();
        moduleLifecycleAndProvider();
        disabledFlagRegistersNoListener();
        failedRegistrationCleansUp();
        operatorCommandsAreReadOnlyAndPermissioned();
        noBukkitReferencesAreRetained();
        centralRegistrationAndDefaultsRemainUntouched();
    }

    private static void readOnlyCreatesNoFiles() throws Exception {
        Path root = Files.createTempDirectory("projects-track1-readonly-");
        Path players = root.resolve("plugins/ProjectS/beta-staging/players");
        try {
            var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY),
                    new StagingPlayerProgressFileStore(players, Set.of()), CLOCK);
            service.start();
            assert service.onJoin(snapshot(2, 0), WORLD, true).status()
                    == PlayerProgressObservationStatus.STAGING_MISSING;
            PlayerProgressSaveObservation saved = service.onQuit(snapshot(2, 0), WORLD, true)
                    .toCompletableFuture().join();
            assert saved.status() == PlayerProgressSaveObservation.Status.READ_ONLY;
            service.close();
            assert !Files.exists(players) : "READ_ONLY created staging files";
        } finally { deleteTree(root); }
    }

    private static void stagingWriteIsIsolatedAndReconnects() throws Exception {
        Path root = Files.createTempDirectory("projects-track1-write-");
        Path players = root.resolve("plugins/ProjectS/beta-staging/players");
        try {
            var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.STAGING_WRITE),
                    new StagingPlayerProgressFileStore(players, Set.of()), CLOCK);
            service.start();
            service.onJoin(snapshot(4, 0), WORLD, true);
            PlayerProgressSaveObservation committed = service.onQuit(snapshot(4, 0), WORLD, true)
                    .toCompletableFuture().join();
            assert committed.status() == PlayerProgressSaveObservation.Status.COMMITTED;
            assert committed.path().orElseThrow().normalize().startsWith(players.normalize());
            assert Files.isRegularFile(players.resolve(PLAYER + ".yml"));
            assert !Files.exists(root.resolve("plugins/ProjectS/data/players"));
            assert service.onJoin(snapshot(4, 0), WORLD, true).status()
                    == PlayerProgressObservationStatus.OBSERVED_MATCH;
            assert service.activeSessions() == 1;
            service.onQuit(snapshot(4, 0), WORLD, true).toCompletableFuture().join();
            assert service.activeSessions() == 0;
            service.close();
        } finally { deleteTree(root); }
    }

    private static void staleSaveAndDisableDrain() {
        FakeStore stale = new FakeStore();
        stale.saveStatus = PlayerProgressSaveObservation.Status.STALE;
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.STAGING_WRITE), stale, CLOCK);
        service.start();
        service.onJoin(snapshot(3, 7), WORLD, true);
        PlayerProgressSaveObservation result = service.onQuit(snapshot(3, 1), WORLD, true)
                .toCompletableFuture().join();
        assert result.status() == PlayerProgressSaveObservation.Status.STALE;
        assert stale.saved.getFirst().revision() == 8 : "revision did not advance from observed record";

        FakeStore draining = new FakeStore();
        var drainingService = new StagingPlayerProgressService(
                policy(BetaMutationPolicy.STAGING_WRITE), draining, CLOCK);
        drainingService.start();
        drainingService.onJoin(snapshot(5, 0), WORLD, true);
        drainingService.close();
        assert draining.saved.size() == 1 : "disable did not final-save active session";
        assert drainingService.activeSessions() == 0;
        assert drainingService.observation(PLAYER).isEmpty();
        drainingService.close();
    }

    private static void malformedSourceIsQuarantinedWithoutMutation() {
        FakeStore store = new FakeStore();
        store.load = new StagingPlayerProgressStore.Load(
                StagingPlayerProgressStore.Load.Status.MALFORMED,
                Optional.empty(), "malformed fixture");
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY), store, CLOCK);
        service.start();
        var observed = service.onJoin(snapshot(1, 0), WORLD, true);
        assert observed.status() == PlayerProgressObservationStatus.QUARANTINED;
        assert store.saved.isEmpty();
        service.close();
    }

    private static void repositoryExceptionsFailSafe() {
        FakeStore store = new FakeStore();
        store.throwOnLoad = true;
        var service = new StagingPlayerProgressService(
                policy(BetaMutationPolicy.STAGING_WRITE), store, CLOCK);
        service.start();
        assert service.onJoin(snapshot(1, 0), WORLD, true).status()
                == PlayerProgressObservationStatus.QUARANTINED;
        store.throwOnLoad = false;
        service.onJoin(snapshot(1, 0), WORLD, true);
        store.throwOnSave = true;
        assert service.onQuit(snapshot(1, 0), WORLD, true).toCompletableFuture().join().status()
                == PlayerProgressSaveObservation.Status.FAILED;
        service.close();
    }

    private static void comparisonDetectsLegacyDifferences() {
        FakeStore store = new FakeStore();
        store.load = loaded(snapshot(9, 4));
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY), store, CLOCK);
        service.start();
        var mismatch = service.onJoin(snapshot(3, 1), WORLD, true);
        assert mismatch.status() == PlayerProgressObservationStatus.OBSERVED_MISMATCH;
        assert mismatch.differences().contains("level");
        service.close();
    }

    private static void allowlistAndWorldDenials() {
        FakeStore store = new FakeStore();
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY), store, CLOCK);
        service.start();
        PlayerProgressSnapshot stranger = new PlayerProgressBuilder(
                UUID.fromString("00000000-0000-0000-0000-000000000999"))
                .lastSavedAt(Instant.EPOCH).build();
        assert service.onJoin(stranger, WORLD, true).status()
                == PlayerProgressObservationStatus.POLICY_DENIED;
        assert service.onJoin(snapshot(1, 0), "production", true).status()
                == PlayerProgressObservationStatus.POLICY_DENIED;
        assert store.loads == 0;
        service.close();
    }

    private static void equipmentInspectionIsReadOnly() {
        EquipmentInspectionService service = new EquipmentInspectionService(CLOCK);
        service.start();
        FakePdc pdc = readablePdc();
        Map<String, Object> before = Map.copyOf(pdc.values);
        byte[] bytes = "immutable-pdc".getBytes(StandardCharsets.UTF_8);
        var snapshot = service.inspect(PLAYER, List.of(new EquipmentScanEntry(
                "weapon", pdc, Optional.empty(), List.of(), bytes)));
        assert snapshot.items().size() == 1;
        assert snapshot.readableLegacyItems() == 1;
        assert snapshot.validV1Items() == 1;
        assert pdc.values.equals(before) : "inspection mutated PDC source";
        assert snapshot.items().getFirst().projection().orElseThrow().instanceId().isEmpty();
        assert snapshot.items().getFirst().sourceFingerprint().length() == 64;
        service.close();
    }

    private static void unknownModsAreIsolatedAndNoUuidIsGenerated() {
        EquipmentInspectionService service = new EquipmentInspectionService(CLOCK);
        service.start();
        UnknownModEntry unknown = new UnknownModEntry(0, "future-mod", 7,
                "projects:unknown-mod", new byte[] {1, 2, 3});
        var result = service.inspect(PLAYER, List.of(new EquipmentScanEntry(
                "weapon", readablePdc(), Optional.empty(), List.of(unknown), new byte[] {7})));
        assert result.isolatedUnknownMods() == 1;
        assert result.items().getFirst().isolatedUnknownModIds().contains("projects:unknown-mod");
        assert !unknown.effectEnabled();
        assert result.items().getFirst().projection().orElseThrow().instanceId().isEmpty();
        service.close();
    }

    private static void unstartedReadOnlyInspectionIsSafeWithDefaultFeatures() {
        EquipmentInspectionService service = new EquipmentInspectionService(CLOCK);
        FakePdc pdc = readablePdc();
        Map<String, Object> before = Map.copyOf(pdc.values);
        UnknownModEntry unknown = new UnknownModEntry(0, "future-mod", 7,
                "projects:future-mod", new byte[] {1, 2, 3});
        assert !FeatureFlagSnapshot.allDisabled().isEnabled(FeatureKey.EQUIPMENT_V2);
        assert !FeatureFlagSnapshot.allDisabled().isEnabled(FeatureKey.MOD_SYSTEM);
        var snapshot = service.inspectReadOnly(PLAYER, List.of(new EquipmentScanEntry(
                "weapon", pdc, Optional.of(StagingEconomyCatalog.previewBlade(EquipmentTier.T1)),
                List.of(unknown), new byte[] {9, 8, 7})));
        assert !service.running();
        assert service.latest(PLAYER).isEmpty() : "read-only inspection retained a snapshot";
        assert snapshot.items().getFirst().projection().orElseThrow().itemId()
                .equals(StagingEconomyCatalog.TEST_BLADE_T1);
        assert snapshot.items().getFirst().isolatedUnknownModIds()
                .equals(List.of("projects:future-mod"));
        assert !unknown.effectEnabled();
        assert pdc.values.equals(before) : "default-off inspection mutated PDC";
        service.close();
    }

    private static void moduleLifecycleAndProvider() {
        FakeRegistrar registrar = new FakeRegistrar(false);
        StagingPlayerProgressService progress = new StagingPlayerProgressService(
                policy(BetaMutationPolicy.READ_ONLY), new FakeStore(), CLOCK);
        EquipmentInspectionService equipment = new EquipmentInspectionService(CLOCK);
        Listener listener = new Listener() { };
        var playerModule = new PlayerPersistenceRuntimeModule(progress, registrar, listener);
        var equipmentModule = new EquipmentRuntimeModule(equipment);
        var provider = new Track1RuntimeModuleProvider(playerModule, equipmentModule);
        assert provider.modules().size() == 2;
        BetaRuntimeModuleContext context = context(true);
        assert playerModule.prepare(context).state() == BetaRuntimeModuleState.READY;
        assert playerModule.start().state() == BetaRuntimeModuleState.RUNNING;
        assert playerModule.start().state() == BetaRuntimeModuleState.RUNNING;
        assert registrar.registered == 1;
        assert equipmentModule.prepare(context).state() == BetaRuntimeModuleState.READY;
        assert equipmentModule.start().state() == BetaRuntimeModuleState.RUNNING;
        assert playerModule.stop().state() == BetaRuntimeModuleState.STOPPED;
        assert playerModule.stop().state() == BetaRuntimeModuleState.STOPPED;
        assert registrar.unregistered == 1;
        assert equipmentModule.stop().state() == BetaRuntimeModuleState.STOPPED;
    }

    private static void disabledFlagRegistersNoListener() {
        FakeRegistrar registrar = new FakeRegistrar(false);
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY),
                new FakeStore(), CLOCK);
        var module = new PlayerPersistenceRuntimeModule(service, registrar, new Listener() { });
        BetaRuntimeModuleResult result = module.prepare(context(false));
        assert !result.success();
        assert result.state() == BetaRuntimeModuleState.DISABLED;
        assert registrar.registered == 0;
        service.close();
    }

    private static void failedRegistrationCleansUp() {
        FakeRegistrar registrar = new FakeRegistrar(true);
        var service = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY),
                new FakeStore(), CLOCK);
        var module = new PlayerPersistenceRuntimeModule(service, registrar, new Listener() { });
        assert module.prepare(context(true)).success();
        assert !module.start().success();
        assert module.state() == BetaRuntimeModuleState.FAILED;
        assert !service.running();
    }

    private static void operatorCommandsAreReadOnlyAndPermissioned() {
        var progress = new StagingPlayerProgressService(policy(BetaMutationPolicy.READ_ONLY),
                new FakeStore(), CLOCK);
        var equipment = new EquipmentInspectionService(CLOCK);
        progress.start();
        equipment.start();
        progress.onJoin(snapshot(1, 0), WORLD, true);
        var commands = new Track1OperatorCommandContributor(
                policy(BetaMutationPolicy.READ_ONLY), progress, equipment,
                ignored -> List.of(new EquipmentScanEntry("weapon", readablePdc(),
                        Optional.empty(), List.of(), new byte[] {1})));
        BetaOperatorSubject subject = new BetaOperatorSubject(PLAYER, WORLD, true);
        assert !commands.execute(false, subject, List.of("staging", "player", "status")).accepted();
        assert commands.execute(true, subject, List.of("staging", "player", "status")).accepted();
        assert commands.execute(true, subject, List.of("player", "status")).accepted();
        assert commands.execute(true, subject, List.of("staging", "player", "snapshot")).accepted();
        assert commands.execute(true, subject, List.of("staging", "equipment", "inspect")).accepted();
        assert !commands.execute(true, subject, List.of("staging", "player", "enable")).accepted();
        assert !commands.execute(true,
                new BetaOperatorSubject(PLAYER, "production", true),
                List.of("staging", "player", "status")).accepted();
        progress.close();
        equipment.close();
    }

    private static void noBukkitReferencesAreRetained() {
        for (Class<?> type : List.of(StagingPlayerProgressService.class,
                EquipmentInspectionService.class,
                io.github.gyai.projects.beta.activation.track1.player.PlayerProgressObservation.class,
                io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionSnapshot.class)) {
            for (var field : type.getDeclaredFields()) {
                String name = field.getType().getName();
                assert !name.startsWith("org.bukkit.entity.") : type + " retains " + name;
            }
        }
    }

    private static void centralRegistrationAndDefaultsRemainUntouched() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java"));
        String command = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/command/ProjectCommand.java"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assert !plugin.contains("Track1RuntimeModuleProvider");
        assert !plugin.contains("BukkitTrack1PlayerListener");
        assert !command.contains("Track1OperatorCommandContributor");
        assert !config.contains("track-1");
        assert !config.contains("beta-staging");
        for (FeatureKey key : FeatureKey.values()) {
            assert config.contains(key.id() + ": false") : "feature enabled or missing: " + key;
        }
        assert !Files.readString(Path.of("src/main/resources/plugin.yml"))
                .contains("beta staging player");
    }

    private static BetaRuntimeModuleContext context(boolean enabled) {
        EnumMap<FeatureKey, Boolean> flags = new EnumMap<>(FeatureKey.class);
        if (enabled) {
            flags.put(FeatureKey.PLAYER_PERSISTENCE, true);
            flags.put(FeatureKey.EQUIPMENT_V2, true);
        }
        return new BetaRuntimeModuleContext(policy(BetaMutationPolicy.READ_ONLY),
                FeatureFlagSnapshot.of(flags),
                Set.of("track1.bukkit-listener", "track1.staging-player-store",
                        "track1.inventory-reader"), CLOCK, true);
    }

    private static BetaActivationPolicy policy(BetaMutationPolicy mutation) {
        return new BetaActivationPolicy(BetaActivationAudience.ALLOWLIST,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY, mutation,
                Set.of(PLAYER), Set.of(WORLD), true, false);
    }

    private static PlayerProgressSnapshot snapshot(int level, long revision) {
        return new PlayerProgressBuilder(PLAYER)
                .level(level)
                .persistentResources(Map.of("projects:fighting-spirit", 5L))
                .revision(revision)
                .lastSavedAt(Instant.EPOCH)
                .build();
    }

    private static StagingPlayerProgressStore.Load loaded(PlayerProgressSnapshot snapshot) {
        return new StagingPlayerProgressStore.Load(
                StagingPlayerProgressStore.Load.Status.LOADED,
                Optional.of(snapshot), "");
    }

    private static FakePdc readablePdc() {
        FakePdc source = new FakePdc();
        source.values.put("item_id", "starter_sword");
        source.values.put("enhancement_level", 0);
        source.values.put("weapon_broken", (byte) 0);
        source.values.put("weapon_attack_power_bonus", 2.0D);
        return source;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class FakeStore implements StagingPlayerProgressStore {
        private Load load = new Load(Load.Status.MISSING, Optional.empty(), "");
        private PlayerProgressSaveObservation.Status saveStatus =
                PlayerProgressSaveObservation.Status.COMMITTED;
        private final List<PlayerProgressSnapshot> saved = new ArrayList<>();
        private int loads;
        private boolean closed;
        private boolean throwOnLoad;
        private boolean throwOnSave;
        @Override public Load load(UUID playerId) {
            loads++;
            if (throwOnLoad) throw new IllegalStateException("fixture load failure");
            return load;
        }
        @Override public CompletionStage<PlayerProgressSaveObservation> save(PlayerProgressSnapshot snapshot) {
            if (throwOnSave) throw new IllegalStateException("fixture save failure");
            saved.add(snapshot);
            return CompletableFuture.completedFuture(new PlayerProgressSaveObservation(
                    snapshot.playerId(), saveStatus, snapshot.revision(), Optional.empty(), "fixture"));
        }
        @Override public void close() { closed = true; }
    }

    private static final class FakeRegistrar implements Track1ListenerRegistrar {
        private final boolean fail;
        private int registered;
        private int unregistered;
        private FakeRegistrar(boolean fail) { this.fail = fail; }
        @Override public void register(String key, Listener listener) {
            if (fail) throw new IllegalStateException("fixture registration failure");
            registered++;
        }
        @Override public void unregister(String key, Listener listener) { unregistered++; }
    }

    private static final class FakePdc implements LegacyPdcSource {
        private final Map<String, Object> values = new HashMap<>();
        @Override public String materialIdentity() { return "minecraft:iron_sword"; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Optional<String> stringValue(String key) {
            return values.get(key) instanceof String value ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<Integer> integerValue(String key) {
            return values.get(key) instanceof Integer value ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<Byte> byteValue(String key) {
            return values.get(key) instanceof Byte value ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<Double> doubleValue(String key) {
            return values.get(key) instanceof Double value ? Optional.of(value) : Optional.empty();
        }
    }
}
