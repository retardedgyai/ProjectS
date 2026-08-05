package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.combat.damage.DamageApplicationResult;
import io.github.gyai.projects.combat.damage.DamageRequest;

/** Narrow post-application observation boundary. Implementations must never reapply damage. */
@FunctionalInterface
public interface ConfirmedDamageHitObserver {
    ConfirmedDamageHitObserver NO_OP = (hitId, request, result) -> { };

    void confirmed(String hitId, DamageRequest request, DamageApplicationResult result);
}
