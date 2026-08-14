package io.github.gyai.projects.content.definition;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Bukkit-free schema-v1 document describing a boss encounter graph. */
public record EncounterDefinition(
        int schemaVersion,
        String encounterId,
        long revision,
        List<Actor> actors,
        List<Phase> phases,
        ResetPolicy resetPolicy,
        VictoryPolicy victoryPolicy,
        FailurePolicy failurePolicy,
        List<String> rewardReferences
) {
    public static final int SCHEMA_VERSION = 1;

    public EncounterDefinition {
        actors = DefinitionSupport.immutableList(actors);
        phases = DefinitionSupport.immutableList(phases);
        rewardReferences = DefinitionSupport.immutableList(rewardReferences);
    }

    public String id() {
        return encounterId;
    }

    public record Actor(String actorId, String mobReference) {
        public String id() {
            return actorId;
        }

        public String mobId() {
            return mobReference;
        }
    }

    public record Phase(
            String phaseId,
            boolean entry,
            List<ActorBehavior> actorBehaviors,
            List<Transition> transitions
    ) {
        public Phase {
            actorBehaviors = DefinitionSupport.immutableList(actorBehaviors);
            transitions = DefinitionSupport.immutableList(transitions);
        }

        public Phase(String phaseId, boolean entry, Collection<ActorBehavior> actorBehaviors,
                     List<Transition> transitions) {
            this(phaseId, entry,
                    actorBehaviors == null ? List.of() : List.copyOf(actorBehaviors),
                    transitions);
        }

        public List<ActorBehavior> behaviors() {
            return actorBehaviors;
        }
    }

    /** Ability selection and actor state are scoped to one actor in one phase. */
    public record ActorBehavior(
            String actorId,
            ActorState state,
            Set<String> allowedAbilityReferences,
            AbilitySelectionPolicy abilitySelectionPolicy
    ) {
        public ActorBehavior {
            allowedAbilityReferences = DefinitionSupport.immutableSet(
                    allowedAbilityReferences);
        }

        public ActorBehavior(String actorId, ActorState state,
                             Collection<String> allowedAbilityReferences,
                             AbilitySelectionPolicy abilitySelectionPolicy) {
            this(actorId, state,
                    allowedAbilityReferences == null ? Set.of()
                            : Set.copyOf(allowedAbilityReferences),
                    abilitySelectionPolicy);
        }

        public Set<String> allowedAbilities() {
            return allowedAbilityReferences;
        }
    }

    public enum ActorState {
        ACTIVE,
        DOWNED
    }

    /**
     * Typed encounter-owned effects for the ACTIVE/DOWNED boundary.  The enum
     * values intentionally expose only the two canonical policies: entering
     * down cancels the ability, clears current CC, and suppresses new CC
     * without buffering; leaving down unsuppresses CC without restoring or
     * buffering the old CC.
     */
    public enum DownControlPolicy {
        CANCEL_ABILITY_CLEAR_CURRENT_CC_SUPPRESS_NO_BUFFER,
        UNSUPPRESS_CC_NO_RESTORE;

        /** Alias for callers describing the entering edge by direction. */
        public static final DownControlPolicy ENTER_DOWN =
                CANCEL_ABILITY_CLEAR_CURRENT_CC_SUPPRESS_NO_BUFFER;

        /** Alias for callers describing the leaving edge by direction. */
        public static final DownControlPolicy EXIT_DOWN = UNSUPPRESS_CC_NO_RESTORE;

        public boolean enteringDown() {
            return this == CANCEL_ABILITY_CLEAR_CURRENT_CC_SUPPRESS_NO_BUFFER;
        }

        public boolean leavingDown() {
            return this == UNSUPPRESS_CC_NO_RESTORE;
        }

        public boolean cancelsCurrentAbility() {
            return enteringDown();
        }

        public boolean clearsCurrentCc() {
            return enteringDown();
        }

        public boolean suppressesNewCc() {
            return enteringDown();
        }

        public boolean buffersNewCc() {
            return false;
        }

        public boolean restoresPreviousCc() {
            return false;
        }
    }

    /** A typed actor-state effect attached to one phase transition. */
    public record ActorStateTransition(
            String actorId,
            ActorState from,
            ActorState to,
            DownControlPolicy downControlPolicy
    ) {
        public ActorStateTransition(String actorId, ActorState from, ActorState to) {
            this(actorId, from, to, null);
        }
    }

    public record Transition(
            String transitionId,
            Condition condition,
            String targetPhaseId,
            List<ActorStateTransition> actorStateTransitions
    ) {
        public Transition {
            actorStateTransitions = DefinitionSupport.immutableList(actorStateTransitions);
        }

        public Transition(String transitionId, Condition condition, String targetPhaseId) {
            this(transitionId, condition, targetPhaseId, List.of());
        }

        public Transition(String transitionId, String targetPhaseId, Condition condition) {
            this(transitionId, condition, targetPhaseId, List.of());
        }

        public Transition(String transitionId, String targetPhaseId, Condition condition,
                          Collection<ActorStateTransition> actorStateTransitions) {
            this(transitionId, condition, targetPhaseId,
                    actorStateTransitions == null ? List.of() : List.copyOf(actorStateTransitions));
        }
    }

    public sealed interface AbilitySelectionPolicy
            permits OrderedSelection, WeightedSelection {
    }

    public record OrderedSelection(List<String> abilityReferences)
            implements AbilitySelectionPolicy {
        public OrderedSelection {
            abilityReferences = DefinitionSupport.immutableList(abilityReferences);
        }

        public OrderedSelection(Collection<String> abilityReferences) {
            this(abilityReferences == null ? List.of() : List.copyOf(abilityReferences));
        }

        public List<String> abilities() {
            return abilityReferences;
        }
    }

    public record WeightedSelection(List<WeightedAbility> entries)
            implements AbilitySelectionPolicy {
        public WeightedSelection {
            entries = DefinitionSupport.immutableList(entries);
        }

        public WeightedSelection(WeightedAbility... entries) {
            this(entries == null ? List.of() : List.of(entries));
        }
    }

    public record WeightedAbility(String abilityReference, double weight) {
        public String abilityId() {
            return abilityReference;
        }
    }

    public sealed interface Condition permits Always, ActorHealthRatioAtMost,
            ElapsedTicksAtLeast, All, Any {
    }

    public record Always() implements Condition {
        public static final Always INSTANCE = new Always();
    }

    public record ActorHealthRatioAtMost(String actorId, double ratio)
            implements Condition {
        public String actorReference() {
            return actorId;
        }
    }

    public record ElapsedTicksAtLeast(long ticks, Clock clock) implements Condition {
        public ElapsedTicksAtLeast(long ticks) {
            this(ticks, Clock.PHASE);
        }
    }

    public enum Clock {
        PHASE,
        ENCOUNTER
    }

    public record All(List<Condition> conditions) implements Condition {
        public All {
            conditions = DefinitionSupport.immutableList(conditions);
        }

        public All(Condition... conditions) {
            this(conditions == null ? List.of() : List.of(conditions));
        }
    }

    public record Any(List<Condition> conditions) implements Condition {
        public Any {
            conditions = DefinitionSupport.immutableList(conditions);
        }

        public Any(Condition... conditions) {
            this(conditions == null ? List.of() : List.of(conditions));
        }
    }

    public record ResetPolicy(
            double leashRadius,
            int resetAfterNoTargetTicks,
            boolean resetOnLeash,
            boolean resetHealth,
            boolean resetPhase
    ) {
        public ResetPolicy(double leashRadius, int resetAfterNoTargetTicks) {
            this(leashRadius, resetAfterNoTargetTicks, true, true, true);
        }
    }

    public record VictoryPolicy(Condition condition) {
    }

    public record FailurePolicy(Condition condition, FailureMode mode) {
        public FailurePolicy(Condition condition) {
            this(condition, FailureMode.RESET);
        }
    }

    public enum FailureMode {
        RESET,
        ABORT,
        TIMEOUT
    }
}
