package io.github.gyai.projects.status;

import io.github.gyai.projects.combat.skill.CcResistanceProfile;
import io.github.gyai.projects.combat.skill.CcResistanceResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StatusEffectManager {
    private static final int MAX_DURATION_TICKS = 20 * 60 * 60;
    private static final double MAX_STRENGTH = 100.0;

    private final JavaPlugin plugin;
    private final NamespacedKey slowModifierKey;
    private final Map<UUID, ActiveStatuses> effects = new HashMap<>();
    private CcResistanceResolver resistanceResolver =
            entity -> CcResistanceProfile.DEFAULT;

    public StatusEffectManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.slowModifierKey =
                new NamespacedKey(plugin, "status_slow");
    }

    public void setResistanceResolver(CcResistanceResolver resolver) {
        resistanceResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public StatusApplicationResult slow(
            LivingEntity target,
            LivingEntity source,
            int ticks,
            double strength
    ) {
        return apply(target, StatusEffectType.SLOW, source, ticks, strength);
    }

    public StatusApplicationResult apply(
            LivingEntity target,
            StatusEffectType type,
            LivingEntity source,
            int ticks,
            double strength
    ) {
        if (!validTarget(target)
                || type == null
                || ticks <= 0
                || !Double.isFinite(strength)
                || strength < 0.0) {
            return StatusApplicationResult.INVALID_TARGET;
        }
        CcResistanceProfile profile = safeProfile(target);
        if (profile.statusDurationMultiplier() <= 0.0) {
            return StatusApplicationResult.REJECTED_IMMUNE;
        }
        int adjustedTicks = (int) Math.clamp(
                Math.round(ticks * profile.statusDurationMultiplier()),
                1L,
                MAX_DURATION_TICKS);
        double adjustedStrength = Math.clamp(strength, 0.0, MAX_STRENGTH);
        long currentTick = plugin.getServer().getCurrentTick();
        ActiveStatuses active = effects.computeIfAbsent(
                target.getUniqueId(),
                ignored -> new ActiveStatuses(
                        target, target.getWorld().getUID()));
        StatusEffectState current = active.values.get(type);
        StatusEffectState.Transition transition = StatusEffectState.apply(
                current,
                type,
                source == null ? null : source.getUniqueId(),
                currentTick,
                adjustedTicks,
                adjustedStrength);
        active.values.put(type, transition.state());
        if (type == StatusEffectType.SLOW) {
            applySlow(target, transition.state().strength());
        }
        return transition.result();
    }

    public void tick(long currentTick) {
        for (Map.Entry<UUID, ActiveStatuses> entry
                : Map.copyOf(effects).entrySet()) {
            ActiveStatuses active = entry.getValue();
            Entity current =
                    plugin.getServer().getEntity(entry.getKey());
            if (current != active.entity
                    || !active.entity.isValid()
                    || active.entity.isDead()
                    || !active.entity.getWorld().getUID().equals(active.worldId)) {
                clear(active.entity);
                continue;
            }
            active.values.entrySet().removeIf(effect -> {
                if (effect.getValue().activeAt(currentTick)) {
                    return false;
                }
                removePhysicalEffect(active.entity, effect.getKey());
                return true;
            });
            if (active.values.isEmpty()) {
                effects.remove(entry.getKey(), active);
            }
        }
    }

    public List<Snapshot> snapshots(
            LivingEntity target,
            long currentTick
    ) {
        ActiveStatuses active = target == null
                ? null
                : effects.get(target.getUniqueId());
        if (active == null || active.entity != target) {
            return List.of();
        }
        List<Snapshot> snapshots = new ArrayList<>(active.values.size());
        for (StatusEffectState state : active.values.values()) {
            if (state.activeAt(currentTick)) {
                snapshots.add(new Snapshot(
                        state.type(),
                        state.strength(),
                        state.originalDurationTicks(),
                        state.remainingTicks(currentTick),
                        state.endTick()));
            }
        }
        snapshots.sort(Comparator.comparingInt(
                (Snapshot value) -> value.type().uiPriority()).reversed());
        return List.copyOf(snapshots);
    }

    public boolean has(LivingEntity target, StatusEffectType type) {
        ActiveStatuses active = target == null
                ? null
                : effects.get(target.getUniqueId());
        StatusEffectState state = active == null
                ? null
                : active.values.get(type);
        return state != null
                && state.activeAt(plugin.getServer().getCurrentTick());
    }

    public void clear(LivingEntity target) {
        if (target == null) {
            return;
        }
        ActiveStatuses removed =
                effects.remove(target.getUniqueId());
        if (removed == null) {
            return;
        }
        for (StatusEffectType type : removed.values.keySet()) {
            removePhysicalEffect(removed.entity, type);
        }
    }

    public void clear() {
        for (ActiveStatuses active
                : Map.copyOf(effects).values()) {
            clear(active.entity);
        }
    }

    private void applySlow(LivingEntity target, double strength) {
        AttributeInstance movement =
                target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        AttributeModifier existing =
                movement.getModifier(slowModifierKey);
        if (existing != null) {
            movement.removeModifier(existing);
        }
        double amount = -Math.clamp(
                0.15 * (Math.floor(strength) + 1.0),
                0.0,
                0.95);
        if (amount < 0.0) {
            movement.addTransientModifier(new AttributeModifier(
                    slowModifierKey,
                    amount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void removePhysicalEffect(
            LivingEntity target,
            StatusEffectType type
    ) {
        if (type != StatusEffectType.SLOW) {
            return;
        }
        AttributeInstance movement =
                target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        AttributeModifier modifier =
                movement.getModifier(slowModifierKey);
        if (modifier != null) {
            movement.removeModifier(modifier);
        }
    }

    private CcResistanceProfile safeProfile(LivingEntity target) {
        CcResistanceProfile profile = resistanceResolver.resolve(target);
        return profile == null ? CcResistanceProfile.DEFAULT : profile;
    }

    private static boolean validTarget(LivingEntity target) {
        return target != null
                && !(target instanceof Player)
                && !(target instanceof ArmorStand)
                && target.isValid()
                && !target.isDead();
    }

    public record Snapshot(
            StatusEffectType type,
            double strength,
            int totalTicks,
            int remainingTicks,
            long endTick
    ) {
    }

    private static final class ActiveStatuses {
        private final LivingEntity entity;
        private final UUID worldId;
        private final Map<StatusEffectType, StatusEffectState> values =
                new EnumMap<>(StatusEffectType.class);

        private ActiveStatuses(LivingEntity entity, UUID worldId) {
            this.entity = entity;
            this.worldId = worldId;
        }
    }
}
