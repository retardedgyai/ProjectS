package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.AbilityVisualDefinition;
import java.util.List;
import java.util.UUID;

/** Narrow service seam for the permission-first packet boundary. */
public interface SkillVfxEditorServiceAccess {
    UUID serverSession();
    List<SkillVfxEditorService.CatalogItem> catalog();
    SkillVfxEditorService.Snapshot snapshot(String abilityId);
    SkillVfxEditorService.Snapshot apply(UUID session, String abilityId, long revision, String baseFingerprint, String effectiveFingerprint, AbilityVisualDefinition visual);
    /** Legacy v1 applies cannot express appearance; the concrete service performs this atomically. */
    default SkillVfxEditorService.Snapshot applyV1(UUID session, String abilityId, long revision, String baseFingerprint, String effectiveFingerprint, AbilityVisualDefinition visual) {
        return apply(session,abilityId,revision,baseFingerprint,effectiveFingerprint,SkillVfxEditorService.mergeV1Appearance(snapshot(abilityId).effective(),visual));
    }
    SkillVfxEditorService.Snapshot revert(UUID session, String abilityId, long revision, String baseFingerprint, String effectiveFingerprint);
}
