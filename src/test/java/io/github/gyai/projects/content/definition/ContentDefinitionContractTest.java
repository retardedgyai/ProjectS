package io.github.gyai.projects.content.definition;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.ability.TargetSelector;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Assertion-main coverage for the frozen schema-v1 contract. */
public final class ContentDefinitionContractTest {
    private ContentDefinitionContractTest() {
    }

    public static void main(String[] args) {
        validGrohmFixture();
        unresolvedReferences();
        actorBehaviorRules();
        actorMobAbilityReferences();
        transitionPolicyRules();
        actorStateEffectRules();
        duplicateLocalIds();
        entryPhaseRules();
        graphRules();
        cycleProgressRules();
        largePhaseGraphRules();
        overDepthProgressAnalysis();
        selectionRules();
        conditionClockRules();
        catalogAndDataRules();
        entityTypeCatalogRules();
        elementMapKeyRules();
        damageMetadataRules();
        damageMetadataBounds();
        maxIdLengthPolicy();
        deterministicIssues();
        sourceBoundary();
        System.out.println("Content definition contract tests passed");
    }

    private static void validGrohmFixture() {
        ContentDefinitionValidator.Catalog catalog = GrohmBossContentFixture.catalog();
        ContentDefinitionValidator.ValidationResult result =
                new ContentDefinitionValidator().validate(catalog);
        assert result.valid() : result.issues();

        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        assert encounter.phases().size() == 3;
        assert encounter.phases().get(0).actorBehaviors().getFirst().state()
                == EncounterDefinition.ActorState.ACTIVE;
        assert encounter.phases().get(1).actorBehaviors().getFirst().state()
                == EncounterDefinition.ActorState.ACTIVE;
        EncounterDefinition.Phase downed = encounter.phases().get(2);
        assert downed.actorBehaviors().getFirst().state() == EncounterDefinition.ActorState.DOWNED;
        assert downed.actorBehaviors().getFirst().allowedAbilityReferences().isEmpty();
        assert downed.actorBehaviors().getFirst().abilitySelectionPolicy() == null;

        EncounterDefinition.Transition enterDown = encounter.phases().get(1).transitions().getFirst();
        EncounterDefinition.ActorStateTransition enterEffect =
                enterDown.actorStateTransitions().getFirst();
        assert enterEffect.downControlPolicy() == EncounterDefinition.DownControlPolicy.ENTER_DOWN;
        EncounterDefinition.Transition leaveDown = downed.transitions().getFirst();
        assert leaveDown.condition() instanceof EncounterDefinition.ElapsedTicksAtLeast;
        assert ((EncounterDefinition.ElapsedTicksAtLeast) leaveDown.condition()).clock()
                == EncounterDefinition.Clock.PHASE;
        assert leaveDown.actorStateTransitions().getFirst().downControlPolicy()
                == EncounterDefinition.DownControlPolicy.EXIT_DOWN;

        EncounterDefinition.ActorBehavior firstBehavior =
                encounter.phases().getFirst().actorBehaviors().getFirst();
        EncounterDefinition.ActorBehavior secondBehavior =
                encounter.phases().get(1).actorBehaviors().getFirst();
        assert firstBehavior.abilitySelectionPolicy()
                instanceof EncounterDefinition.OrderedSelection;
        assert secondBehavior.abilitySelectionPolicy()
                instanceof EncounterDefinition.WeightedSelection;

        assert GrohmBossContentFixture.slam().timeline().stream()
                .anyMatch(action -> action instanceof AbilityDefinition.Telegraph
                        && ((AbilityDefinition.Telegraph) action).shape()
                        instanceof AbilityDefinition.Circle);
        assert GrohmBossContentFixture.charge().timeline().stream()
                .anyMatch(action -> action instanceof AbilityDefinition.Telegraph
                        && ((AbilityDefinition.Telegraph) action).shape()
                        instanceof AbilityDefinition.Line);
        assert GrohmBossContentFixture.shockwave().timeline().stream()
                .anyMatch(action -> action instanceof AbilityDefinition.Telegraph
                        && ((AbilityDefinition.Telegraph) action).shape()
                        instanceof AbilityDefinition.Donut);

        Map<String, AbilityVisualDefinition> visuals = catalog.visuals().stream()
                .collect(java.util.stream.Collectors.toMap(AbilityVisualDefinition::id, value -> value));
        assert visuals.get(GrohmBossContentFixture.SLAM_VISUAL_ID).bindings().getFirst()
                .emissions().getFirst().primitives().getFirst().type()
                == AbilityVisualDefinition.PrimitiveType.CIRCLE;
        assert visuals.get(GrohmBossContentFixture.CHARGE_VISUAL_ID).bindings().getFirst()
                .emissions().getFirst().primitives().getFirst().type()
                == AbilityVisualDefinition.PrimitiveType.LINE;
        List<AbilityVisualDefinition.PrimitiveSpec> donutPrimitives = visuals
                .get(GrohmBossContentFixture.SHOCKWAVE_VISUAL_ID).bindings().getFirst()
                .emissions().getFirst().primitives();
        assert donutPrimitives.size() == 2;
        assert donutPrimitives.stream().allMatch(
                primitive -> primitive.type() == AbilityVisualDefinition.PrimitiveType.ARC);
        assert GrohmBossContentFixture.mob().equipmentReferences()
                .contains(GrohmBossContentFixture.EQUIPMENT_ID);
    }

