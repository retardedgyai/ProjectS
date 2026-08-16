package io.github.gyai.projects.ability;

import io.github.gyai.projects.combat.damage.*;
import io.github.gyai.projects.combat.telegraph.TelegraphInstance;
import io.github.gyai.projects.combat.telegraph.TelegraphRequest;
import io.github.gyai.projects.manager.TelegraphManager;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.monster.editor.MobStatsDefinition;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Paper composition adapter. It is the sole ability package scheduler/Bukkit boundary. */
public final class BukkitAbilityRuntime {
    private final JavaPlugin plugin;
    private final AbilityRuntime runtime;
    private final AbilityVisualRegistry visualRegistry;
    private volatile AbilityVisualResolver visualResolver;
    public BukkitAbilityRuntime(JavaPlugin plugin, DamageService damageService, TelegraphManager telegraphs, MonsterManager monsters) {
        this.plugin = plugin;
        visualRegistry = new AbilityVisualRegistry(); try { DevAbilityVisuals.registerInto(visualRegistry); } catch (RuntimeException ignored) { } visualResolver=visualRegistry::resolve;
        runtime = new AbilityRuntime(AbilityRuntime.standardActions(),
                (ticks, task) -> {
                    var scheduled = plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
                    return scheduled::cancel;
                }, new Entities(plugin.getServer()), new Telegraphs(plugin, telegraphs),
                new Damage(damageService, plugin.getServer(), monsters), new AbilityVisualAdapter(id->visualResolver.resolve(id),new BukkitAbilityVfxCueSink(plugin),()->plugin.getServer().getCurrentTick()),
                (context, ref) -> snapshot(plugin.getServer(), ref));
    }
    public AbilityRuntime runtime() { return runtime; }
    public AbilityVisualRegistry visualRegistry() { return visualRegistry; }
    public void setVisualResolver(AbilityVisualResolver resolver) { visualResolver=java.util.Objects.requireNonNull(resolver); }
    public AbilityCastContext playerContext(Player source, LivingEntity primaryTarget, String abilityId) {
        return context(source, primaryTarget, abilityId, SourceKind.PLAYER);
    }
    public AbilityCastContext mobContext(LivingEntity source, MobStatsDefinition stats, Player primaryTarget, String abilityId) {
        if (stats == null) throw new IllegalArgumentException("Editor mob stats are required");
        return context(source, primaryTarget, abilityId, SourceKind.MOB);
    }
    public AbilityCastContext bossContext(LivingEntity source, Player primaryTarget, String abilityId) {
        return context(source, primaryTarget, abilityId, SourceKind.BOSS);
    }
    private AbilityCastContext context(LivingEntity source, LivingEntity target, String abilityId, SourceKind kind) {
        Location location = source.getLocation();
        return new AbilityCastContext(UUID.randomUUID(), abilityId,
                new AbilityCastContext.EntityRef(source.getUniqueId()), kind,
                new AbilityCastContext.Origin(location.getWorld().getUID(), location.getWorld().getKey().toString(), location.getX(), location.getY(), location.getZ()),
                target == null ? null : new AbilityCastContext.EntityRef(target.getUniqueId()), java.util.Map.of());
    }
    public void close() { runtime.close(); }
    private static AnchorFrame snapshot(Server server, AbilityCastContext.EntityRef ref) { Entity e=server.getEntity(ref.id()); if(!(e instanceof LivingEntity living)||!living.isValid()||living.isDead()) throw new IllegalArgumentException("Invalid visual anchor"); AnchorFrame frame=frame(living.getLocation()); if(frame==null) throw new IllegalArgumentException("Invalid visual anchor"); return frame; }
    private static AnchorFrame frame(Location l) { try { org.bukkit.util.Vector d=l.getDirection(); double n=Math.sqrt(d.lengthSquared()); if(!Double.isFinite(n)||n<1e-9) return null; double x=d.getX()/n,y=d.getY()/n,z=d.getZ()/n; double ux=Math.abs(y)>.98?1:0,uy=Math.abs(y)>.98?0:1,uz=0; return new AnchorFrame(l.getWorld().getUID(),l.getWorld().getKey().toString(),l.getX(),l.getY(),l.getZ(),x,y,z,ux,uy,uz); } catch(RuntimeException ignored) { return null; } }

    static void requireDetonation(BooleanSupplier detonation) {
        if (!detonation.getAsBoolean()) {
            throw new IllegalStateException("Telegraph detonation was rejected");
        }
    }

