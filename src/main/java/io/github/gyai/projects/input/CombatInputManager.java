package io.github.gyai.projects.input;

import io.github.gyai.projects.manager.CombatHudManager;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.network.SkillInputType;
import io.github.gyai.projects.skill.SkillManager;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class CombatInputManager {
    private static final String DODGE_COOLDOWN_ID = "dodge";
    private static final long INPUT_DEBOUNCE_MILLIS = 75L;

    private final ItemManager itemManager;
    private final SkillManager skillManager;
    private final CombatHudManager hudManager;
    private final boolean allowCreativeSkillTest;
    private final boolean debug;
    private final Logger logger;
    private final ClassManager classManager;
    private final Map<InputKey, Long> lastInputs = new HashMap<>();

    public CombatInputManager(
            ItemManager itemManager,
            SkillManager skillManager,
            CombatHudManager hudManager,
            boolean allowCreativeSkillTest,
            boolean debug,
            Logger logger,
            ClassManager classManager
    ) {
        this.itemManager = itemManager;
        this.skillManager = skillManager;
        this.hudManager = hudManager;
        this.allowCreativeSkillTest = allowCreativeSkillTest;
        this.debug = debug;
        this.logger = logger;
        this.classManager = classManager;
    }

    public void handle(Player player, SkillInputType inputType) {
        if (!canAcceptInput(player) || isDebounced(player, inputType)) {
            return;
        }

        if (inputType == SkillInputType.DODGE) {
            executeDodge(player);
            return;
        }
        if (classManager.handle(player, inputType)) {
            return;
        }
        switch (inputType) {
            case SKILL_1 -> skillManager.useSkill(player, "spin_slash");
            case SKILL_2, SKILL_3, SKILL_4, ULTIMATE -> hudManager.showTemporary(
                    player, Component.text("未実装スキル", NamedTextColor.YELLOW));
            case DODGE, BOW_FIRE_START, BOW_FIRE_STOP, OPEN_DEV_MENU -> { }
        }
    }

    public void removePlayer(Player player) {
        lastInputs.keySet().removeIf(key -> key.playerId().equals(player.getUniqueId()));
    }

    public void clear() {
        lastInputs.clear();
    }

    private boolean canAcceptInput(Player player) {
        GameMode gameMode = player.getGameMode();
        if (!player.isOnline() || player.isDead() || gameMode == GameMode.SPECTATOR) {
            return false;
        }
        if (gameMode == GameMode.CREATIVE) {
            if (!allowCreativeSkillTest) {
                if (debug) {
                    logger.info("[ProjectS] Input rejected: creative mode");
                }
                return false;
            }
        }
        boolean accepted = !isStunned(player) && (
                itemManager.isCustomItem(player.getInventory().getItemInMainHand(), "starter_sword")
                        || classManager.getActive(player) != null);
        if (accepted && gameMode == GameMode.CREATIVE && debug) {
            logger.info("[ProjectS] Creative skill test allowed for " + player.getName());
        }
        return accepted;
    }

    private boolean isStunned(Player player) {
        // 将来の状態異常システムの接続点。
        return false;
    }

    private boolean isDebounced(Player player, SkillInputType inputType) {
        long now = System.currentTimeMillis();
        InputKey key = new InputKey(player.getUniqueId(), inputType);
        Long previous = lastInputs.put(key, now);
        return previous != null && now - previous < INPUT_DEBOUNCE_MILLIS;
    }

    private void executeDodge(Player player) {
        double remaining = skillManager.getRemainingCooldownSeconds(player, DODGE_COOLDOWN_ID);
        if (remaining > 0.0) {
            hudManager.showTemporary(player, Component.text(
                    "回避 CD: %.1f秒".formatted(remaining), NamedTextColor.RED));
            return;
        }

        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() > 0.0) {
            direction.normalize().multiply(1.25).setY(0.12);
            player.setVelocity(direction);
        }
        player.getWorld().spawnParticle(
                Particle.SMOKE, player.getLocation().add(0, 0.5, 0), 15, 0.4, 0.3, 0.4, 0.03);
        player.getWorld().playSound(
                player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.7f);
        skillManager.startCooldown(player, DODGE_COOLDOWN_ID, 4.0, 0.0);
        hudManager.showTemporary(player, Component.text("回避！", NamedTextColor.AQUA));
    }

    private record InputKey(UUID playerId, SkillInputType inputType) {
    }
}
