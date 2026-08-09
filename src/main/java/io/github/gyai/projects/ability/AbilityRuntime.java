package io.github.gyai.projects.ability;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Scheduler-bound runtime; actions stay unaware of Bukkit and source implementations. */
public final class AbilityRuntime implements AutoCloseable {
    private final ActionRegistry actions;
    private final Scheduler scheduler;
    private final EntityResolver entities;
    private final TelegraphGateway telegraphs;
    private final DamageGateway damage;
    private final AbilityLifecycleObserver observer;
    private final AnchorResolver anchors;
    private final Map<UUID, Cast> active = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public AbilityRuntime(ActionRegistry actions, Scheduler scheduler, EntityResolver entities,
                          TelegraphGateway telegraphs, DamageGateway damage) {
        this(actions, scheduler, entities, telegraphs, damage, AbilityLifecycleObserver.NOOP, AnchorResolver.DEFAULT);
    }
    public AbilityRuntime(ActionRegistry actions, Scheduler scheduler, EntityResolver entities,
                          TelegraphGateway telegraphs, DamageGateway damage,
                          AbilityLifecycleObserver observer, AnchorResolver anchors) {
        this.actions = Objects.requireNonNull(actions); this.scheduler = Objects.requireNonNull(scheduler);
        this.entities = Objects.requireNonNull(entities); this.telegraphs = Objects.requireNonNull(telegraphs);
        this.damage = Objects.requireNonNull(damage);
        this.observer = Objects.requireNonNull(observer); this.anchors = Objects.requireNonNull(anchors);
    }
    public Cast cast(AbilityDefinition definition, AbilityCastContext context) {
        if (closed) throw new IllegalStateException("Ability runtime is closed");
        actions.validate(definition);
        if (!definition.id().equals(context.abilityId())) throw new IllegalArgumentException("Ability id mismatch");
        Cast cast = new Cast(definition, context);
        if (active.putIfAbsent(context.castId(), cast) != null) {
            throw new IllegalStateException("Active cast id already exists");
        }
        cast.advance();
        return cast;
    }
    public Optional<Cast> active(UUID castId) { return Optional.ofNullable(active.get(castId)); }
    public int activeCount() { return active.size(); }
    public ActionRegistry actionRegistry() { return actions; }
    @Override public void close() { closed = true; List.copyOf(active.values()).forEach(c -> c.cancel(CancelReason.SHUTDOWN)); }

    public enum State { CREATED, RUNNING, COMPLETED, CANCELLED, FAILED }
    public enum CancelReason { EXPLICIT, SOURCE_INVALID, TARGET_INVALID, SHUTDOWN, FAILURE }
    public interface Scheduler { Scheduled schedule(int ticks, Runnable task); interface Scheduled { void cancel(); } }
    public interface EntityResolver { boolean valid(AbilityCastContext.EntityRef ref); }
    public interface AnchorResolver { AnchorFrame snapshot(AbilityCastContext context, AbilityCastContext.EntityRef ref); AnchorResolver DEFAULT = (context, ref) -> new AnchorFrame(context.origin().worldId(), context.origin().dimension(), context.origin().x(),context.origin().y(),context.origin().z(),0,0,1,0,1,0); }
    public interface TelegraphGateway { TelegraphHandle create(AbilityCastContext context, AbilityCastContext.EntityRef target, AbilityCastContext.EntityRef origin, AbilityDefinition.CircleTelegraph spec); }
    public interface TelegraphHandle { void detonate(); void cancel(); AnchorFrame anchor(); }
    public interface DamageGateway { DamageOutcome apply(AbilityCastContext context, AbilityCastContext.EntityRef target, AbilityDefinition.Damage spec); }
    /** Gateway result is observational: malformed values suppress HIT rather than retroactively failing damage. */
    public record DamageOutcome(boolean attempted, double shieldDamage, double healthDamage, AnchorFrame anchor) { public boolean dealtDamage() { return attempted && (Double.isFinite(shieldDamage)&&shieldDamage>0 || Double.isFinite(healthDamage)&&healthDamage>0); } }