    private static void unresolvedReferences() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition broken = new EncounterDefinition(
                encounter.schemaVersion(), encounter.encounterId(), encounter.revision(),
                List.of(new EncounterDefinition.Actor("grohm", "projects:mob/missing")),
                encounter.phases(), encounter.resetPolicy(), encounter.victoryPolicy(),
                encounter.failurePolicy(), List.of("projects:reward/missing"));
        ContentDefinitionValidator.Catalog catalog = catalogWith(
                GrohmBossContentFixture.mob(), broken, List.of(GrohmBossContentFixture.REWARD_ID));
        Set<String> codes = codes(catalog);
        assert codes.contains(ContentDefinitionValidator.Codes.UNRESOLVED_MOB_REFERENCE);
        assert codes.contains(ContentDefinitionValidator.Codes.UNRESOLVED_REWARD_REFERENCE);
    }

    private static void actorBehaviorRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase base = encounter.phases().getFirst();
        EncounterDefinition.ActorBehavior activeWithoutPool = new EncounterDefinition.ActorBehavior(
                "grohm", EncounterDefinition.ActorState.ACTIVE, Set.of(),
                new EncounterDefinition.OrderedSelection(List.of()));
        assert codes(withEncounter(withPhases(encounter, List.of(new EncounterDefinition.Phase(
                base.phaseId(), true, List.of(activeWithoutPool), base.transitions())))))
                .contains(ContentDefinitionValidator.Codes.MISSING_ACTOR_ABILITY);

        EncounterDefinition.ActorBehavior downedWithPool = new EncounterDefinition.ActorBehavior(
                "grohm", EncounterDefinition.ActorState.DOWNED, Set.of(GrohmBossContentFixture.SLAM_ID),
                new EncounterDefinition.OrderedSelection(List.of(GrohmBossContentFixture.SLAM_ID)));
        assert codes(withEncounter(withPhases(encounter, List.of(new EncounterDefinition.Phase(
                base.phaseId(), true, List.of(downedWithPool), base.transitions())))))
                .contains(ContentDefinitionValidator.Codes.DOWNED_ABILITY_POOL);

        EncounterDefinition.ActorBehavior unknownActor = new EncounterDefinition.ActorBehavior(
                "missing", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.SLAM_ID),
                new EncounterDefinition.OrderedSelection(List.of(GrohmBossContentFixture.SLAM_ID)));
        assert codes(withEncounter(withPhases(encounter, List.of(new EncounterDefinition.Phase(
                base.phaseId(), true, List.of(unknownActor), base.transitions())))))
                .contains(ContentDefinitionValidator.Codes.UNRESOLVED_ACTOR_REFERENCE);
    }

    private static void actorMobAbilityReferences() {
        MobDefinition mob = GrohmBossContentFixture.mob();
        MobDefinition withoutSlam = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(),
                mob.entityType(), mob.category(), mob.stats(), mob.elementValues(),
                mob.resistanceValues(), mob.equipmentReferences(),
                List.of(GrohmBossContentFixture.CHARGE_ID, GrohmBossContentFixture.SHOCKWAVE_ID));
        ContentDefinitionValidator validator = new ContentDefinitionValidator();
        ContentDefinitionValidator.Catalog catalog = catalogWith(
                withoutSlam, GrohmBossContentFixture.encounter(),
                List.of(GrohmBossContentFixture.REWARD_ID));
        ContentDefinitionValidator.ValidationResult first = validator.validate(catalog);
        ContentDefinitionValidator.ValidationResult second = validator.validate(catalog);

        assert !first.valid() : first.issues();
        assert first.issues().equals(second.issues()) : first.issues();
        assert first.issues().stream().map(ContentDefinitionValidator.Issue::code)
                .allMatch(ContentDefinitionValidator.Codes.MISSING_PHASE_ABILITY::equals)
                : first.issues();
        assert first.issues().stream().map(ContentDefinitionValidator.Issue::path).toList().equals(List.of(
                "encounters[0].phases[0].actorBehaviors[0].abilitySelectionPolicy.abilityReferences[0]",
                "encounters[0].phases[0].actorBehaviors[0].allowedAbilityReferences[1]",
                "encounters[0].phases[1].actorBehaviors[0].abilitySelectionPolicy.entries[0].abilityReference",
                "encounters[0].phases[1].actorBehaviors[0].allowedAbilityReferences[2]"))
                : first.issues();

        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        String secondMobId = "projects:mob/second";
        MobDefinition secondMob = new MobDefinition(
                mob.schemaVersion(), secondMobId, mob.revision(), mob.presentation(),
                mob.entityType(), mob.category(), mob.stats(), mob.elementValues(),
                mob.resistanceValues(), mob.equipmentReferences(),
                List.of(GrohmBossContentFixture.CHARGE_ID));
        EncounterDefinition.ActorBehavior secondPhaseOne = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.CHARGE_ID),
                new EncounterDefinition.OrderedSelection(
                        List.of(GrohmBossContentFixture.CHARGE_ID)));
        EncounterDefinition.ActorBehavior secondPhaseTwo = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.CHARGE_ID),
                new EncounterDefinition.WeightedSelection(List.of(
                        new EncounterDefinition.WeightedAbility(
                                GrohmBossContentFixture.CHARGE_ID, 1.0))));
        EncounterDefinition.ActorBehavior secondDownedPhase = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.CHARGE_ID),
                new EncounterDefinition.OrderedSelection(
                        List.of(GrohmBossContentFixture.CHARGE_ID)));
        EncounterDefinition multiActor = withSecondActor(encounter, secondMobId,
                secondPhaseOne, secondPhaseTwo, secondDownedPhase);
        ContentDefinitionValidator.Catalog multiCatalog = new ContentDefinitionValidator.Catalog(
                List.of(mob, secondMob), catalog.abilities(), List.of(multiActor),
                catalog.visuals(), catalog.rewardReferences(), catalog.equipmentIds(),
                catalog.validEntityTypeIds());
        assert validator.validate(multiCatalog).valid();

        EncounterDefinition.ActorBehavior wrongSecondPhaseOne = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.SLAM_ID),
                new EncounterDefinition.OrderedSelection(
                        List.of(GrohmBossContentFixture.SLAM_ID)));
        EncounterDefinition negativeMultiActor = withSecondActor(encounter, secondMobId,
                wrongSecondPhaseOne, secondPhaseTwo, secondDownedPhase);
        ContentDefinitionValidator.ValidationResult negative = validator.validate(
                new ContentDefinitionValidator.Catalog(
                        List.of(mob, secondMob), catalog.abilities(), List.of(negativeMultiActor),
                        catalog.visuals(), catalog.rewardReferences(), catalog.equipmentIds(),
                        catalog.validEntityTypeIds()));
        assert hasIssue(negative, ContentDefinitionValidator.Codes.MISSING_PHASE_ABILITY,
                "encounters[0].phases[0].actorBehaviors[1].abilitySelectionPolicy.abilityReferences[0]");
        assert hasIssue(negative, ContentDefinitionValidator.Codes.MISSING_PHASE_ABILITY,
                "encounters[0].phases[0].actorBehaviors[1].allowedAbilityReferences[0]");
    }

    private static void transitionPolicyRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase first = encounter.phases().getFirst();
        EncounterDefinition.Phase second = encounter.phases().get(1);
        EncounterDefinition.Phase downed = encounter.phases().get(2);
        EncounterDefinition.Transition missingPolicy = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(1), "phase-downed",
                List.of(new EncounterDefinition.ActorStateTransition("grohm",
                        EncounterDefinition.ActorState.ACTIVE, EncounterDefinition.ActorState.DOWNED)));
        EncounterDefinition.Phase brokenSecond = new EncounterDefinition.Phase(
                second.phaseId(), false, second.actorBehaviors(), List.of(missingPolicy));
        assert codes(withEncounter(withPhases(encounter, List.of(first, brokenSecond, downed))))
                .contains(ContentDefinitionValidator.Codes.MISSING_DOWN_CONTROL_POLICY);

        EncounterDefinition.Transition invalidPolicy = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(1), "phase-downed",
                List.of(new EncounterDefinition.ActorStateTransition("grohm",
                        EncounterDefinition.ActorState.ACTIVE, EncounterDefinition.ActorState.DOWNED,
                        EncounterDefinition.DownControlPolicy.EXIT_DOWN)));
        brokenSecond = new EncounterDefinition.Phase(
                second.phaseId(), false, second.actorBehaviors(), List.of(invalidPolicy));
        assert codes(withEncounter(withPhases(encounter, List.of(first, brokenSecond, downed))))
                .contains(ContentDefinitionValidator.Codes.INVALID_DOWN_CONTROL_POLICY);

        for (EncounterDefinition.DownControlPolicy policy
                : EncounterDefinition.DownControlPolicy.values()) {
            assert !policy.buffersNewCc();
            assert !policy.restoresPreviousCc();
        }
        assert EncounterDefinition.DownControlPolicy.ENTER_DOWN.cancelsCurrentAbility();
        assert EncounterDefinition.DownControlPolicy.ENTER_DOWN.clearsCurrentCc();
        assert EncounterDefinition.DownControlPolicy.ENTER_DOWN.suppressesNewCc();
        assert !EncounterDefinition.DownControlPolicy.EXIT_DOWN.clearsCurrentCc();
    }

    private static void actorStateEffectRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase first = encounter.phases().getFirst();
        EncounterDefinition.Phase second = encounter.phases().get(1);
        EncounterDefinition.Phase downed = encounter.phases().get(2);

        EncounterDefinition.Transition missingEffect = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(120,
                        EncounterDefinition.Clock.PHASE), "phase-downed");
        EncounterDefinition.Phase missingEffectPhase = new EncounterDefinition.Phase(
                second.phaseId(), false, second.actorBehaviors(), List.of(missingEffect));
        assert codes(withEncounter(withPhases(encounter,
                List.of(first, missingEffectPhase, downed))))
                .contains(ContentDefinitionValidator.Codes.MISSING_STATE_TRANSITION);

        EncounterDefinition.ActorStateTransition mismatchedEffect =
                new EncounterDefinition.ActorStateTransition("grohm",
                        EncounterDefinition.ActorState.DOWNED,
                        EncounterDefinition.ActorState.ACTIVE,
                        EncounterDefinition.DownControlPolicy.EXIT_DOWN);
        EncounterDefinition.Transition mismatched = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(120,
                        EncounterDefinition.Clock.PHASE), "phase-downed",
                List.of(mismatchedEffect));
        EncounterDefinition.Phase mismatchedPhase = new EncounterDefinition.Phase(
                second.phaseId(), false, second.actorBehaviors(), List.of(mismatched));
        assert codes(withEncounter(withPhases(encounter,
                List.of(first, mismatchedPhase, downed))))
                .contains(ContentDefinitionValidator.Codes.INVALID_STATE_TRANSITION);

        EncounterDefinition.ActorStateTransition redundantEffect =
                new EncounterDefinition.ActorStateTransition("grohm",
                        EncounterDefinition.ActorState.ACTIVE,
                        EncounterDefinition.ActorState.DOWNED,
                        EncounterDefinition.DownControlPolicy.ENTER_DOWN);
        EncounterDefinition.Transition redundant = new EncounterDefinition.Transition(
                "health-half", new EncounterDefinition.ActorHealthRatioAtMost("grohm", 0.5),
                "phase-two", List.of(redundantEffect));
        EncounterDefinition.Phase redundantFirst = new EncounterDefinition.Phase(
                first.phaseId(), true, first.actorBehaviors(), List.of(redundant));
        assert codes(withEncounter(withPhases(encounter,
                List.of(redundantFirst, second, downed))))
                .contains(ContentDefinitionValidator.Codes.INVALID_STATE_TRANSITION);
    }

    private static void duplicateLocalIds() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase first = encounter.phases().getFirst();
        EncounterDefinition.Phase duplicate = new EncounterDefinition.Phase(
                "phase-one", false, first.actorBehaviors(), first.transitions());
        assert codes(withEncounter(withPhases(encounter, List.of(first, duplicate,
                encounter.phases().get(1), encounter.phases().get(2)))))
                .contains(ContentDefinitionValidator.Codes.DUPLICATE_LOCAL_ID);
    }

    private static void entryPhaseRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase first = encounter.phases().getFirst();
        EncounterDefinition.Phase second = encounter.phases().get(1);
        assert codes(withEncounter(withPhases(encounter, List.of(
                new EncounterDefinition.Phase(first.phaseId(), false, first.actorBehaviors(),
                        first.transitions()), second, encounter.phases().get(2)))))
                .contains(ContentDefinitionValidator.Codes.NO_ENTRY_PHASE);
        assert codes(withEncounter(withPhases(encounter, List.of(
                first,
                new EncounterDefinition.Phase(second.phaseId(), true, second.actorBehaviors(),
                        second.transitions()), encounter.phases().get(2)))))
                .contains(ContentDefinitionValidator.Codes.MULTIPLE_ENTRY_PHASES);
    }

    private static void graphRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase unreachable = new EncounterDefinition.Phase(
                "phase-three", false,
                List.of(new EncounterDefinition.ActorBehavior("grohm",
                        EncounterDefinition.ActorState.ACTIVE,
                        Set.of(GrohmBossContentFixture.SLAM_ID),
                        new EncounterDefinition.OrderedSelection(
                                List.of(GrohmBossContentFixture.SLAM_ID)))),
                List.of());
        assert codes(withEncounter(withPhases(encounter, List.of(
                encounter.phases().get(0), encounter.phases().get(1), encounter.phases().get(2),
                unreachable))))
                .contains(ContentDefinitionValidator.Codes.UNREACHABLE_PHASE);

        EncounterDefinition.ActorBehavior active = encounter.phases().getFirst()
                .actorBehaviors().getFirst();
        EncounterDefinition.Phase cycleOne = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("forward", "phase-two",
                        EncounterDefinition.Always.INSTANCE)));
        EncounterDefinition.Phase cycleTwo = new EncounterDefinition.Phase(
                "phase-two", false, List.of(active),
                List.of(new EncounterDefinition.Transition("back", "phase-one",
                        EncounterDefinition.Always.INSTANCE)));
        Set<String> cycleCodes = codes(withEncounter(withPhases(encounter,
                List.of(cycleOne, cycleTwo))));
        assert cycleCodes.contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);

        assert !codes(GrohmBossContentFixture.catalog())
                .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);
    }

    private static void cycleProgressRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.ActorBehavior active = encounter.phases().getFirst()
                .actorBehaviors().getFirst();
        List<EncounterDefinition.Condition> nonProgressing = List.of(
                EncounterDefinition.Always.INSTANCE,
                new EncounterDefinition.ActorHealthRatioAtMost("grohm", 0.5),
                new EncounterDefinition.ElapsedTicksAtLeast(1, EncounterDefinition.Clock.ENCOUNTER),
                new EncounterDefinition.ElapsedTicksAtLeast(0, EncounterDefinition.Clock.PHASE));

        for (EncounterDefinition.Condition condition : nonProgressing) {
            EncounterDefinition.Phase selfCycle = new EncounterDefinition.Phase(
                    "phase-one", true, List.of(active),
                    List.of(new EncounterDefinition.Transition("loop", condition, "phase-one")));
            assert codes(withEncounter(withPhases(encounter, List.of(selfCycle))))
                    .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE) : condition;

            EncounterDefinition.Phase cycleOne = new EncounterDefinition.Phase(
                    "phase-one", true, List.of(active),
                    List.of(new EncounterDefinition.Transition("forward", condition,
                            "phase-two")));
            EncounterDefinition.Phase cycleTwo = new EncounterDefinition.Phase(
                    "phase-two", false, List.of(active),
                    List.of(new EncounterDefinition.Transition("back", condition,
                            "phase-one")));
            assert codes(withEncounter(withPhases(encounter, List.of(cycleOne, cycleTwo))))
                    .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE) : condition;
        }

        EncounterDefinition.Condition positivePhase = new EncounterDefinition.ElapsedTicksAtLeast(
                1, EncounterDefinition.Clock.PHASE);
        EncounterDefinition.Phase safeSelfCycle = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("loop", positivePhase, "phase-one")));
        assert !codes(withEncounter(withPhases(encounter, List.of(safeSelfCycle))))
                .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);

        EncounterDefinition.Phase mixedCycleOne = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("forward",
                        EncounterDefinition.Always.INSTANCE, "phase-two")));
        EncounterDefinition.Phase mixedCycleTwo = new EncounterDefinition.Phase(
                "phase-two", false, List.of(active),
                List.of(new EncounterDefinition.Transition("back", positivePhase, "phase-one")));
        assert !codes(withEncounter(withPhases(encounter,
                List.of(mixedCycleOne, mixedCycleTwo))))
                .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);

        EncounterDefinition.Condition nestedAll = new EncounterDefinition.All(List.of(
                EncounterDefinition.Always.INSTANCE,
                new EncounterDefinition.Any(List.of(
                        positivePhase,
                        new EncounterDefinition.All(List.of(positivePhase,
                                EncounterDefinition.Always.INSTANCE))))));
        EncounterDefinition.Phase nestedAllPhase = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("loop", nestedAll, "phase-one")));
        assert !codes(withEncounter(withPhases(encounter, List.of(nestedAllPhase))))
                .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);

        EncounterDefinition.Condition unsafeAny = new EncounterDefinition.Any(List.of(
                positivePhase, EncounterDefinition.Always.INSTANCE));
        EncounterDefinition.Phase unsafeAnyPhase = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("loop", unsafeAny, "phase-one")));
        assert codes(withEncounter(withPhases(encounter, List.of(unsafeAnyPhase))))
                .contains(ContentDefinitionValidator.Codes.PHASE_CYCLE);
    }

    private static void largePhaseGraphRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.ActorBehavior active = encounter.phases().getFirst()
                .actorBehaviors().getFirst();
        ContentDefinitionValidator validator = new ContentDefinitionValidator();

        int linearPhaseCount = 50_000;
        ContentDefinitionValidator.Catalog linearCatalog = withEncounter(withPhases(encounter,
                generatedPhaseGraph(linearPhaseCount, false, active)));
        ContentDefinitionValidator.ValidationResult first = validator.validate(linearCatalog);
        ContentDefinitionValidator.ValidationResult second = validator.validate(linearCatalog);
        assert first.valid() : first.issues();
        assert first.issues().equals(second.issues())
                : "large linear graph validation must be deterministic";

        int cyclePhaseCount = 10_000;
        ContentDefinitionValidator.ValidationResult cycleResult = validator.validate(
                withEncounter(withPhases(encounter,
                        generatedPhaseGraph(cyclePhaseCount, true, active))));
        long cycleIssueCount = cycleResult.issues().stream()
                .filter(issue -> issue.code().equals(ContentDefinitionValidator.Codes.PHASE_CYCLE))
                .count();
        assert cycleIssueCount == cyclePhaseCount : cycleResult.issues().size();
        assert hasIssue(cycleResult, ContentDefinitionValidator.Codes.PHASE_CYCLE,
                "encounters[0].phases[0].phaseId");
        assert hasIssue(cycleResult, ContentDefinitionValidator.Codes.PHASE_CYCLE,
                "encounters[0].phases[" + (cyclePhaseCount - 1) + "].phaseId");
    }

    private static List<EncounterDefinition.Phase> generatedPhaseGraph(
            int phaseCount, boolean cycle, EncounterDefinition.ActorBehavior behavior) {
        String[] phaseIds = new String[phaseCount];
        for (int i = 0; i < phaseCount; i++) {
            phaseIds[i] = "phase-" + i;
        }

        List<EncounterDefinition.Phase> phases = new ArrayList<>(phaseCount);
        for (int i = 0; i < phaseCount; i++) {
            boolean hasTarget = i + 1 < phaseCount || cycle;
            List<EncounterDefinition.Transition> transitions = hasTarget
                    ? List.of(new EncounterDefinition.Transition("next",
                    EncounterDefinition.Always.INSTANCE,
                    phaseIds[i + 1 < phaseCount ? i + 1 : 0]))
                    : List.of();
            phases.add(new EncounterDefinition.Phase(phaseIds[i], i == 0,
                    List.of(behavior), transitions));
        }
        return phases;
    }

    private static void overDepthProgressAnalysis() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.ActorBehavior active = encounter.phases().getFirst()
                .actorBehaviors().getFirst();
        ContentDefinitionValidator validator = new ContentDefinitionValidator();
        int maxDepth = validator.policy().bounds().maxConditionDepth();
        int nesting = maxDepth + 10_000;
        EncounterDefinition.Condition condition = new EncounterDefinition.ElapsedTicksAtLeast(
                1, EncounterDefinition.Clock.PHASE);
        boolean sawAll = false;
        boolean sawAny = false;
        for (int i = 0; i < nesting; i++) {
            if ((i & 1) == 0) {
                condition = new EncounterDefinition.All(List.of(condition));
                sawAll = true;
            } else {
                condition = new EncounterDefinition.Any(List.of(condition));
                sawAny = true;
            }
        }
        assert sawAll && sawAny;

        EncounterDefinition.Phase deepPhase = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active),
                List.of(new EncounterDefinition.Transition("loop", condition, "phase-one")));
        ContentDefinitionValidator.Catalog catalog = withEncounter(
                withPhases(encounter, List.of(deepPhase)));
        ContentDefinitionValidator.ValidationResult first = validator.validate(catalog);
        ContentDefinitionValidator.ValidationResult second = validator.validate(catalog);

        assert !first.valid() : "over-depth condition must be invalid";
        assert first.issues().equals(second.issues()) : "validation must be deterministic";
        String expectedDepthPath = "encounters[0].phases[0].transitions[0].condition"
                + ".conditions[0]".repeat(maxDepth + 1);
        assert first.issues().stream().anyMatch(issue ->
                issue.code().equals(ContentDefinitionValidator.Codes.NUMBER_OUT_OF_RANGE)
                        && issue.path().equals(expectedDepthPath)) : first.issues();
        assert first.issues().stream().anyMatch(issue ->
                issue.code().equals(ContentDefinitionValidator.Codes.PHASE_CYCLE)
                        && issue.path().equals("encounters[0].phases[0].phaseId")) : first.issues();
    }

    private static void selectionRules() {
        EncounterDefinition encounter = GrohmBossContentFixture.encounter();
        EncounterDefinition.Phase phase = encounter.phases().get(1);
        EncounterDefinition.ActorBehavior broken = new EncounterDefinition.ActorBehavior(
                "grohm", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.SLAM_ID),
                new EncounterDefinition.WeightedSelection(List.of(
                        new EncounterDefinition.WeightedAbility(GrohmBossContentFixture.SLAM_ID, 0.0))));
        Set<String> codes = codes(withEncounter(withPhases(encounter, List.of(
                encounter.phases().getFirst(),
                new EncounterDefinition.Phase(phase.phaseId(), false, List.of(broken), phase.transitions()),
                encounter.phases().get(2)))));
        assert codes.contains(ContentDefinitionValidator.Codes.INVALID_WEIGHT);
    }

    private static void conditionClockRules() {
        assert new EncounterDefinition.ElapsedTicksAtLeast(5, EncounterDefinition.Clock.PHASE)
                .clock() == EncounterDefinition.Clock.PHASE;
        assert new EncounterDefinition.ElapsedTicksAtLeast(5, EncounterDefinition.Clock.ENCOUNTER)
                .clock() == EncounterDefinition.Clock.ENCOUNTER;
        assert Set.of(EncounterDefinition.Condition.class.getPermittedSubclasses())
                .stream().map(Class::getSimpleName).noneMatch(name -> name.contains("Cc"));
    }

    private static void catalogAndDataRules() {
        MobDefinition mob = GrohmBossContentFixture.mob();
        MobDefinition missingEquipment = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(), mob.entityType(),
                mob.category(), mob.stats(), mob.elementValues(), mob.resistanceValues(),
                List.of("projects:equipment/missing"), mob.abilityReferences());
        assert codes(catalogWith(missingEquipment, GrohmBossContentFixture.encounter(),
                List.of(GrohmBossContentFixture.REWARD_ID)))
                .contains(ContentDefinitionValidator.Codes.UNRESOLVED_EQUIPMENT_REFERENCE);

        MobDefinition invalidEntityType = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(), "RAVAGER",
                mob.category(), mob.stats(), mob.elementValues(), mob.resistanceValues(),
                mob.equipmentReferences(), mob.abilityReferences());
        assert codes(catalogWith(invalidEntityType, GrohmBossContentFixture.encounter(),
                List.of(GrohmBossContentFixture.REWARD_ID)))
                .contains(ContentDefinitionValidator.Codes.INVALID_NAMESPACED_ID);

        AbilityDefinition.Damage missingMetadata = new AbilityDefinition.Damage(
                "metadata-missing", TargetSelector.PRIMARY_TARGET,
                new AbilityDefinition.Circle(1.0), DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                1.0, 0.0, false, null);
        AbilityDefinition brokenAbility = new AbilityDefinition(
                1, "projects:ability/test/metadata", 1, "Missing Metadata",
                new AbilityDefinition.Timing(0, 1, 1),
                new AbilityDefinition.Targeting(TargetSelector.PRIMARY_TARGET, 4.0),
                List.of(missingMetadata), AbilityDefinition.InterruptPolicy.ALWAYS);
        ContentDefinitionValidator.Catalog metadataCatalog = new ContentDefinitionValidator.Catalog(
                List.of(mob),
                List.of(GrohmBossContentFixture.slam(), GrohmBossContentFixture.charge(),
                        GrohmBossContentFixture.shockwave(), brokenAbility),
                List.of(GrohmBossContentFixture.encounter()),
                 List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                         GrohmBossContentFixture.shockwaveVisual()),
                 List.of(GrohmBossContentFixture.REWARD_ID),
                 List.of(GrohmBossContentFixture.EQUIPMENT_ID),
                 List.of("minecraft:ravager"));
        assert codes(metadataCatalog).contains(ContentDefinitionValidator.Codes.MISSING_VALUE);

        assert codes(new ContentDefinitionValidator.Catalog(
                List.of(mob), List.of(GrohmBossContentFixture.slam(), GrohmBossContentFixture.charge(),
                        GrohmBossContentFixture.shockwave()), List.of(GrohmBossContentFixture.encounter()),
                 List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                         GrohmBossContentFixture.shockwaveVisual()), List.of(GrohmBossContentFixture.REWARD_ID),
                 List.of("equipment/bad"), List.of("minecraft:ravager")))
                 .contains(ContentDefinitionValidator.Codes.INVALID_NAMESPACED_ID);
     }

    private static void entityTypeCatalogRules() {
        MobDefinition mob = GrohmBossContentFixture.mob();
        ContentDefinitionValidator validator = new ContentDefinitionValidator();
        assert validator.validate(GrohmBossContentFixture.catalog()).valid();

        MobDefinition unresolved = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(),
                "minecraft:not_real", mob.category(), mob.stats(), mob.elementValues(),
                mob.resistanceValues(), mob.equipmentReferences(), mob.abilityReferences());
        ContentDefinitionValidator.ValidationResult unresolvedResult = validator.validate(
                catalogWith(unresolved, GrohmBossContentFixture.encounter(),
                        List.of(GrohmBossContentFixture.REWARD_ID)));
        assert hasIssue(unresolvedResult, ContentDefinitionValidator.Codes.UNRESOLVED_ENTITY_TYPE,
                "mobs[0].entityType");

        List<String> suppliedEntityTypes = new ArrayList<>(
                List.of("minecraft:zombie", "minecraft:ravager"));
        ContentDefinitionValidator.Catalog copiedCatalog = new ContentDefinitionValidator.Catalog(
                List.of(mob), List.of(GrohmBossContentFixture.slam(),
                        GrohmBossContentFixture.charge(), GrohmBossContentFixture.shockwave()),
                List.of(GrohmBossContentFixture.encounter()),
                List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                        GrohmBossContentFixture.shockwaveVisual()),
                List.of(GrohmBossContentFixture.REWARD_ID),
                List.of(GrohmBossContentFixture.EQUIPMENT_ID), suppliedEntityTypes);
        suppliedEntityTypes.set(0, "minecraft:not_real");
        assert copiedCatalog.validEntityTypeIds()
                .equals(List.of("minecraft:ravager", "minecraft:zombie"));
        assertThrowsUnsupported(() -> copiedCatalog.validEntityTypeIds().add("minecraft:pig"));

        ContentDefinitionValidator.Catalog invalidCatalog = new ContentDefinitionValidator.Catalog(
                List.of(mob), List.of(GrohmBossContentFixture.slam(),
                        GrohmBossContentFixture.charge(), GrohmBossContentFixture.shockwave()),
                List.of(GrohmBossContentFixture.encounter()),
                List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                        GrohmBossContentFixture.shockwaveVisual()),
                List.of(GrohmBossContentFixture.REWARD_ID),
                List.of(GrohmBossContentFixture.EQUIPMENT_ID),
                List.of("minecraft:ravager", "Minecraft:RAVAGER", "minecraft:ravager",
                        "not-an-entity"));
        List<ContentDefinitionValidator.Issue> first = validator.validate(invalidCatalog).issues();
        List<ContentDefinitionValidator.Issue> second = validator.validate(invalidCatalog).issues();
        assert first.equals(second);
        assert hasIssue(first, ContentDefinitionValidator.Codes.INVALID_NAMESPACED_ID,
                "validEntityTypeIds[0]");
        assert hasIssue(first, ContentDefinitionValidator.Codes.INVALID_NAMESPACED_ID,
                "validEntityTypeIds[3]");
        assert hasIssue(first, ContentDefinitionValidator.Codes.DUPLICATE_ID,
                "validEntityTypeIds[2]");
    }

    private static void elementMapKeyRules() {
        MobDefinition mob = GrohmBossContentFixture.mob();
        ContentDefinitionValidator validator = new ContentDefinitionValidator();

        MobDefinition valid = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(),
                mob.entityType(), mob.category(), mob.stats(),
                Map.of(DamageElement.FIRE, 1.0), mob.resistanceValues(),
                mob.equipmentReferences(), mob.abilityReferences());
        assert validator.validate(catalogWith(valid, GrohmBossContentFixture.encounter(),
                List.of(GrohmBossContentFixture.REWARD_ID))).valid();

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<DamageElement, Double> invalidValues = (Map) Map.of(AttackTag.MELEE, 1.0);
        MobDefinition invalid = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), mob.revision(), mob.presentation(),
                mob.entityType(), mob.category(), mob.stats(), invalidValues,
                mob.resistanceValues(), mob.equipmentReferences(), mob.abilityReferences());
        ContentDefinitionValidator.Catalog catalog = catalogWith(
                invalid, GrohmBossContentFixture.encounter(),
                List.of(GrohmBossContentFixture.REWARD_ID));

        ContentDefinitionValidator.ValidationResult first = validator.validate(catalog);
        ContentDefinitionValidator.ValidationResult second = validator.validate(catalog);
        assert !first.valid() : first.issues();
        assert first.issues().equals(second.issues()) : first.issues();
        assert first.codes().equals(List.of(ContentDefinitionValidator.Codes.INVALID_VALUE))
                : first.issues();
        assert first.paths().equals(List.of("mobs[0].elementValues[MELEE]"))
                : first.issues();
    }

    private static void damageMetadataRules() {
        Set<String> physicalMissing = codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(), Map.of(), Map.of()));
        assert physicalMissing.contains(ContentDefinitionValidator.Codes.DAMAGE_TYPE_TAG_MISMATCH);

        Set<String> magicalMissing = codesForDamage(DamageType.MAGICAL,
                metadata(Set.of(), Map.of(), Map.of()));
        assert magicalMissing.contains(ContentDefinitionValidator.Codes.DAMAGE_TYPE_TAG_MISMATCH);

        Set<String> fireValueMissing = codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL), Map.of(DamageElement.FIRE, 1.0), Map.of()));
        assert fireValueMissing.contains(ContentDefinitionValidator.Codes.ELEMENT_TAG_MISMATCH);
        assert !codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.FIRE),
                        Map.of(DamageElement.FIRE, 1.0), Map.of()))
                .contains(ContentDefinitionValidator.Codes.ELEMENT_TAG_MISMATCH);
        assert codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.FIRE),
                        Map.of(DamageElement.FIRE, 1.0), Map.of())).isEmpty();

        Set<String> fireScalingMissing = codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL), Map.of(),
                        Map.of(DamageElement.FIRE, 0.5)));
        assert fireScalingMissing.contains(ContentDefinitionValidator.Codes.ELEMENT_TAG_MISMATCH);
        assert !codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.FIRE), Map.of(),
                        Map.of(DamageElement.FIRE, 0.5)))
                .contains(ContentDefinitionValidator.Codes.ELEMENT_TAG_MISMATCH);
        assert codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.FIRE), Map.of(),
                        Map.of(DamageElement.FIRE, 0.5))).isEmpty();

        assert codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.FIRE), Map.of(), Map.of()))
                .isEmpty();
        assert codesForDamage(DamageType.MAGICAL,
                metadata(Set.of(AttackTag.MAGIC), Map.of(), Map.of())).isEmpty();
        assert codesForDamage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.MAGIC), Map.of(), Map.of()))
                .contains(ContentDefinitionValidator.Codes.CONTRADICTORY_DEFINITION);
        assert codesForDamage(DamageType.MAGICAL,
                metadata(Set.of(AttackTag.MAGIC, AttackTag.PHYSICAL), Map.of(), Map.of()))
                .contains(ContentDefinitionValidator.Codes.CONTRADICTORY_DEFINITION);
        assert codesForDamage(DamageType.TRUE,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.MAGIC), Map.of(), Map.of()))
                .isEmpty();

        AbilityDefinition.Damage missingTagDamage = damage(DamageType.PHYSICAL,
                metadata(Set.of(), Map.of(DamageElement.FIRE, 1.0), Map.of()));
        List<ContentDefinitionValidator.Issue> first = validateDamage(missingTagDamage).issues();
        List<ContentDefinitionValidator.Issue> second = validateDamage(missingTagDamage).issues();
        assert first.equals(second);
        assert hasIssue(first, ContentDefinitionValidator.Codes.DAMAGE_TYPE_TAG_MISMATCH,
                "abilities[3].timeline[0].metadata.tags");
        assert hasIssue(first, ContentDefinitionValidator.Codes.ELEMENT_TAG_MISMATCH,
                "abilities[3].timeline[0].metadata.elements.values[FIRE]");

        AbilityDefinition.Damage contradictoryTagsDamage = damage(DamageType.PHYSICAL,
                metadata(Set.of(AttackTag.PHYSICAL, AttackTag.MAGIC), Map.of(), Map.of()));
        first = validateDamage(contradictoryTagsDamage).issues();
        second = validateDamage(contradictoryTagsDamage).issues();
        assert first.equals(second);
        assert hasIssue(first, ContentDefinitionValidator.Codes.CONTRADICTORY_DEFINITION,
                "abilities[3].timeline[0].metadata.tags");
    }

    private static void damageMetadataBounds() {
        ContentDefinitionValidator.Bounds defaults = ContentDefinitionValidator.DEFAULT_BOUNDS;
        ContentDefinitionValidator validator = new ContentDefinitionValidator(
                new ContentDefinitionValidator.Policy(new ContentDefinitionValidator.Bounds(
                        defaults.maxIdLength(),
                        defaults.maxLongRevision(),
                        defaults.maxHealth(),
                        2.0,
                        defaults.maxAttackDamage(),
                        defaults.maxMovementSpeed(),
                        defaults.maxTicks(),
                        defaults.maxLongTicks(),
                        defaults.maxTargetRange(),
                        defaults.maxShapeWidth(),
                        3.0,
                        defaults.maxDamage(),
                        defaults.maxCoefficient(),
                        defaults.maxShapeRadius(),
                        defaults.maxShapeLength(),
                        defaults.maxLeashRadius(),
                        defaults.maxConditionDepth())));
        Set<AttackTag> tags = Set.of(AttackTag.PHYSICAL, AttackTag.FIRE);
        double maxValue = validator.policy().bounds().maxElementValue();
        double maxRate = validator.policy().bounds().maxElementMultiplier();
        String valuePath = "abilities[3].timeline[0].metadata.elements.values[FIRE]";
        String ratePath = "abilities[3].timeline[0].metadata.elements.scalingRates[FIRE]";

        assert validateDamage(validator, damage(DamageType.PHYSICAL,
                metadata(tags, Map.of(DamageElement.FIRE, maxValue), Map.of()))).valid();
        assert validateDamage(validator, damage(DamageType.PHYSICAL,
                metadata(tags, Map.of(DamageElement.FIRE, Math.nextUp(maxValue)), Map.of())))
                .issues().stream().anyMatch(issue -> issue.code().equals(
                        ContentDefinitionValidator.Codes.NUMBER_OUT_OF_RANGE)
                        && issue.path().equals(valuePath));
        ContentDefinitionValidator.ValidationResult valueAbove = validateDamage(validator,
                damage(DamageType.PHYSICAL,
                        metadata(tags, Map.of(DamageElement.FIRE, Math.nextUp(maxValue)), Map.of())));
        assert valueAbove.codes().equals(
                List.of(ContentDefinitionValidator.Codes.NUMBER_OUT_OF_RANGE)) : valueAbove.issues();
        assert valueAbove.paths().equals(List.of(valuePath)) : valueAbove.issues();
        assert valueAbove.issues().equals(validateDamage(validator,
                damage(DamageType.PHYSICAL,
                        metadata(tags, Map.of(DamageElement.FIRE, Math.nextUp(maxValue)), Map.of())))
                .issues());

        assert validateDamage(validator, damage(DamageType.PHYSICAL,
                metadata(tags, Map.of(), Map.of(DamageElement.FIRE, maxRate)))).valid();
        ContentDefinitionValidator.ValidationResult rateAbove = validateDamage(validator,
                damage(DamageType.PHYSICAL,
                        metadata(tags, Map.of(), Map.of(DamageElement.FIRE, Math.nextUp(maxRate)))));
        assert rateAbove.codes().equals(
                List.of(ContentDefinitionValidator.Codes.NUMBER_OUT_OF_RANGE)) : rateAbove.issues();
        assert rateAbove.paths().equals(List.of(ratePath)) : rateAbove.issues();
        assert rateAbove.issues().equals(validateDamage(validator,
                damage(DamageType.PHYSICAL,
                        metadata(tags, Map.of(), Map.of(DamageElement.FIRE, Math.nextUp(maxRate)))))
                .issues());

        ContentDefinitionValidator defaultValidator = new ContentDefinitionValidator();
        double defaultMaxValue = defaultValidator.policy().bounds().maxElementValue();
        double defaultMaxRate = defaultValidator.policy().bounds().maxElementMultiplier();
        assert validateDamage(defaultValidator, damage(DamageType.PHYSICAL,
                metadata(tags, Map.of(DamageElement.FIRE, defaultMaxValue), Map.of()))).valid();
        assert validateDamage(defaultValidator, damage(DamageType.PHYSICAL,
                metadata(tags, Map.of(), Map.of(DamageElement.FIRE, defaultMaxRate)))).valid();
    }

    private static void maxIdLengthPolicy() {
        ContentDefinitionValidator.Bounds defaults = ContentDefinitionValidator.DEFAULT_BOUNDS;
        ContentDefinitionValidator.Bounds bounds = new ContentDefinitionValidator.Bounds(
                8,
                defaults.maxLongRevision(),
                defaults.maxHealth(),
                defaults.maxElementValue(),
                defaults.maxAttackDamage(),
                defaults.maxMovementSpeed(),
                defaults.maxTicks(),
                defaults.maxLongTicks(),
                defaults.maxTargetRange(),
                defaults.maxShapeWidth(),
                defaults.maxElementMultiplier(),
                defaults.maxDamage(),
                defaults.maxCoefficient(),
                defaults.maxShapeRadius(),
                defaults.maxShapeLength(),
                defaults.maxLeashRadius(),
                defaults.maxConditionDepth());
        ContentDefinitionValidator validator = new ContentDefinitionValidator(
                new ContentDefinitionValidator.Policy(bounds));

        ContentDefinitionValidator.Catalog longNamespacedId = new ContentDefinitionValidator.Catalog(
                List.of(), List.of(), List.of(), List.of(), List.of("p:abcdefgh"), List.of());
        assert codes(validator, longNamespacedId)
                .contains(ContentDefinitionValidator.Codes.INVALID_NAMESPACED_ID);

        AbilityDefinition longLocalId = new AbilityDefinition(
                1,
                "p:a",
                1,
                "Short",
                new AbilityDefinition.Timing(0, 1, 1),
                new AbilityDefinition.Targeting(TargetSelector.PRIMARY_TARGET, 1.0),
                List.of(new AbilityDefinition.Wait("long-step", 1)),
                AbilityDefinition.InterruptPolicy.ALWAYS);
        ContentDefinitionValidator.Catalog longLocalCatalog = new ContentDefinitionValidator.Catalog(
                List.of(), List.of(longLocalId), List.of(), List.of(), List.of(), List.of());
        assert codes(validator, longLocalCatalog)
                .contains(ContentDefinitionValidator.Codes.INVALID_LOCAL_ID);
    }

    private static void deterministicIssues() {
        MobDefinition mob = GrohmBossContentFixture.mob();
        MobDefinition brokenMob = new MobDefinition(
                mob.schemaVersion(), mob.mobId(), -1, mob.presentation(), mob.entityType(),
                mob.category(), new MobDefinition.Stats(Double.NaN, -1.0,
                        mob.stats().movementSpeed(), mob.stats().knockbackResistance(),
                        mob.stats().followRange(), mob.stats().scale()), mob.elementValues(),
                mob.resistanceValues(), mob.equipmentReferences(), mob.abilityReferences());
        ContentDefinitionValidator.Catalog catalog = new ContentDefinitionValidator.Catalog(
                List.of(brokenMob), List.of(GrohmBossContentFixture.slam(),
                        GrohmBossContentFixture.charge(), GrohmBossContentFixture.shockwave()),
                List.of(GrohmBossContentFixture.encounter()),
                 List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                         GrohmBossContentFixture.shockwaveVisual()), List.of(GrohmBossContentFixture.REWARD_ID),
                 List.of(GrohmBossContentFixture.EQUIPMENT_ID), List.of("minecraft:ravager"));
        ContentDefinitionValidator validator = new ContentDefinitionValidator();
        List<ContentDefinitionValidator.Issue> first = validator.validate(catalog).issues();
        List<ContentDefinitionValidator.Issue> second = validator.validate(catalog).issues();
        assert first.equals(second);
        assert first.stream().anyMatch(issue -> issue.code()
                .equals(ContentDefinitionValidator.Codes.NEGATIVE_REVISION));
        assert first.stream().anyMatch(issue -> issue.code()
                .equals(ContentDefinitionValidator.Codes.NON_FINITE_NUMBER));
        assert first.equals(first.stream().sorted(Comparator.comparing(
                ContentDefinitionValidator.Issue::path)
                .thenComparing(ContentDefinitionValidator.Issue::code)
                .thenComparing(ContentDefinitionValidator.Issue::detail)).toList());
    }

    private static void sourceBoundary() {
        Path sourceRoot = Path.of("src/main/java/io/github/gyai/projects/content/definition");
        Path classRoot = Path.of("build/classes/java/main/io/github/gyai/projects/content/definition");
        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    assert !source.contains("org.bukkit");
                    assert !source.contains("io.papermc");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        assert Files.isDirectory(classRoot) : "compiled definition package is required";
        try (Stream<Path> classes = Files.walk(classRoot)) {
            classes.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                try {
                    String constantPool = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                    assert !constantPool.contains("org/bukkit/");
                    assert !constantPool.contains("io/papermc/");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ContentDefinitionValidator.Catalog catalogWith(
            MobDefinition mob, EncounterDefinition encounter, List<String> rewards) {
        return new ContentDefinitionValidator.Catalog(
                List.of(mob), List.of(GrohmBossContentFixture.slam(), GrohmBossContentFixture.charge(),
                        GrohmBossContentFixture.shockwave()), List.of(encounter),
                List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                        GrohmBossContentFixture.shockwaveVisual()), rewards,
                List.of(GrohmBossContentFixture.EQUIPMENT_ID),
                List.of("minecraft:ravager"));
    }

    private static ContentDefinitionValidator.Catalog withEncounter(EncounterDefinition encounter) {
        return catalogWith(GrohmBossContentFixture.mob(), encounter,
                List.of(GrohmBossContentFixture.REWARD_ID));
    }

    private static EncounterDefinition withPhases(EncounterDefinition encounter,
                                                   List<EncounterDefinition.Phase> phases) {
        return new EncounterDefinition(encounter.schemaVersion(), encounter.encounterId(),
                encounter.revision(), encounter.actors(), phases, encounter.resetPolicy(),
                encounter.victoryPolicy(), encounter.failurePolicy(), encounter.rewardReferences());
    }

    private static EncounterDefinition withSecondActor(
            EncounterDefinition encounter, String mobReference,
            EncounterDefinition.ActorBehavior phaseOneBehavior,
            EncounterDefinition.ActorBehavior phaseTwoBehavior,
            EncounterDefinition.ActorBehavior downedPhaseBehavior) {
        List<EncounterDefinition.Actor> actors = new ArrayList<>(encounter.actors());
        actors.add(new EncounterDefinition.Actor("second", mobReference));
        List<EncounterDefinition.Phase> phases = List.of(
                appendBehavior(encounter.phases().get(0), phaseOneBehavior),
                appendBehavior(encounter.phases().get(1), phaseTwoBehavior),
                appendBehavior(encounter.phases().get(2), downedPhaseBehavior));
        return new EncounterDefinition(encounter.schemaVersion(), encounter.encounterId(),
                encounter.revision(), actors, phases, encounter.resetPolicy(),
                encounter.victoryPolicy(), encounter.failurePolicy(), encounter.rewardReferences());
    }

    private static EncounterDefinition.Phase appendBehavior(
            EncounterDefinition.Phase phase, EncounterDefinition.ActorBehavior behavior) {
        List<EncounterDefinition.ActorBehavior> behaviors = new ArrayList<>(phase.actorBehaviors());
        behaviors.add(behavior);
        return new EncounterDefinition.Phase(phase.phaseId(), phase.entry(), behaviors,
                phase.transitions());
    }

    private static Set<String> codes(ContentDefinitionValidator.Catalog catalog) {
        return Set.copyOf(new ContentDefinitionValidator().validate(catalog).codes());
    }

    private static Set<String> codes(ContentDefinitionValidator validator,
                                      ContentDefinitionValidator.Catalog catalog) {
        return Set.copyOf(validator.validate(catalog).codes());
    }

    private static Set<String> codesForDamage(DamageType damageType, AttackMetadata metadata) {
        return Set.copyOf(validateDamage(damage(damageType, metadata)).codes());
    }

    private static AbilityDefinition.Damage damage(DamageType damageType,
                                                   AttackMetadata metadata) {
        return new AbilityDefinition.Damage("metadata-test", TargetSelector.PRIMARY_TARGET,
                new AbilityDefinition.Circle(1.0), damageType, DamageKind.DIRECT_SKILL,
                1.0, 0.0, false, metadata);
    }

    private static AttackMetadata metadata(Set<AttackTag> tags,
                                            Map<DamageElement, Double> values,
                                            Map<DamageElement, Double> scalingRates) {
        return new AttackMetadata(tags, new ElementProfile(values, scalingRates));
    }

    private static ContentDefinitionValidator.ValidationResult validateDamage(
            AbilityDefinition.Damage damage) {
        return validateDamage(new ContentDefinitionValidator(), damage);
    }

    private static ContentDefinitionValidator.ValidationResult validateDamage(
            ContentDefinitionValidator validator, AbilityDefinition.Damage damage) {
        AbilityDefinition testAbility = new AbilityDefinition(
                1, "projects:ability/test/metadata-check", 1, "Metadata Check",
                new AbilityDefinition.Timing(0, 1, 1),
                new AbilityDefinition.Targeting(TargetSelector.PRIMARY_TARGET, 4.0),
                List.of(damage), AbilityDefinition.InterruptPolicy.ALWAYS);
        ContentDefinitionValidator.Catalog catalog = new ContentDefinitionValidator.Catalog(
                List.of(GrohmBossContentFixture.mob()),
                List.of(GrohmBossContentFixture.slam(), GrohmBossContentFixture.charge(),
                        GrohmBossContentFixture.shockwave(), testAbility),
                List.of(GrohmBossContentFixture.encounter()),
                List.of(GrohmBossContentFixture.slamVisual(), GrohmBossContentFixture.chargeVisual(),
                        GrohmBossContentFixture.shockwaveVisual()),
                 List.of(GrohmBossContentFixture.REWARD_ID),
                 List.of(GrohmBossContentFixture.EQUIPMENT_ID),
                 List.of("minecraft:ravager"));
        return validator.validate(catalog);
    }

    private static boolean hasIssue(ContentDefinitionValidator.ValidationResult result,
                                    String code, String path) {
        return hasIssue(result.issues(), code, path);
    }

    private static boolean hasIssue(List<ContentDefinitionValidator.Issue> issues,
                                    String code, String path) {
        return issues.stream().anyMatch(issue -> issue.code().equals(code)
                && issue.path().equals(path));
    }

    private static void assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
            assert false : "expected UnsupportedOperationException";
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
