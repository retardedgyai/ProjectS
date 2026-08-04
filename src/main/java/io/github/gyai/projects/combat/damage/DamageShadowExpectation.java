package io.github.gyai.projects.combat.damage;

import java.util.Objects;
import java.util.Set;

/** Exact immutable context expected by one damage-shadow subject. */
public record DamageShadowExpectation(
        String subjectId,
        DamageType damageType,
        DamageKind damageKind,
        DamageMode damageMode,
        Set<AttackTag> exactTags,
        ElementProfile elements
) {
    public static final DamageShadowExpectation STARTER_SWORD =
            new DamageShadowExpectation(
                    "starter-sword",
                    DamageType.PHYSICAL,
                    DamageKind.NORMAL_ATTACK,
                    DamageMode.PVE,
                    Set.of(
                            AttackTag.NORMAL_ATTACK,
                            AttackTag.MELEE,
                            AttackTag.PHYSICAL),
                    ElementProfile.EMPTY);

    public DamageShadowExpectation {
        if (subjectId == null
                || subjectId.length() > 64
                || !subjectId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "subjectId must be a lowercase safe slug");
        }
        damageType = Objects.requireNonNull(damageType, "damageType");
        damageKind = Objects.requireNonNull(damageKind, "damageKind");
        damageMode = Objects.requireNonNull(damageMode, "damageMode");
        exactTags = exactTags == null ? Set.of() : Set.copyOf(exactTags);
        elements = elements == null ? ElementProfile.EMPTY : elements;
    }

    public boolean matches(DamageRequest request) {
        return request != null
                && request.damageType() == damageType
                && request.damageKind() == damageKind
                && request.mode() == damageMode
                && request.attackMetadata().tags().equals(exactTags)
                && request.attackMetadata().elements().equals(elements);
    }
}
