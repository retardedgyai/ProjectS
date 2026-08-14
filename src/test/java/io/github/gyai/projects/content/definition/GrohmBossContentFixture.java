package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.ability.TargetSelector;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contract-only multi-phase Grohm example; it is not runtime registration. */
public final class GrohmBossContentFixture {
    public static final String MOB_ID = "projects:mob/grohm";
    public static final String SLAM_ID = "projects:ability/grohm/slam";
    public static final String CHARGE_ID = "projects:ability/grohm/charge";
    public static final String SHOCKWAVE_ID = "projects:ability/grohm/shockwave";
    public static final String SLAM_VISUAL_ID = "projects:vfx/grohm/slam-circle";
    public static final String CHARGE_VISUAL_ID = "projects:vfx/grohm/charge-line";
    public static final String SHOCKWAVE_VISUAL_ID = "projects:vfx/grohm/shockwave-donut";
    public static final String EQUIPMENT_ID = "projects:equipment/grohm/anchor";
    public static final String REWARD_ID = "projects:reward/grohm-placeholder";
    public static final String ENCOUNTER_ID = "projects:encounter/grohm";

    private GrohmBossContentFixture() {
    }

    public static ContentDefinitionValidator.Catalog catalog() {
        return new ContentDefinitionValidator.Catalog(
                List.of(mob()),
                List.of(slam(), charge(), shockwave()),
                List.of(encounter()),
                List.of(slamVisual(), chargeVisual(), shockwaveVisual()),
                List.of(REWARD_ID),
                List.of(EQUIPMENT_ID),
                List.of("minecraft:ravager"));
    }

    public static MobDefinition mob() {
        return new MobDefinition(
                1,
                MOB_ID,
                1,
                new MobDefinition.Presentation("Grohm", "boss_bar"),
                "minecraft:ravager",
                MobDefinition.Category.BOSS,
                new MobDefinition.Stats(1200.0, 24.0, 0.35, 1.0, 32.0, 1.0),
                Map.of(),
                Map.of(),
                 List.of(EQUIPMENT_ID),
                List.of(SLAM_ID, CHARGE_ID, SHOCKWAVE_ID));
    }

    public static AbilityDefinition slam() {
        return new AbilityDefinition(
                1,
                SLAM_ID,
                1,
                "Breakwater Slam",
                new AbilityDefinition.Timing(0, 10, 80),
                new AbilityDefinition.Targeting(TargetSelector.PRIMARY_TARGET, 7.0),
                List.of(
                        new AbilityDefinition.Wait("windup", 20),
                        new AbilityDefinition.Telegraph("slam-warning", TargetSelector.SELF,
                                new AbilityDefinition.Circle(3.5), 20, true),
                        new AbilityDefinition.Damage("slam-hit", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Circle(3.5), DamageType.PHYSICAL,
                                DamageKind.DIRECT_SKILL, 18.0, 0.0, false,
                                new AttackMetadata(Set.of(AttackTag.PHYSICAL), null)),
                        new AbilityDefinition.Knockback("slam-knockback", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Circle(3.5), 0.6, 0.25)),
                AbilityDefinition.InterruptPolicy.ON_HARD_CONTROL,
                 SLAM_VISUAL_ID);
    }

    public static AbilityDefinition charge() {
        return new AbilityDefinition(
                1,
                CHARGE_ID,
                1,
                "Hullbreaker Charge",
                new AbilityDefinition.Timing(0, 12, 120),
                new AbilityDefinition.Targeting(TargetSelector.PRIMARY_TARGET, 32.0),
                List.of(
                        new AbilityDefinition.Telegraph("charge-warning", TargetSelector.SELF,
                                new AbilityDefinition.Line(12.0, 1.25), 15, true),
                        new AbilityDefinition.Charge("charge-movement",
                                new AbilityDefinition.Line(12.0, 1.0),
                                TargetSelector.PRIMARY_TARGET, 18, 1.0),
                        new AbilityDefinition.Damage("charge-hit", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Line(12.0, 1.5), DamageType.PHYSICAL,
                                DamageKind.DIRECT_SKILL, 24.0, 0.0, false,
                                new AttackMetadata(Set.of(AttackTag.PHYSICAL), null))),
                AbilityDefinition.InterruptPolicy.ON_HARD_CONTROL,
                 CHARGE_VISUAL_ID);
    }

