package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.combat.skill.PainterPassiveManager;
import io.github.gyai.projects.combat.skill.PainterSkillExecutor;
import io.github.gyai.projects.combat.skill.PainterSpell;
import io.github.gyai.projects.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PainterMageController implements ClassController {
    public static final long SELECTION_TIMEOUT_MILLIS = 5_000;
    private final PainterSkillExecutor executor;
    private final PainterPassiveManager passiveManager;
    private final SkillManager cooldowns;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public PainterMageController(PainterSkillExecutor executor, PainterPassiveManager passiveManager, SkillManager cooldowns) {
        this.executor = executor; this.passiveManager = passiveManager; this.cooldowns = cooldowns;
    }

    @Override public void handle(Player player, SkillSlot input) {
        Subject subject = getSubject(player);
        if (subject == Subject.NONE) {
            switch (input) {
                case SKILL_Q -> select(player, Subject.DESTRUCTION);
                case SKILL_W -> select(player, Subject.HARMONY);
                case SKILL_E -> select(player, Subject.BINDING);
                case SKILL_R -> cast(player, PainterSpell.SPIRALING_DESPAIR);
            }
            return;
        }
        if (input == SkillSlot.SKILL_R) { washBrush(player); return; }
        PainterSpell spell = PainterSpell.find(subject, input);
        if (spell != null && cast(player, spell)) selections.remove(player.getUniqueId());
    }

    private void select(Player player, Subject subject) {
        selections.put(player.getUniqueId(), new Selection(subject, System.currentTimeMillis()));
        player.sendMessage(Component.text(subject.displayName(), NamedTextColor.AQUA));
    }

    private boolean cast(Player player, PainterSpell spell) {
        boolean cast = executor.cast(player, spell);
        if (!cast) player.sendMessage(Component.text("マナ不足、クールダウン中、または無効なスキルです。", NamedTextColor.RED));
        return cast;
    }

    private void washBrush(Player player) {
        selections.remove(player.getUniqueId());
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0,1,0), 2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .3f, 1.8f);
        player.sendMessage(Component.text("Wash Brush", NamedTextColor.GRAY));
    }

    public Subject getSubject(Player player) {
        Selection selection=selections.get(player.getUniqueId());
        if(selection==null)return Subject.NONE;
        if(System.currentTimeMillis()-selection.time>=SELECTION_TIMEOUT_MILLIS){selections.remove(player.getUniqueId());return Subject.NONE;}
        return selection.subject;
    }

    public String cooldownSummary(Player player) {
        return "Disaster %.1fs | Serenity %.1fs | Torment %.1fs | Spiraling Despair %.1fs".formatted(
                cooldowns.getRemainingCooldownSeconds(player,"painter_subject_destruction"),
                cooldowns.getRemainingCooldownSeconds(player,"painter_subject_harmony"),
                cooldowns.getRemainingCooldownSeconds(player,"painter_subject_binding"),
                cooldowns.getRemainingCooldownSeconds(player,"painter_ultimate"));
    }

    @Override public void reset(Player player) {
        selections.remove(player.getUniqueId()); passiveManager.reset(player); executor.clearPlayer(player);
    }

    public void resetPassive(Player player){passiveManager.reset(player);}
    public java.util.List<String> passiveSummary(Player player){java.util.List<String> records=passiveManager.describe(player);return records.isEmpty()?java.util.List.of("記録なし"):records;}
    public void clearEffects(Player player){executor.clearPlayer(player);}
    public PainterSkillExecutor.EffectQuality cycleQuality(){return executor.cycleQuality();}
    public PainterSkillExecutor.EffectQuality getQuality(){return executor.getQuality();}

    @Override public Component getSelectionHud(Player player) {
        Subject subject=getSubject(player);
        if(subject==Subject.NONE)return Component.text("Subject: Disaster | Subject: Serenity | Subject: Torment | Spiraling Despair | "+cooldownSummary(player),NamedTextColor.AQUA);
        PainterSpell q=PainterSpell.find(subject,SkillSlot.SKILL_Q),w=PainterSpell.find(subject,SkillSlot.SKILL_W),e=PainterSpell.find(subject,SkillSlot.SKILL_E);
        return Component.text(subject.displayName()+" [SKILL_Q] "+q.displayName+" [SKILL_W] "+w.displayName+
                " [SKILL_E] "+e.displayName+" [SKILL_R] Wash Brush",NamedTextColor.AQUA);
    }

    public enum Subject {
        NONE("未選択"), DESTRUCTION("Subject: Disaster"), HARMONY("Subject: Serenity"), BINDING("Subject: Torment");
        private final String displayName; Subject(String displayName){this.displayName=displayName;} public String displayName(){return displayName;}
    }
    private record Selection(Subject subject,long time){}
}
