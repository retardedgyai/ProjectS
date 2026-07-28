package io.github.gyai.projects.combat.skill;

import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class SkillEffectRenderer {
    private static final int MAX_PARTICLES_PER_TICK = 180;
    private final double maxDistance;
    private final boolean reduceDistant;
    private PainterSkillExecutor.EffectQuality quality;
    private int budgetTick = Integer.MIN_VALUE;
    private int emittedThisTick;

    public SkillEffectRenderer(org.bukkit.configuration.ConfigurationSection config) {
        String raw=config.getString("skills.painter.effects.default-quality","HIGH");
        try{quality=PainterSkillExecutor.EffectQuality.valueOf(raw.toUpperCase(java.util.Locale.ROOT));}
        catch(IllegalArgumentException ignored){quality=PainterSkillExecutor.EffectQuality.HIGH;}
        maxDistance=config.getDouble("skills.painter.effects.max-distance",64);
        reduceDistant=config.getBoolean("skills.painter.effects.reduce-distant-particles",true);
    }

    public PainterSkillExecutor.EffectQuality getQuality(){return quality;}
    public PainterSkillExecutor.EffectQuality cycle(){quality=PainterSkillExecutor.EffectQuality.values()[(quality.ordinal()+1)%3];return quality;}
    private double density(Location at){double q=switch(quality){case LOW->1;case MEDIUM->1.8;case HIGH->3.4;};
        if(reduceDistant&&!hasObserver(at))q*=.35;return q;}
    private boolean hasObserver(Location at){double max=maxDistance*maxDistance;for(Player p:at.getWorld().getPlayers())if(p.getLocation().distanceSquared(at)<=max)return true;return false;}
    private int reserve(int requested){int tick=Bukkit.getCurrentTick();if(tick!=budgetTick){budgetTick=tick;emittedThisTick=0;}int granted=Math.min(Math.max(0,requested),MAX_PARTICLES_PER_TICK-emittedThisTick);emittedThisTick+=granted;return granted;}
    public void particle(Location at,Particle particle,int base){if(at.getWorld()==null)return;int count=reserve(Math.clamp((int)Math.ceil(base*density(at)),1,MAX_PARTICLES_PER_TICK));if(count>0)at.getWorld().spawnParticle(particle,at,count,.12,.12,.12,.01);}
    public void dust(Location at,Color color,float size){if(at.getWorld()!=null&&reserve(1)>0)at.getWorld().spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color,size));}
    public void transition(Location at,Color from,Color to,float size){if(at.getWorld()!=null&&reserve(1)>0)at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION,at,1,0,0,0,0,new Particle.DustTransition(from,to,size));}
    public void drawRing(Location center,double radius,Particle particle){int points=(int)(18*density(center));for(int i=0;i<points;i++){double a=i*Math.PI*2/points;particle(center.clone().add(Math.cos(a)*radius,.08,Math.sin(a)*radius),particle,1);}}
    public void drawGroundRune(Location center,double radius,double phase){int rings=quality==PainterSkillExecutor.EffectQuality.HIGH?3:quality==PainterSkillExecutor.EffectQuality.MEDIUM?2:1;for(int r=1;r<=rings;r++){double rr=radius*r/rings;int points=(int)(16*density(center));for(int i=0;i<points;i++){double a=i*Math.PI*2/points+phase*(r%2==0?-1:1);Location p=center.clone().add(Math.cos(a)*rr,.06,Math.sin(a)*rr);Color c=i%3==0?Color.RED:i%3==1?Color.BLUE:Color.PURPLE;dust(p,c,1.1f);}}}
    public void drawHelix(Location center,double radius,double height,double phase){int strands=switch(quality){case LOW->1;case MEDIUM->2;case HIGH->3;};int points=(int)(12*density(center));for(int s=0;s<strands;s++)for(int i=0;i<points;i++){double t=i/(double)points;double a=phase+t*Math.PI*4+s*Math.PI*2/strands;particle(center.clone().add(Math.cos(a)*radius,t*height,Math.sin(a)*radius),Particle.END_ROD,1);}}
    public void drawLine(Location from,Location to,Particle particle){if(from.getWorld()==null||to.getWorld()==null||!from.getWorld().equals(to.getWorld()))return;Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length==0)return;Vector step=delta.normalize().multiply(Math.max(.2,.55/density(from)));Location p=from.clone();for(double d=0;d<length&&d<MAX_PARTICLES_PER_TICK;d+=step.length()){particle(p,particle,1);p.add(step);}}
    public void drawLightningArc(Location from,Location to,int branches){Vector delta=to.toVector().subtract(from.toVector());int segments=Math.min(32,Math.max(6,(int)delta.length()*2));Location previous=from.clone();for(int i=1;i<=segments;i++){double t=i/(double)segments;Location next=from.clone().add(delta.clone().multiply(t));if(i<segments){next.add(ThreadLocalRandom.current().nextDouble(-.3,.3),0,ThreadLocalRandom.current().nextDouble(-.3,.3));}drawLine(previous,next,Particle.ELECTRIC_SPARK);previous=next;}int visibleBranches=switch(quality){case LOW->0;case MEDIUM->Math.max(1,branches/2);case HIGH->branches;};for(int i=0;i<visibleBranches;i++){Location start=from.clone().add(delta.clone().multiply(ThreadLocalRandom.current().nextDouble(.2,.8)));drawLine(start,start.clone().add(ThreadLocalRandom.current().nextDouble(-2,2),-2,ThreadLocalRandom.current().nextDouble(-2,2)),Particle.ELECTRIC_SPARK);}}
    public void drawShockwave(Location center,double radius){drawRing(center,radius,Particle.ELECTRIC_SPARK);if(quality!=PainterSkillExecutor.EffectQuality.LOW)drawRing(center,radius*.65,Particle.END_ROD);}
    public void drawProjectile(Location center,Vector direction,Particle core){particle(center,core,quality==PainterSkillExecutor.EffectQuality.HIGH?7:3);Vector side=direction.clone().crossProduct(new Vector(0,1,0));if(side.lengthSquared()>0){side.normalize().multiply(.35);particle(center.clone().add(side),Particle.END_ROD,2);particle(center.clone().subtract(side),Particle.ELECTRIC_SPARK,2);}}
}
