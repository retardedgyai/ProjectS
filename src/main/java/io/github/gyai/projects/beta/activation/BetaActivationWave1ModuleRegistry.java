package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureKey;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable central registration point for the eight Activation Wave 1 modules. */
public final class BetaActivationWave1ModuleRegistry {
    private final List<BetaRuntimeModule> modules;

    public BetaActivationWave1ModuleRegistry(Collection<? extends BetaRuntimeModule> source) {
        if (source == null) throw new IllegalArgumentException("modules are required");
        EnumMap<BetaRuntimeModuleId, BetaRuntimeModule> values =
                new EnumMap<>(BetaRuntimeModuleId.class);
        for (BetaRuntimeModule module : source) {
            if (module == null || values.put(module.id(), module) != null) {
                throw new IllegalArgumentException("duplicate or null module");
            }
        }
        if (!values.keySet().equals(Set.of(BetaRuntimeModuleId.values()))) {
            throw new IllegalArgumentException("all eight Beta modules must be registered");
        }
        modules = java.util.Arrays.stream(BetaRuntimeModuleId.values())
                .map(values::get).toList();
    }

    /**
     * Safe central plan used by the plugin while repository defaults remain OFF.
     * Concrete Track providers replace these descriptors at the activation/configuration gate;
     * the descriptors intentionally cannot start or register Bukkit resources.
     */
    public static BetaActivationWave1ModuleRegistry disabledPlan() {
        return new BetaActivationWave1ModuleRegistry(List.of(
                planned(BetaRuntimeModuleId.PLAYER_PERSISTENCE, Set.of(),
                        Set.of(FeatureKey.PLAYER_PERSISTENCE), BetaMutationPolicy.READ_ONLY, true,
                        Set.of("track1.bukkit-listener", "track1.staging-player-store")),
                planned(BetaRuntimeModuleId.EQUIPMENT, Set.of(),
                        Set.of(FeatureKey.EQUIPMENT_V2), BetaMutationPolicy.READ_ONLY, true,
                        Set.of("track1.inventory-reader")),
                planned(BetaRuntimeModuleId.COMBAT_ELEMENTS, Set.of(),
                        Set.of(FeatureKey.FIRE_SYSTEM, FeatureKey.ICE_SYSTEM), BetaMutationPolicy.READ_ONLY, true,
                        Set.of("training-dummy-boundary", "damage-service-secondary")),
                planned(BetaRuntimeModuleId.GATHERING_CRAFTING, Set.of(),
                        Set.of(FeatureKey.GATHERING, FeatureKey.REFINING, FeatureKey.CRAFTING),
                        BetaMutationPolicy.STAGING_WRITE, false,
                        Set.of("track3.staging-inventory", "track3.staging-transaction-journal")),
                planned(BetaRuntimeModuleId.ENHANCEMENT_REPAIR,
                        Set.of(BetaRuntimeModuleId.GATHERING_CRAFTING),
                        Set.of(FeatureKey.TIER_PROMOTION, FeatureKey.ENHANCEMENT_V2, FeatureKey.REPAIR_V2),
                        BetaMutationPolicy.STAGING_WRITE, false,
                        Set.of("track3.staging-inventory", "track3.staging-transaction-journal")),
                planned(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                        Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                                BetaRuntimeModuleId.GATHERING_CRAFTING),
                        Set.of(FeatureKey.PARTY, FeatureKey.QUESTS, FeatureKey.REWARD_V2),
                        BetaMutationPolicy.READ_ONLY, true,
                        Set.of("track1-progress-port", "track3-item-delivery-port")),
                planned(BetaRuntimeModuleId.MOB_EDITOR_V2, Set.of(),
                        Set.of(FeatureKey.MOB_EDITOR_V2), BetaMutationPolicy.READ_ONLY, true,
                        Set.of("beta-staging-mob-repository")),
                planned(BetaRuntimeModuleId.CLIENT_BETA_PROTOCOL,
                        Set.of(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                                BetaRuntimeModuleId.MOB_EDITOR_V2),
                        Set.of(FeatureKey.CLIENT_BETA_UI), BetaMutationPolicy.READ_ONLY, true,
                        Set.of("minecraft-plugin-messaging"))));
    }

    public List<BetaRuntimeModule> modules() { return modules; }
    public int size() { return modules.size(); }

    private static BetaRuntimeModule planned(
            BetaRuntimeModuleId id, Set<BetaRuntimeModuleId> dependencies,
            Set<FeatureKey> features, BetaMutationPolicy mutationPolicy,
            boolean readOnly, Set<String> infrastructure
    ) {
        return new PlannedModule(new BetaRuntimeModuleDescriptor(id, dependencies, features,
                mutationPolicy, readOnly, infrastructure));
    }

    private static final class PlannedModule implements BetaRuntimeModule {
        private final BetaRuntimeModuleDescriptor descriptor;
        private BetaRuntimeModuleState state = BetaRuntimeModuleState.NOT_INSTALLED;
        private PlannedModule(BetaRuntimeModuleDescriptor descriptor) { this.descriptor = descriptor; }
        @Override public BetaRuntimeModuleId id() { return descriptor.id(); }
        @Override public Set<BetaRuntimeModuleId> dependencies() { return descriptor.dependencies(); }
        @Override public BetaRuntimeModuleDescriptor descriptor() { return descriptor; }
        @Override public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
            state = BetaRuntimeModuleState.BLOCKED;
            return new BetaRuntimeModuleResult(false, state,
                    "concrete provider requires an approved staging restart");
        }
        @Override public BetaRuntimeModuleResult start() {
            state = BetaRuntimeModuleState.BLOCKED;
            return new BetaRuntimeModuleResult(false, state, "central plan is registration-only");
        }
        @Override public BetaRuntimeModuleResult stop() {
            state = BetaRuntimeModuleState.STOPPED;
            return BetaRuntimeModuleResult.stopped();
        }
        @Override public BetaRuntimeModuleState state() { return state; }
    }
}
