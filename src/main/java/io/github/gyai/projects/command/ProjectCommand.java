package io.github.gyai.projects.command;

import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.dev.DevMenuManager;
import io.github.gyai.projects.listener.EnhancementListener;
import io.github.gyai.projects.manager.EnhancementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ProjectCommand implements CommandExecutor {
    private final ItemManager itemManager;
    private final TrainingDummyManager dummyManager;
    private final DevMenuManager devMenuManager;
    private final EnhancementListener enhancementListener;

    public ProjectCommand(
            ItemManager itemManager,
            TrainingDummyManager dummyManager,
            DevMenuManager devMenuManager,
            EnhancementListener enhancementListener
    ) {
        this.itemManager = itemManager;
        this.dummyManager = dummyManager;
        this.devMenuManager = devMenuManager;
        this.enhancementListener = enhancementListener;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはゲーム内で実行してください。");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("dummy")) {
            return handleDummyCommand(player, args);
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("dev")
                || args[0].equalsIgnoreCase("devmenu"))) {
            devMenuManager.open(player);
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("enhance")
                || args[0].equalsIgnoreCase("強化"))) {
            enhancementListener.open(player);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("materials")) {
            giveMaterial(player, EnhancementManager.ENHANCEMENT_MATERIAL_ID, 64);
            giveMaterial(player, EnhancementManager.REPAIR_MATERIAL_ID, 32);
            player.sendMessage("§aテスト用の強化素材を受け取りました！");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("bow")) {
            ItemStack bow = itemManager.createItem("starter_bow");
            if (bow == null) {
                player.sendMessage("§c弓の作成に失敗しました。");
                return true;
            }
            player.getInventory().addItem(bow);
            player.sendMessage("§a風追いの弓を受け取りました！");
            return true;
        }

        ItemStack sword = itemManager.createItem("starter_sword");

        if (sword == null) {
            player.sendMessage("§cアイテムの作成に失敗しました。");
            return true;
        }

        player.getInventory().addItem(sword);
        player.sendMessage("§aProjectSの剣を受け取りました！");

        return true;
    }

    private void giveMaterial(Player player, String id, int amount) {
        ItemStack item = itemManager.createItem(id);
        if (item == null) return;
        item.setAmount(amount);
        player.getInventory().addItem(item);
    }

    private boolean handleDummyCommand(Player player, String[] args) {
        if (args.length == 1) {
            if (dummyManager.spawn(player) != null) {
                player.sendMessage(Component.text("訓練ダミーを生成しました。", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("安全な生成位置が見つかりません。", NamedTextColor.RED));
            }
            return true;
        }

        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "remove" -> {
                if (dummyManager.removeNearest(player)) {
                    player.sendMessage(Component.text("最も近い訓練ダミーを削除しました。", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("訓練ダミーが見つかりません。", NamedTextColor.RED));
                }
            }
            case "removeall" -> {
                int count = dummyManager.removeAll();
                player.sendMessage(Component.text("訓練ダミーを%d体削除しました。".formatted(count), NamedTextColor.GREEN));
            }
            case "reset" -> {
                dummyManager.resetPlayer(player);
                player.sendMessage(Component.text("訓練ダミーの計測をリセットしました。", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "使用法: /projects dummy [remove|removeall|reset]", NamedTextColor.YELLOW));
        }
        return true;
    }
}
