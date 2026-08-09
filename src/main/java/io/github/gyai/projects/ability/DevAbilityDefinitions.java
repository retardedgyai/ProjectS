package io.github.gyai.projects.ability;

import io.github.gyai.projects.authoring.DevArcaneBurstAuthoring;

public final class DevAbilityDefinitions {
    public static final String SHARED_ARCANE_BURST_ID = "projects:dev-shared-arcane-burst";
    private DevAbilityDefinitions() { }
    public static AbilityDefinition sharedArcaneBurst() {
        return DevArcaneBurstAuthoring.sharedArcaneBurst();
    }
}
