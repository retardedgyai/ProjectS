package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.combat.resource.ResourceDefinition;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PainterSkillExecutor {
    private final JavaPlugin plugin;
    private final ResourceManager resources;
    private final ResourceDefinition mana;
    private final SkillManager cooldowns;
    private final TargetingService targeting;
    private final SkillDamageService damage;
    private final CrowdControlManager crowdControl;
    private final Map<PainterSpell, PainterSpellSettings> settings = new EnumMap<>(PainterSpell.class);
    private final Map<UUID, Map<EffectKey, List<BukkitTask>>> tasks = new HashMap<>();
    private final Map<UUID, LightState> lights = new HashMap<>();
    private final Set<UUID> completedSeveringBoltCasts = new java.util.HashSet<>();
    private final SkillEffectRenderer effects;
    private final SeveringBoltSettings severingSettings;

    public PainterSkillExecutor(JavaPlugin plugin, ResourceManager resources, ResourceDefinition mana,
                                SkillManager cooldowns, TargetingService targeting,
                                SkillDamageService damage, CrowdControlManager crowdControl,
                                SkillEffectRenderer effects) {
        this.plugin=plugin; this.resources=resources; this.mana=mana; this.cooldowns=cooldowns;
        this.targeting=targeting; this.damage=damage; this.crowdControl=crowdControl;
        this.effects=effects;
        this.severingSettings=SeveringBoltSettings.load(plugin.getConfig());
        for (PainterSpell spell : PainterSpell.values()) settings.put(spell, PainterSpellSettings.load(plugin.getConfig(), spell));
    }

    public boolean cast(Player caster, PainterSpell spell) {
        PainterSpellSettings value = settings.get(spell);
        if (!value.enabled()) return false;
        double remaining = cooldowns.getRemainingCooldownSeconds(caster, spell.cooldownId());
        if (remaining > 0) return false;
        if (!resources.consume(caster, mana, value.manaCost())) return false;
        switch (spell) {
            case DEVASTATING_FIRE -> projectile(caster, spell, value, ProjectileMode.FIRE);
            case SEVERING_BOLT -> severingBolt(caster, spell, value);
            case MOLTEN_FISSURE -> moltenFissure(caster, spell, value);
            case FLEETING_CURRENT -> fleetingCurrent(caster, value);
            case POOL_OF_REFLECTION -> poolOfReflection(caster, value);
            case STIRRING_LIGHTS -> stirringLights(caster, value);
            case GRIM_VISAGE -> projectile(caster, spell, value, ProjectileMode.FEAR);
            case GAZE_OF_THE_ABYSS -> gaze(caster, spell, value);
            case CRUSHING_MAW -> crushingMaw(caster, spell, value);
            case SPIRALING_DESPAIR -> projectile(caster, spell, value, ProjectileMode.ULTIMATE);
        }
        cooldowns.startCooldown(caster, spell.cooldownId(), value.cooldown(), 0);
        return true;
    }

    private void projectile(Player caster, PainterSpell spell, PainterSpellSettings s, ProjectileMode mode) {
        Location position = caster.getEyeLocation().clone();
        Vector step = position.getDirection().normalize().multiply(Math.max(.3, s.projectileSpeed()));
        SkillDamageService.UUIDCast cast = SkillDamageService.UUIDCast.create();
        BukkitRunnable runnable = new BukkitRunnable() {
            double traveled;
            @Override public void run() {
                if (!caster.isOnline() || traveled >= s.range() || !position.getBlock().isPassable()) {
                    if (mode == ProjectileMode.FIRE) explode(caster, spell, position, s, cast, true);
                    cancel(); return;
                }
                position.add(step); traveled += step.length();
                effects.drawProjectile(position,step.clone().normalize(),mode == ProjectileMode.FIRE ? Particle.FLAME : Particle.SOUL);
                LivingEntity hit = targeting.enemies(caster, position, .8).stream().findFirst().orElse(null);
                if (hit != null) {
                    if (mode == ProjectileMode.FIRE) explode(caster, spell, position, s, cast, true);
                    else if (mode == ProjectileMode.FEAR) {
                        damage.damage(caster, hit, spell.configKey, s.baseDamage(), false, true, cast);
                        crowdControl.fear(hit, caster, seconds(s.ccDuration()));
                    } else attachUltimate(caster, hit, spell, s, cast);
                    cancel();
                }
            }
        };
        track(caster, EffectKey.PROJECTILE, runnable.runTaskTimer(plugin, 0, 1));
    }

    private void explode(Player caster, PainterSpell spell, Location at, PainterSpellSettings s,
                         SkillDamageService.UUIDCast cast, boolean maxHealthBonus) {
        particle(at, Particle.EXPLOSION, 4); effects.drawShockwave(at,s.radius());effects.drawHelix(at,.4,3,0);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, .45f, 1.4f);
        for (LivingEntity enemy : targeting.enemies(caster, at, s.radius())) {
            double bonus = maxHealthBonus && enemy.getAttribute(Attribute.MAX_HEALTH) != null
                    ? enemy.getAttribute(Attribute.MAX_HEALTH).getValue() * .03 : 0;
            damage.damage(caster, enemy, spell.configKey, s.baseDamage() + bonus, false, true, cast);
        }
    }

    private void severingBolt(Player caster, PainterSpell spell, PainterSpellSettings s) {
        Location foundTarget = severingTarget(caster, s.range());
        Location target = foundTarget == null ? null : foundTarget.clone();
        if (target == null || !target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) return;
        cancelEffect(caster, EffectKey.SEVERING_BOLT);
        int totalTicks=Math.max(1,seconds(severingSettings.telegraphDelay()));
        UUID castId=UUID.randomUUID();
        BukkitRunnable telegraph=new BukkitRunnable(){int elapsedTicks;boolean impacted;
            @Override public void run(){
                if(!caster.isOnline()||!target.getWorld().isChunkLoaded(target.getBlockX()>>4,target.getBlockZ()>>4)){finishSeveringTask(caster,castId,getTaskId());cancel();return;}
                if(impacted){finishSeveringTask(caster,castId,getTaskId());cancel();return;}
                double progress=Math.min(1,elapsedTicks/(double)totalTicks);double outer=s.radius()*(1.6-.6*progress);
                int warningRings=Math.clamp((int)Math.ceil(severingSettings.warningRingDensity()),1,4);
                for(int ringIndex=0;ringIndex<warningRings;ringIndex++)effects.drawRing(target,outer-ringIndex*.18,ringIndex%2==0?Particle.ELECTRIC_SPARK:Particle.END_ROD);
                effects.drawRing(target,s.radius(),Particle.END_ROD);
                particle(target.clone().add(0,.12,0),Particle.WAX_ON,1);
                particle(target.clone().add(0,.2,0),Particle.SOUL_FIRE_FLAME,1);
                effects.transition(target.clone().add(0,.1,0),Color.BLUE,Color.PURPLE,1.2f);
                Location sky=target.clone().add(0,severingSettings.lightningHeight(),0);
                int branches=Math.clamp((int)Math.ceil(severingSettings.lightningParticleDensity()
                        *(effects.getQuality()==EffectQuality.HIGH?2:effects.getQuality()==EffectQuality.MEDIUM?1:.5)),1,6);
                effects.drawLightningArc(sky,target,branches);
                if(elapsedTicks==0)target.getWorld().playSound(target,Sound.BLOCK_BEACON_POWER_SELECT,.45f,1.7f);
                if(elapsedTicks%4==0){target.getWorld().playSound(target,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.3f,1.3f+(float)progress*.5f);debug(caster,"[SeveringBolt] telegraph castId="+castId+" elapsed="+elapsedTicks+"/"+totalTicks);}
                if(elapsedTicks>=totalTicks){impacted=true;strikeSeveringBoltOnce(caster,spell,s,target,castId);finishSeveringTask(caster,castId,getTaskId());cancel();return;}
                elapsedTicks+=2;
            }};
        BukkitTask task=telegraph.runTaskTimer(plugin,0,2);
        track(caster,EffectKey.SEVERING_BOLT,task);
        debug(caster,"[SeveringBolt] cast started castId="+castId+" task="+task.getTaskId());
    }

    private void strikeSeveringBoltOnce(Player caster,PainterSpell spell,PainterSpellSettings s,Location target,UUID castId){
        if(!completedSeveringBoltCasts.add(castId)){debug(caster,"[SeveringBolt] duplicate impact prevented castId="+castId);return;}
        try{strikeSeveringBoltImpact(caster,spell,s,target,castId);}
        finally{plugin.getServer().getScheduler().runTaskLater(plugin,()->completedSeveringBoltCasts.remove(castId),100L);}
    }

    private void strikeSeveringBoltImpact(Player caster,PainterSpell spell,PainterSpellSettings s,Location target,UUID castId){
        if(severingSettings.useLightningEffect())target.getWorld().strikeLightningEffect(target);
        target.getWorld().playSound(target,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,.65f,1.15f);
        target.getWorld().playSound(target,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,.65f,1.35f);
        effects.particle(target,Particle.FLASH,1);effects.particle(target,Particle.SONIC_BOOM,1);effects.particle(target,Particle.ELECTRIC_SPARK,12);effects.particle(target.clone().add(0,1,0),Particle.END_ROD,8);
        effects.drawShockwave(target,s.radius());effects.drawHelix(target,.5,severingSettings.lightningHeight(),0);
        List<LivingEntity> enemies=targeting.enemies(caster,target,s.radius());
        debug(caster,"[SeveringBolt] impact castId="+castId+" targets="+enemies.size());
        if(enemies.isEmpty())return;
        SkillDamageService.UUIDCast cast=new SkillDamageService.UUIDCast(castId);boolean isolated=enemies.size()==1;
        for(LivingEntity enemy:enemies){double multiplier=1;
            if(isolated)multiplier+=Math.max(0,severingSettings.isolatedMultiplier()-1);
            if(crowdControl.isControlled(enemy))multiplier+=Math.max(0,severingSettings.controlledMultiplier()-1);
            var max=enemy.getAttribute(Attribute.MAX_HEALTH);if(max!=null&&max.getValue()>0){double missing=Math.clamp(1-enemy.getHealth()/max.getValue(),0,1);multiplier+=missing*Math.max(0,severingSettings.missingHealthScaling());}
            double finalDamage=s.baseDamage()*multiplier;if(!Double.isFinite(finalDamage)||finalDamage<=0)finalDamage=.1;
            damage.damage(caster,enemy,"severing-bolt",finalDamage,false,true,cast);
        }
    }

    private Location severingTarget(Player caster,double range){Location eye=caster.getEyeLocation();Vector direction=eye.getDirection().normalize();
        RayTraceResult entityHit=caster.getWorld().rayTraceEntities(eye,direction,range,.35,entity->entity instanceof LivingEntity living&&targeting.isEnemy(caster,living));
        Location raw;if(entityHit!=null&&entityHit.getHitEntity() instanceof LivingEntity living)raw=living.getLocation();else{RayTraceResult block=caster.getWorld().rayTraceBlocks(eye,direction,range);raw=block!=null&&block.getHitPosition()!=null?block.getHitPosition().toLocation(caster.getWorld()):eye.clone().add(direction.multiply(range));}
        RayTraceResult ground=raw.getWorld().rayTraceBlocks(raw.clone().add(0,3,0),new Vector(0,-1,0),8);if(ground!=null&&ground.getHitPosition()!=null)raw=ground.getHitPosition().toLocation(raw.getWorld()).add(0,.08,0);return raw;}

    private void moltenFissure(Player caster, PainterSpell spell, PainterSpellSettings s) {
        cancelEffect(caster, EffectKey.MOLTEN_FISSURE);
        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        List<Location> points = new ArrayList<>();
        for (int i=2; i<=s.range(); i+=2) {
            Location point = caster.getLocation().clone().add(direction.clone().multiply(i));
            RayTraceResult ground = point.getWorld().rayTraceBlocks(point.clone().add(0,3,0), new Vector(0,-1,0), 6);
            if (ground != null && ground.getHitPosition() != null) points.add(ground.getHitPosition().toLocation(point.getWorld()).add(0,.1,0));
        }
        SkillDamageService.UUIDCast cast = SkillDamageService.UUIDCast.create();
        BukkitRunnable zone = new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!caster.isOnline() || ticks >= seconds(s.duration())) { cancel(); return; }
                if (ticks % Math.max(1, seconds(s.tickInterval())) == 0) {
                    Set<UUID> hit = new java.util.HashSet<>();
                    for (Location point : points) {
                        particle(point, Particle.FLAME, 3);effects.drawRing(point,s.radius(),Particle.ASH);
                        for (LivingEntity enemy : targeting.enemies(caster, point, s.radius())) if (hit.add(enemy.getUniqueId())) {
                            damage.damage(caster, enemy, spell.configKey, s.baseDamage(), true, true, cast);
                            crowdControl.slow(enemy, seconds(s.tickInterval()+.5), s.slowStrength());
                        }
                    }
                } ticks++;
            }
        };
        track(caster, EffectKey.MOLTEN_FISSURE, zone.runTaskTimer(plugin, 0, 1));
    }

    private void fleetingCurrent(Player caster, PainterSpellSettings s) {
        cancelEffect(caster, EffectKey.FLEETING_CURRENT); Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        BukkitRunnable path = new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!caster.isOnline() || ticks++ >= seconds(s.duration())) { cancel(); return; }
                for (int i=0;i<=s.range();i+=2) {
                    Location point = caster.getLocation().clone().add(direction.clone().multiply(i));
                    particle(point, Particle.SPLASH, 2);effects.drawLine(point,point.clone().add(direction.clone().multiply(1.5)),Particle.END_ROD);
                    if (caster.getLocation().distanceSquared(point) <= 4) caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1));
                }
            }
        }; track(caster, EffectKey.FLEETING_CURRENT, path.runTaskTimer(plugin, 0, 4));
    }

    private void poolOfReflection(Player caster, PainterSpellSettings s) {
        cancelEffect(caster, EffectKey.POOL_OF_REFLECTION);
        Location center = targetLocation(caster, s.range());
        BukkitRunnable pool = new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!caster.isOnline() || ticks++ >= seconds(s.duration())) { cancel(); return; }
                ring(center, s.radius(), Particle.ENCHANT);if(ticks==0)effects.drawGroundRune(center,s.radius(),0);
                if (caster.getLocation().distanceSquared(center) <= s.radius()*s.radius())
                    caster.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 30, 1));
            }
        }; track(caster, EffectKey.POOL_OF_REFLECTION, pool.runTaskTimer(plugin, 0, 5));
    }

    private void stirringLights(Player caster, PainterSpellSettings s) {
        cancelEffect(caster, EffectKey.STIRRING_LIGHTS);
        lights.put(caster.getUniqueId(), new LightState(3, System.currentTimeMillis() + Math.round(s.duration()*1000)));
        BukkitRunnable visual = new BukkitRunnable() {
            double angle;
            @Override public void run() {
                LightState state = lights.get(caster.getUniqueId());
                if (!caster.isOnline() || state == null || state.expires < System.currentTimeMillis()) { lights.remove(caster.getUniqueId()); cancel(); return; }
                for (int i=0;i<state.stacks;i++) {
                    double a=angle+i*Math.PI*2/3; particle(caster.getLocation().add(Math.cos(a),1.2,Math.sin(a)),Particle.END_ROD,1);
                } angle += .35;
            }
        }; track(caster, EffectKey.STIRRING_LIGHTS, visual.runTaskTimer(plugin, 0, 2));
    }

    public void consumeStirringLight(Player caster, LivingEntity target, UUID castId) {
        LightState state=lights.get(caster.getUniqueId());
        if (state==null || state.expires<System.currentTimeMillis() || state.lastCast.equals(castId)) return;
        state.lastCast=castId; state.stacks--;
        damage.bonusDamage(caster,target,"stirring-lights",3); resources.set(caster,mana,resources.get(caster,mana)+15);
        particle(target.getLocation().add(0,1,0),Particle.ELECTRIC_SPARK,6);
        if (state.stacks<=0) lights.remove(caster.getUniqueId());
    }

    public double enhanceNormalAttack(Player caster, LivingEntity target) {
        LightState state=lights.get(caster.getUniqueId());
        if(state==null||state.expires<System.currentTimeMillis())return 0;
        state.stacks--; resources.set(caster,mana,resources.get(caster,mana)+15);
        particle(target.getLocation().add(0,1,0),Particle.ELECTRIC_SPARK,6);
        if(state.stacks<=0)lights.remove(caster.getUniqueId());
        return 3;
    }

    private void gaze(Player caster, PainterSpell spell, PainterSpellSettings s) {
        Location eye=targetLocation(caster,s.range());
        BukkitRunnable gaze=new BukkitRunnable(){ int ticks;
            @Override public void run(){
                if(!caster.isOnline()||ticks++>=seconds(s.duration())){cancel();return;}
                particle(eye,Particle.REVERSE_PORTAL,4);
                LivingEntity target=targeting.enemies(caster,eye,s.radius()).stream().findFirst().orElse(null);
                if(target!=null){ damage.damage(caster,target,spell.configKey,s.baseDamage(),false,true,SkillDamageService.UUIDCast.create());
                    crowdControl.root(target,seconds(s.ccDuration())); cancel(); }
            }}; cancelEffect(caster,EffectKey.GAZE);track(caster,EffectKey.GAZE,gaze.runTaskTimer(plugin,14,4));
    }

    private void crushingMaw(Player caster, PainterSpell spell, PainterSpellSettings s) {
        Location center=targetLocation(caster,s.range()); ring(center,s.radius(),Particle.SQUID_INK);effects.drawGroundRune(center,s.radius(),0);effects.drawShockwave(center,s.radius());
        SkillDamageService.UUIDCast cast=SkillDamageService.UUIDCast.create();
        for(LivingEntity enemy:targeting.enemies(caster,center,s.radius())){
            damage.damage(caster,enemy,spell.configKey,s.baseDamage(),false,true,cast);
            crowdControl.pull(enemy,center,.55); crowdControl.slow(enemy,seconds(s.ccDuration()),s.slowStrength());
        }
    }

    private void attachUltimate(Player caster, LivingEntity anchor, PainterSpell spell, PainterSpellSettings s,
                                SkillDamageService.UUIDCast cast) {
        BukkitRunnable ultimate=new BukkitRunnable(){ int ticks;
            Location last=anchor.getLocation();
            @Override public void run(){
                if(!caster.isOnline()){cancel();return;} if(anchor.isValid())last=anchor.getLocation();
                double progress=Math.min(1,ticks/(double)seconds(s.duration())); double radius=Math.max(1,s.radius()*progress);
                ring(last,radius,Particle.DRAGON_BREATH);
                if(ticks%Math.max(1,seconds(s.tickInterval()))==0) for(LivingEntity enemy:targeting.enemies(caster,last,radius)){
                    damage.damage(caster,enemy,spell.configKey,s.baseDamage()/4,true,true,cast);
                    crowdControl.slow(enemy,seconds(s.tickInterval()+.5),Math.min(4,(int)(progress*4)));
                }
                if(ticks++>=seconds(s.duration())){explode(caster,spell,last,s,cast,false);cancel();}
            }}; cancelEffect(caster,EffectKey.ULTIMATE);track(caster,EffectKey.ULTIMATE,ultimate.runTaskTimer(plugin,0,1));
    }

    private Location targetLocation(Player caster,double range){ RayTraceResult result=caster.rayTraceBlocks(range);
        return result==null?caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(range)):
                result.getHitPosition().toLocation(caster.getWorld()).add(0,.1,0); }
    private int seconds(double value){return Math.max(1,(int)Math.round(value*20));}
    private void debug(Player caster,String message){if(plugin.getConfig().getBoolean("debug.painter-skills",false)){caster.sendMessage(message);plugin.getLogger().info(message);}}
    private String format(Location location){return "%.1f,%.1f,%.1f".formatted(location.getX(),location.getY(),location.getZ());}
    private void particle(Location at,Particle particle,int base){effects.particle(at,particle,base);}
    private void ring(Location center,double radius,Particle particle){effects.drawRing(center,radius,particle);}
    private void track(Player owner,EffectKey key,BukkitTask task){tasks.computeIfAbsent(owner.getUniqueId(),ignored->new EnumMap<>(EffectKey.class)).computeIfAbsent(key,ignored->new ArrayList<>()).add(task);}
    private void finishSeveringTask(Player owner,UUID castId,int taskId){Map<EffectKey,List<BukkitTask>> owned=tasks.get(owner.getUniqueId());if(owned!=null){List<BukkitTask> selected=owned.get(EffectKey.SEVERING_BOLT);if(selected!=null){selected.removeIf(task->task.getTaskId()==taskId);if(selected.isEmpty())owned.remove(EffectKey.SEVERING_BOLT);}if(owned.isEmpty())tasks.remove(owner.getUniqueId());}debug(owner,"[SeveringBolt] task finished castId="+castId);}
    private void cancelEffect(Player owner,EffectKey key){Map<EffectKey,List<BukkitTask>> owned=tasks.get(owner.getUniqueId());if(owned==null)return;List<BukkitTask> selected=owned.remove(key);if(selected!=null)selected.forEach(BukkitTask::cancel);if(owned.isEmpty())tasks.remove(owner.getUniqueId());}
    public void clearPlayer(Player player){Map<EffectKey,List<BukkitTask>> owned=tasks.remove(player.getUniqueId());if(owned!=null)owned.values().forEach(list->list.forEach(BukkitTask::cancel));lights.remove(player.getUniqueId());}
    public void clearAll(){tasks.values().forEach(map->map.values().forEach(list->list.forEach(BukkitTask::cancel)));tasks.clear();lights.clear();completedSeveringBoltCasts.clear();}
    public EffectQuality cycleQuality(){return effects.cycle();}
    public EffectQuality getQuality(){return effects.getQuality();}
    private enum ProjectileMode{FIRE,FEAR,ULTIMATE}
    public enum EffectQuality{LOW,MEDIUM,HIGH}
    private enum EffectKey{PROJECTILE,SEVERING_BOLT,MOLTEN_FISSURE,FLEETING_CURRENT,POOL_OF_REFLECTION,STIRRING_LIGHTS,GAZE,ULTIMATE}
    private static final class LightState{int stacks;final long expires;UUID lastCast=new UUID(0,0);LightState(int stacks,long expires){this.stacks=stacks;this.expires=expires;}}
}
