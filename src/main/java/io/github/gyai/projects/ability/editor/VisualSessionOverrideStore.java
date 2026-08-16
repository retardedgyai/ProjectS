package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory revisioned slots. Tombstones deliberately remain to prevent ABA. */
public final class VisualSessionOverrideStore {
    public record Slot(long revision, AbilityVisualDefinition visual, String effectiveFingerprint) {
        public boolean overridden() { return visual != null; }
    }
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();
    public Slot current(String abilityId, AbilityVisualDefinition base, String baseFingerprint) {
        return slots.getOrDefault(abilityId, new Slot(0, null, baseFingerprint));
    }
    public synchronized Slot apply(String abilityId, long expected, AbilityVisualDefinition visual, String fingerprint) {
        Slot old=slots.get(abilityId); long actual=old==null?0:old.revision();
        if(actual!=expected) throw new StaleRevisionException();
        Slot next=new Slot(actual+1, Objects.requireNonNull(visual), fingerprint); slots.put(abilityId,next); return next;
    }
    public synchronized Slot revert(String abilityId, long expected, String baseFingerprint) {
        Slot old=slots.get(abilityId); long actual=old==null?0:old.revision();
        if(actual!=expected) throw new StaleRevisionException();
        Slot next=new Slot(actual+1,null,baseFingerprint); slots.put(abilityId,next); return next;
    }
    public void clear() { slots.clear(); }
    public static final class StaleRevisionException extends RuntimeException { }
}
