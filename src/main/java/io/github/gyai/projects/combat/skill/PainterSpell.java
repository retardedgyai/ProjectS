package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.combat.classsystem.PainterMageController.Subject;
import io.github.gyai.projects.combat.classsystem.SkillSlot;

public enum PainterSpell {
    DEVASTATING_FIRE("devastating-fire", "Devastating Fire", Subject.DESTRUCTION, SkillSlot.SKILL_Q, 80, 10, 16, 3, 8),
    SEVERING_BOLT("severing-bolt", "Severing Bolt", Subject.DESTRUCTION, SkillSlot.SKILL_W, 80, 10, 28, 2.5, 10),
    MOLTEN_FISSURE("molten-fissure", "Molten Fissure", Subject.DESTRUCTION, SkillSlot.SKILL_E, 80, 10, 14, 2.5, 3),
    FLEETING_CURRENT("fleeting-current", "Fleeting Current", Subject.HARMONY, SkillSlot.SKILL_Q, 90, 12, 12, 2, 0),
    POOL_OF_REFLECTION("pool-of-reflection", "Pool of Reflection", Subject.HARMONY, SkillSlot.SKILL_W, 90, 12, 14, 4, 0),
    STIRRING_LIGHTS("stirring-lights", "Stirring Lights", Subject.HARMONY, SkillSlot.SKILL_E, 90, 12, 0, 0, 0),
    GRIM_VISAGE("grim-visage", "Grim Visage", Subject.BINDING, SkillSlot.SKILL_Q, 50, 12, 16, 1, 6),
    GAZE_OF_THE_ABYSS("gaze-of-the-abyss", "Gaze of the Abyss", Subject.BINDING, SkillSlot.SKILL_W, 50, 12, 18, 5, 5),
    CRUSHING_MAW("crushing-maw", "Crushing Maw", Subject.BINDING, SkillSlot.SKILL_E, 50, 12, 14, 4, 7),
    SPIRALING_DESPAIR("spiraling-despair", "Spiraling Despair", Subject.NONE, SkillSlot.SKILL_R, 100, 100, 18, 5, 12);

    public final String configKey, displayName;
    public final Subject subject;
    public final SkillSlot slot;
    public final int defaultMana;
    public final double defaultCooldown, defaultRange, defaultRadius, defaultDamage;
    PainterSpell(String key, String name, Subject subject, SkillSlot slot, int mana, double cooldown,
                 double range, double radius, double damage) {
        this.configKey=key; this.displayName=name; this.subject=subject; this.slot=slot; this.defaultMana=mana;
        this.defaultCooldown=cooldown; this.defaultRange=range; this.defaultRadius=radius; this.defaultDamage=damage;
    }
    public String cooldownId() { return subject == Subject.NONE ? "painter_ultimate" : "painter_subject_" + subject.name().toLowerCase(); }
    public static PainterSpell find(Subject subject, SkillSlot slot) {
        for (PainterSpell spell : values()) if (spell.subject == subject && spell.slot == slot) return spell;
        return null;
    }
}