    public final class Cast {
        private final AbilityDefinition definition; private final AbilityCastContext context;
        private final List<Scheduler.Scheduled> scheduled = new ArrayList<>(); private final List<TelegraphHandle> telegraphHandles = new ArrayList<>();
        private int step; private State state = State.CREATED;
        private boolean terminalEmitted, castEmitted;
        private Cast(AbilityDefinition definition, AbilityCastContext context) { this.definition = definition; this.context = context; }
        public AbilityCastContext context() { return context; } public State state() { return state; }
        public boolean isActive() { return state == State.CREATED || state == State.RUNNING; }
        public void cancel(CancelReason reason) {
            if (!isActive()) return;
            state = State.CANCELLED;
            cancelOwnedResources();
            emitTerminal(AbilityLifecycleEvent.Hook.CANCEL, reason);
            cleanup();
        }
        public void fail() {
            if (!isActive()) return;
            state = State.FAILED;
            cancelOwnedResources();
            emitTerminal(AbilityLifecycleEvent.Hook.CANCEL, CancelReason.FAILURE);
            cleanup();
        }
        void schedule(int ticks, Runnable task) {
            scheduled.add(scheduler.schedule(ticks, () -> {
                if (!isActive()) return;
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    fail();
                }
            }));
        }
        void own(TelegraphHandle handle) { telegraphHandles.add(handle); }
        TelegraphHandle createTelegraph(AbilityCastContext.EntityRef target, AbilityCastContext.EntityRef origin, AbilityDefinition.CircleTelegraph spec) {
            return telegraphs.create(context, target, origin, spec);
        }
        void onTelegraph(AbilityDefinition.CircleTelegraph spec, AbilityCastContext.EntityRef target, TelegraphHandle handle, int actionIndex) {
            AnchorFrame frame=null; try { frame=handle.anchor(); } catch (RuntimeException ignored) { }
            emit(AbilityLifecycleEvent.Hook.TELEGRAPH, actionIndex, spec, target, null, frame);
        }
        void applyDamage(AbilityCastContext.EntityRef target, AbilityDefinition.Damage spec, int actionIndex) {
            DamageOutcome outcome = damage.apply(context, target, spec);
            if (outcome.dealtDamage()) emit(AbilityLifecycleEvent.Hook.HIT, actionIndex, spec, target, null, outcome.anchor());
        }
        boolean valid(AbilityCastContext.EntityRef ref, boolean requiredTarget) {
            if (ref == null || !entities.valid(ref)) { cancel(requiredTarget ? CancelReason.TARGET_INVALID : CancelReason.SOURCE_INVALID); return false; }
            return true;
        }
        private void advance() {
            if (!isActive()) return;
            if (!valid(context.source(), false)) return;
            state = State.RUNNING;
            if (!castEmitted) { castEmitted = true; emit(AbilityLifecycleEvent.Hook.CAST, -1, null, context.source(), null, anchor(context.source())); }
            if (step >= definition.steps().size()) {
                state = State.COMPLETED;
                releaseOwnedReferences();
                emitTerminal(AbilityLifecycleEvent.Hook.EXPIRE, null);
                cleanup();
                return;
            }
            int actionIndex = step; var action = definition.steps().get(step++);
            try { actions.executor(action).execute(action, this); } catch (RuntimeException exception) { fail(); throw exception; }
        }
        void continueNow() { advance(); }
        AnchorFrame anchor(AbilityCastContext.EntityRef ref) { try { return anchors.snapshot(context, ref); } catch (RuntimeException ignored) { return null; } }
        void emit(AbilityLifecycleEvent.Hook hook, int actionIndex, AbilityDefinition.ActionSpec action, AbilityCastContext.EntityRef target, CancelReason reason, AnchorFrame anchor) {
            try { observer.onLifecycle(new AbilityLifecycleEvent(context, hook, actionIndex, action, target, reason, anchor)); } catch (RuntimeException ignored) { /* presentation cannot affect gameplay */ }
        }
        private void emitTerminal(AbilityLifecycleEvent.Hook hook, CancelReason reason) { if (!terminalEmitted) { terminalEmitted=true; emit(hook, -1, null, context.source(), reason, anchor(context.source())); } }
        private void cancelOwnedResources() {
            for (Scheduler.Scheduled task : scheduled) {
                try {
                    task.cancel();
                } catch (RuntimeException ignored) {
                    // Continue terminal cleanup; host adapters own cancellation logging.
                }
            }
            for (TelegraphHandle handle : telegraphHandles) {
                try {
                    handle.cancel();
                } catch (RuntimeException ignored) {
                    // Continue terminal cleanup; host adapters own cancellation logging.
                }
            }
            releaseOwnedReferences();
        }
        private void releaseOwnedReferences() {
            scheduled.clear();
            telegraphHandles.clear();
        }
        private void cleanup() { active.remove(context.castId(), this); }
    }

    public static ActionRegistry standardActions() {
        ActionRegistry registry = new ActionRegistry();
        registry.register("wait", AbilityDefinition.Wait.class, new ActionRegistry.ActionExecutor<>() {
            public void validate(AbilityDefinition.Wait value) { if (value.ticks() < 0) throw new IllegalArgumentException("Negative wait"); }
            public void execute(AbilityDefinition.Wait value, Cast cast) { cast.schedule(value.ticks(), cast::continueNow); }
        });
        registry.register("telegraph.circle", AbilityDefinition.CircleTelegraph.class, new ActionRegistry.ActionExecutor<>() {
            public void validate(AbilityDefinition.CircleTelegraph value) { if (value.target() == null || value.origin() == null || !Double.isFinite(value.radius()) || value.radius() <= 0 || value.radius() > 128 || value.durationTicks() <= 0 || value.durationTicks() >= 1200) throw new IllegalArgumentException("Invalid circle telegraph"); }
            public void execute(AbilityDefinition.CircleTelegraph value, Cast cast) {
                var target = value.target().select(cast.context()); var origin = value.origin().select(cast.context());
                if (!cast.valid(target, value.target() == TargetSelector.PRIMARY_TARGET) || !cast.valid(origin, value.origin() == TargetSelector.PRIMARY_TARGET)) return;
                int actionIndex = cast.step - 1; TelegraphHandle handle = cast.createTelegraph(target, origin, value); cast.own(handle); cast.onTelegraph(value, target, handle, actionIndex);
                cast.schedule(value.durationTicks(), () -> {
                    if (cast.valid(cast.context().source(), false)
                            && cast.valid(target, value.target() == TargetSelector.PRIMARY_TARGET)
                            && cast.valid(origin, value.origin() == TargetSelector.PRIMARY_TARGET)) {
                        handle.detonate();
                    }
                });
                cast.continueNow();
            }
        });
        registry.register("damage", AbilityDefinition.Damage.class, new ActionRegistry.ActionExecutor<>() {
            public void validate(AbilityDefinition.Damage value) { if (value.target() == null || value.damageType() == null || value.damageKind() == null || !Double.isFinite(value.fixedDamage()) || !Double.isFinite(value.coefficient()) || value.fixedDamage() < 0 || value.coefficient() < 0 || value.metadata() == null) throw new IllegalArgumentException("Malformed damage"); }
            public void execute(AbilityDefinition.Damage value, Cast cast) {
                var target = value.target().select(cast.context()); if (!cast.valid(target, value.target() == TargetSelector.PRIMARY_TARGET)) return;
                cast.applyDamage(target, value, cast.step - 1); cast.continueNow();
            }
        });
        return registry;
    }
}