    private record Entities(Server server) implements AbilityRuntime.EntityResolver {
        @Override public boolean valid(AbilityCastContext.EntityRef ref) {
            Entity entity = server.getEntity(ref.id());
            return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
        }
        LivingEntity living(AbilityCastContext.EntityRef ref) {
            Entity entity = server.getEntity(ref.id());
            return entity instanceof LivingEntity living && living.isValid() && !living.isDead() ? living : null;
        }
    }
    private static final class Telegraphs implements AbilityRuntime.TelegraphGateway {
        private final JavaPlugin plugin; private final TelegraphManager manager;
        private Telegraphs(JavaPlugin plugin, TelegraphManager manager) { this.plugin = plugin; this.manager = manager; }
        @Override public AbilityRuntime.TelegraphHandle create(AbilityCastContext context, AbilityCastContext.EntityRef target, AbilityCastContext.EntityRef origin, AbilityDefinition.CircleTelegraph spec) {
            LivingEntity source = living(context.source()); LivingEntity center = living(origin);
            if (source == null || center == null) throw new IllegalArgumentException("Invalid telegraph entity");
            Location at = center.getLocation(); long start = plugin.getServer().getCurrentTick(); long detonate = start + spec.durationTicks();
            TelegraphRequest request = new TelegraphRequest(context.abilityId(), at.getWorld().getUID(), at.getWorld().getKey().toString(),
                    TelegraphInstance.Shape.CIRCLE, TelegraphInstance.VisualTheme.DAMAGE, TelegraphInstance.VisualStyle.STANDARD,
                    at.getX(), at.getY(), at.getZ(), 1, 0, spec.radius(), 0, 0, 0,
                    start, start + 1, detonate, detonate + 1,
                    spec.lockAtCreation() ? TelegraphInstance.TrackingMode.FIXED : TelegraphInstance.TrackingMode.TARGET,
                    spec.lockAtCreation() ? null : target.id(), 3.0);
            UUID id = manager.create(source, request);
            AnchorFrame frame = BukkitAbilityRuntime.frame(at);
            return new AbilityRuntime.TelegraphHandle() {
                public void detonate() { requireDetonation(() -> manager.detonate(id)); }
                public void cancel() { manager.cancel(id, TelegraphInstance.CancellationReason.SOURCE_REMOVED); manager.removeNow(id); }
                public AnchorFrame anchor() { return frame; }
            };
        }
        private LivingEntity living(AbilityCastContext.EntityRef ref) {
            Entity entity = plugin.getServer().getEntity(ref.id()); return entity instanceof LivingEntity living && living.isValid() && !living.isDead() ? living : null;
        }
    }
    private static final class Damage implements AbilityRuntime.DamageGateway {
        private final DamageService service; private final Server server; private final MonsterManager monsters;
        private Damage(DamageService service, Server server, MonsterManager monsters) { this.service = service; this.server = server; this.monsters = monsters; }
        @Override public AbilityRuntime.DamageOutcome apply(AbilityCastContext context, AbilityCastContext.EntityRef targetRef, AbilityDefinition.Damage spec) {
            LivingEntity source = living(context.source()); LivingEntity target = living(targetRef);
            if (source == null || target == null) throw new IllegalArgumentException("Invalid damage entity");
            if (context.sourceKind() == SourceKind.PLAYER && source instanceof Player player) {
                DamageApplicationResult result=service.apply(DamageRequest.builder(player, target).skillId(context.abilityId()).castId(context.castId())
                        .damageType(spec.damageType()).damageKind(spec.damageKind()).fixedDamage(spec.fixedDamage()).coefficient(spec.coefficient())
                        .criticalAllowed(spec.criticalAllowed()).attackMetadata(spec.metadata()).build());
                return outcome(result,target);
            } else if (context.sourceKind() == SourceKind.MOB
                    || context.sourceKind() == SourceKind.BOSS) {
                MobStatsDefinition stats = monsters.abilityStats(source);
                if (stats == null) throw new IllegalArgumentException("Mob ability source is not managed");
                return outcome(service.applyMobAbility(source, target, stats, context.castId(), spec.damageType(), spec.damageKind(),
                        spec.fixedDamage(), spec.coefficient(), spec.criticalAllowed()),target);
            } else throw new IllegalArgumentException("Unsupported ability source");
        }
        private AbilityRuntime.DamageOutcome outcome(DamageApplicationResult result,LivingEntity target) { return new AbilityRuntime.DamageOutcome(result.attempted(),result.shieldDamage(),result.healthDamage(),BukkitAbilityRuntime.frame(target.getLocation())); }
        private LivingEntity living(AbilityCastContext.EntityRef ref) { Entity entity = server.getEntity(ref.id()); return entity instanceof LivingEntity living && living.isValid() && !living.isDead() ? living : null; }
    }
}
