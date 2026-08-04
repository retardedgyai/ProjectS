package io.github.gyai.projects.combat.damage;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.EnumSet;

public final class AttackMetadataAdapterTest {
    private AttackMetadataAdapterTest() {
    }

    public static void main(String[] args) {
        Player attacker = proxy(Player.class);
        LivingEntity target = proxy(LivingEntity.class);

        DamageRequest legacyCompatible = DamageRequest
                .builder(attacker, target)
                .build();
        assert legacyCompatible.attackMetadata().equals(AttackMetadata.EMPTY);
        assert legacyCompatible.damageType() == DamageType.PHYSICAL;
        assert legacyCompatible.damageKind() == DamageKind.DIRECT_SKILL;
        assert legacyCompatible.mode() == DamageMode.PVE;

        AttackMetadata swordMetadata = new AttackMetadata(
                EnumSet.of(
                        AttackTag.NORMAL_ATTACK,
                        AttackTag.MELEE,
                        AttackTag.PHYSICAL),
                ElementProfile.EMPTY);
        DamageRequest adapted = DamageRequest
                .builder(attacker, target)
                .damageType(DamageType.PHYSICAL)
                .damageKind(DamageKind.NORMAL_ATTACK)
                .attackMetadata(swordMetadata)
                .build();
        assert adapted.attackMetadata().equals(swordMetadata);
        assert adapted.attackMetadata().hasTag(AttackTag.NORMAL_ATTACK);
        assert adapted.attackMetadata().hasTag(AttackTag.MELEE);
        assert adapted.attackMetadata().hasTag(AttackTag.PHYSICAL);
        assert adapted.attackMetadata().elements().equals(ElementProfile.EMPTY);

        DamageRequest safeNull = DamageRequest
                .builder(attacker, target)
                .attackMetadata(null)
                .build();
        assert safeNull.attackMetadata().equals(AttackMetadata.EMPTY);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> defaultValue(
                        method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unknown primitive: " + type);
    }
}
