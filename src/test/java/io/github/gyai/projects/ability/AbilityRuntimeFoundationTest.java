package io.github.gyai.projects.ability;

import io.github.gyai.projects.combat.damage.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Executable, server-free v0.1 contract coverage. Run with -ea. */
public final class AbilityRuntimeFoundationTest {
    public static void main(String[] args) throws Exception {
        validDefinitionAndSchema(); bossDefinitionAndRuntime(); rejectionCoverage(); telegraphAdapterFailureBoundary(); timelineAndNoEarlyDamage(); cancellationAndInvalidTarget();
        sharedDefinitionAndSourceNeutrality(); lifecycleAndDamageBoundary(); scheduledFailureAndDuplicateSafety(); productionBoundaryAndDirectDamageScan();
        System.out.println("Ability Runtime v0.1 foundation tests passed");
    }
    private static void validDefinitionAndSchema() {
        AbilityDefinition definition = DevAbilityDefinitions.sharedArcaneBurst();
        assert definition.id().equals("projects:dev-shared-arcane-burst"); assert definition.steps().size() == 3;
        new AbilityRegistry(AbilityRuntime.standardActions()).register(definition);
    }
    private static void bossDefinitionAndRuntime() {
        AbilityDefinition definition = BossAbilityDefinitions.grohmBasicAttack();
        assert definition.id().equals(BossAbilityDefinitions.GROHM_BASIC_ATTACK_ID);
        assert definition.steps().size() == 1;
        AbilityDefinition.Damage damage = (AbilityDefinition.Damage) definition.steps().getFirst();
        assert damage.target() == TargetSelector.PRIMARY_TARGET;
        assert damage.damageType() == DamageType.PHYSICAL;
        assert damage.damageKind() == DamageKind.NORMAL_ATTACK;
        assert damage.fixedDamage() == 0.0 && damage.coefficient() == 1.0;
        assert !damage.criticalAllowed();
        new AbilityRegistry(AbilityRuntime.standardActions()).register(definition);

        Fixture fixture = new Fixture();
        AbilityRuntime.Cast cast = fixture.runtime.cast(
                definition,
                fixture.context(SourceKind.BOSS, definition.id()));
        assert cast.state() == AbilityRuntime.State.COMPLETED;
        assert fixture.damageCalls == 1 && fixture.damageSourceKind == SourceKind.BOSS;
        assert fixture.runtime.activeCount() == 0
                && fixture.scheduler.activePendingCount() == 0;
    }
    private static void rejectionCoverage() {
        expect(() -> new AbilityDefinition(2, "projects:x", "x", List.of(new AbilityDefinition.Wait(0))));
        expect(() -> new AbilityDefinition(1, "bad", "x", List.of(new AbilityDefinition.Wait(0))));
        expect(() -> { AbilityRegistry r = new AbilityRegistry(new ActionRegistry()); r.register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.Wait(0)))); });
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.Wait(-1)))));
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.CircleTelegraph(TargetSelector.SELF, TargetSelector.SELF, 0, 1, true)))));
        new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:duration-1199", "x", List.of(new AbilityDefinition.CircleTelegraph(TargetSelector.SELF, TargetSelector.SELF, 1, 1199, true))));
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:duration-1200", "x", List.of(new AbilityDefinition.CircleTelegraph(TargetSelector.SELF, TargetSelector.SELF, 1, 1200, true)))));
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.Damage(TargetSelector.SELF, null, DamageKind.DIRECT_SKILL, 1, 0, true, AttackMetadata.EMPTY)))));
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.Damage(TargetSelector.SELF, DamageType.MAGICAL, DamageKind.DIRECT_SKILL, -1, 0, true, AttackMetadata.EMPTY)))));
        expect(() -> new AbilityRegistry(AbilityRuntime.standardActions()).register(new AbilityDefinition(1, "projects:x", "x", List.of(new AbilityDefinition.Damage(null, DamageType.MAGICAL, DamageKind.DIRECT_SKILL, 1, 0, true, AttackMetadata.EMPTY)))));
    }
    private static void telegraphAdapterFailureBoundary() {
        BukkitAbilityRuntime.requireDetonation(() -> true);
        try {
            BukkitAbilityRuntime.requireDetonation(() -> false);
            throw new AssertionError("Expected rejected telegraph detonation");
        } catch (IllegalStateException expected) {
        }
    }
    private static void timelineAndNoEarlyDamage() {
        Fixture f = new Fixture(); AbilityRuntime.Cast cast = f.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), f.context(SourceKind.PLAYER));
        assert cast.state() == AbilityRuntime.State.RUNNING; assert f.events.equals(List.of("telegraph")); f.scheduler.advance(19); assert f.events.equals(List.of("telegraph"));
        f.scheduler.advance(1); assert f.events.equals(List.of("telegraph", "detonate", "damage"));
        assert cast.state() == AbilityRuntime.State.COMPLETED && f.scheduler.activePendingCount() == 0;
    }
    private static void cancellationAndInvalidTarget() {
        Fixture f = new Fixture(); AbilityRuntime.Cast cast = f.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), f.context(SourceKind.PLAYER)); cast.cancel(AbilityRuntime.CancelReason.EXPLICIT); f.scheduler.advance(20);
        assert f.damageCalls == 0 && f.events.equals(List.of("telegraph", "cancel"))
                && f.runtime.activeCount() == 0 && f.scheduler.activePendingCount() == 0;
        Fixture invalid = new Fixture(); invalid.valid.remove(invalid.target.id()); invalid.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), invalid.context(SourceKind.PLAYER));
        assert invalid.damageCalls == 0 && invalid.runtime.activeCount() == 0 && invalid.scheduler.activePendingCount() == 0;
        Fixture removedSource = new Fixture(); removedSource.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), removedSource.context(SourceKind.PLAYER));
        removedSource.valid.remove(removedSource.source.id()); removedSource.scheduler.advance(20);
        assert removedSource.damageCalls == 0 && !removedSource.events.contains("detonate")
                && removedSource.runtime.activeCount() == 0 && removedSource.scheduler.activePendingCount() == 0;
    }
    private static void sharedDefinitionAndSourceNeutrality() {
        Fixture f = new Fixture(); AbilityDefinition shared = DevAbilityDefinitions.sharedArcaneBurst(); AbilityRegistry registry = new AbilityRegistry(f.runtime.actionRegistry()); registry.register(shared);
        assert registry.find(shared.id()).orElseThrow() == shared;
        f.runtime.cast(registry.find(shared.id()).orElseThrow(), f.context(SourceKind.PLAYER)); f.scheduler.advance(20); List<String> player = List.copyOf(f.events);
        f.events.clear(); f.runtime.cast(registry.find(shared.id()).orElseThrow(), f.context(SourceKind.MOB)); f.scheduler.advance(20);
        assert player.equals(f.events);
    }
    private static void lifecycleAndDamageBoundary() {
        Fixture f = new Fixture(); f.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), f.context(SourceKind.MOB)); f.scheduler.advance(20);
        assert f.damageCalls == 1 && f.runtime.activeCount() == 0 && f.scheduler.activePendingCount() == 0;
        Fixture closed = new Fixture(); closed.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), closed.context(SourceKind.PLAYER)); closed.runtime.close(); closed.scheduler.advance(20);
        assert closed.damageCalls == 0 && closed.runtime.activeCount() == 0 && closed.events.contains("cancel")
                && closed.scheduler.activePendingCount() == 0;
    }
    private static void scheduledFailureAndDuplicateSafety() {
        Fixture failing = new Fixture(true);
        AbilityRuntime.Cast cast = failing.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), failing.context(SourceKind.PLAYER));
        failing.scheduler.advance(20);
        assert cast.state() == AbilityRuntime.State.FAILED && failing.damageCalls == 0
                && failing.runtime.activeCount() == 0 && failing.events.contains("cancel")
                && failing.scheduler.activePendingCount() == 0;
        Fixture duplicate = new Fixture(); AbilityCastContext context = duplicate.context(SourceKind.PLAYER);
        duplicate.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), context);
        try { duplicate.runtime.cast(DevAbilityDefinitions.sharedArcaneBurst(), context); throw new AssertionError("Expected duplicate cast rejection"); }
        catch (IllegalStateException expected) { assert duplicate.runtime.activeCount() == 1; }
    }
    private static void productionBoundaryAndDirectDamageScan() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/github/gyai/projects/ability/BukkitAbilityRuntime.java"));
        assert source.contains("DamageRequest.builder") && source.contains("applyMobAbility");
        assert source.contains("bossContext") && source.contains("SourceKind.BOSS")
                && source.contains("monsters.abilityStats(source)");
        String boss = Files.readString(Path.of("src/main/java/io/github/gyai/projects/monster/boss/HarborDevourerBoss.java"));
        assert boss.contains("abilityCaster.cast(")
                && boss.contains("BASIC_ATTACK_ABILITY");
        String damageService = Files.readString(Path.of("src/main/java/io/github/gyai/projects/combat/damage/DamageService.java"));
        assert damageService.contains("values.damageType(), DamageKind.NORMAL_ATTACK")
                && damageService.contains("values.fixedDamage(), values.coefficient()")
                && damageService.contains("values.criticalAllowed())");
        assert damageService.contains("criticalAllowed && damageKind.criticalAllowed()");
        try (var paths = Files.walk(Path.of("src/main/java/io/github/gyai/projects/ability"))) {
            assert paths.filter(p -> p.toString().endsWith(".java")).map(p -> { try { return Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); } })
                    .noneMatch(value -> value.contains(".damage("));
        }
    }
    private static void expect(Runnable action) { try { action.run(); throw new AssertionError("Expected rejection"); } catch (IllegalArgumentException expected) { } }
    private static final class Fixture {
        final ManualScheduler scheduler = new ManualScheduler(); final List<String> events = new ArrayList<>(); final AbilityCastContext.EntityRef source = new AbilityCastContext.EntityRef(UUID.randomUUID()); final AbilityCastContext.EntityRef target = new AbilityCastContext.EntityRef(UUID.randomUUID()); final Set<UUID> valid = new HashSet<>(Set.of(source.id(), target.id())); boolean detonationFails; int damageCalls; SourceKind damageSourceKind;
        Fixture() { this(false); }
        Fixture(boolean detonationFails) { this.detonationFails = detonationFails; }
        final AbilityRuntime runtime = new AbilityRuntime(AbilityRuntime.standardActions(), scheduler, ref -> valid.contains(ref.id()),
                (context, selected, origin, spec) -> { events.add("telegraph"); return new AbilityRuntime.TelegraphHandle() { public void detonate() { events.add("detonate"); if (detonationFails) throw new IllegalStateException("detonation failure"); } public void cancel() { events.add("cancel"); } public AnchorFrame anchor() { return frame(context); } }; },
                (context, selected, spec) -> { damageCalls++; damageSourceKind = context.sourceKind(); events.add("damage"); return new AbilityRuntime.DamageOutcome(true,0,1,frame(context)); });
        private static AnchorFrame frame(AbilityCastContext context) { return new AnchorFrame(context.origin().worldId(),context.origin().dimension(),0,0,0,0,0,1,0,1,0); }
        AbilityCastContext context(SourceKind kind) { return context(kind, DevAbilityDefinitions.SHARED_ARCANE_BURST_ID); }
        AbilityCastContext context(SourceKind kind, String abilityId) { return new AbilityCastContext(UUID.randomUUID(), abilityId, source, kind, new AbilityCastContext.Origin(UUID.randomUUID(), "minecraft:overworld", 0, 0, 0), target, Map.of()); }
    }
    private static final class ManualScheduler implements AbilityRuntime.Scheduler {
        long now; long sequence; final List<Entry> entries = new ArrayList<>();
        public Scheduled schedule(int ticks, Runnable task) { Entry entry = new Entry(now + ticks, sequence++, task); entries.add(entry); return () -> entry.cancelled = true; }
        void advance(int ticks) { long end = now + ticks; while (true) { Entry next = entries.stream().filter(e -> !e.cancelled && e.tick <= end).min(Comparator.comparingLong((Entry e) -> e.tick).thenComparingLong(e -> e.sequence)).orElse(null); if (next == null) break; entries.remove(next); now = next.tick; next.task.run(); } now = end; }
        int activePendingCount() { entries.removeIf(entry -> entry.cancelled); return entries.size(); }
        private static final class Entry { final long tick, sequence; final Runnable task; boolean cancelled; Entry(long tick, long sequence, Runnable task) { this.tick = tick; this.sequence = sequence; this.task = task; } }
    }
}
