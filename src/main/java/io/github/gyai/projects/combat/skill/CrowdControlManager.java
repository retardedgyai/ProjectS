package io.github.gyai.projects.combat.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CrowdControlManager {
    private static final int MAX_DURATION_TICKS = 20 * 60 * 60;
    private static final double FORCED_MOVE_DISTANCE = 7.0;

    private final JavaPlugin plugin;
    private final NamespacedKey movementModifierKey;
    private final Map<UUID, ActiveControl> controls = new HashMap<>();
    private CcResistanceResolver resistanceResolver =
            entity -> CcResistanceProfile.DEFAULT;
    private ControlChangeListener changeListener = (entity, previous, current) -> {
    };

    public CrowdControlManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.movementModifierKey =
                new NamespacedKey(plugin, "hard_cc_movement");
    }

    public void setResistanceResolver(CcResistanceResolver resolver) {
        resistanceResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void setChangeListener(ControlChangeListener listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
    }

    public HardControlApplicationResult stun(
            LivingEntity target,
            LivingEntity source,
            int ticks
    ) {
        return apply(target, HardControlType.STUN, source, ticks);
    }

    public HardControlApplicationResult root(LivingEntity target, int ticks) {
        return apply(target, HardControlType.ROOT, null, ticks);
    }

    public HardControlApplicationResult root(
            LivingEntity target,
            LivingEntity source,
            int ticks
    ) {
        return apply(target, HardControlType.ROOT, source, ticks);
    }

    public HardControlApplicationResult fear(
            LivingEntity target,
            Player source,
            int ticks
    ) {
        return apply(target, HardControlType.FEAR, source, ticks);
    }

    public HardControlApplicationResult charm(
            LivingEntity target,
            LivingEntity source,
            int ticks
    ) {
        return apply(target, HardControlType.CHARM, source, ticks);
    }

    public void pull(
            LivingEntity target,
            Location center,
            double strength
    ) {
        if (!validTarget(target)
                || center == null
                || !center.getWorld().equals(target.getWorld())
                || !Double.isFinite(strength)
                || strength <= 0.0) {
            return;
        }
        Vector pull = center.toVector()
                .subtract(target.getLocation().toVector())
                .setY(0.08);
        if (pull.lengthSquared() > 0.0) {
            target.setVelocity(pull.normalize().multiply(
                    Math.min(strength, target instanceof Mob ? 0.65 : 0.4)));
        }
    }

    public HardControlApplicationResult apply(
            LivingEntity target,
            HardControlType type,
            LivingEntity source,
            int ticks
    ) {
        if (!validTarget(target) || type == null || ticks <= 0) {
            return HardControlApplicationResult.INVALID_TARGET;
        }
        CcResistanceProfile profile = safeProfile(target);
        if (profile.immuneTo(type)
                || profile.hardControlDurationMultiplier() <= 0.0) {
            return HardControlApplicationResult.REJECTED_IMMUNE;
        }
        int adjustedTicks = (int) Math.clamp(
                Math.round(ticks * profile.hardControlDurationMultiplier()),
                1L,
                MAX_DURATION_TICKS);
        long currentTick = plugin.getServer().getCurrentTick();
        UUID targetId = target.getUniqueId();
        ActiveControl current = controls.get(targetId);
        HardControlState previousState = current == null ? null : current.state;
        HardControlState.Transition transition = HardControlState.apply(
                previousState,
                type,
                source == null ? null : source.getUniqueId(),
                currentTick,
                adjustedTicks);
        if (transition.result()
                == HardControlApplicationResult.REJECTED_LOWER_PRIORITY) {
            return transition.result();
        }
        Location sourceLocation = source == null
                ? current == null ? null : current.sourceLocation
                : source.getLocation().clone();
        if (transition.result() == HardControlApplicationResult.REFRESHED
                && current != null) {
            current.state = transition.state();
            current.sourceLocation = sourceLocation;
            changeListener.onChange(target, previousState, current.state);
            return transition.result();
        }

        if (current != null) {
            endControl(current, HardControlRemovalReason.REPLACED, false);
        }
        ActiveControl replacement = new ActiveControl(
                target,
                transition.state(),
                sourceLocation,
                target.getWorld().getUID(),
                target instanceof Mob mob && mob.getTarget() != null
                        ? mob.getTarget().getUniqueId()
                        : null,
                target.hasAI());
        controls.put(targetId, replacement);
        applyPhysicalState(replacement);
        changeListener.onChange(target, previousState, replacement.state);
        return transition.result();
    }

    public void tick(long currentTick) {
        for (ActiveControl control
                : Map.copyOf(controls).values()) {
            LivingEntity entity = control.entity;
            Entity currentEntity =
                    plugin.getServer().getEntity(entity.getUniqueId());
            if (currentEntity != entity
                    || !entity.isValid()
                    || entity.isDead()) {
                removeInternal(
                        entity.getUniqueId(),
                        HardControlRemovalReason.ENTITY_INVALID);
                continue;
            }
            if (!entity.getWorld().getUID().equals(control.worldId)) {
                removeInternal(
                        entity.getUniqueId(),
                        HardControlRemovalReason.WORLD_CHANGED);
                continue;
            }
            if (!control.state.activeAt(currentTick)) {
                removeInternal(
                        entity.getUniqueId(),
                        HardControlRemovalReason.EXPIRED);
                continue;
            }
            maintainPhysicalState(control);
        }
    }

    public boolean isControlled(LivingEntity target) {
        ActiveControl control = target == null
                ? null
                : controls.get(target.getUniqueId());
        return control != null
                && control.entity == target
                && control.state.activeAt(plugin.getServer().getCurrentTick());
    }

    public HardControlType getType(LivingEntity target) {
        ActiveControl control = target == null
                ? null
                : controls.get(target.getUniqueId());
        if (control == null
                || control.entity != target
                || !control.state.activeAt(
                        plugin.getServer().getCurrentTick())) {
            return null;
        }
        return control.state.type();
    }

    public Snapshot snapshot(LivingEntity target, long currentTick) {
        ActiveControl control = target == null
                ? null
                : controls.get(target.getUniqueId());
        if (control == null
                || control.entity != target
                || !control.state.activeAt(currentTick)) {
            return null;
        }
        return new Snapshot(
                control.state.type(),
                control.state.originalDurationTicks(),
                control.state.remainingTicks(currentTick),
                control.state.endTick());
    }

    public void clear(LivingEntity target, HardControlRemovalReason reason) {
        if (target != null) {
            removeInternal(target.getUniqueId(), reason);
        }
    }

    public void clear() {
        clear(HardControlRemovalReason.PLUGIN_STOP);
    }

    public void clear(HardControlRemovalReason reason) {
        for (UUID entityId : Map.copyOf(controls).keySet()) {
            removeInternal(entityId, reason);
        }
    }

    private void removeInternal(
            UUID entityId,
            HardControlRemovalReason reason
    ) {
        ActiveControl control = controls.remove(entityId);
        if (control != null) {
            endControl(control, reason, true);
        }
    }

    private void applyPhysicalState(ActiveControl control) {
        HardControlType type = control.state.type();
        if (type == HardControlType.STUN || type == HardControlType.ROOT) {
            addMovementLock(control.entity);
        }
        if (control.entity instanceof Mob mob) {
            if (type == HardControlType.STUN) {
                mob.getPathfinder().stopPathfinding();
                mob.setTarget(null);
                mob.setAI(false);
            } else if (type.forcesBehavior()) {
                mob.setTarget(null);
                mob.setAI(true);
                moveForced(control, mob);
            }
        }
        if (type == HardControlType.STUN) {
            Vector velocity = control.entity.getVelocity();
            control.entity.setVelocity(new Vector(0.0, velocity.getY(), 0.0));
        }
    }

    private void maintainPhysicalState(ActiveControl control) {
        HardControlType type = control.state.type();
        if (type == HardControlType.STUN) {
            Vector velocity = control.entity.getVelocity();
            control.entity.setVelocity(new Vector(0.0, velocity.getY(), 0.0));
            if (control.entity instanceof Mob mob) {
                mob.getPathfinder().stopPathfinding();
                mob.setTarget(null);
                mob.setAI(false);
            }
        } else if (type.forcesBehavior()
                && control.entity instanceof Mob mob) {
            mob.setTarget(null);
            if (!mob.hasAI()) {
                mob.setAI(true);
            }
            moveForced(control, mob);
        }
    }

    private void moveForced(ActiveControl control, Mob mob) {
        Location source = resolveSourceLocation(control);
        if (source == null
                || !source.getWorld().equals(mob.getWorld())) {
            mob.getPathfinder().stopPathfinding();
            return;
        }
        if (control.state.type() == HardControlType.CHARM) {
            if (mob.getLocation().distanceSquared(source) <= 2.25) {
                mob.getPathfinder().stopPathfinding();
            } else {
                mob.getPathfinder().moveTo(source, 1.1);
            }
            return;
        }
        Vector away = mob.getLocation().toVector()
                .subtract(source.toVector())
                .setY(0.0);
        if (away.lengthSquared() < 0.0001) {
            away = new Vector(1.0, 0.0, 0.0);
        }
        Location destination = mob.getLocation().clone()
                .add(away.normalize().multiply(FORCED_MOVE_DISTANCE));
        mob.getPathfinder().moveTo(destination, 1.15);
    }

    private Location resolveSourceLocation(ActiveControl control) {
        UUID sourceId = control.state.sourceId();
        if (sourceId != null) {
            Entity source = plugin.getServer().getEntity(sourceId);
            if (source instanceof LivingEntity living
                    && living.isValid()
                    && !living.isDead()) {
                control.sourceLocation = living.getLocation().clone();
            }
        }
        return control.sourceLocation == null
                ? null
                : control.sourceLocation.clone();
    }

    private void endControl(
            ActiveControl control,
            HardControlRemovalReason reason,
            boolean notify
    ) {
        control.removalReason = reason;
        removeMovementLock(control.entity);
        if (control.entity.isValid()
                && control.entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setAI(control.previousAi);
            if (control.previousAi && control.previousTargetId != null) {
                Entity previous =
                        plugin.getServer().getEntity(control.previousTargetId);
                if (previous instanceof LivingEntity living
                        && living.isValid()
                        && !living.isDead()
                        && living.getWorld().equals(mob.getWorld())) {
                    mob.setTarget(living);
                }
            }
        }
        if (notify) {
            changeListener.onChange(
                    control.entity, control.state, null);
        }
    }

    private void addMovementLock(LivingEntity target) {
        AttributeInstance movement =
                target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        removeMovementLock(target);
        movement.addTransientModifier(new AttributeModifier(
                movementModifierKey,
                -1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeMovementLock(LivingEntity target) {
        AttributeInstance movement =
                target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        AttributeModifier modifier =
                movement.getModifier(movementModifierKey);
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
            HardControlType type,
            int totalTicks,
            int remainingTicks,
            long endTick
    ) {
    }

    @FunctionalInterface
    public interface ControlChangeListener {
        void onChange(
                LivingEntity entity,
                HardControlState previous,
                HardControlState current);
    }

    private static final class ActiveControl {
        private final LivingEntity entity;
        private HardControlState state;
        private Location sourceLocation;
        private final UUID worldId;
        private final UUID previousTargetId;
        private final boolean previousAi;
        private HardControlRemovalReason removalReason =
                HardControlRemovalReason.NONE;

        private ActiveControl(
                LivingEntity entity,
                HardControlState state,
                Location sourceLocation,
                UUID worldId,
                UUID previousTargetId,
                boolean previousAi
        ) {
            this.entity = entity;
            this.state = state;
            this.sourceLocation = sourceLocation;
            this.worldId = worldId;
            this.previousTargetId = previousTargetId;
            this.previousAi = previousAi;
        }
    }
}
