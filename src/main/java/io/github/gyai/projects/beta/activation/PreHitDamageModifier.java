package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.combat.damage.DamageRequest;

/** Pure pre-application request adaptation. Implementations must fail open to legacy combat. */
@FunctionalInterface
public interface PreHitDamageModifier {
    PreHitDamageModifier NO_OP = (hitId, request) -> request;

    DamageRequest modify(String hitId, DamageRequest request);
}
