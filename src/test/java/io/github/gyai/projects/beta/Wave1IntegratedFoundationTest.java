package io.github.gyai.projects.beta;

import io.github.gyai.projects.ProjectSPlugin;
import io.github.gyai.projects.combat.element.ElementAttackSchool;
import io.github.gyai.projects.combat.element.ElementTargetCategory;
import io.github.gyai.projects.combat.element.fire.FireElementEngine;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.combat.element.lightning.DisabledLightningElementEngine;
import io.github.gyai.projects.combat.element.lightning.LightningElementEngine;
import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.BindingPolicy;
import io.github.gyai.projects.equipment.EquipmentCategory;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentQuality;
import io.github.gyai.projects.equipment.EquipmentRarity;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.item.compatibility.LegacyItemCompatibilityReader;
import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;
import io.github.gyai.projects.item.compatibility.LegacyPdcSource;
import io.github.gyai.projects.mod.UnknownModEntry;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.persistence.player.FilePlayerProgressRepository;
import io.github.gyai.projects.persistence.player.PlayerProgressLoadStatus;
import io.github.gyai.projects.persistence.player.PlayerProgressSaveStatus;
import io.github.gyai.projects.player.progress.PlayerProgressBuilder;
import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionEngine;
import io.github.gyai.projects.transaction.TransactionParticipant;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cross-track smoke test for the disabled, Bukkit-free Wave 1 foundations. */
public final class Wave1IntegratedFoundationTest {
    private static final UUID PLAYER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    private Wave1IntegratedFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        schemasAndFlagsRemainClosed();
        PlayerProgressSnapshot player = trackARepositoryRoundTrip();
        trackBEquipmentAndCompatibilityBoundaries();
        trackCElementBoundaries();
        trackDTransactionBoundaries(player);
        publicDomainBoundariesStayBukkitFree();
        gameplayEntrypointRemainsDisconnected();
    }

    private static void schemasAndFlagsRemainClosed() {
        assert SchemaVersions.PLAYER_DATA == 1;
        assert SchemaVersions.EQUIPMENT_ITEM == 1;
        assert SchemaVersions.MOD_DEFINITION == 1;
        assert SchemaVersions.RECIPE_DEFINITION == 1;
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.MOB_DEFINITION == 2;
        assert SchemaVersions.supportedReadVersions(SchemaId.MOB_DEFINITION)
                .equals(Set.of(1, 2));
        assert SchemaVersions.CLIENT_PROTOCOL == 1;
        assert SchemaVersions.currentVersion(SchemaId.CLIENT_PROTOCOL)
                .orElseThrow() == 1;

        FeatureFlagService flags = new FeatureFlagService();
        for (FeatureKey key : List.of(
                FeatureKey.PLAYER_PERSISTENCE,
                FeatureKey.EQUIPMENT_V2,
                FeatureKey.MOD_SYSTEM,
                FeatureKey.FIRE_SYSTEM,
                FeatureKey.ICE_SYSTEM,
                FeatureKey.LIGHTNING_SYSTEM,
                FeatureKey.GATHERING,
                FeatureKey.REFINING,
                FeatureKey.CRAFTING)) {
            assert !flags.isEnabled(key) : key;
        }
    }

    private static PlayerProgressSnapshot trackARepositoryRoundTrip()
            throws Exception {
        Path root = Files.createTempDirectory("projects-wave1-integration-");
        FilePlayerProgressRepository repository =
                new FilePlayerProgressRepository(root);
        PlayerProgressSnapshot snapshot = new PlayerProgressBuilder(PLAYER_ID)
                .level(12)
                .experience(345)
                .revision(7)
                .lastSavedAt(CLOCK.instant())
                .build();
        try {
            assert repository.save(
                    new PlayerProgressRecordV1(snapshot), UUID.randomUUID())
                    .get().status() == PlayerProgressSaveStatus.COMMITTED;
            var loaded = repository.load(PLAYER_ID);
            assert loaded.status() == PlayerProgressLoadStatus.LOADED;
            PlayerProgressSnapshot restored =
                    loaded.loadedRecord().orElseThrow().snapshot();
            assert restored.playerId().equals(PLAYER_ID);
            assert restored.revision() == 7;
            assert restored.level() == 12;
            return restored;
        } finally {
            repository.close();
            assert !repository.acceptingWrites();
            try (var paths = Files.walk(root)) {
                assert paths.noneMatch(path -> path.getFileName().toString()
                        .endsWith(".tmp"));
            }
            deleteRecursively(root);
        }
    }

    private static void trackBEquipmentAndCompatibilityBoundaries() {
        UnknownModEntry unknown = new UnknownModEntry(
                0, "mod-definition", 99,
                "future:unrecognized", new byte[]{1, 2, 3});
        EquipmentItemV1 equipment = new EquipmentItemV1(
                SchemaVersions.EQUIPMENT_ITEM,
                "starter_sword",
                EquipmentCategory.WEAPON,
                EquipmentSlot.WEAPON,
                EquipmentTier.T1,
                12,
                EquipmentRarity.COMMON,
                EquipmentQuality.UNSPECIFIED,
                List.of(new BaseStatRoll("projects:physical-attack", 10)),
                List.of(new EquipmentModSlot(0, Optional.of(unknown))),
                Optional.empty(),
                0,
                false,
                BindingPolicy.UNBOUND,
                TradePolicy.DENY_ALL,
                Optional.empty());
        assert equipment.schemaVersion() == 1;
        assert equipment.tier() == EquipmentTier.T1;
        assert equipment.itemLevel() == 12;
        assert equipment.modSlots().size()
                == equipment.rarity().modCapacity();
        assert !unknown.effectEnabled();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LegacyItemCompatibilityReader.ITEM_ID_KEY, "starter_sword");
        values.put(LegacyItemCompatibilityReader.ENHANCEMENT_LEVEL_KEY, 3);
        values.put(LegacyItemCompatibilityReader.BROKEN_KEY, (byte) 0);
        values.put(LegacyItemCompatibilityReader.ATTACK_POWER_BONUS_KEY, 4.5);
        Map<String, Object> before = Map.copyOf(values);
        LegacyItemReadResult result = new LegacyItemCompatibilityReader()
                .read(new MapLegacyPdcSource("IRON_SWORD", values));
        assert result.status() == LegacyItemReadResult.Status.READABLE;
        assert result.view().orElseThrow().itemId().equals("starter_sword");
        assert values.equals(before) : "legacy compatibility read mutated data";
    }

    private static void trackCElementBoundaries() {
        UUID contributor = UUID.fromString(
                "20000000-0000-0000-0000-000000000001");
        FireElementEngine fire = new FireElementEngine(
                FireElementEngine.Policy.waveOne(4, 4));
        FireElementEngine.TargetProfile fireTarget =
                new FireElementEngine.TargetProfile(
                        ElementTargetCategory.NORMAL, 25);
        var first = fire.apply(new FireElementEngine.Hit(
                "wave1-fire", fireTarget, contributor,
                ElementAttackSchool.PHYSICAL, 225, 100, 0));
        assert first.state().stacks() == 9;
        assert first.detonation().isEmpty();
        var tenth = fire.apply(new FireElementEngine.Hit(
                "wave1-fire", fireTarget, contributor,
                ElementAttackSchool.PHYSICAL, 25, 100, 1));
        assert tenth.detonation().isPresent();
        assert first.state().stacks() + 1 - tenth.state().stacks() == 7;
        assert tenth.state().stacks() == 3;

        IceElementEngine ice = new IceElementEngine(
                IceElementEngine.Policy.waveOne(4, 4));
        IceElementEngine.TargetProfile iceTarget =
                new IceElementEngine.TargetProfile(
                        ElementTargetCategory.NORMAL, 100, .25, .75);
        var frozen = ice.apply(iceHit(
                "wave1-ice", iceTarget, contributor, 100, true, 0));
        assert frozen.frozeNow();
        assert frozen.shatter().isEmpty();
        var shattered = ice.apply(iceHit(
                "wave1-ice", iceTarget, contributor, 0, true, 1));
        assert shattered.shatter().isPresent();
        var after = ice.apply(iceHit(
                "wave1-ice", iceTarget, contributor, 0, true, 2));
        assert after.shatter().isEmpty();

        LightningElementEngine lightning =
                new DisabledLightningElementEngine();
        var disabled = lightning.evaluate(
                new LightningElementEngine.Input("wave1"));
        assert !lightning.enabled();
        assert !disabled.applied();
        assert disabled.reason().equals("LIGHTNING_SYSTEM_DISABLED");
    }

    private static IceElementEngine.Hit iceHit(
            String target,
            IceElementEngine.TargetProfile profile,
            UUID contributor,
            double cold,
            boolean shatter,
            long time
    ) {
        return new IceElementEngine.Hit(
                target, profile, contributor,
                ElementAttackSchool.PHYSICAL,
                IceElementEngine.DamageOrigin.SKILL_DIRECT,
                cold, 80, shatter, 100, time);
    }

    private static void trackDTransactionBoundaries(
            PlayerProgressSnapshot player
    ) {
        TransactionEngine engine = new TransactionEngine(2, 8, CLOCK);
        try {
            TransactionRequest successRequest = transactionRequest(
                    UUID.fromString("30000000-0000-0000-0000-000000000001"),
                    player.playerId(), 1);
            assert successRequest.playerId().equals(player.playerId());
            assert !"starter_sword".equals(successRequest.recipeId());
            RecordingParticipant success = new RecordingParticipant(null);
            TransactionAuditResult committed = engine.execute(
                    successRequest, success);
            assert committed.outcome()
                    == TransactionAuditResult.Outcome.COMMITTED;
            for (TransactionStage stage : TransactionStage.values()) {
                assert success.count(stage) == 1 : stage;
            }
            assert success.rollbackCount == 0;

            RecordingParticipant failure = new RecordingParticipant(
                    TransactionStage.PRODUCE);
            TransactionAuditResult rolledBack = engine.execute(
                    transactionRequest(
                            UUID.fromString(
                                    "30000000-0000-0000-0000-000000000002"),
                            player.playerId(), 2),
                    failure);
            assert rolledBack.outcome()
                    == TransactionAuditResult.Outcome.ROLLED_BACK;
            assert failure.rollbackCount == 1;
            assert failure.count(TransactionStage.PRODUCE) == 1;
            assert failure.count(TransactionStage.COMMIT) == 0;
        } finally {
            engine.close();
        }
    }

    private static TransactionRequest transactionRequest(
            UUID requestId,
            UUID playerId,
            long revision
    ) {
        return new TransactionRequest(
                requestId,
                playerId,
                "projects:operation/craft",
                "projects:craft/starter-sword",
                revision,
                1,
                List.of(new TransactionRequest.InputRevision(
                        "projects:item/iron-ingot", revision)));
    }

    private static void publicDomainBoundariesStayBukkitFree() {
        assertNoBukkitPublicApi(
                PlayerProgressSnapshot.class,
                EquipmentItemV1.class,
                UnknownModEntry.class,
                FireElementEngine.class,
                IceElementEngine.class,
                LightningElementEngine.class,
                TransactionRequest.class,
                TransactionParticipant.class,
                TransactionAuditResult.class);
    }

    private static void assertNoBukkitPublicApi(Class<?>... types) {
        for (Class<?> type : types) {
            for (Field field : type.getFields()) {
                assertNotBukkit(type, field.getGenericType());
            }
            for (Method method : type.getMethods()) {
                assertNotBukkit(type, method.getGenericReturnType());
                for (Type parameter : method.getGenericParameterTypes()) {
                    assertNotBukkit(type, parameter);
                }
            }
            for (var constructor : type.getConstructors()) {
                assertExecutableTypes(type, constructor);
            }
        }
    }

    private static void assertExecutableTypes(Class<?> owner, Executable executable) {
        for (Type parameter : executable.getGenericParameterTypes()) {
            assertNotBukkit(owner, parameter);
        }
    }

    private static void assertNotBukkit(Class<?> owner, Type type) {
        assert !type.getTypeName().contains("org.bukkit.")
                : owner.getName() + " exposes " + type.getTypeName();
    }

    private static void gameplayEntrypointRemainsDisconnected() {
        for (Field field : ProjectSPlugin.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            assert type != FeatureFlagService.class;
            assert !isWaveOneFoundation(type) : field;
        }
        for (Method method : ProjectSPlugin.class.getDeclaredMethods()) {
            assert !isWaveOneFoundation(method.getReturnType()) : method;
            for (Class<?> parameter : method.getParameterTypes()) {
                assert !isWaveOneFoundation(parameter) : method;
            }
        }
    }

    private static boolean isWaveOneFoundation(Class<?> type) {
        String name = type.getName();
        return name.startsWith("io.github.gyai.projects.player.progress.")
                || name.startsWith("io.github.gyai.projects.persistence.player.")
                || name.startsWith("io.github.gyai.projects.equipment.")
                || name.startsWith("io.github.gyai.projects.mod.")
                || name.startsWith("io.github.gyai.projects.combat.element.")
                || name.startsWith("io.github.gyai.projects.transaction.")
                || name.startsWith("io.github.gyai.projects.gathering.")
                || name.startsWith("io.github.gyai.projects.crafting.");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private record MapLegacyPdcSource(
            String materialIdentity,
            Map<String, Object> values
    ) implements LegacyPdcSource {
        private MapLegacyPdcSource {
            values = java.util.Collections.unmodifiableMap(values);
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Optional<String> stringValue(String key) {
            return typed(key, String.class);
        }

        @Override
        public Optional<Integer> integerValue(String key) {
            return typed(key, Integer.class);
        }

        @Override
        public Optional<Byte> byteValue(String key) {
            return typed(key, Byte.class);
        }

        @Override
        public Optional<Double> doubleValue(String key) {
            return typed(key, Double.class);
        }

        private <T> Optional<T> typed(String key, Class<T> type) {
            Object value = values.get(key);
            return type.isInstance(value)
                    ? Optional.of(type.cast(value)) : Optional.empty();
        }
    }

    private static final class RecordingParticipant
            implements TransactionParticipant {
        private final List<TransactionStage> calls = new ArrayList<>();
        private final Map<UUID, TransactionAuditResult> terminals =
                new LinkedHashMap<>();
        private final TransactionStage failureStage;
        private int rollbackCount;

        private RecordingParticipant(TransactionStage failureStage) {
            this.failureStage = failureStage;
        }

        @Override
        public Optional<TransactionAuditResult> findTerminal(
                TransactionRequest request
        ) {
            return Optional.ofNullable(terminals.get(request.requestId()));
        }

        @Override
        public Validation validate(TransactionRequest request) {
            stage(TransactionStage.VALIDATE);
            return Validation.allow(
                    InventoryCapacityProposal.reservedInventory(1));
        }

        @Override
        public ReservationToken reserve(
                TransactionRequest request,
                InventoryCapacityProposal capacityProposal
        ) {
            stage(TransactionStage.RESERVE);
            return new ReservationToken("wave1-" + request.requestId());
        }

        @Override
        public void consume(
                TransactionRequest request,
                ReservationToken token
        ) {
            stage(TransactionStage.CONSUME);
        }

        @Override
        public OutputProposal produce(
                TransactionRequest request,
                ReservationToken token
        ) {
            stage(TransactionStage.PRODUCE);
            return new OutputProposal("projects:starter-sword", 1, true);
        }

        @Override
        public void persist(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output
        ) {
            stage(TransactionStage.PERSIST);
        }

        @Override
        public TransactionAuditResult commit(
                TransactionRequest request,
                ReservationToken token,
                OutputProposal output,
                TransactionAuditResult proposedCommittedResult
        ) {
            stage(TransactionStage.COMMIT);
            terminals.put(request.requestId(), proposedCommittedResult);
            return proposedCommittedResult;
        }

        @Override
        public void recordTerminal(TransactionAuditResult terminalResult) {
            terminals.putIfAbsent(terminalResult.requestId(), terminalResult);
        }

        @Override
        public void rollback(
                TransactionRequest request,
                ReservationToken token,
                TransactionStage lastCompletedStage,
                OutputProposal output
        ) {
            rollbackCount++;
        }

        private long count(TransactionStage stage) {
            return calls.stream().filter(stage::equals).count();
        }

        private void stage(TransactionStage stage) {
            calls.add(stage);
            if (failureStage == stage) {
                throw new IllegalStateException("injected-" + stage);
            }
        }
    }
}