    public static AbilityDefinition shockwave() {
        return new AbilityDefinition(
                1,
                SHOCKWAVE_ID,
                1,
                "Deep Tide Shockwave",
                new AbilityDefinition.Timing(0, 10, 100),
                new AbilityDefinition.Targeting(TargetSelector.SELF, 8.0),
                List.of(
                        new AbilityDefinition.Telegraph("shockwave-warning", TargetSelector.SELF,
                                new AbilityDefinition.Donut(2.0, 6.0), 25, true),
                        new AbilityDefinition.Damage("shockwave-hit", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Donut(2.0, 6.0), DamageType.MAGICAL,
                                DamageKind.DIRECT_SKILL, 20.0, 0.0, false,
                                new AttackMetadata(Set.of(AttackTag.MAGIC), null)),
                        new AbilityDefinition.Knockback("shockwave-knockback",
                                TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Donut(2.0, 6.0), 0.8, 0.3)),
                AbilityDefinition.InterruptPolicy.ON_HARD_CONTROL,
                 SHOCKWAVE_VISUAL_ID);
    }

    public static EncounterDefinition encounter() {
        EncounterDefinition.ActorBehavior phaseOneBehavior = active(
                List.of(SLAM_ID, CHARGE_ID),
                new EncounterDefinition.OrderedSelection(List.of(SLAM_ID, CHARGE_ID)));
        EncounterDefinition.ActorBehavior phaseTwoBehavior = active(
                List.of(SLAM_ID, CHARGE_ID, SHOCKWAVE_ID),
                new EncounterDefinition.WeightedSelection(List.of(
                        new EncounterDefinition.WeightedAbility(SLAM_ID, 1.0),
                        new EncounterDefinition.WeightedAbility(CHARGE_ID, 1.0),
                        new EncounterDefinition.WeightedAbility(SHOCKWAVE_ID, 2.0))));
        EncounterDefinition.ActorBehavior downedBehavior = new EncounterDefinition.ActorBehavior(
                "grohm", EncounterDefinition.ActorState.DOWNED, Set.of(), null);
        EncounterDefinition.Phase phaseOne = new EncounterDefinition.Phase(
                "phase-one", true, List.of(phaseOneBehavior),
                List.of(new EncounterDefinition.Transition(
                        "health-half", new EncounterDefinition.ActorHealthRatioAtMost("grohm", 0.5),
                        "phase-two")));
        EncounterDefinition.Phase phaseTwo = new EncounterDefinition.Phase(
                "phase-two", false, List.of(phaseTwoBehavior),
                List.of(new EncounterDefinition.Transition(
                        "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(120,
                                EncounterDefinition.Clock.PHASE), "phase-downed",
                        List.of(new EncounterDefinition.ActorStateTransition("grohm",
                                EncounterDefinition.ActorState.ACTIVE,
                                EncounterDefinition.ActorState.DOWNED,
                                EncounterDefinition.DownControlPolicy.ENTER_DOWN)))));
        EncounterDefinition.Phase phaseDowned = new EncounterDefinition.Phase(
                "phase-downed", false, List.of(downedBehavior),
                List.of(new EncounterDefinition.Transition(
                        "leave-down", new EncounterDefinition.ElapsedTicksAtLeast(60,
                                EncounterDefinition.Clock.PHASE), "phase-two",
                        List.of(new EncounterDefinition.ActorStateTransition("grohm",
                                EncounterDefinition.ActorState.DOWNED,
                                EncounterDefinition.ActorState.ACTIVE,
                                EncounterDefinition.DownControlPolicy.EXIT_DOWN)))));
        return new EncounterDefinition(
                1,
                ENCOUNTER_ID,
                1,
                List.of(new EncounterDefinition.Actor("grohm", MOB_ID)),
                List.of(phaseOne, phaseTwo, phaseDowned),
                new EncounterDefinition.ResetPolicy(24.0, 100),
                new EncounterDefinition.VictoryPolicy(new EncounterDefinition.ActorHealthRatioAtMost(
                        "grohm", 0.0)),
                new EncounterDefinition.FailurePolicy(
                        new EncounterDefinition.Any(List.of(
                                new EncounterDefinition.ElapsedTicksAtLeast(7_200,
                                        EncounterDefinition.Clock.ENCOUNTER),
                                new EncounterDefinition.All(List.of(
                                        new EncounterDefinition.ElapsedTicksAtLeast(7_200,
                                                EncounterDefinition.Clock.ENCOUNTER),
                                        EncounterDefinition.Always.INSTANCE)))),
                        EncounterDefinition.FailureMode.RESET),
                List.of(REWARD_ID));
    }

    private static EncounterDefinition.ActorBehavior active(List<String> abilities,
                                                             EncounterDefinition.AbilitySelectionPolicy selection) {
        return new EncounterDefinition.ActorBehavior("grohm", EncounterDefinition.ActorState.ACTIVE,
                abilities, selection);
    }

    public static AbilityVisualDefinition slamVisual() {
        return visual(SLAM_VISUAL_ID, "grohm-circle", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                new AbilityVisualDefinition.Literal(3.5), null);
    }

    public static AbilityVisualDefinition chargeVisual() {
        return visual(CHARGE_VISUAL_ID, "grohm-line", AbilityVisualDefinition.PrimitiveType.LINE,
                null, new AbilityVisualDefinition.Literal(12.0));
    }

    public static AbilityVisualDefinition shockwaveVisual() {
        AbilityVisualDefinition.PrimitiveSpec outer = arc("grohm-donut-outer", 6.0);
        AbilityVisualDefinition.PrimitiveSpec inner = arc("grohm-donut-inner", 2.0);
        return new AbilityVisualDefinition(
                1,
                SHOCKWAVE_VISUAL_ID,
                List.of(new AbilityVisualDefinition.HookBinding(
                        io.github.gyai.projects.ability.AbilityLifecycleEvent.Hook.TELEGRAPH,
                        List.of(new AbilityVisualDefinition.Emission(
                                "grohm-donut-telegraph", -1, List.of(outer, inner))))));
    }

    private static AbilityVisualDefinition.PrimitiveSpec arc(String id, double radius) {
        return new AbilityVisualDefinition.PrimitiveSpec(
                id,
                AbilityVisualDefinition.PrimitiveType.ARC,
                0,
                20,
                0x7fff3344,
                1.0,
                8,
                1L,
                new AbilityVisualDefinition.Vec(0.0, 0.0, 0.0),
                0.0,
                null,
                new AbilityVisualDefinition.Literal(radius),
                null,
                null,
                null,
                null,
                new AbilityVisualDefinition.Literal(2.0 * Math.PI),
                null,
                0,
                List.of());
    }

    private static AbilityVisualDefinition visual(String visualId, String primitiveId,
                                                  AbilityVisualDefinition.PrimitiveType type,
                                                  AbilityVisualDefinition.Scalar radius,
                                                  AbilityVisualDefinition.Scalar length) {
        AbilityVisualDefinition.PrimitiveSpec primitive =
                new AbilityVisualDefinition.PrimitiveSpec(
                        primitiveId,
                        type,
                        0,
                        20,
                        0x7fff3344,
                        1.0,
                        8,
                        1L,
                        new AbilityVisualDefinition.Vec(0.0, 0.0, 0.0),
                        0.0,
                        null,
                        radius,
                        length,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        List.of());
        return new AbilityVisualDefinition(
                1,
                visualId,
                List.of(new AbilityVisualDefinition.HookBinding(
                        io.github.gyai.projects.ability.AbilityLifecycleEvent.Hook.TELEGRAPH,
                        List.of(new AbilityVisualDefinition.Emission(
                                "grohm-telegraph", -1, List.of(primitive))))));
    }
}
