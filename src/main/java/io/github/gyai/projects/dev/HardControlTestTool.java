package io.github.gyai.projects.dev;

import io.github.gyai.projects.combat.skill.HardControlApplicationResult;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.monster.CustomMonster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class HardControlTestTool {
    public static final String ITEM_ID = "dev_hard_cc_tester";
    public static final String PERMISSION = "projects.dev";
    public static final int DEFAULT_DURATION_TICKS = 60;

    private final ItemManager itemManager;
    private final NamespacedKey modeKey;
    private final NamespacedKey durationKey;

    public HardControlTestTool(JavaPlugin plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
        modeKey = new NamespacedKey(plugin, "dev_cc_mode");
        durationKey = new NamespacedKey(plugin, "dev_cc_duration_ticks");
        itemManager.registerSimpleItem(
                ITEM_ID,
                "§b[開発用] ハードCCテスター：スタン",
                Material.BLAZE_ROD);
    }

    public ItemStack createItem() {
        ItemStack item = itemManager.createItem(ITEM_ID);
        if (item == null) {
            throw new IllegalStateException("Hard CC tester is not registered");
        }
        update(item, HardControlType.STUN, DEFAULT_DURATION_TICKS);
        return item;
    }

    public boolean isTestTool(ItemStack item) {
        return itemManager.isCustomItem(item, ITEM_ID);
    }

    public HardControlType getMode(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return HardControlType.STUN;
        }
        String raw = meta.getPersistentDataContainer().get(
                modeKey, PersistentDataType.STRING);
        if (raw == null) {
            return HardControlType.STUN;
        }
        try {
            HardControlType type = HardControlType.valueOf(raw);
            return HardControlTestSelection.supports(type)
                    ? type
                    : HardControlType.STUN;
        } catch (IllegalArgumentException ignored) {
            return HardControlType.STUN;
        }
    }

    public int getDurationTicks(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return DEFAULT_DURATION_TICKS;
        }
        Integer ticks = meta.getPersistentDataContainer().get(
                durationKey, PersistentDataType.INTEGER);
        return ticks != null && HardControlTestSelection.supportsDuration(ticks)
                ? ticks
                : DEFAULT_DURATION_TICKS;
    }

    public HardControlType cycleMode(ItemStack item) {
        HardControlType next = HardControlTestSelection.nextMode(getMode(item));
        update(item, next, getDurationTicks(item));
        return next;
    }

    public int cycleDuration(ItemStack item) {
        int next = HardControlTestSelection.nextDurationTicks(
                getDurationTicks(item));
        update(item, getMode(item), next);
        return next;
    }

    public void giveTo(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendActionBar(Component.text(
                    "このアイテムを取得する権限がありません",
                    NamedTextColor.RED));
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createItem());
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(
                player.getLocation(), item));
        player.sendMessage(Component.text(
                "ハードCCテスターを1本受け取りました。",
                NamedTextColor.GREEN));
    }

    public void showApplicationResult(
            Player player,
            CustomMonster monster,
            HardControlType type,
            int durationTicks,
            HardControlApplicationResult result
    ) {
        String target = monster.getData().displayName();
        double seconds = durationTicks / 20.0;
        String message = switch (result) {
            case APPLIED -> "%sへ%sを%.1f秒付与しました"
                    .formatted(target, type.displayName(), seconds);
            case REPLACED -> "%sの現在のCCを%sへ置き換えました"
                    .formatted(target, type.displayName());
            case REFRESHED -> "%sの%sの残り時間を更新しました"
                    .formatted(target, type.displayName());
            case REJECTED_LOWER_PRIORITY ->
                    "より優先度の高いCCが有効なため拒否されました";
            case REJECTED_IMMUNE -> "%sは%sを無効化しました"
                    .formatted(target, type.displayName());
            case INVALID_TARGET -> "対象へCCを付与できません";
        };
        NamedTextColor color = switch (result) {
            case APPLIED, REPLACED, REFRESHED -> NamedTextColor.GREEN;
            case REJECTED_LOWER_PRIORITY, REJECTED_IMMUNE -> NamedTextColor.YELLOW;
            case INVALID_TARGET -> NamedTextColor.RED;
        };
        player.sendActionBar(Component.text(message, color));
    }

    private void update(ItemStack item, HardControlType type, int durationTicks) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(modeKey, PersistentDataType.STRING, type.name());
        data.set(durationKey, PersistentDataType.INTEGER, durationTicks);
        meta.displayName(noItalic(Component.text(
                "[開発用] ハードCCテスター：" + type.displayName(),
                NamedTextColor.AQUA)));
        meta.lore(List.of(
                noItalic(Component.text("[開発専用]", NamedTextColor.GOLD)),
                noItalic(Component.text(
                        "現在のCC：" + type.displayName(),
                        NamedTextColor.WHITE)),
                noItalic(Component.text(
                        "効果時間：%.1f秒".formatted(durationTicks / 20.0),
                        NamedTextColor.WHITE)),
                Component.empty(),
                lore("通常右クリック：CCを切り替える"),
                lore("スニーク＋右クリック：時間を切り替える"),
                lore("攻撃：選択中のCCを付与"),
                lore("スニーク＋攻撃：CCを解除"),
                Component.empty(),
                lore("対象：ProjectSカスタムモブのみ"),
                noItalic(Component.text(
                        "通常ダメージは発生しません",
                        NamedTextColor.YELLOW))));
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
    }

    private Component lore(String text) {
        return noItalic(Component.text(text, NamedTextColor.GRAY));
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
