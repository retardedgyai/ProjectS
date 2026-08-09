package io.github.gyai.projects.ability;

import io.github.gyai.projects.network.AbilityVfxPacket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Presentation-only observer: all lookup, resolution, encoding and delivery failures are dropped. */
public final class AbilityVisualAdapter implements AbilityLifecycleObserver {
    public interface CueSink { void send(AbilityVfxPacket.Cue cue); }
    private final AbilityVisualRegistry registry; private final CueSink sink; private final LongSupplier ticks; private final UUID session=UUID.randomUUID(); private final AtomicLong sequence=new AtomicLong();
    public AbilityVisualAdapter(AbilityVisualRegistry registry, CueSink sink) { this(registry,sink,()->0L); }
    public AbilityVisualAdapter(AbilityVisualRegistry registry, CueSink sink, LongSupplier ticks) { this.registry=Objects.requireNonNull(registry); this.sink=Objects.requireNonNull(sink); this.ticks=Objects.requireNonNull(ticks); }
    @Override public void onLifecycle(AbilityLifecycleEvent event) {
        try {
            if(event.anchor()==null) return;
            AbilityVisualDefinition visual=registry.resolve(event.context().abilityId()).orElse(null); if(visual==null) return;
            List<AbilityVisualDefinition.Emission> emissions=visual.emissions().getOrDefault(event.hook(), List.of()); for (int emissionIndex=0; emissionIndex<emissions.size(); emissionIndex++) try { AbilityVisualDefinition.Emission emission=emissions.get(emissionIndex);
                if(emission.actionIndex()!=-1 && emission.actionIndex()!=event.actionIndex()) continue;
                List<AbilityVfxPacket.Primitive> values=new ArrayList<>();
                for (AbilityVisualDefinition.PrimitiveSpec p:emission.primitives()) values.add(resolve(p,event.action()));
                long n=sequence.updateAndGet(v -> v==Long.MAX_VALUE ? 1 : v+1);
                UUID cueId=stable(session,event.context().castId(),visual.id(),event.hook(),event.actionIndex(),emission.id());
                long now=ticks.getAsLong(); int lifetime=values.stream().mapToInt(p->p.delayTicks()+p.durationTicks()).max().orElseThrow();
                sink.send(new AbilityVfxPacket.Cue(session,n,cueId,event.context().castId(),visual.id(),event.hook(),event.actionIndex(),emissionIndex,now,now,lifetime,event.anchor(),values));
            } catch (RuntimeException ignored) { /* one malformed emission does not suppress peers */ }
        } catch (RuntimeException ignored) { /* strictly presentation only */ }
    }
    private static AbilityVfxPacket.Primitive resolve(AbilityVisualDefinition.PrimitiveSpec p, AbilityDefinition.ActionSpec action) {
        return new AbilityVfxPacket.Primitive(p.type(),p.delayTicks(),p.durationTicks(),p.argb(),p.width(),p.density(),p.seed(),p.localOffset(),p.yawRadians(), scalar(p.size(),action),scalar(p.radius(),action),scalar(p.length(),action),scalar(p.height(),action),scalar(p.angle(),action),scalar(p.startAngle(),action),scalar(p.sweepAngle(),action),scalar(p.turns(),action),p.count(),p.controlPoints());
    }
    private static double scalar(AbilityVisualDefinition.Scalar scalar, AbilityDefinition.ActionSpec action) {
        if(scalar==null) return 0;
        if(scalar instanceof AbilityVisualDefinition.Literal literal) return literal.value();
        if(!(action instanceof AbilityDefinition.CircleTelegraph circle)) throw new IllegalArgumentException("Action field unavailable");
        return switch((AbilityVisualDefinition.ActionField)scalar) { case RADIUS -> circle.radius(); case INNER_RADIUS, WIDTH, LENGTH -> throw new IllegalArgumentException("Action field unavailable"); };
    }
    private static UUID stable(UUID session,UUID cast,String visual,AbilityLifecycleEvent.Hook hook,int action,String emission) {
        try { MessageDigest d=MessageDigest.getInstance("SHA-256"); d.update(session.toString().getBytes(StandardCharsets.UTF_8)); d.update(cast.toString().getBytes(StandardCharsets.UTF_8)); d.update(visual.getBytes(StandardCharsets.UTF_8)); d.update((byte)hook.ordinal()); d.update(java.nio.ByteBuffer.allocate(4).putInt(action).array()); d.update(emission.getBytes(StandardCharsets.UTF_8)); byte[] b=d.digest(); return new UUID(bytes(b,0),bytes(b,8)); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static long bytes(byte[] b,int o) { long x=0; for(int i=0;i<8;i++) x=(x<<8)|(b[o+i]&255L); return x; }
}
