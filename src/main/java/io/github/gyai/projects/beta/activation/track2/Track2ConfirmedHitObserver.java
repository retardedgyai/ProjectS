package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaRuntimeModuleState;
import io.github.gyai.projects.beta.activation.ConfirmedDamageHitObserver;
import io.github.gyai.projects.combat.damage.DamageApplicationResult;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** Bukkit boundary converting one already-confirmed legacy hit into one Track 2 observation. */
public final class Track2ConfirmedHitObserver implements ConfirmedDamageHitObserver {
    private final Supplier<BetaRuntimeModuleState> moduleState;
    private final TrainingDummyElementRuntime runtime;
    private final TrainingDummyManager dummies;
    private final Clock clock;

    public Track2ConfirmedHitObserver(
            Supplier<BetaRuntimeModuleState> moduleState,
            TrainingDummyElementRuntime runtime,
            TrainingDummyManager dummies,
            Clock clock
    ) {
        this.moduleState = Objects.requireNonNull(moduleState, "moduleState");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.dummies = Objects.requireNonNull(dummies, "dummies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public void confirmed(
            String hitId,
            DamageRequest request,
            DamageApplicationResult result
    ) {
        if (moduleState.get() != BetaRuntimeModuleState.RUNNING || request == null
                || result == null || !result.attempted()
                || request.target() instanceof Player
                || !dummies.isTrainingDummy(request.target())
                || request.offenseSnapshot() != null) return; // secondary damage never recurses
        TrainingDummyElementRuntime.AttackType attackType;
        IceElementEngine.DamageOrigin origin;
        if (request.damageKind() == DamageKind.NORMAL_ATTACK
                && "normal_attack".equals(request.skillId())) {
            attackType = TrainingDummyElementRuntime.AttackType.STARTER_SWORD_NORMAL;
            origin = IceElementEngine.DamageOrigin.NORMAL_ATTACK_DIRECT;
        } else if (request.damageKind() == DamageKind.DIRECT_SKILL
                && "spin_slash".equals(request.skillId())) {
            attackType = TrainingDummyElementRuntime.AttackType.SPIN_SLASH;
            origin = IceElementEngine.DamageOrigin.SKILL_DIRECT;
        } else return;
        try {
            runtime.observe(new TrainingDummyElementRuntime.AttackInput(
                    hitId, request.attacker().getUniqueId(), request.target().getUniqueId(),
                    attackType == TrainingDummyElementRuntime.AttackType.STARTER_SWORD_NORMAL
                            ? "starter_sword" : "spin_slash",
                    attackType, origin, request.attackMetadata(),
                    preCritical(result),
                    result.calculation().critical(), true, false, false,
                    request.attacker().getWorld().getName(),
                    request.attacker().hasPermission("projects.dev"), clock.millis()));
        } catch (RuntimeException ignored) {
            // Observation is fail-open and occurs only after the legacy application boundary.
        }
    }

    private static double preCritical(DamageApplicationResult result) {
        double resolved = result.calculation().offenseResolvedDamage();
        if (!result.calculation().critical()) return resolved;
        double multiplier = result.calculation().criticalMultiplier();
        return multiplier > 0.0 && Double.isFinite(multiplier)
                ? resolved / multiplier : resolved;
    }
}
