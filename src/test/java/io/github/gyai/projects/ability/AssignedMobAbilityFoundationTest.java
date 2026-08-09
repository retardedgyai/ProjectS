package io.github.gyai.projects.ability;

import io.github.gyai.projects.monster.editor.MobDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executable Bukkit-free v0.2 assigned-Mob runtime contract. Run with -ea. */
public final class AssignedMobAbilityFoundationTest {
    public static void main(String[] args) {
        assignmentAndResolutionBoundary();
        assignedMobTimelineAndCancellation();
        invalidEntitiesAndUnknownFailClosed();
        System.out.println("Ability Runtime v0.2 assigned-Mob tests passed");
    }

    private static void assignmentAndResolutionBoundary() {
        Fixture fixture = new Fixture();
        AbilityDefinition shared = DevAbilityDefinitions.sharedArcaneBurst();
        AbilityRegistry registry = new AbilityRegistry(fixture.runtime.actionRegistry());
        registry.register(shared);
        expect(() -> registry.register(shared));
        assert registry.find(shared.id()).orElseThrow() == shared;
        assert registry.list().equals(List.of(shared));
        try {
            registry.list().add(shared);
            throw new AssertionError("Registry list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }

        MobAbilityAssignmentPolicy policy = new MobAbilityAssignmentPolicy(registry);
        MobDefinition source = MobDefinition.create("assigned_mob");
        MobDefinition assigned = policy.assign(source, List.of(shared.id()));
        assert assigned.abilityIds().equals(List.of(shared.id()));
        expect(() -> policy.assign(source, List.of("projects:missing")));
        assert policy.resolve(assigned, "bad").status()
                == MobAbilityAssignmentPolicy.Status.MALFORMED;
        assert policy.resolve(assigned, "projects:other").status()
                == MobAbilityAssignmentPolicy.Status.UNASSIGNED;
        assert policy.resolve(assigned, shared.id()).definition() == shared;

        AbilityRegistry empty = new AbilityRegistry(fixture.runtime.actionRegistry());
        assert new MobAbilityAssignmentPolicy(empty).resolve(assigned, shared.id()).status()
                == MobAbilityAssignmentPolicy.Status.ASSIGNED_BUT_UNKNOWN;
    }

    private static void assignedMobTimelineAndCancellation() {
        Fixture fixture = new Fixture();
        AbilityDefinition shared = DevAbilityDefinitions.sharedArcaneBurst();
        AbilityRegistry registry = new AbilityRegistry(fixture.runtime.actionRegistry());
        registry.register(shared);
        AbilityDefinition resolved = new MobAbilityAssignmentPolicy(registry)
                .resolve(MobDefinition.create("timeline_mob")
                        .withAbilityIds(List.of(shared.id())), shared.id())
                .definition();
        assert resolved == shared;
        AbilityRuntime.Cast cast = fixture.runtime.cast(resolved, fixture.context());
        assert cast.context().sourceKind() == SourceKind.MOB;
        assert cast.context().primaryTarget().equals(fixture.target);
        assert fixture.events.equals(List.of("telegraph"));
        fixture.scheduler.advance(19);
        assert fixture.events.equals(List.of("telegraph"));
        fixture.scheduler.advance(1);
        assert fixture.events.equals(List.of("telegraph", "detonate", "damage"));
        assert fixture.damageCalls == 1 && cast.state() == AbilityRuntime.State.COMPLETED;

        Fixture cancelled = new Fixture();
        AbilityRuntime.Cast pending = cancelled.runtime.cast(shared, cancelled.context());
        pending.cancel(AbilityRuntime.CancelReason.EXPLICIT);
        cancelled.scheduler.advance(20);
        assert cancelled.damageCalls == 0
                && cancelled.events.equals(List.of("telegraph", "cancel"));
    }

    private static void invalidEntitiesAndUnknownFailClosed() {
        Fixture invalidTarget = new Fixture();
        invalidTarget.valid.remove(invalidTarget.target.id());
        invalidTarget.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(),
                invalidTarget.context());
        assert invalidTarget.damageCalls == 0 && invalidTarget.events.isEmpty();

        Fixture invalidSource = new Fixture();
        invalidSource.valid.remove(invalidSource.source.id());
        invalidSource.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(),
                invalidSource.context());
        assert invalidSource.damageCalls == 0 && invalidSource.events.isEmpty();

        Fixture unknown = new Fixture();
        MobDefinition assignedUnknown = MobDefinition.create("unknown_mob")
                .withAbilityIds(List.of("projects:removed"));
        MobAbilityAssignmentPolicy.Resolution result = new MobAbilityAssignmentPolicy(
                new AbilityRegistry(unknown.runtime.actionRegistry()))
                .resolve(assignedUnknown, "projects:removed");
        assert result.status() == MobAbilityAssignmentPolicy.Status.ASSIGNED_BUT_UNKNOWN;
        assert unknown.runtime.activeCount() == 0 && unknown.damageCalls == 0;
    }

    private static void expect(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected rejection");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static final class Fixture {
        final ManualScheduler scheduler = new ManualScheduler();
        final List<String> events = new ArrayList<>();
        final AbilityCastContext.EntityRef source = new AbilityCastContext.EntityRef(UUID.randomUUID());
        final AbilityCastContext.EntityRef target = new AbilityCastContext.EntityRef(UUID.randomUUID());
        final Set<UUID> valid = new HashSet<>(Set.of(source.id(), target.id()));
        int damageCalls;
        final AbilityRuntime runtime = new AbilityRuntime(AbilityRuntime.standardActions(), scheduler,
                ref -> valid.contains(ref.id()),
                (context, selected, origin, spec) -> {
                    assert context.sourceKind() == SourceKind.MOB;
                    assert selected.equals(target) && origin.equals(target);
                    events.add("telegraph");
                    return new AbilityRuntime.TelegraphHandle() {
                        public void detonate() { events.add("detonate"); }
                        public void cancel() { events.add("cancel"); }
                    };
                },
                (context, selected, spec) -> {
                    assert context.sourceKind() == SourceKind.MOB && selected.equals(target);
                    damageCalls++;
                    events.add("damage");
                });

        AbilityCastContext context() {
            return new AbilityCastContext(UUID.randomUUID(),
                    DevAbilityDefinitions.SHARED_ARCANE_BURST_ID, source, SourceKind.MOB,
                    new AbilityCastContext.Origin(UUID.randomUUID(),
                            "minecraft:overworld", 0, 0, 0), target, Map.of());
        }
    }

    private static final class ManualScheduler implements AbilityRuntime.Scheduler {
        long now;
        long sequence;
        final List<Entry> entries = new ArrayList<>();

        public Scheduled schedule(int ticks, Runnable task) {
            Entry entry = new Entry(now + ticks, sequence++, task);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }

        void advance(int ticks) {
            long end = now + ticks;
            while (true) {
                Entry next = entries.stream().filter(value -> !value.cancelled && value.tick <= end)
                        .min(Comparator.comparingLong((Entry value) -> value.tick)
                                .thenComparingLong(value -> value.sequence)).orElse(null);
                if (next == null) break;
                entries.remove(next);
                now = next.tick;
                next.task.run();
            }
            now = end;
        }

        private static final class Entry {
            final long tick;
            final long sequence;
            final Runnable task;
            boolean cancelled;

            Entry(long tick, long sequence, Runnable task) {
                this.tick = tick;
                this.sequence = sequence;
                this.task = task;
            }
        }
    }
}
