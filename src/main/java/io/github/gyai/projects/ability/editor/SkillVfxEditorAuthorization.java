package io.github.gyai.projects.ability.editor;

/** Pure policy: preview is a capability only and never authorizes mutation. */
public final class SkillVfxEditorAuthorization {
    public record Decision(boolean allowed, boolean previewAllowed) { }
    private SkillVfxEditorAuthorization() { }
    public static Decision decide(SkillVfxEditorProtocol.Operation operation, boolean open, boolean preview, boolean apply) {
        return new Decision(open && (operation != SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION && operation != SkillVfxEditorProtocol.Operation.REVERT_VISUAL_SESSION || apply), preview);
    }
}
