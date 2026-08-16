package io.github.gyai.projects.ability;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;

import java.util.List;

public final class BossAbilityDefinitions {
    public static final String GROHM_BASIC_ATTACK_ID =
            "projects:boss/grohm/basic-attack";

    private BossAbilityDefinitions() {
    }

    public static AbilityDefinition grohmBasicAttack() {
        return new AbilityDefinition(
                AbilityDefinition.SCHEMA_VERSION,
                GROHM_BASIC_ATTACK_ID,
                "Grohm Basic Attack",
                List.of(new AbilityDefinition.Damage(
                        TargetSelector.PRIMARY_TARGET,
                        DamageType.PHYSICAL,
                        DamageKind.NORMAL_ATTACK,
                        0.0,
                        1.0,
                        false,
                        AttackMetadata.EMPTY)));
    }
}
