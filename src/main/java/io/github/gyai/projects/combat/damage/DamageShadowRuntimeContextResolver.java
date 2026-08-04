package io.github.gyai.projects.combat.damage;

@FunctionalInterface
public interface DamageShadowRuntimeContextResolver {
    DamageShadowRuntimeContext resolve(DamageRequest request);
}
