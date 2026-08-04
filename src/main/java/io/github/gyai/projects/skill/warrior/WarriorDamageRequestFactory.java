package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Keeps the established Warrior skill request fields in one testable adapter. */
public final class WarriorDamageRequestFactory {
    private WarriorDamageRequestFactory() {
    }

    public static DamageRequest create(
            Player player,
            LivingEntity target,
            double fixedDamage,
            double coefficient,
            String skillId,
            UUID castId,
            boolean areaDamage,
            double modeMultiplier,
            AttackMetadata attackMetadata
    ) {
        return DamageRequest.builder(player, target)
                .skillId(skillId)
                .castId(castId)
                .damageType(DamageType.PHYSICAL)
                .damageKind(DamageKind.DIRECT_SKILL)
                .mode(DamageMode.PVE)
                .areaDamage(areaDamage)
                .fixedDamage(fixedDamage)
                .coefficient(coefficient)
                .pveMultiplier(modeMultiplier)
                .attackMetadata(attackMetadata)
                .build();
    }
}
