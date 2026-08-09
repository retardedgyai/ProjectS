package io.github.gyai.projects.ability;

import io.github.gyai.projects.combat.damage.*;
import java.util.Set;

public final class DevAbilityDefinitions {
    public static final String SHARED_ARCANE_BURST_ID = "projects:dev-shared-arcane-burst";
    private DevAbilityDefinitions() { }
    public static AbilityDefinition sharedArcaneBurst() {
        return new AbilityDefinition(AbilityDefinition.SCHEMA_VERSION, SHARED_ARCANE_BURST_ID, "Dev Shared Arcane Burst", java.util.List.of(
                new AbilityDefinition.CircleTelegraph(TargetSelector.PRIMARY_TARGET, TargetSelector.PRIMARY_TARGET, 3.0, 20, true),
                new AbilityDefinition.Wait(20),
                new AbilityDefinition.Damage(TargetSelector.PRIMARY_TARGET, DamageType.MAGICAL, DamageKind.DIRECT_SKILL, 12.0, .5, true,
                        new AttackMetadata(Set.of(AttackTag.MAGIC, AttackTag.SKILL), ElementProfile.EMPTY))));
    }
}
