package io.github.gyai.projects.combat.damage;

import java.util.Set;

public final class StarterSwordRoutePolicyTest {
    private StarterSwordRoutePolicyTest() {
    }

    public static void main(String[] args) {
        StarterSwordDamageRoutePolicy policy =
                new StarterSwordDamageRoutePolicy();
        assertDecision(policy, input(false),
                StarterSwordRouteDecision.LEGACY_DISABLED);
        assertDecision(policy, new StarterSwordRouteInput(
                        true, "other", DamageType.PHYSICAL,
                        DamageKind.NORMAL_ATTACK, DamageMode.PVE,
                        StarterSwordRouteTestFixtures.METADATA,
                        false, 0, false),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_ITEM);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_KIND);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.DAMAGE_OVER_TIME,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_KIND);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.REFLECTED,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_KIND);
        assertDecision(policy, change(
                        DamageType.MAGICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_TYPE);
        assertDecision(policy, change(
                        DamageType.TRUE, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_TYPE);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVP, StarterSwordRouteTestFixtures.METADATA),
                StarterSwordRouteDecision.LEGACY_UNSUPPORTED_MODE);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, AttackMetadata.EMPTY),
                StarterSwordRouteDecision.LEGACY_METADATA);
        assertDecision(policy, change(
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, new AttackMetadata(Set.of(
                                AttackTag.NORMAL_ATTACK,
                                AttackTag.MELEE,
                                AttackTag.PHYSICAL,
                                AttackTag.SKILL), ElementProfile.EMPTY)),
                StarterSwordRouteDecision.LEGACY_METADATA);
        assertDecision(policy, new StarterSwordRouteInput(
                        true, StarterSwordDamageShadow.ITEM_ID,
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA,
                        true, 0, false),
                StarterSwordRouteDecision.LEGACY_CRITICAL);
        assertDecision(policy, new StarterSwordRouteInput(
                        true, StarterSwordDamageShadow.ITEM_ID,
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA,
                        false, 1, false),
                StarterSwordRouteDecision.LEGACY_SHIELD);
        assertDecision(policy, new StarterSwordRouteInput(
                        true, StarterSwordDamageShadow.ITEM_ID,
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA,
                        false, Double.NaN, false),
                StarterSwordRouteDecision.LEGACY_SHIELD);
        assertDecision(policy, new StarterSwordRouteInput(
                        true, StarterSwordDamageShadow.ITEM_ID,
                        DamageType.PHYSICAL, DamageKind.NORMAL_ATTACK,
                        DamageMode.PVE, StarterSwordRouteTestFixtures.METADATA,
                        false, 0, true),
                StarterSwordRouteDecision.LEGACY_SPECIAL_STATE);
        assertDecision(policy, input(true),
                StarterSwordRouteDecision.NEW_AUTHORITATIVE);
    }

    private static StarterSwordRouteInput input(boolean enabled) {
        return new StarterSwordRouteInput(
                enabled,
                StarterSwordDamageShadow.ITEM_ID,
                DamageType.PHYSICAL,
                DamageKind.NORMAL_ATTACK,
                DamageMode.PVE,
                StarterSwordRouteTestFixtures.METADATA,
                false,
                0,
                false);
    }

    private static StarterSwordRouteInput change(
            DamageType type,
            DamageKind kind,
            DamageMode mode,
            AttackMetadata metadata
    ) {
        return new StarterSwordRouteInput(
                true, StarterSwordDamageShadow.ITEM_ID,
                type, kind, mode, metadata, false, 0, false);
    }

    private static void assertDecision(
            StarterSwordDamageRoutePolicy policy,
            StarterSwordRouteInput input,
            StarterSwordRouteDecision expected
    ) {
        StarterSwordRouteDecision actual = policy.decide(input);
        if (actual != expected) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
