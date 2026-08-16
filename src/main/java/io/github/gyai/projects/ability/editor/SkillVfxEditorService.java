package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Authoritative, runtime-only visual editing facade. Definitions are never changed. */
public final class SkillVfxEditorService implements AbilityVisualResolver, SkillVfxEditorServiceAccess {
    public record CatalogItem(String abilityId, String displayName, String visualId, boolean hasVisual, long revision, String baseFingerprint, String effectiveFingerprint, boolean sessionOverride) { }
    public static final class NotFound extends RuntimeException { public NotFound(String m){super(m);} }
    public static final class StaleSession extends RuntimeException { }
    public static final class Conflict extends RuntimeException { }
    public record Snapshot(AbilityDefinition ability, String visualId, AbilityVisualDefinition base, AbilityVisualDefinition effective, long revision, String baseFingerprint, String effectiveFingerprint, boolean sessionOverride) { }
    private final AbilityRegistry abilities; private final AbilityVisualRegistry visuals; private final VisualSessionOverrideStore store=new VisualSessionOverrideStore(); private final UUID session=UUID.randomUUID();
    public SkillVfxEditorService(AbilityRegistry abilities, AbilityVisualRegistry visuals) { this.abilities=Objects.requireNonNull(abilities); this.visuals=Objects.requireNonNull(visuals); }
    public UUID serverSession() { return session; }
    public List<CatalogItem> catalog() { return abilities.list().stream().sorted(Comparator.comparing(AbilityDefinition::id)).map(a -> { try { Snapshot s=snapshot(a.id()); return new CatalogItem(a.id(),a.displayName(),s.visualId(),true,s.revision(),s.baseFingerprint(),s.effectiveFingerprint(),s.sessionOverride()); } catch(NotFound ignored) { return new CatalogItem(a.id(),a.displayName(),"",false,0,"","",false); }}).toList(); }
    public Snapshot snapshot(String abilityId) {
        AbilityDefinition ability=abilities.find(abilityId).orElseThrow(() -> new NotFound("Unknown ability"));
        String visualId=visuals.boundVisualId(abilityId).orElseThrow(() -> new NotFound("No visual binding"));
        AbilityVisualDefinition base=visuals.find(visualId).orElseThrow(() -> new NotFound("Missing bound visual")); String baseFp=fingerprint(base);
        VisualSessionOverrideStore.Slot slot=store.current(abilityId,base,baseFp); AbilityVisualDefinition effective=slot.overridden()?slot.visual():base;
        return new Snapshot(ability,visualId,base,effective,slot.revision(),baseFp,slot.overridden()?slot.effectiveFingerprint():baseFp,slot.overridden());
    }
    public synchronized Snapshot apply(UUID requestedSession,String abilityId,long expectedRevision,String expectedBase,String expectedEffective,AbilityVisualDefinition candidate) {
        requireSession(requestedSession); Snapshot before=snapshot(abilityId); compare(before,expectedRevision,expectedBase,expectedEffective);
        if(candidate==null || !candidate.id().equals(before.visualId())) throw new Conflict();
        AbilityVisualCrossValidator.validate(before.ability(),candidate); String fp=fingerprint(candidate);
        store.apply(abilityId,expectedRevision,candidate,fp); return snapshot(abilityId);
    }
    /** v1 clients cannot see appearance.  Keep the authoritative material for matching stable primitive ids. */
    public synchronized Snapshot applyV1(UUID requestedSession,String abilityId,long expectedRevision,String expectedBase,String expectedEffective,AbilityVisualDefinition candidate) {
        Snapshot before=snapshot(abilityId);
        return apply(requestedSession,abilityId,expectedRevision,expectedBase,expectedEffective,mergeV1Appearance(before.effective(),candidate));
    }
    /** v2 carries Appearance but cannot carry the server-owned Motion field. */
    public synchronized Snapshot applyV2(UUID requestedSession,String abilityId,long expectedRevision,String expectedBase,String expectedEffective,AbilityVisualDefinition candidate) {
        Snapshot before=snapshot(abilityId);
        return apply(requestedSession,abilityId,expectedRevision,expectedBase,expectedEffective,mergeV2Motion(before.effective(),candidate));
    }
    public static AbilityVisualDefinition mergeV1Appearance(AbilityVisualDefinition authoritative,AbilityVisualDefinition v1Candidate) {
        return mergeHidden(authoritative,v1Candidate,true,true);
    }
    public static AbilityVisualDefinition mergeV2Motion(AbilityVisualDefinition authoritative,AbilityVisualDefinition v2Candidate) {
        return mergeHidden(authoritative,v2Candidate,false,true);
    }
    private static AbilityVisualDefinition mergeHidden(AbilityVisualDefinition authoritative,AbilityVisualDefinition candidate,boolean preserveAppearance,boolean preserveMotion) {
        if(authoritative==null||candidate==null) throw new IllegalArgumentException("Missing visual");
        Map<String,AbilityVisualDefinition.PrimitiveSpec> existing=new HashMap<>();
        for(var h:authoritative.bindings()) for(var e:h.emissions()) for(var p:e.primitives()) existing.put(p.id(),p);
        List<AbilityVisualDefinition.HookBinding> bindings=new ArrayList<>();
        for(var h:candidate.bindings()) { List<AbilityVisualDefinition.Emission> emissions=new ArrayList<>(); for(var e:h.emissions()) { List<AbilityVisualDefinition.PrimitiveSpec> primitives=new ArrayList<>(); for(var p:e.primitives()) {
            AbilityVisualDefinition.PrimitiveSpec old=existing.get(p.id());
            AbilityVisualDefinition.Appearance appearance=preserveAppearance&&old!=null?old.appearance():p.appearance();
            MotionSpec motion=preserveMotion&&old!=null?old.motion():MotionSpec.LEGACY_DEFAULT;
            if(old!=null&&old.type()!=p.type()&&!motion.supports(p.type())) throw new IllegalArgumentException("Hidden Motion is incompatible with primitive type change");
            primitives.add(new AbilityVisualDefinition.PrimitiveSpec(p.id(),p.type(),p.delayTicks(),p.durationTicks(),p.argb(),p.width(),p.density(),p.seed(),p.localOffset(),p.yawRadians(),p.size(),p.radius(),p.length(),p.height(),p.angle(),p.startAngle(),p.sweepAngle(),p.turns(),p.count(),p.controlPoints(),appearance,motion));
        } emissions.add(new AbilityVisualDefinition.Emission(e.id(),e.actionIndex(),primitives)); } bindings.add(new AbilityVisualDefinition.HookBinding(h.hook(),emissions)); }
        return new AbilityVisualDefinition(candidate.schemaVersion(),candidate.id(),bindings);
    }
    public synchronized Snapshot revert(UUID requestedSession,String abilityId,long expectedRevision,String expectedBase,String expectedEffective) {
        requireSession(requestedSession); Snapshot before=snapshot(abilityId); compare(before,expectedRevision,expectedBase,expectedEffective);
        store.revert(abilityId,expectedRevision,before.baseFingerprint()); return snapshot(abilityId);
    }
    @Override public Optional<AbilityVisualDefinition> resolve(String abilityId) { try { return Optional.of(snapshot(abilityId).effective()); } catch(RuntimeException ignored) { return Optional.empty(); } }
    private void requireSession(UUID requested) { if(!session.equals(requested)) throw new StaleSession(); }
    private static void compare(Snapshot s,long revision,String base,String effective) { if(s.revision()!=revision) throw new VisualSessionOverrideStore.StaleRevisionException(); if(!s.baseFingerprint().equals(base) || !s.effectiveFingerprint().equals(effective)) throw new Conflict(); }
    /** Existing all-debug hashes stay v1; appearance and Motion use domain-separated additive forms. */
    public static String fingerprint(AbilityVisualDefinition visual) { try { boolean motion=visual.bindings().stream().flatMap(h->h.emissions().stream()).flatMap(e->e.primitives().stream()).anyMatch(p->!p.motion().isLegacyDefault()); boolean particles=visual.bindings().stream().flatMap(h->h.emissions().stream()).flatMap(e->e.primitives().stream()).anyMatch(p->p.appearance().kind()==AbilityVisualDefinition.AppearanceKind.PARTICLE); byte[] body; String prefixText; if(motion){body=SkillVfxEditorProtocolV3.encodeVisual(visual);prefixText="projects:skill_vfx_motion_v3\0";} else if(particles){body=SkillVfxEditorProtocolV2.encodeVisual(visual);prefixText="projects:skill_vfx_appearance_v2\0";} else {body=SkillVfxEditorProtocol.encodeVisual(visual);prefixText="";} if(!prefixText.isEmpty()){byte[] prefix=prefixText.getBytes(StandardCharsets.UTF_8);byte[] joined=Arrays.copyOf(prefix,prefix.length+body.length);System.arraycopy(body,0,joined,prefix.length,body.length);body=joined;} return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); } catch(Exception e) { throw new IllegalStateException(e); } }
}
