package io.github.gyai.projects.skill.warrior;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageMode;
import io.github.gyai.projects.combat.damage.DamageRequest;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;

public final class SpinSlashAttackMetadataTest {
    private SpinSlashAttackMetadataTest() {
    }

    public static void main(String[] args) {
        metadataIsExactAndImmutable();
        requestAdapterPreservesEstablishedFields();
        unmigratedMetadataCanRemainEmpty();
    }

    private static void metadataIsExactAndImmutable() {
        AttackMetadata metadata = WarriorAttackMetadata.SPIN_SLASH;
        assert metadata.tags().equals(Set.of(
                AttackTag.SKILL,
                AttackTag.MELEE,
                AttackTag.PHYSICAL));
        assert metadata.elements().equals(ElementProfile.EMPTY);
        assert !metadata.hasTag(AttackTag.NORMAL_ATTACK);
        assert !metadata.hasTag(AttackTag.PROJECTILE);
        assert !metadata.hasTag(AttackTag.MAGIC);
        assert !metadata.hasTag(AttackTag.SHATTER);
        assert !metadata.hasTag(AttackTag.FIRE);
        assert !metadata.hasTag(AttackTag.ICE);
        assert !metadata.hasTag(AttackTag.LIGHTNING);
        boolean immutable = false;
        try {
            metadata.tags().add(AttackTag.FIRE);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        assert immutable;
    }

    private static void requestAdapterPreservesEstablishedFields() {
        Player player = proxy(Player.class);
        LivingEntity target = proxy(LivingEntity.class);
        UUID castId = UUID.fromString(
                "00000000-0000-0000-0000-000000000123");
        DamageRequest request = WarriorDamageRequestFactory.create(
                player, target, 11.5, 1.2, "spin_slash", castId,
                true, 1.0, WarriorAttackMetadata.SPIN_SLASH);
        assert request.attacker() == player;
        assert request.target() == target;
        assert request.skillId().equals("spin_slash");
        assert request.castId().equals(castId);
        assert request.damageType() == DamageType.PHYSICAL;
        assert request.damageKind() == DamageKind.DIRECT_SKILL;
        assert request.mode() == DamageMode.PVE;
        assert request.areaDamage();
        assert request.fixedDamage() == 11.5;
        assert request.coefficient() == 1.2;
        assert request.modeMultiplier() == 1.0;
        assert request.attackMetadata().equals(
                WarriorAttackMetadata.SPIN_SLASH);
        assert request.lifeStealEfficiency()
                == DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                        true, DamageType.PHYSICAL);
    }

    private static void unmigratedMetadataCanRemainEmpty() {
        DamageRequest request = WarriorDamageRequestFactory.create(
                proxy(Player.class), proxy(LivingEntity.class),
                5, .5, "sweeping_slash", UUID.randomUUID(),
                true, 1.0, AttackMetadata.EMPTY);
        assert request.attackMetadata().equals(AttackMetadata.EMPTY);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (value, method, arguments) -> defaultValue(
                        method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }
}
