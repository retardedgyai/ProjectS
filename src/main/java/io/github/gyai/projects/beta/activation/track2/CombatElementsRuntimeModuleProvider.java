package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;

import java.time.Clock;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.ConfirmedDamageHitObserver;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import java.util.function.Supplier;
import java.util.UUID;

/** Concrete Track 2 provider discovered and registered only by the future Gate. */
public final class CombatElementsRuntimeModuleProvider implements BetaRuntimeModuleProvider {
    private final TrainingDummyElementRuntime runtime;
    private final CombatElementsRuntimeModule module;
    private final CombatElementsOperatorCommandContributor commands;

    public CombatElementsRuntimeModuleProvider(
            TrainingDummyElementBoundary boundary,
            Clock clock
    ) {
        runtime = new TrainingDummyElementRuntime(boundary, clock);
        module = new CombatElementsRuntimeModule(runtime);
        commands = new CombatElementsOperatorCommandContributor(runtime);
    }

    @Override
    public BetaRuntimeModuleId moduleId() {
        return BetaRuntimeModuleId.COMBAT_ELEMENTS;
    }

    @Override
    public BetaRuntimeModule module() {
        return module;
    }

    public CombatElementsRuntimeModule combatElementsModule() {
        return module;
    }

    public BetaOperatorCommandContributor operatorCommands() {
        return commands;
    }

    public ElementRuntimeSnapshotPort snapshots() {
        return runtime.snapshots();
    }

    public TrainingDummyParticipationPort participation() {
        return runtime.participation();
    }

    public void playerDisconnected(UUID playerId) {
        runtime.playerLoggedOut(playerId);
    }

    public void clearPlayerProfiles() {
        runtime.clearPlayerProfiles();
    }

    public ConfirmedDamageHitObserver confirmedHitObserver(
            Supplier<BetaRuntimeModuleState> state,
            TrainingDummyManager dummies,
            Clock clock,
            CompatibleElementsClientPort compatibleElementsClient
    ) {
        return new Track2ConfirmedHitObserver(
                state, runtime, dummies::isTrainingDummy, clock, compatibleElementsClient);
    }
}
