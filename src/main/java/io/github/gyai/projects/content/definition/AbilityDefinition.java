package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.ability.TargetSelector;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;

import java.util.List;
import java.util.Optional;

/**
 * Bukkit-free schema-v1 authoring document for a mob ability.
 *
 * <p>This type is intentionally separate from the runtime
 * {@code io.github.gyai.projects.ability.AbilityDefinition}.</p>
 */
public record AbilityDefinition(
        int schemaVersion,
        String abilityId,
        long revision,
        String displayName,
        Timing timing,
        Targeting targeting,
        List<TimelineAction> timeline,
        InterruptPolicy interruptPolicy,
        String visualReference
) {
    public static final int SCHEMA_VERSION = 1;

    public AbilityDefinition {
        timeline = DefinitionSupport.immutableList(timeline);
    }

    public AbilityDefinition(int schemaVersion, String abilityId, long revision,
                             String displayName, Timing timing, Targeting targeting,
                             List<TimelineAction> timeline, InterruptPolicy interruptPolicy) {
        this(schemaVersion, abilityId, revision, displayName, timing, targeting, timeline,
                interruptPolicy, null);
    }

    public String id() {
        return abilityId;
    }

    public List<TimelineAction> actions() {
        return timeline;
    }

    /** A nullable string keeps the document representation simple and codec-friendly. */
    public Optional<String> visualDefinitionReference() {
        return Optional.ofNullable(visualReference);
    }

    public record Timing(int castTicks, int recoveryTicks, int cooldownTicks) {
    }

    /** Ability-level target policy; individual actions may choose their own target selector. */
    public record Targeting(TargetSelector selector, double maxRange) {
        public Targeting(TargetSelector selector) {
            this(selector, 64.0);
        }

        public TargetSelector target() {
            return selector;
        }
    }

    public enum InterruptPolicy {
        NOT_INTERRUPTIBLE,
        ON_HARD_CONTROL,
        ON_DAMAGE,
        ON_DEATH,
        ALWAYS
    }

    /** Ordered actions form the authoring timeline; no expression language is involved. */
    public sealed interface TimelineAction
            permits Wait, Telegraph, Damage, Charge, Knockback {
        String stepId();

        default String actionId() {
            return stepId();
        }
    }

    public record Wait(String stepId, int ticks) implements TimelineAction {
        public Wait(int ticks) {
            this("wait", ticks);
        }

        public int durationTicks() {
            return ticks;
        }
    }

    public record Telegraph(
            String stepId,
            TargetSelector origin,
            RelativeShape shape,
            int durationTicks,
            boolean lockAtCreation
    ) implements TimelineAction {
        public Telegraph(String stepId, RelativeShape shape, TargetSelector origin,
                         int durationTicks, boolean lockAtCreation) {
            this(stepId, origin, shape, durationTicks, lockAtCreation);
        }

        public Telegraph(RelativeShape shape, TargetSelector origin,
                         int durationTicks, boolean lockAtCreation) {
            this("telegraph", origin, shape, durationTicks, lockAtCreation);
        }
    }

    public record Damage(
            String stepId,
            TargetSelector target,
            RelativeShape shape,
            DamageType damageType,
            DamageKind damageKind,
            double fixedDamage,
            double coefficient,
            boolean criticalAllowed,
            AttackMetadata metadata
    ) implements TimelineAction {
        public Damage(String stepId, RelativeShape shape, TargetSelector target,
                      DamageType damageType, DamageKind damageKind,
                      double fixedDamage, double coefficient,
                      boolean criticalAllowed, AttackMetadata metadata) {
            this(stepId, target, shape, damageType, damageKind, fixedDamage,
                    coefficient, criticalAllowed, metadata);
        }

        public Damage(RelativeShape shape, TargetSelector target,
                      DamageType damageType, DamageKind damageKind,
                      double fixedDamage, double coefficient,
                      boolean criticalAllowed, AttackMetadata metadata) {
            this("damage", target, shape, damageType, damageKind, fixedDamage,
                    coefficient, criticalAllowed, metadata);
        }
    }

    /** Movement action whose relative path is a typed line rather than a free-form script. */
    public record Charge(
            String stepId,
            TargetSelector target,
            Line path,
            int durationTicks,
            double speed
    ) implements TimelineAction {
        public Charge(String stepId, Line path, TargetSelector target,
                      int durationTicks, double speed) {
            this(stepId, target, path, durationTicks, speed);
        }

        public Charge(Line path, TargetSelector target, int durationTicks, double speed) {
            this("charge", target, path, durationTicks, speed);
        }
    }

    public record Knockback(
            String stepId,
            TargetSelector target,
            RelativeShape shape,
            double horizontalStrength,
            double verticalStrength
    ) implements TimelineAction {
        public Knockback(String stepId, RelativeShape shape, TargetSelector target,
                         double horizontalStrength, double verticalStrength) {
            this(stepId, target, shape, horizontalStrength, verticalStrength);
        }

        public Knockback(RelativeShape shape, TargetSelector target,
                         double horizontalStrength, double verticalStrength) {
            this("knockback", target, shape, horizontalStrength, verticalStrength);
        }
    }

    /** Relative, horizontal authoring geometry shared by telegraph and action records. */
    public sealed interface RelativeShape permits Circle, Donut, Line {
    }

    public record Circle(double radius) implements RelativeShape {
    }

    public record Donut(double innerRadius, double outerRadius) implements RelativeShape {
    }

    public record Line(double length, double width) implements RelativeShape {
    }
}
