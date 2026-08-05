package io.github.gyai.projects.command;

import io.github.gyai.projects.beta.activation.BetaRuntimeCommandService;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.HardControlApplicationResult;
import io.github.gyai.projects.combat.skill.HardControlRemovalReason;
import io.github.gyai.projects.combat.skill.HardControlType;
import io.github.gyai.projects.combat.damage.DamageShadowCommandService;
import io.github.gyai.projects.combat.damage.DamageShadowCommandRouter;
import io.github.gyai.projects.combat.damage.StarterSwordRouteCommandService;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.dev.DevMenuManager;
import io.github.gyai.projects.listener.EnhancementListener;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.monster.CustomMonster;
import io.github.gyai.projects.status.StatusApplicationResult;
import io.github.gyai.projects.status.StatusEffectManager;
import io.github.gyai.projects.status.StatusEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
    private final MonsterManager monsterManager;
    private final CrowdControlManager crowdControlManager;
    private final StatusEffectManager statusEffectManager;
    private final PlayerManager playerManager;
    private final DamageShadowCommandRouter damageShadowCommandRouter;
    private final StarterSwordRouteCommandService damageRouteCommandService;
    private final BetaRuntimeCommandService betaRuntimeCommandService;

    public ProjectCommand(
            ItemManager itemManager,
            TrainingDummyManager dummyManager,
            DevMenuManager devMenuManager,
            EnhancementListener enhancementListener,
            MonsterManager monsterManager,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            PlayerManager playerManager,
            DamageShadowCommandService damageShadowCommandService,
            StarterSwordRouteCommandService damageRouteCommandService
    ) {
        this(itemManager, dummyManager, devMenuManager, enhancementListener,
                monsterManager, crowdControlManager, statusEffectManager,
                playerManager, damageShadowCommandService, null,
                damageRouteCommandService, null);
    }

    public ProjectCommand(
            ItemManager itemManager,
            TrainingDummyManager dummyManager,
            DevMenuManager devMenuManager,
            EnhancementListener enhancementListener,
            MonsterManager monsterManager,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            PlayerManager playerManager,
            DamageShadowCommandService damageShadowCommandService,
            DamageShadowCommandService spinSlashShadowCommandService,
            StarterSwordRouteCommandService damageRouteCommandService
    ) {
        this(itemManager, dummyManager, devMenuManager, enhancementListener,
                monsterManager, crowdControlManager, statusEffectManager,
                playerManager, damageShadowCommandService,
                spinSlashShadowCommandService, damageRouteCommandService, null);
    }

    public ProjectCommand(
            ItemManager itemManager,
            TrainingDummyManager dummyManager,
            DevMenuManager devMenuManager,
            EnhancementListener enhancementListener,
            MonsterManager monsterManager,
            CrowdControlManager crowdControlManager,
            StatusEffectManager statusEffectManager,
            PlayerManager playerManager,
            DamageShadowCommandService damageShadowCommandService,
            DamageShadowCommandService spinSlashShadowCommandService,
            StarterSwordRouteCommandService damageRouteCommandService,
            BetaRuntimeCommandService betaRuntimeCommandService
    ) {
        this.itemManager = itemManager;
        this.dummyManager = dummyManager;
        this.devMenuManager = devMenuManager;
        this.enhancementListener = enhancementListener;
        this.monsterManager = monsterManager;
        this.crowdControlManager = crowdControlManager;
        this.statusEffectManager = statusEffectManager;
        this.playerManager = playerManager;
        damageShadowCommandRouter = new DamageShadowCommandRouter(
                damageShadowCommandService, spinSlashShadowCommandService);
        this.damageRouteCommandService = damageRouteCommandService;
        this.betaRuntimeCommandService = betaRuntimeCommandService;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length > 0
                && args[0].equalsIgnoreCase("damage-shadow")) {
            return handleDamageShadowCommand(sender, args);
        }
        if (args.length > 0
                && args[0].equalsIgnoreCase("damage-route")) {
            return handleDamageRouteCommand(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("beta")) {
            return handleBetaCommand(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはゲーム内で実行してください。");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("dummy")) {
            return handleDummyCommand(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("boss")) {
            return handleBossCommand(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cc")) {
            return handleCrowdControlCommand(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            return handleStatusCommand(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("level")) {
            return handleLevelCommand(player, args);
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

    private boolean handleDamageShadowCommand(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission("projects.dev")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }
        DamageShadowCommandService.Response response =
                damageShadowCommandRouter.execute(
                        java.util.Arrays.copyOfRange(args, 1, args.length));
        String color = response.success() ? "§e" : "§c";
        for (String message : response.messages()) {
            sender.sendMessage(color + message);
        }
        return true;
    }

    private boolean handleDamageRouteCommand(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission("projects.dev")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }
        StarterSwordRouteCommandService.Response response =
                damageRouteCommandService.execute(
                        args.length >= 2 ? args[1] : "status");
        String color = response.success() ? "§e" : "§c";
        for (String message : response.messages()) {
            sender.sendMessage(color + message);
        }
        return true;
    }

    private boolean handleBetaCommand(CommandSender sender, String[] args) {
        if (betaRuntimeCommandService == null) {
            sender.sendMessage("§cBeta runtime diagnostics are unavailable.");
            return true;
        }
        BetaRuntimeCommandService.Response response = betaRuntimeCommandService.execute(
                args.length >= 2
                        ? java.util.Arrays.asList(args).subList(1, args.length)
                        : java.util.List.of("status"),
                new io.github.gyai.projects.beta.activation.BetaOperatorContributorRegistry.Context(
                        sender instanceof Player player ? player.getUniqueId() : null,
                        sender instanceof Player player ? player.getWorld().getName() : "console",
                        sender.hasPermission("projects.dev"), false));
        String color = response.success() ? "§e" : "§c";
        for (String message : response.messages()) sender.sendMessage(color + message);
        return true;
    }

    private boolean handleCrowdControlCommand(
            Player player,
            String[] args
    ) {
        if (!requireDevPermission(player)) {
            return true;
        }
        CustomMonster target = targetedMonster(player);
        if (target == null) {
            return true;
        }
        if (args.length >= 2
                && args[1].equalsIgnoreCase("clear")) {
            crowdControlManager.clear(
                    target.getEntity(),
                    HardControlRemovalReason.CLEARED);
            player.sendMessage("§a対象のハードCCを解除しました。");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(
                    "§e使用法: /projects cc <stun|fear|charm|root> <seconds> または clear");
            return true;
        }
        HardControlType type;
        try {
            type = HardControlType.valueOf(
                    args[1].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c不明なCCタイプです。");
            return true;
        }
        Integer ticks = parseDurationTicks(player, args[2]);
        if (ticks == null) {
            return true;
        }
        HardControlApplicationResult result =
                crowdControlManager.apply(
                        target.getEntity(), type, player, ticks);
        player.sendMessage(switch (result) {
            case APPLIED -> "§a" + type.displayName() + "を付与しました。";
            case REPLACED -> "§a既存CCを" + type.displayName() + "へ置換しました。";
            case REFRESHED -> "§a" + type.displayName() + "の時間を更新しました。";
            case REJECTED_LOWER_PRIORITY ->
                    "§c優先度が低いため付与されませんでした。";
            case REJECTED_IMMUNE -> "§c対象はこのCCに耐性があります。";
            case INVALID_TARGET -> "§c対象へCCを付与できませんでした。";
        });
        return true;
    }

    private boolean handleStatusCommand(
            Player player,
            String[] args
    ) {
        if (!requireDevPermission(player)) {
            return true;
        }
        CustomMonster target = targetedMonster(player);
        if (target == null) {
            return true;
        }
        if (args.length >= 2
                && args[1].equalsIgnoreCase("clear")) {
            statusEffectManager.clear(target.getEntity());
            player.sendMessage("§a対象の状態異常を解除しました。");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(
                    "§e使用法: /projects status <slow|poison|bleed|burn> <seconds> [strength] または clear");
            return true;
        }
        StatusEffectType type;
        try {
            type = StatusEffectType.valueOf(
                    args[1].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c不明な状態異常タイプです。");
            return true;
        }
        Integer ticks = parseDurationTicks(player, args[2]);
        if (ticks == null) {
            return true;
        }
        double strength = 0.0;
        if (args.length >= 4) {
            try {
                strength = Double.parseDouble(args[3]);
            } catch (NumberFormatException exception) {
                player.sendMessage("§c強度は0以上の数値で指定してください。");
                return true;
            }
            if (!Double.isFinite(strength)
                    || strength < 0.0
                    || strength > 100.0) {
                player.sendMessage("§c強度は0〜100で指定してください。");
                return true;
            }
        }
        StatusApplicationResult result =
                statusEffectManager.apply(
                        target.getEntity(),
                        type,
                        player,
                        ticks,
                        strength);
        player.sendMessage(switch (result) {
            case APPLIED -> "§a" + type.displayName() + "を付与しました。";
            case REFRESHED -> "§a" + type.displayName() + "を更新しました。";
            case REJECTED_IMMUNE -> "§c対象は状態異常に耐性があります。";
            case INVALID_TARGET -> "§c対象へ状態異常を付与できませんでした。";
        });
        return true;
    }

    private boolean handleLevelCommand(
            Player player,
            String[] args
    ) {
        if (!requireDevPermission(player)) {
            return true;
        }
        if (args.length == 2
                && args[1].equalsIgnoreCase("get")) {
            int level = playerManager.getPlayerData(player)
                    .getCombatLevel();
            player.sendMessage("§e現在の戦闘レベル: " + level);
            return true;
        }
        if (args.length == 3
                && args[1].equalsIgnoreCase("set")) {
            try {
                int level = Integer.parseInt(args[2]);
                if (level < 1 || level > 999) {
                    throw new NumberFormatException();
                }
                playerManager.getPlayerData(player)
                        .setCombatLevel(level);
                player.sendMessage(
                        "§a戦闘レベルを" + level + "に変更しました。");
            } catch (NumberFormatException exception) {
                player.sendMessage(
                        "§c戦闘レベルは1〜999で指定してください。");
            }
            return true;
        }
        player.sendMessage(
                "§e使用法: /projects level <set <1-999>|get>");
        return true;
    }

    private boolean handleBossCommand(Player player, String[] args) {
        if (!player.hasPermission("projects.dev")) {
            player.sendMessage("§cこのコマンドを実行する権限がありません。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§e使用法: /projects boss <spawn|remove>");
            return true;
        }

        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "spawn" -> {
                if (monsterManager.hasActiveHarborDevourer()) {
                    player.sendMessage("§cグロームはすでに生成されています。");
                    return true;
                }
                Location location = monsterManager.findSafeSpawnLocation(player);
                if (location == null) {
                    player.sendMessage("§c安全な生成位置が見つかりません。");
                    return true;
                }
                if (monsterManager.spawnHarborDevourer(location) == null) {
                    player.sendMessage("§cグロームの生成に失敗しました。");
                    return true;
                }
                player.sendMessage("§a港喰らいの巨獣 グロームを生成しました。");
            }
            case "remove" -> {
                if (monsterManager.removeHarborDevourer()) {
                    player.sendMessage("§aグロームを削除しました。");
                } else {
                    player.sendMessage("§c稼働中のグロームはいません。");
                }
            }
            default -> player.sendMessage("§e使用法: /projects boss <spawn|remove>");
        }
        return true;
    }

    private void giveMaterial(Player player, String id, int amount) {
        ItemStack item = itemManager.createItem(id);
        if (item == null) return;
        item.setAmount(amount);
        player.getInventory().addItem(item);
    }

    private CustomMonster targetedMonster(Player player) {
        CustomMonster target =
                monsterManager.findTargetedCustomMonster(player, 32.0);
        if (target == null) {
            player.sendMessage(
                    "§c視線先にProjectSカスタムモブがいません。");
        }
        return target;
    }

    private boolean requireDevPermission(Player player) {
        if (player.hasPermission("projects.dev")) {
            return true;
        }
        player.sendMessage(
                "§cこのコマンドを実行する権限がありません。");
        return false;
    }

    private Integer parseDurationTicks(
            Player player,
            String value
    ) {
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds)
                    || seconds <= 0.0
                    || seconds > 3600.0) {
                throw new NumberFormatException();
            }
            return (int) Math.max(1L, Math.round(seconds * 20.0));
        } catch (NumberFormatException exception) {
            player.sendMessage(
                    "§c秒数は0より大きい3600以下の数値で指定してください。");
            return null;
        }
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
