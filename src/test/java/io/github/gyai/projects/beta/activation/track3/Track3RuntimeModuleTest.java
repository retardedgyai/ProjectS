package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaActivationTargetScope;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleContext;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Track3RuntimeModuleTest {
    private Track3RuntimeModuleTest() {
    }

    public static void main(String[] args) throws Exception {
        providerPublishesTwoUnregisteredModules();
        lifecycleAndFeatureGatesAreFailClosedAndIdempotent();
        contributorIsPermissionBoundAndUnregistered();
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
            assert contributor.commandPaths().size() == 9;
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
