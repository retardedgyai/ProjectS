package io.github.gyai.projects.command;

import io.github.gyai.projects.manager.ItemManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ProjectCommand implements CommandExecutor {

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

        ItemStack sword = ItemManager.createItem("starter_sword");

        if (sword == null) {
            player.sendMessage("§cアイテムの作成に失敗しました。");
            return true;
        }

        player.getInventory().addItem(sword);
        player.sendMessage("§aProjectSの剣を受け取りました！");

        return true;
    }
}