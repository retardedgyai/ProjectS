package io.github.gyai.projects.combat.damage;

public interface DamageShadowRoute extends DamageRequestApplier {
    boolean supports(DamageRequest request);

    default void recordDispatchFailure(
            DamageRequest request,
            RuntimeException exception
    ) {
    }
}
