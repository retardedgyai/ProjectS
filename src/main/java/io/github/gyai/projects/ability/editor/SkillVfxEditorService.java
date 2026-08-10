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
    public synchronized Snapshot revert(UUID requestedSession,String abilityId,long expectedRevision,String expectedBase,String expectedEffective) {
        requireSession(requestedSession); Snapshot before=snapshot(abilityId); compare(before,expectedRevision,expectedBase,expectedEffective);
        store.revert(abilityId,expectedRevision,before.baseFingerprint()); return snapshot(abilityId);
    }
    @Override public Optional<AbilityVisualDefinition> resolve(String abilityId) { try { return Optional.of(snapshot(abilityId).effective()); } catch(RuntimeException ignored) { return Optional.empty(); } }
    private void requireSession(UUID requested) { if(!session.equals(requested)) throw new StaleSession(); }
    private static void compare(Snapshot s,long revision,String base,String effective) { if(s.revision()!=revision) throw new VisualSessionOverrideStore.StaleRevisionException(); if(!s.baseFingerprint().equals(base) || !s.effectiveFingerprint().equals(effective)) throw new Conflict(); }
    /** SHA-256 of the canonical wire representation, not object identity. */
    public static String fingerprint(AbilityVisualDefinition visual) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(SkillVfxEditorProtocol.encodeVisual(visual))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
