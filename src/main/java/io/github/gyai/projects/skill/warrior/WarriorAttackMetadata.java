package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.ElementProfile;

import java.util.Set;

/** Canonical immutable metadata for explicitly migrated Warrior attacks. */
public final class WarriorAttackMetadata {
    public static final AttackMetadata SPIN_SLASH = new AttackMetadata(
            Set.of(
                    AttackTag.SKILL,
                    AttackTag.MELEE,
                    AttackTag.PHYSICAL),
            ElementProfile.EMPTY);

    private WarriorAttackMetadata() {
    }
}
