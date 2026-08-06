package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.PreHitDamageModifier;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** Reads established Ice state without mutating it before direct damage is applied. */
final class Track2PreHitDamageModifier implements PreHitDamageModifier {
    private final Supplier<BetaRuntimeModuleState> moduleState;
    private final TrainingDummyElementRuntime runtime;
    private final TrainingDummyTargetPort targets;
    private final Clock clock;
    private final CompatibleElementsClientPort compatibleElementsClient;

    Track2PreHitDamageModifier(Supplier<BetaRuntimeModuleState> moduleState,
                               TrainingDummyElementRuntime runtime,
                               TrainingDummyTargetPort targets, Clock clock,
                               CompatibleElementsClientPort compatibleElementsClient) {
        this.moduleState = Objects.requireNonNull(moduleState, "moduleState");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.compatibleElementsClient = Objects.requireNonNull(
                compatibleElementsClient, "compatibleElementsClient");
    }

    @Override public DamageRequest modify(String hitId, DamageRequest request) {
        try {
            if (request == null || hitId == null || hitId.isBlank()
                    || moduleState.get() != BetaRuntimeModuleState.RUNNING
                    || request.mode() != DamageMode.PVE
                    || request.offenseSnapshot() != null
                    || !targets.isTrainingDummy(request.target())) return request;
            IceElementEngine.DamageOrigin origin = origin(request);
            if (origin == null) return request;
            double multiplier = runtime.directDamageMultiplier(
                    request.attacker().getUniqueId(), request.target().getUniqueId(),
                    origin == IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT
                            ? "starter_sword" : request.skillId(), origin, request.attackMetadata(),
                    request.attacker().getWorld().getName(),
                    Track2ConfirmedHitObserver.resolveCompatible(
                            compatibleElementsClient, request.attacker().getUniqueId()), clock.millis());
            return multiplier == 1.0 ? request
                    : request.toBuilder().iceDirectDamageMultiplier(multiplier).build();
        } catch (RuntimeException ignored) {
            return request;
        }
    }

    private static IceElementEngine.DamageOrigin origin(DamageRequest request) {
        if (request.damageKind() == DamageKind.NORMAL_ATTACK
                && "normal_attack".equals(request.skillId())) return IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT;
        if (request.damageKind() == DamageKind.DIRECT_SKILL
                && "spin_slash".equals(request.skillId())) return IceElementEngine.DamageOrigin.SKILL_DIRECT;
        return null;
    }
}
