package io.github.gyai.projects.ability.editor;

/** Pure, testable terminal result mapping shared by the Bukkit boundary. */
public final class SkillVfxEditorStatusMapper {
    private SkillVfxEditorStatusMapper() { }
    public static SkillVfxEditorProtocol.Status map(Throwable error) {
        if (error instanceof SkillVfxEditorService.NotFound) return SkillVfxEditorProtocol.Status.NOT_FOUND;
        if (error instanceof SkillVfxEditorService.StaleSession || error instanceof VisualSessionOverrideStore.StaleRevisionException) return SkillVfxEditorProtocol.Status.STALE;
        if (error instanceof SkillVfxEditorService.Conflict) return SkillVfxEditorProtocol.Status.CONFLICT;
        return SkillVfxEditorProtocol.Status.MALFORMED;
    }
}
