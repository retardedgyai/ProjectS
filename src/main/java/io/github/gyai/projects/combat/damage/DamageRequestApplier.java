package io.github.gyai.projects.combat.damage;

@FunctionalInterface
public interface DamageRequestApplier {
    DamageApplicationResult apply(DamageRequest request);
}
