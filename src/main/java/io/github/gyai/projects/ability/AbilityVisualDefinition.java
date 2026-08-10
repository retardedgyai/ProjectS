package io.github.gyai.projects.ability;

import io.github.gyai.projects.transaction.DomainId;
import java.util.*;

/** Pure v1 visual primitive schema. Local offset/yaw are applied in the anchor basis by renderers. */
public record AbilityVisualDefinition(int schemaVersion, String id, List<HookBinding> bindings) {
    public static final int SCHEMA_VERSION = 1;
    public AbilityVisualDefinition {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported visual schema");
        DomainId.requireNamespaced(id, "visual id");
        List<HookBinding> copy = List.copyOf(Objects.requireNonNull(bindings)); Set<AbilityLifecycleEvent.Hook> hooks=new HashSet<>(); Set<String> primitiveIds=new HashSet<>();
        for (HookBinding entry : copy) {
            if (entry.hook() == AbilityLifecycleEvent.Hook.TRAVEL || !hooks.add(entry.hook())) throw new IllegalArgumentException("Duplicate or reserved hook");
            List<Emission> list=entry.emissions(); if(list.isEmpty() || list.size()>16) throw new IllegalArgumentException("Invalid emissions");
            Set<String> ids=new HashSet<>(); for(Emission e:list) { if(!ids.add(e.id())) throw new IllegalArgumentException("Duplicate emission id"); for(PrimitiveSpec p:e.primitives()) if(!primitiveIds.add(p.id())) throw new IllegalArgumentException("Duplicate primitive id across visual"); }
        }
        bindings=copy;
    }
    public Map<AbilityLifecycleEvent.Hook,List<Emission>> emissions() { Map<AbilityLifecycleEvent.Hook,List<Emission>> result=new EnumMap<>(AbilityLifecycleEvent.Hook.class); for(HookBinding b:bindings) result.put(b.hook(),b.emissions()); return Collections.unmodifiableMap(result); }
    public record HookBinding(AbilityLifecycleEvent.Hook hook, List<Emission> emissions) { public HookBinding { Objects.requireNonNull(hook); emissions=List.copyOf(Objects.requireNonNull(emissions)); } }
    public record Emission(String id, int actionIndex, List<PrimitiveSpec> primitives) {
        public Emission { if(id==null||id.isBlank()||id.length()>64||actionIndex < -1) throw new IllegalArgumentException("Invalid emission"); primitives=List.copyOf(primitives); if(primitives.isEmpty()||primitives.size()>16) throw new IllegalArgumentException("Invalid primitive count"); Set<String> seen=new HashSet<>(); for(PrimitiveSpec p:primitives) if(!seen.add(p.id())) throw new IllegalArgumentException("Duplicate primitive id"); }
    }
    public enum PrimitiveType { POINT, LINE, ARC, CIRCLE, CONE, SPIRAL, SPHERE, WAVE, BEZIER, BURST }
    /** Platform-neutral rendering material; shape remains in {@link PrimitiveType}. */
    public enum AppearanceKind { DEBUG_QUAD, PARTICLE }
    /** Bounded, authoritative appearance catalog.  v0.1 deliberately has no free-form payload. */
    public record Appearance(AppearanceKind kind, String id) {
        public static final Appearance DEBUG_QUAD = new Appearance(AppearanceKind.DEBUG_QUAD, "projects:debug_quad");
        private static final Set<String> PARTICLES=Set.of("minecraft:ash", "minecraft:cloud", "minecraft:crit", "minecraft:enchanted_hit", "minecraft:end_rod", "minecraft:firework", "minecraft:flame", "minecraft:soul", "minecraft:soul_fire_flame");
        public Appearance {
            if(kind==null || id==null || !id.equals(id.toLowerCase(Locale.ROOT)) || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ||
                    (kind==AppearanceKind.DEBUG_QUAD && !"projects:debug_quad".equals(id)) ||
                    (kind==AppearanceKind.PARTICLE && !PARTICLES.contains(id))) throw new IllegalArgumentException("Unsupported appearance");
        }
        public static Appearance particle(String id) { return new Appearance(AppearanceKind.PARTICLE,id); }
        public static Set<String> particleIds() { return PARTICLES; }
    }
    public sealed interface Scalar permits Literal, ActionField { }
    public record Literal(double value) implements Scalar { public Literal { if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite literal"); } }
    public enum ActionField implements Scalar { RADIUS, INNER_RADIUS, WIDTH, LENGTH }
    public record Vec(double x,double y,double z) { public Vec { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>128||Math.abs(y)>128||Math.abs(z)>128) throw new IllegalArgumentException("Invalid local vector"); } }
    /** Zero fields are unused. type requirements: circle radius; line length; cone angle/length; bezier control points. */
    public record PrimitiveSpec(String id, PrimitiveType type, int delayTicks, int durationTicks, int argb, double width, int density, long seed, Vec localOffset, double yawRadians, Scalar size, Scalar radius, Scalar length, Scalar height, Scalar angle, Scalar startAngle, Scalar sweepAngle, Scalar turns, int count, List<Vec> controlPoints, Appearance appearance, MotionSpec motion) {
        /** Exact v1 source signature; old definitions are debug quads. */
        public PrimitiveSpec(String id, PrimitiveType type, int delayTicks, int durationTicks, int argb, double width, int density, long seed, Vec localOffset, double yawRadians, Scalar size, Scalar radius, Scalar length, Scalar height, Scalar angle, Scalar startAngle, Scalar sweepAngle, Scalar turns, int count, List<Vec> controlPoints) { this(id,type,delayTicks,durationTicks,argb,width,density,seed,localOffset,yawRadians,size,radius,length,height,angle,startAngle,sweepAngle,turns,count,controlPoints,Appearance.DEBUG_QUAD,MotionSpec.LEGACY_DEFAULT); }
        public PrimitiveSpec(String id, PrimitiveType type, int delayTicks, int durationTicks, int argb, double width, int density, long seed, Vec localOffset, double yawRadians, Scalar size, Scalar radius, Scalar length, Scalar height, Scalar angle, Scalar startAngle, Scalar sweepAngle, Scalar turns, int count, List<Vec> controlPoints, Appearance appearance) { this(id,type,delayTicks,durationTicks,argb,width,density,seed,localOffset,yawRadians,size,radius,length,height,angle,startAngle,sweepAngle,turns,count,controlPoints,appearance,MotionSpec.LEGACY_DEFAULT); }
        public PrimitiveSpec { if(id==null||id.isBlank()||id.length()>64||type==null||appearance==null||motion==null||delayTicks<0||delayTicks>200||durationTicks<1||durationTicks>1200||delayTicks+durationTicks>1200||!Double.isFinite(width)||width<=0||width>16||density<1||density>256||localOffset==null||!Double.isFinite(yawRadians)||count<0||count>64||!bounded(size,128)||!bounded(radius,128)||!bounded(length,128)||!bounded(height,128)||!bounded(angle,Math.PI)||!bounded(sweepAngle,4*Math.PI)||!bounded(turns,32)) throw new IllegalArgumentException("Invalid primitive common values"); controlPoints=List.copyOf(controlPoints); if(controlPoints.size()>8) throw new IllegalArgumentException("Too many control points"); validate(type,size,radius,length,height,angle,startAngle,sweepAngle,turns,count,controlPoints); motion.validateFor(type); }
        private static void validate(PrimitiveType t,Scalar s,Scalar r,Scalar l,Scalar h,Scalar a,Scalar start,Scalar sweep,Scalar turns,int count,List<Vec> cp) {
            boolean common=zero(s)&&zero(r)&&zero(l)&&zero(h)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0;
            switch(t) {
                case POINT -> require(positive(s)&&zero(r)&&zero(l)&&zero(h)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0&&cp.isEmpty());
                case LINE -> require(positive(l)&&zero(s)&&zero(r)&&zero(h)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0&&(cp.isEmpty()||cp.size()==2));
                case ARC -> require(positive(r)&&nonZero(sweep)&&zero(s)&&zero(l)&&zero(h)&&zero(a)&&zero(turns)&&count==0&&cp.isEmpty());
                case CIRCLE -> require(positive(r)&&zero(s)&&zero(l)&&zero(h)&&zero(a)&&zero(sweep)&&zero(turns)&&count==0&&cp.isEmpty());
                case SPHERE -> require(positive(r)&&zero(s)&&zero(l)&&zero(h)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0&&cp.isEmpty());
                case CONE -> require(positive(l)&&angle(a)&&zero(s)&&zero(r)&&zero(h)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0&&cp.isEmpty());
                case SPIRAL -> require(positive(r)&&positive(turns)&&nonNegative(h)&&zero(s)&&zero(l)&&zero(a)&&zero(start)&&zero(sweep)&&count==0&&cp.isEmpty());
                case WAVE -> require(positive(l)&&positive(r)&&nonNegative(h)&&zero(s)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count==0&&cp.isEmpty());
                case BEZIER -> require(common && (cp.size()==3||cp.size()==4));
                case BURST -> require(positive(r)&&zero(s)&&zero(l)&&zero(h)&&zero(a)&&zero(start)&&zero(sweep)&&zero(turns)&&count>0&&cp.isEmpty());
            }
        }
        private static boolean nonZero(Scalar value) { return value != null && (!(value instanceof Literal l) || l.value()!=0); }
        private static boolean positive(Scalar value) { return value != null && (!(value instanceof Literal l) || l.value()>0); }
        private static boolean nonNegative(Scalar value) { return value == null || !(value instanceof Literal l) || l.value()>=0; }
        private static boolean angle(Scalar value) { return value != null && (!(value instanceof Literal l) || l.value()>0 && l.value()<Math.PI); }
        /** Action fields are resolved later; literals are constrained to Client v1's accepted domain. */
        private static boolean bounded(Scalar value,double maximum) { return !(value instanceof Literal l) || Math.abs(l.value())<=maximum; }
        private static void require(boolean valid) { if(!valid) throw new IllegalArgumentException("Invalid primitive type slots"); }
        private static boolean zero(Scalar value) { return value == null || value instanceof Literal literal && literal.value() == 0; }
    }
}
