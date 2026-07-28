package io.github.gyai.projects.combat.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PainterPassiveManager {
    private final JavaPlugin plugin;
    private final TargetingService targeting;
    private final SkillEffectRenderer effects;
    private final boolean debug;
    private final Map<HitKey, HitRecord> hits = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> tasks = new HashMap<>();
    private final BukkitTask cleanupTask;
    private long windowMillis = 4_000;
    private long delayTicks = 12;
    private double radius = 2.5;
    private double damage = 6;
    private SkillDamageService damageService;

    public PainterPassiveManager(JavaPlugin plugin, TargetingService targeting, SkillEffectRenderer effects, boolean debug) {
        this.plugin=plugin;this.targeting=targeting;this.effects=effects;this.debug=debug;
        cleanupTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::cleanupExpired,40,40);
    }

    public void configure(double windowSeconds,double delaySeconds,double radius,double damage){
        windowMillis=Math.round(windowSeconds*1000);delayTicks=Math.max(2,Math.round(delaySeconds*20));this.radius=radius;this.damage=damage;
    }
    public void setDamageService(SkillDamageService damageService){this.damageService=damageService;}

    public void record(Player caster,LivingEntity target,String skillId){
        if(!targeting.isEnemy(caster,target))return;
        long now=System.currentTimeMillis();HitKey key=new HitKey(caster.getUniqueId(),target.getUniqueId());HitRecord previous=hits.get(key);
        if(previous==null||now-previous.time>windowMillis||!previous.world.equals(target.getWorld().getUID())){
            hits.put(key,new HitRecord(skillId,now,target.getWorld().getUID()));log(caster,"[PainterPassive] first hit caster="+caster.getName()+" target="+target.getUniqueId()+" skill="+skillId);return;
        }
        if(previous.skillId.equals(skillId)){log(caster,"[PainterPassive] same skill ignored skill="+skillId);return;}
        hits.remove(key);log(caster,"[PainterPassive] second distinct hit skill="+skillId);createSignature(caster,groundLocation(target.getLocation()));
    }

    private Location groundLocation(Location source){Location fixed=source.clone();RayTraceResult ground=fixed.getWorld().rayTraceBlocks(fixed.clone().add(0,2,0),new Vector(0,-1,0),6);if(ground!=null&&ground.getHitPosition()!=null)fixed=ground.getHitPosition().toLocation(fixed.getWorld()).add(0,.08,0);return fixed;}

    private void createSignature(Player caster,Location location){
        Location fixed=location.clone();log(caster,"[PainterPassive] signature created location="+format(fixed));
        BukkitRunnable animation=new BukkitRunnable(){long tick;
            @Override public void run(){
                if(!caster.isOnline()||!fixed.getWorld().isChunkLoaded(fixed.getBlockX()>>4,fixed.getBlockZ()>>4)){cancel();return;}
                double progress=Math.min(1,tick/(double)delayTicks);double shrinking=1.8*(1-.45*progress);
                effects.drawGroundRune(fixed,shrinking,tick*.22);effects.drawHelix(fixed,.55,2.8,tick*.3);
                effects.drawRing(fixed,shrinking,Particle.REVERSE_PORTAL);
                effects.particle(fixed.clone().add(0,.2,0),Particle.WITCH,2);
                effects.particle(fixed.clone().add(0,.35,0),Particle.ENCHANT,2);
                effects.transition(fixed.clone().add(0,.15,0),Color.BLUE,Color.PURPLE,1.4f);
                if(tick%4==0)fixed.getWorld().playSound(fixed,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.3f,1.2f+(float)progress*.6f);
                if(tick==Math.max(2,delayTicks-4))fixed.getWorld().playSound(fixed,Sound.BLOCK_BEACON_POWER_SELECT,.35f,1.7f);
                if(tick>=delayTicks){explode(caster,fixed);cancel();return;}tick+=2;
            }};
        BukkitTask task=animation.runTaskTimer(plugin,0,2);tasks.computeIfAbsent(caster.getUniqueId(),ignored->new ArrayList<>()).add(task);
    }

    private void explode(Player caster,Location location){
        effects.particle(location,Particle.FLASH,1);effects.particle(location,Particle.SONIC_BOOM,1);effects.particle(location,Particle.EXPLOSION,5);
        effects.drawShockwave(location,radius);effects.drawHelix(location,.7,4,0);effects.particle(location,Particle.ELECTRIC_SPARK,12);
        location.getWorld().playSound(location,Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS,.35f,1.5f);
        location.getWorld().playSound(location,Sound.ENTITY_GENERIC_EXPLODE,.5f,1.35f);
        int count=0;for(LivingEntity enemy:targeting.enemies(caster,location,radius)){if(damageService!=null)damageService.bonusDamage(caster,enemy,"signature-of-the-visionary",damage);count++;}
        log(caster,"[PainterPassive] signature exploded targets="+count+" damage="+damage);
    }

    private void cleanupExpired(){long now=System.currentTimeMillis();hits.entrySet().removeIf(entry->{if(now-entry.getValue().time>windowMillis)return true;Entity target=plugin.getServer().getEntity(entry.getKey().target);return !(target instanceof LivingEntity living)||!living.isValid()||living.isDead()||!living.getWorld().getUID().equals(entry.getValue().world);});}
    public List<String> describe(Player caster){long now=System.currentTimeMillis();return hits.entrySet().stream().filter(e->e.getKey().caster.equals(caster.getUniqueId())).map(e->"対象: "+e.getKey().target+" | 前回: "+e.getValue().skillId+" | 残り: %.1f秒".formatted(Math.max(0,(windowMillis-(now-e.getValue().time))/1000.0))).toList();}
    public void reset(Player player){hits.keySet().removeIf(key->key.caster.equals(player.getUniqueId()));List<BukkitTask> owned=tasks.remove(player.getUniqueId());if(owned!=null)owned.forEach(BukkitTask::cancel);}
    public void clear(){cleanupTask.cancel();hits.clear();tasks.values().forEach(list->list.forEach(BukkitTask::cancel));tasks.clear();}
    private void log(Player caster,String message){if(debug){caster.sendMessage(message);plugin.getLogger().info(message);}}
    private String format(Location location){return "%.1f,%.1f,%.1f".formatted(location.getX(),location.getY(),location.getZ());}
    private record HitKey(UUID caster,UUID target){}
    private record HitRecord(String skillId,long time,UUID world){}
}
