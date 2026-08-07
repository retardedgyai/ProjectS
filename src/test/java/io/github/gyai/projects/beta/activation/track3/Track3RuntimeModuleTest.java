package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.BetaOperatorContributorRegistry;
import io.github.gyai.projects.beta.activation.BetaRuntimeHealthSnapshot;
import io.github.gyai.projects.beta.activation.BetaRuntimeHealthStatus;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class Track3RuntimeModuleTest {
    private Track3RuntimeModuleTest() {
    }

    public static void main(String[] args) throws Exception {
        providerPublishesTwoUnregisteredModules();
        lifecycleAndFeatureGatesAreFailClosedAndIdempotent();
        contributorIsPermissionBoundAndUnregistered();
        uiCanOpenReadOnlyWhileWritesRemainRejected();
        registryRoutesOnlyReadOnlyUiWhenEconomyIsNotRunning();
        contributorInspectionIsBoundedWithoutDroppingNormalItemSummary();
        publicDomainPortsContainNoBukkitTypes();
        repositoryDefaultsAndProductionPathsRemainUntouched();
    }

    private static void providerPublishesTwoUnregisteredModules() throws Exception {
        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Clock.systemUTC())) {
            assert provider.modules().size() == 2;
            assert provider.modules().get(0).id() == BetaRuntimeModuleId.GATHERING_CRAFTING;
            assert provider.modules().get(1).id() == BetaRuntimeModuleId.ENHANCEMENT_REPAIR;
            assert provider.modules().get(1).dependencies()
                    .equals(Set.of(BetaRuntimeModuleId.GATHERING_CRAFTING));
            assert provider.track3Modules().get(0).descriptor().activationFeatures()
                    .equals(Set.of(FeatureKey.GATHERING, FeatureKey.REFINING,
                            FeatureKey.CRAFTING));
            assert provider.track3Modules().get(1).descriptor().activationFeatures()
                    .equals(Set.of(FeatureKey.TIER_PROMOTION, FeatureKey.ENHANCEMENT_V2,
                            FeatureKey.REPAIR_V2));
        }
        String factory = read("src/main/java/io/github/gyai/projects/beta/activation/BetaRuntimeFactory.java");
        assert !factory.contains("Track3RuntimeModuleProvider");
        assert !factory.contains("track3");
    }

    private static void lifecycleAndFeatureGatesAreFailClosedAndIdempotent() {
        UUID player = uuid(1);
        BetaActivationPolicy policy = new BetaActivationPolicy(
                BetaActivationAudience.ALLOWLIST,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.STAGING_WRITE,
                Set.of(player), Set.of("staging_world"), true, false);
        Set<String> infrastructure = Set.of(
                "track3.staging-inventory", "track3.staging-transaction-journal");

        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Track3TestFixtures.CLOCK)) {
            Track3RuntimeModule gathering = provider.track3Modules().get(0);
            assert !gathering.prepare(new BetaRuntimeModuleContext(
                    policy, FeatureFlagSnapshot.allDisabled(), infrastructure,
                    Track3TestFixtures.CLOCK, false)).success();
            assert gathering.state() == BetaRuntimeModuleState.FAILED;
            assert provider.service().status(player).resources().isEmpty();
        }

        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Track3TestFixtures.CLOCK)) {
            FeatureFlagSnapshot enabled = enabledFlags();
            BetaRuntimeModuleContext context = new BetaRuntimeModuleContext(
                    policy, enabled, infrastructure, Track3TestFixtures.CLOCK, false);
            Track3RuntimeModule gathering = provider.track3Modules().get(0);
            Track3RuntimeModule enhancement = provider.track3Modules().get(1);
            assert gathering.prepare(context).success();
            assert gathering.prepare(context).success();
            assert gathering.start().success();
            assert gathering.start().success();
            assert gathering.health().startCount() == 1;
            assert enhancement.prepare(context).success();
            assert enhancement.start().success();
            StagingOperationAccess access = Track3TestFixtures.access(player);
            provider.service().selectEnhancementOutcome(access, EnhancementOutcome.BROKEN);
            assert enhancement.stop().success();
            assert enhancement.stop().success();
            assert enhancement.health().stopCount() == 1;
            assert gathering.stop().success();
            assert gathering.state() == BetaRuntimeModuleState.STOPPED;
        }
    }

    private static void contributorIsPermissionBoundAndUnregistered() {
        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Track3TestFixtures.CLOCK)) {
            StagingEconomyOperatorContributor contributor =
                    new StagingEconomyOperatorContributor(provider.service());
            assert contributor.commandPaths().size() == 11;
            assert contributor.commandPaths().contains(
                    "/projects beta staging economy enhance");
            UUID player = uuid(2);
            StagingOperationAccess allowed = Track3TestFixtures.access(player);
            StagingOperationAccess denied = new StagingOperationAccess(
                    player, allowed.worldName(), false, allowed.activationPolicy());
            assert !contributor.execute(denied, List.of("status")).success();
            assert contributor.execute(allowed, List.of("status")).success();
        }
    }

    private static void uiCanOpenReadOnlyWhileWritesRemainRejected() {
        UUID player = uuid(3);
        AtomicReference<UUID> opened = new AtomicReference<>();
        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Track3TestFixtures.CLOCK)) {
            StagingEconomyOperatorContributor contributor = new StagingEconomyOperatorContributor(
                    provider.service(), id -> {
                        opened.set(id);
                        return true;
                    });
            StagingOperationAccess readOnly = new StagingOperationAccess(
                    player, "staging_world", true, BetaActivationPolicy.defaults());
            assert contributor.execute(readOnly, List.of("ui")).success();
            assert player.equals(opened.get()) : "read-only UI did not resolve the command player";
            assert !contributor.execute(readOnly, List.of(
                    "give", StagingEconomyCatalog.IRON_ORE, "1")).success()
                    : "read-only UI access gained a write path";
        }
    }

    /** Exercises the production-shaped registry -> contributor path, not the contributor alone. */
    private static void registryRoutesOnlyReadOnlyUiWhenEconomyIsNotRunning() {
        UUID player = uuid(31);
        AtomicReference<UUID> opened = new AtomicReference<>();
        try (Track3RuntimeModuleProvider provider =
                     Track3RuntimeModuleProvider.unregisteredStaging(Track3TestFixtures.CLOCK)) {
            StagingEconomyOperatorContributor contributor = new StagingEconomyOperatorContributor(
                    provider.service(), id -> {
                        opened.set(id);
                        return true;
                    });
            BetaOperatorContributorRegistry registry = new BetaOperatorContributorRegistry(List.of(
                    new BetaOperatorContributorRegistry.Entry("economy",
                            BetaRuntimeModuleId.GATHERING_CRAFTING, (context, args) -> {
                        var result = contributor.execute(new StagingOperationAccess(
                                context.actorId(), context.worldName(), context.projectsDev(),
                                BetaActivationPolicy.defaults()), args);
                        return new BetaOperatorContributorRegistry.Result(result.success(),
                                List.of(result.message()));
                    })));
            var health = new BetaRuntimeHealthSnapshot(Instant.EPOCH,
                    BetaRuntimeHealthStatus.DISABLED,
                    java.util.Map.of(BetaRuntimeModuleId.GATHERING_CRAFTING,
                            BetaRuntimeModuleState.DISABLED), java.util.Map.of(), List.of(),
                    0, 0, "", true);
            var context = new BetaOperatorContributorRegistry.Context(
                    player, "staging_world", true, false);
            assert registry.execute(List.of("staging", "economy", "ui"), health, context).success();
            assert player.equals(opened.get()) : "registry preempted read-only UI";
            assert !registry.execute(List.of("staging", "economy", "status"), health, context).success();
            assert !registry.execute(List.of("staging", "economy", "give",
                    StagingEconomyCatalog.IRON_ORE, "1"), health, context).success();
            assert !registry.execute(List.of("staging", "economy", "ui", "extra"),
                    health, context).success();
        }
    }

    private static void contributorInspectionIsBoundedWithoutDroppingNormalItemSummary() {
        UUID player = uuid(4);
        try (Track3TestFixtures.Fixture fixture = Track3TestFixtures.fixture(4)) {
            StagingOperationAccess access = Track3TestFixtures.access(player);
            fixture.inventory().seedResource(player, StagingEconomyCatalog.IRON_INGOT, 3);
            assert fixture.service().execute(StagingEconomyOperationPort.OperationRequest.action(
                    uuid(40), access, StagingEconomyOperationPort.OperationKind.CRAFT)).status()
                    == StagingEconomyOperationPort.Status.COMMITTED;
            StagingEconomyOperatorContributor contributor = new StagingEconomyOperatorContributor(
                    fixture.service(), ignored -> true);
            String message = contributor.execute(access, List.of("inspect")).message();
            assert message.length() <= 256;
            for (String field : List.of("ID=", "UUID=", "Tier=", "ILv=", "Rarity=", "Quality=",
                    "Category=", "Slot=", "Enhancement=", "Broken=", "Binding=", "Trade=",
                    "MOD slots=")) {
                assert message.contains(field) : "bounded inspection omitted " + field;
            }
        }
    }

    private static void publicDomainPortsContainNoBukkitTypes() {
        for (Class<?> type : List.of(
                StagingEconomyCatalog.class,
                StagingEquipmentCodec.class,
                StagingEquipmentDocument.class,
                StagingInventoryPort.class,
                StagingEconomyOperationPort.class,
                StagingItemDeliveryPort.class,
                StagingEconomyService.class,
                Track3RuntimeModule.class,
                Track3RuntimeModuleProvider.class)) {
            for (Method method : type.getMethods()) {
                assertNoBukkit(type, method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertNoBukkit(type, parameter);
                }
            }
        }
    }

    private static void repositoryDefaultsAndProductionPathsRemainUntouched() throws Exception {
        for (FeatureKey key : Set.of(
                FeatureKey.GATHERING, FeatureKey.REFINING, FeatureKey.CRAFTING,
                FeatureKey.TIER_PROMOTION, FeatureKey.ENHANCEMENT_V2,
                FeatureKey.REPAIR_V2)) {
            assert !new FeatureFlagService().isEnabled(key);
        }
        String config = read("src/main/resources/config.yml");
        for (String id : List.of("gathering", "refining", "crafting",
                "tier-promotion", "enhancement-v2", "repair-v2")) {
            assert config.contains("  " + id + ": false");
        }
        String plugin = read("src/main/java/io/github/gyai/projects/ProjectSPlugin.java");
        String command = read("src/main/java/io/github/gyai/projects/command/ProjectCommand.java");
        assert !plugin.contains("Track3RuntimeModuleProvider");
        assert !command.contains("StagingEconomyOperatorContributor");
        String source = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/beta/activation/track3/StagingEconomyService.java"),
                StandardCharsets.UTF_8);
        assert !source.contains("plugins/ProjectS/data");
        assert !source.contains("PRODUCTION_WRITE");
        String composition = read("src/main/java/io/github/gyai/projects/beta/activation/"
                + "BetaActivationWave1CompositionRoot.java");
        assert composition.contains("flags.isEnabled(FeatureKey.EQUIPMENT_V2)");
        assert composition.contains("flags.isEnabled(FeatureKey.MOD_SYSTEM)");
        assert composition.contains("equipment.inspectReadOnly(");
    }

    private static FeatureFlagSnapshot enabledFlags() {
        EnumMap<FeatureKey, Boolean> flags = new EnumMap<>(FeatureKey.class);
        for (FeatureKey key : Set.of(
                FeatureKey.GATHERING, FeatureKey.REFINING, FeatureKey.CRAFTING,
                FeatureKey.TIER_PROMOTION, FeatureKey.ENHANCEMENT_V2,
                FeatureKey.REPAIR_V2)) {
            flags.put(key, true);
        }
        return FeatureFlagSnapshot.of(flags);
    }

    private static void assertNoBukkit(Class<?> owner, Class<?> type) {
        assert !type.getName().startsWith("org.bukkit.")
                : owner.getName() + " leaks " + type.getName();
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
