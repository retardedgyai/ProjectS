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
    SkillVfxEditorService.Snapshot revert(UUID session, String abilityId, long revision, String baseFingerprint, String effectiveFingerprint);
}
