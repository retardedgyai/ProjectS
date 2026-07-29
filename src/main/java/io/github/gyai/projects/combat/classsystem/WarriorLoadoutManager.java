package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.skill.SkillManager;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarriorLoadoutManager {
    private static final Map<WarriorLoadoutSlot, Set<String>> CANDIDATES =
            createCandidates();

    private final WarriorCombatManager combatManager;
    private final SkillManager skillManager;
    private final Map<UUID, WarriorLoadout> loadouts = new java.util.HashMap<>();
    private WarriorEffectManager effectManager;

    public WarriorLoadoutManager(
            WarriorCombatManager combatManager,
            SkillManager skillManager
    ) {
        this.combatManager = combatManager;
        this.skillManager = skillManager;
    }

    public void setEffectManager(WarriorEffectManager effectManager) {
        this.effectManager = effectManager;
    }

    public WarriorLoadout get(Player player) {
        return loadouts.computeIfAbsent(
                player.getUniqueId(), ignored -> WarriorLoadout.defaults());
    }

    public Set<String> candidates(WarriorLoadoutSlot slot) {
        return CANDIDATES.get(slot);
    }

    public ChangeResult select(
            Player player,
            WarriorLoadoutSlot slot,
            String skillId
    ) {
        ChangeResult validation = validate(player, slot, skillId);
        if (!validation.success()) return validation;

        WarriorLoadout current = get(player);
        String previousSkill = current.skill(slot);
        if (previousSkill.equals(skillId)) {
            return ChangeResult.success("すでに装備中です");
        }
        if (effectManager != null) {
            effectManager.cancelSkill(player, previousSkill);
        }
        loadouts.put(player.getUniqueId(), current.with(slot, skillId));
        return ChangeResult.success(
                slot.displayKey() + "スキルを変更しました");
    }

    public ChangeResult resetToDefaults(Player player) {
        if (!combatManager.isWarrior(player)) {
            return ChangeResult.failure("ウォーリアー装備中のみ変更できます");
        }
        if (combatManager.isInCombat(player)) {
            return ChangeResult.failure("戦闘中はスキルを変更できません");
        }
        WarriorLoadout previous = get(player);
        WarriorLoadout defaults = WarriorLoadout.defaults();
        if (effectManager != null) {
            for (WarriorLoadoutSlot slot : WarriorLoadoutSlot.values()) {
                if (!previous.skill(slot).equals(defaults.skill(slot))) {
                    effectManager.cancelSkill(player, previous.skill(slot));
                }
            }
        }
        loadouts.put(player.getUniqueId(), defaults);
        return ChangeResult.success("デフォルトロードアウトへ戻しました");
    }

    public ChangeResult validate(
            Player player,
            WarriorLoadoutSlot slot,
            String skillId
    ) {
        if (!combatManager.isWarrior(player)) {
            return ChangeResult.failure("ウォーリアー装備中のみ変更できます");
        }
        if (combatManager.isInCombat(player)) {
            return ChangeResult.failure("戦闘終了から10秒間は変更できません");
        }
        if (slot == null || skillId == null || skillId.length() > 64) {
            return ChangeResult.failure("不正なロードアウト要求です");
        }
        if (!CANDIDATES.get(slot).contains(skillId)) {
            return ChangeResult.failure("そのスロットには装備できないスキルです");
        }
        if (!skillManager.isRegistered(skillId)
                || !skillManager.getSkill(skillId).isEnabled()) {
            return ChangeResult.failure("利用できないスキルです");
        }
        return ChangeResult.success("");
    }

    public void removePlayer(Player player) {
        loadouts.remove(player.getUniqueId());
    }

    public void clear() {
        loadouts.clear();
    }

    private static Map<WarriorLoadoutSlot, Set<String>> createCandidates() {
        Map<WarriorLoadoutSlot, Set<String>> values =
                new EnumMap<>(WarriorLoadoutSlot.class);
        values.put(WarriorLoadoutSlot.Q, Set.of(
                "spin_slash", "sweeping_slash"));
        values.put(WarriorLoadoutSlot.E, Set.of(
                "warrior_charge", "execution_leap", "earth_shatter"));
        values.put(WarriorLoadoutSlot.R, Set.of(
                "indomitable_spirit", "battlefield_aura", "endure"));
        values.put(WarriorLoadoutSlot.F, Set.of(
                "fighting_spirit_release", "blood_battle", "end_war_strike"));
        return Map.copyOf(values);
    }

    public record ChangeResult(boolean success, String reason) {
        public static ChangeResult success(String reason) {
            return new ChangeResult(true, reason);
        }

        public static ChangeResult failure(String reason) {
            return new ChangeResult(false, reason);
        }
    }
}
