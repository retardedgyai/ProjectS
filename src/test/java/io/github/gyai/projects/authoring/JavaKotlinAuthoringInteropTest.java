package io.github.gyai.projects.authoring;

import io.github.gyai.projects.ability.AbilityVisualRegistry;
import io.github.gyai.projects.ability.DevAbilityDefinitions;
import io.github.gyai.projects.ability.DevAbilityVisuals;
import io.github.gyai.projects.combat.shape.Vec3;

/** Verifies that Java callers retain the historical Dev APIs and can call Kotlin @JvmStatic facades. */
public final class JavaKotlinAuthoringInteropTest {
    private JavaKotlinAuthoringInteropTest() { }

    public static void main(String[] args) {
        assert DevAbilityDefinitions.sharedArcaneBurst().id()
                .equals(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID);
        assert DevAbilityVisuals.arcaneBurst().id()
                .equals(DevAbilityVisuals.ARCANE_BURST_VISUAL_ID);
        AbilityVisualRegistry registry = new AbilityVisualRegistry();
        DevAbilityVisuals.registerInto(registry);
        assert registry.resolve(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID).orElseThrow().id()
                .equals(DevAbilityVisuals.ARCANE_BURST_VISUAL_ID);
        assert DevArcaneBurstAuthoring.sharedArcaneBurst().equals(DevAbilityDefinitions.sharedArcaneBurst());
        assert CombatShapeAuthoring.sphere(new Vec3(0, 0, 0), 1).radius() == 1;
        assert AbilityAuthoring.validate(DevAbilityDefinitions.sharedArcaneBurst())
                .equals(DevAbilityDefinitions.sharedArcaneBurst());
    }
}
