package io.github.gyai.projects.ability;

import io.github.gyai.projects.authoring.DevArcaneBurstAuthoring;
/** The deliberately small v0.1 vertical slice; gameplay values are never duplicated here. */
public final class DevAbilityVisuals {
    public static final String ARCANE_BURST_VISUAL_ID="projects:vfx/dev-arcane-burst";
    private DevAbilityVisuals() { }
    public static void registerInto(AbilityVisualRegistry registry) {
        DevArcaneBurstAuthoring.registerInto(registry);
    }
    public static AbilityVisualDefinition arcaneBurst() {
        return DevArcaneBurstAuthoring.arcaneBurst();
    }
}
