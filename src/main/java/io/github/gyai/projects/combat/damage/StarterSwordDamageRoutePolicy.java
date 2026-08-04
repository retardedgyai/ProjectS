package io.github.gyai.projects.combat.damage;

import java.util.Set;

/** Pure allow-list policy for the conditions validated in Phase 2.5. */
public final class StarterSwordDamageRoutePolicy {
    private static final Set<AttackTag> SUPPORTED_TAGS = Set.of(
            AttackTag.NORMAL_ATTACK,
            AttackTag.MELEE,
            AttackTag.PHYSICAL);

    public StarterSwordRouteDecision decide(StarterSwordRouteInput input) {
        if (!input.authoritativeEnabled()) {
            return StarterSwordRouteDecision.LEGACY_DISABLED;
        }
        if (!StarterSwordDamageShadow.ITEM_ID.equals(input.itemId())) {
            return StarterSwordRouteDecision.LEGACY_UNSUPPORTED_ITEM;
        }
        if (input.damageKind() != DamageKind.NORMAL_ATTACK) {
            return StarterSwordRouteDecision.LEGACY_UNSUPPORTED_KIND;
        }
        if (input.damageType() != DamageType.PHYSICAL) {
            return StarterSwordRouteDecision.LEGACY_UNSUPPORTED_TYPE;
        }
        if (input.damageMode() != DamageMode.PVE) {
            return StarterSwordRouteDecision.LEGACY_UNSUPPORTED_MODE;
        }
        if (!input.attackMetadata().tags().equals(SUPPORTED_TAGS)
                || !input.attackMetadata().elements()
                .equals(ElementProfile.EMPTY)) {
            return StarterSwordRouteDecision.LEGACY_METADATA;
        }
        if (input.critical()) {
            return StarterSwordRouteDecision.LEGACY_CRITICAL;
        }
        if (!Double.isFinite(input.shieldAmount())
                || Double.compare(input.shieldAmount(), 0.0) != 0) {
            return StarterSwordRouteDecision.LEGACY_SHIELD;
        }
        if (input.specialState()) {
            return StarterSwordRouteDecision.LEGACY_SPECIAL_STATE;
        }
        return StarterSwordRouteDecision.NEW_AUTHORITATIVE;
    }
}
