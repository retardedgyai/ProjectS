package io.github.gyai.projects.dev;

import io.github.gyai.projects.ProjectSPlugin;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.dummy.TrainingDummySession;
import io.github.gyai.projects.input.CombatInputManager;
import io.github.gyai.projects.item.CustomItem;
import io.github.gyai.projects.item.Weapon;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.network.ClientInputListener;
import io.github.gyai.projects.network.SkillInputType;
import io.github.gyai.projects.player.PlayerData;
import io.github.gyai.projects.skill.Skill;
import io.github.gyai.projects.skill.SkillManager;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import io.github.gyai.projects.combat.classsystem.ClassRegistry;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.combat.resource.ResourceType;
import io.github.gyai.projects.combat.classsystem.PainterMageController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.Attribute;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DevMenuManager implements Listener {
    public static final String PERMISSION = "projects.dev";
    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ProjectSPlugin plugin;
    private final ItemManager itemManager;
    private final PlayerManager playerManager;
    private final SkillManager skillManager;
    private final TrainingDummyManager dummyManager;
    private final CombatInputManager combatInputManager;
    private final ClientInputListener inputListener;
    private final ClassManager classManager;
    private final ClassRegistry classRegistry;
    private final ResourceManager resourceManager;
    private final EnhancementManager enhancementManager;
    private final Map<UUID, org.bukkit.Location> savedLocations = new HashMap<>();

    public DevMenuManager(
            ProjectSPlugin plugin,
            ItemManager itemManager,
            PlayerManager playerManager,
            SkillManager skillManager,
            TrainingDummyManager dummyManager,
            CombatInputManager combatInputManager,
            ClientInputListener inputListener,
            ClassManager classManager,
            ClassRegistry classRegistry,
            ResourceManager resourceManager,
            EnhancementManager enhancementManager
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.playerManager = playerManager;
        this.skillManager = skillManager;
        this.dummyManager = dummyManager;
        this.combatInputManager = combatInputManager;
        this.inputListener = inputListener;
        this.classManager = classManager;
        this.classRegistry = classRegistry;
        this.resourceManager = resourceManager;
        this.enhancementManager = enhancementManager;
    }

    public void open(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("Dev Menuを使用する権限がありません。", NamedTextColor.RED));
            return;
        }
        open(player, Page.MAIN, 0);
    }

    private void open(Player player, Page page, int pageNumber) {
        if (!player.hasPermission(PERMISSION)) {
            player.closeInventory();
            return;
        }
        DevMenuHolder holder = new DevMenuHolder(page, Math.max(0, pageNumber));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(page.title));
        holder.setInventory(inventory);
        fillBackground(inventory);
        switch (page) {
            case MAIN -> renderMain(holder, inventory);
            case WEAPONS -> renderItems(holder, inventory, true);
            case ITEMS -> renderItems(holder, inventory, false);
            case DUMMIES -> renderDummies(holder, inventory);
            case COMMANDS -> renderCommands(holder, inventory);
            case PLAYER -> renderPlayerTools(holder, inventory);
            case COMBAT -> renderCombatTools(holder, inventory);
            case STATS -> renderStats(holder, inventory, player);
            case DEBUG -> renderDebug(holder, inventory, player);
            case CLASSES -> renderClasses(holder, inventory, player);
            case CONFIRM_CLEAR, CONFIRM_REMOVE_ALL -> renderConfirmation(holder, inventory);
        }
        player.openInventory(inventory);
    }

    private void renderMain(DevMenuHolder holder, Inventory inventory) {
        category(holder, inventory, 10, Material.IRON_SWORD, "武器", Page.WEAPONS);
        category(holder, inventory, 12, Material.CHEST, "アイテム", Page.ITEMS);
        category(holder, inventory, 14, Material.ARMOR_STAND, "訓練ダミー", Page.DUMMIES);
        category(holder, inventory, 16, Material.COMMAND_BLOCK, "コマンド一覧", Page.COMMANDS);
        category(holder, inventory, 28, Material.PLAYER_HEAD, "プレイヤー操作", Page.PLAYER);
        category(holder, inventory, 30, Material.DIAMOND_SWORD, "戦闘テスト", Page.COMBAT);
        category(holder, inventory, 32, Material.BLAZE_ROD, "職業テスト", Page.CLASSES);
        category(holder, inventory, 34, Material.COMPARATOR, "デバッグ情報", Page.DEBUG);
        category(holder, inventory, 26, Material.NETHERITE_SWORD, "ステータス調整", Page.STATS);
        button(holder, inventory, 49, Material.SUNFLOWER, "更新", List.of("表示内容を再取得"),
                (player, click) -> open(player, Page.MAIN, 0));
        button(holder, inventory, 50, Material.BARRIER, "閉じる", List.of(), (player, click) -> player.closeInventory());
    }

    private void renderClasses(DevMenuHolder holder, Inventory inventory, Player player) {
        int slot = 10;
        for (ClassRegistry.RegisteredClass entry : classRegistry.getAll()) {
            var definition = entry.definition();
            button(holder, inventory, slot++, definition.devIcon(), definition.displayName(),
                    List.of("ID: " + definition.id(), definition.description(),
                            "専用武器: " + definition.requiredWeaponId(), "クリックで専用武器を取得"),
                    (target, click) -> giveById(target, definition.requiredWeaponId()));
        }
        button(holder, inventory, 12, Material.IRON_SWORD, "starter_swordを取得",
                List.of("既存の戦士テスト武器を1個取得します"),
                (target, click) -> giveById(target, "starter_sword"));
        var active = classManager.getActive(player);
        String className = active == null ? "未選択" : active.definition().displayName();
        String resource = active == null ? "なし" : "%s %.0f/%d".formatted(
                active.definition().resource().type().getDisplayName(),
                resourceManager.get(player, active.definition().resource()), active.definition().resource().maximum());
        button(holder, inventory, 19, Material.BOOK, "現在の職業", List.of(className, "リソース: " + resource),
                (target, click) -> open(target, Page.CLASSES, 0));
        button(holder, inventory, 28, Material.LAPIS_LAZULI, "マナを全回復",
                List.of("現在のマナを最大まで回復します", "クリックで実行"), (target, click) -> {
                    var current = classManager.getActive(target);
                    if (current == null || current.definition().resource().type() != ResourceType.MANA) {
                        target.sendMessage(Component.text("現在の職業リソースはマナではありません。", NamedTextColor.RED));
                    } else resourceManager.set(target, current.definition().resource(), current.definition().resource().maximum());
                });
        boolean infinite = resourceManager.hasInfiniteMana(player);
        button(holder, inventory, 30, infinite ? Material.LIME_DYE : Material.RED_DYE,
                "無限マナ: " + (infinite ? "ON" : "OFF"),
                List.of("スキル使用時のマナ消費を切り替えます"),
                (target, click) -> { resourceManager.toggleInfiniteMana(target); open(target, Page.CLASSES, 0); });
        boolean fullCdr = skillManager.hasFullCooldownReduction(player);
        button(holder, inventory, 32, fullCdr ? Material.LIME_DYE : Material.RED_DYE,
                "CDR 100%: " + (fullCdr ? "ON" : "OFF"),
                List.of("ON時は既存CDを解除し、新規CDを0にします"),
                (target, click) -> { skillManager.toggleFullCooldownReduction(target); open(target, Page.CLASSES, 0); });
        button(holder, inventory, 34, Material.CLOCK, "全クールダウン解除",
                List.of("ProjectSのスキルCDのみ解除します"),
                (target, click) -> skillManager.clearCooldowns(target));
        if (classRegistry.getAll().stream().filter(entry -> entry.definition().id().equals("painter_mage"))
                .map(ClassRegistry.RegisteredClass::controller).findFirst().orElse(null) instanceof PainterMageController painter) {
            button(holder, inventory, 37, Material.PAPER, "現在のSubject",
                    List.of(painter.getSubject(player).displayName(), painter.cooldownSummary(player)),
                    (target, click) -> open(target, Page.CLASSES, 0));
            button(holder, inventory, 38, Material.MILK_BUCKET, "パッシブ状態をリセット", List.of(),
                    (target, click) -> painter.resetPassive(target));
            button(holder, inventory, 36, Material.WRITABLE_BOOK, "パッシブ記録", painter.passiveSummary(player),
                    (target, click) -> open(target, Page.CLASSES, 0));
            button(holder, inventory, 39, Material.BARRIER, "設置物・継続領域を削除", List.of(),
                    (target, click) -> painter.clearEffects(target));
            button(holder, inventory, 40, Material.FIREWORK_STAR, "演出品質: " + painter.getQuality(),
                    List.of("クリックで LOW / MEDIUM / HIGH を切り替え"),
                    (target, click) -> { painter.cycleQuality(); open(target, Page.CLASSES, 0); });
        }
        navigation(holder, inventory, false, false);
    }

    private void renderItems(DevMenuHolder holder, Inventory inventory, boolean weapons) {
        List<CustomItem> entries = itemManager.getItems().stream()
                .filter(item -> (item instanceof Weapon) == weapons)
                .sorted(Comparator.comparing(CustomItem::getId)).toList();
        renderPaged(holder, inventory, entries, item -> {
            ItemStack icon = item.createItem();
            ItemMeta meta = icon.getItemMeta();
            meta.lore(lore("ID: " + item.getId(), "左クリック: 1個取得",
                    "Shift + 左クリック: 最大スタック取得", "登録元: ItemManager"));
            icon.setItemMeta(meta);
            return icon;
        }, (item, player, shift) -> give(player, item, shift));
    }

    private void renderDummies(DevMenuHolder holder, Inventory inventory) {
        List<TrainingDummyManager.DummyType> types = dummyManager.getDummyTypes();
        for (int slot = 0; slot < Math.min(types.size(), PAGE_SIZE); slot++) {
            TrainingDummyManager.DummyType type = types.get(slot);
            button(holder, inventory, slot, type.icon(), type.displayName(),
                    List.of("ID: " + type.id(), type.description(), "登録元: TrainingDummyManager"),
                    (player, click) -> {
                        boolean spawned = dummyManager.spawn(player, type.id()) != null;
                        player.sendMessage(Component.text(spawned
                                ? "訓練ダミーを生成しました。" : "安全な生成位置が見つかりません。",
                                spawned ? NamedTextColor.GREEN : NamedTextColor.RED));
                    });
        }
        navigation(holder, inventory, false, false);
    }

    private void renderCommands(DevMenuHolder holder, Inventory inventory) {
        List<CommandEntry> commands = List.of(
                new CommandEntry("/projects", "starter_swordを1個付与", "プレイヤー専用", true,
                        player -> giveById(player, "starter_sword")),
                new CommandEntry("/projects dummy", "訓練ダミーを生成", "プレイヤー専用", true,
                        dummyManager::spawn),
                new CommandEntry("/projects dummy remove", "最も近い訓練ダミーを削除", "プレイヤー専用", true,
                        player -> dummyManager.removeNearest(player)),
                new CommandEntry("/projects dummy removeall", "全訓練ダミーを削除（確認あり）", "プレイヤー専用", false,
                        player -> open(player, Page.CONFIRM_REMOVE_ALL, 0)),
                new CommandEntry("/projects dummy reset", "自分のDPS計測をリセット", "プレイヤー専用", true,
                        dummyManager::resetPlayer),
                new CommandEntry("/projects dev | devmenu", "開発メニューを開く", "権限: projects.dev", true,
                        this::open));
        for (int i = 0; i < commands.size(); i++) {
            CommandEntry entry = commands.get(i);
            button(holder, inventory, i, Material.COMMAND_BLOCK, entry.path(),
                    List.of(entry.description(), entry.note(), entry.executable() ? "クリックで実行" : "クリックで確認画面へ"),
                    (player, click) -> entry.action().accept(player));
        }
        navigation(holder, inventory, false, false);
    }

    private void renderPlayerTools(DevMenuHolder holder, Inventory inventory) {
        int slot = 0;
        for (GameMode mode : GameMode.values()) {
            GameMode target = mode;
            button(holder, inventory, slot++, Material.GRASS_BLOCK, gameModeName(mode), List.of("ゲームモードを変更"),
                    (player, click) -> player.setGameMode(target));
        }
        button(holder, inventory, 9, Material.GOLDEN_APPLE, "全回復", List.of("体力を最大まで回復します"),
                (player, click) -> {
                    var attribute = player.getAttribute(Attribute.MAX_HEALTH);
                    if (attribute != null) player.setHealth(attribute.getValue());
                });
        button(holder, inventory, 10, Material.COOKED_BEEF, "満腹度回復", List.of("満腹度と隠し満腹度を回復します"),
                (player, click) -> { player.setFoodLevel(20); player.setSaturation(20); });
        button(holder, inventory, 11, Material.BLAZE_POWDER, "闘気: 最大", List.of(),
                (player, click) -> data(player).setFightingSpirit(PlayerData.MAX_FIGHTING_SPIRIT));
        button(holder, inventory, 12, Material.GUNPOWDER, "闘気: 0", List.of(),
                (player, click) -> data(player).setFightingSpirit(0));
        button(holder, inventory, 13, Material.CLOCK, "クールダウン解除", List.of(),
                (player, click) -> skillManager.clearCooldowns(player));
        button(holder, inventory, 18, Material.MAP, "現在地を保存", List.of(),
                (player, click) -> savedLocations.put(player.getUniqueId(), player.getLocation().clone()));
        button(holder, inventory, 19, Material.ENDER_PEARL, "保存地点へ移動", List.of(),
                (player, click) -> {
                    org.bukkit.Location saved = savedLocations.get(player.getUniqueId());
                    if (saved != null) player.teleport(saved);
                    else player.sendMessage(Component.text("保存地点がありません。", NamedTextColor.RED));
                });
        button(holder, inventory, 26, Material.LAVA_BUCKET, "インベントリを削除", List.of("危険操作: 確認画面を表示"),
                (player, click) -> open(player, Page.CONFIRM_CLEAR, 0));
        navigation(holder, inventory, false, false);
    }

    private void renderCombatTools(DevMenuHolder holder, Inventory inventory) {
        int skillSlot = 0;
        for (Skill skill : skillManager.getSkills().stream().sorted(Comparator.comparing(Skill::getId)).toList()) {
            if (skillSlot >= 8) break;
            button(holder, inventory, skillSlot++, Material.IRON_SWORD, skill.getDisplayName() + "を発動",
                    List.of("ID: " + skill.getId(), "SkillManager経由"),
                    (player, click) -> skillManager.useSkill(player, skill.getId()));
        }
        button(holder, inventory, 8, Material.FEATHER, "回避を発動", List.of("CombatInputManager経由"),
                (player, click) -> combatInputManager.handle(player, SkillInputType.DODGE));
        button(holder, inventory, 9, Material.BLAZE_POWDER, "闘気を最大化", List.of(),
                (player, click) -> data(player).setFightingSpirit(PlayerData.MAX_FIGHTING_SPIRIT));
        button(holder, inventory, 10, Material.CLOCK, "全スキルCD解除", List.of(),
                (player, click) -> skillManager.clearCooldowns(player));
        button(holder, inventory, 18, Material.TARGET, "DPS計測開始", List.of("既存セッションをリセット", "次のダミー攻撃から自動開始"),
                (player, click) -> dummyManager.resetPlayer(player));
        button(holder, inventory, 19, Material.REDSTONE, "DPS計測停止・結果表示", List.of(),
                (player, click) -> {
                    if (dummyManager.finishPlayerSession(player) == null) {
                        player.sendMessage(Component.text("進行中のDPS計測はありません。", NamedTextColor.YELLOW));
                    }
                });
        button(holder, inventory, 20, Material.PAPER, "現在のDPSを表示", List.of(),
                (player, click) -> showDps(player));
        button(holder, inventory, 27, Material.TNT, "全ダミー削除", List.of("危険操作: 確認画面を表示"),
                (player, click) -> open(player, Page.CONFIRM_REMOVE_ALL, 0));
        button(holder, inventory, 28, Material.SHEARS, "周囲のダミー削除", List.of("半径16ブロック"),
                (player, click) -> player.sendMessage(Component.text(
                        dummyManager.removeNearby(player, 16) + "体削除しました。", NamedTextColor.GREEN)));
        navigation(holder, inventory, false, false);
    }

    private void renderStats(DevMenuHolder holder, Inventory inventory, Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        String weaponId = itemManager.getItemId(weapon);
        double finalAttack = enhancementManager.getAttackPower(player, weapon);
        double totalSpeed = enhancementManager.getTotalAttackSpeedBonus(player, weapon);
        double attackBonus = enhancementManager.getWeaponAttackPowerBonus(weapon);
        double speedBonus = enhancementManager.getWeaponAttackSpeedBonus(weapon);

        button(holder, inventory, 4, Material.NETHER_STAR, "現在の戦闘ステータス",
                List.of(
                        "武器: " + (weaponId == null ? "なし" : weaponId),
                        "武器の攻撃力調整: %+.1f".formatted(attackBonus),
                        "現在の最終攻撃力: %.2f".formatted(finalAttack),
                        "武器の速度調整: %+.1f%%".formatted(speedBonus * 100.0),
                        "強化込み攻撃速度: %+.1f%%".formatted(totalSpeed * 100.0)),
                (target, click) -> open(target, Page.STATS, 0));

        statButton(holder, inventory, 19, Material.RED_DYE, "攻撃力 -10", -10.0, 0.0);
        statButton(holder, inventory, 20, Material.ORANGE_DYE, "攻撃力 -1", -1.0, 0.0);
        statButton(holder, inventory, 21, Material.LIME_DYE, "攻撃力 +1", 1.0, 0.0);
        statButton(holder, inventory, 22, Material.GREEN_DYE, "攻撃力 +10", 10.0, 0.0);

        statButton(holder, inventory, 28, Material.REDSTONE, "攻撃速度 -20%", 0.0, -0.20);
        statButton(holder, inventory, 29, Material.GUNPOWDER, "攻撃速度 -5%", 0.0, -0.05);
        statButton(holder, inventory, 30, Material.SUGAR, "攻撃速度 +5%", 0.0, 0.05);
        statButton(holder, inventory, 31, Material.FEATHER, "攻撃速度 +20%", 0.0, 0.20);

        button(holder, inventory, 40, Material.MILK_BUCKET, "ステータス補正をリセット",
                List.of("攻撃力と攻撃速度を初期値へ戻します"),
                (target, click) -> {
                    enhancementManager.resetWeaponBonuses(
                            target.getInventory().getItemInMainHand());
                    open(target, Page.STATS, 0);
                });
        navigation(holder, inventory, false, false);
    }

    private void statButton(
            DevMenuHolder holder,
            Inventory inventory,
            int slot,
            Material material,
            String name,
            double attackPowerChange,
            double attackSpeedChange
    ) {
        button(holder, inventory, slot, material, name,
                List.of("クリックするたびに変更", "メインハンドの武器自体に保存"),
                (player, click) -> {
                    ItemStack weapon = player.getInventory().getItemInMainHand();
                    if (!enhancementManager.isWeapon(weapon)) {
                        player.sendMessage(Component.text(
                                "メインハンドにProjectSの武器を持ってください。", NamedTextColor.RED));
                        return;
                    }
                    if (attackPowerChange != 0.0) {
                        enhancementManager.addWeaponAttackPowerBonus(weapon, attackPowerChange);
                    }
                    if (attackSpeedChange != 0.0) {
                        enhancementManager.addWeaponAttackSpeedBonus(weapon, attackSpeedChange);
                    }
                    open(player, Page.STATS, 0);
                });
    }

    private void renderDebug(DevMenuHolder holder, Inventory inventory, Player player) {
        PlayerData data = data(player);
        String itemId = itemManager.getItemId(player.getInventory().getItemInMainHand());
        ClientInputListener.ReceivedInput input = inputListener.getLastInput(player);
        TrainingDummySession session = dummyManager.getActiveSession(player, System.currentTimeMillis());
        var activeClass = classManager.getActive(player);
        List<String> details = new ArrayList<>(List.of(
                "プレイヤー: " + player.getName(), "UUID: " + player.getUniqueId(),
                "ゲームモード: " + player.getGameMode(), "武器ID: " + (itemId == null ? "なし" : itemId),
                "メインハンドItem ID: " + (itemId == null ? "なし" : itemId),
                "闘気: " + data.getFightingSpirit() + "/" + PlayerData.MAX_FIGHTING_SPIRIT,
                "DPS計測中: " + yesNo(session != null),
                "周囲のダミー: " + dummyManager.countNearby(player, 16),
                "プラグイン: " + plugin.getPluginMeta().getVersion(), "サーバー: " + Bukkit.getVersion()));
        details.add("職業: " + (activeClass == null ? "未選択" : activeClass.definition().displayName()));
        if (activeClass != null) {
            details.add("リソース: %s %.0f/%d".formatted(
                    activeClass.definition().resource().type().getDisplayName(),
                    resourceManager.get(player, activeClass.definition().resource()),
                    activeClass.definition().resource().maximum()));
        }
        details.add("クールダウン: " + formatCooldowns(player));
        details.add("最終Payload: " + (input == null ? "なし" : input.inputType() + " @ "
                + TIME_FORMAT.format(Instant.ofEpochMilli(input.receivedAtMillis()))));
        button(holder, inventory, 13, Material.BOOK, "プレイヤーデバッグ情報", details,
                (target, click) -> details.forEach(line -> target.sendMessage(Component.text(line, NamedTextColor.GRAY))));
        navigation(holder, inventory, false, false);
    }

    private void renderConfirmation(DevMenuHolder holder, Inventory inventory) {
        button(holder, inventory, 20, Material.LIME_CONCRETE, "実行する", List.of("この操作は取り消せません"),
                (player, click) -> {
                    if (holder.page() == Page.CONFIRM_CLEAR) player.getInventory().clear();
                    else player.sendMessage(Component.text(dummyManager.removeAll() + "体削除しました。", NamedTextColor.GREEN));
                    open(player, Page.MAIN, 0);
                });
        button(holder, inventory, 24, Material.RED_CONCRETE, "キャンセル", List.of(),
                (player, click) -> open(player, Page.MAIN, 0));
    }

    private <T> void renderPaged(DevMenuHolder holder, Inventory inventory, List<T> values,
                                 java.util.function.Function<T, ItemStack> icon,
                                 EntryAction<T> action) {
        int start = holder.pageNumber() * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, values.size());
        for (int index = start; index < end; index++) {
            T value = values.get(index);
            int slot = index - start;
            inventory.setItem(slot, icon.apply(value));
            holder.action(slot, (player, click) -> action.run(value, player, click.isShiftClick()));
        }
        navigation(holder, inventory, start > 0, end < values.size());
    }

    private void navigation(DevMenuHolder holder, Inventory inventory, boolean previous, boolean next) {
        if (previous) button(holder, inventory, 45, Material.ARROW, "前のページ", List.of(),
                (player, click) -> open(player, holder.page(), holder.pageNumber() - 1));
        button(holder, inventory, 47, Material.PAPER, "ページ " + (holder.pageNumber() + 1), List.of(),
                (player, click) -> { });
        button(holder, inventory, 48, Material.OAK_DOOR, "戻る", List.of(),
                (player, click) -> open(player, Page.MAIN, 0));
        button(holder, inventory, 49, Material.SUNFLOWER, "更新", List.of(),
                (player, click) -> open(player, holder.page(), holder.pageNumber()));
        button(holder, inventory, 50, Material.BARRIER, "閉じる", List.of(), (player, click) -> player.closeInventory());
        if (next) button(holder, inventory, 53, Material.ARROW, "次のページ", List.of(),
                (player, click) -> open(player, holder.page(), holder.pageNumber() + 1));
    }

    private void category(DevMenuHolder holder, Inventory inventory, int slot, Material material, String name, Page page) {
        button(holder, inventory, slot, material, name, List.of("クリックで開きます"),
                (player, click) -> open(player, page, 0));
    }

    private void button(DevMenuHolder holder, Inventory inventory, int slot, Material material,
                        String name, List<String> description, MenuAction action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.lore(lore(description.toArray(String[]::new)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        holder.action(slot, action);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private List<Component> lore(String... lines) {
        return java.util.Arrays.stream(lines)
                .<Component>map(line -> Component.text(line, NamedTextColor.GRAY)).toList();
    }

    private void give(Player player, CustomItem item, boolean stack) {
        ItemStack result = item.createItem();
        if (stack && result.getMaxStackSize() > 1) result.setAmount(result.getMaxStackSize());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void giveById(Player player, String id) {
        CustomItem item = itemManager.getItem(id);
        if (item != null) give(player, item, false);
    }

    private PlayerData data(Player player) {
        return playerManager.getPlayerData(player);
    }

    private String gameModeName(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "サバイバル";
            case CREATIVE -> "クリエイティブ";
            case ADVENTURE -> "アドベンチャー";
            case SPECTATOR -> "スペクテイター";
        };
    }

    private String yesNo(boolean value) { return value ? "はい" : "いいえ"; }

    private void showDps(Player player) {
        TrainingDummySession session = dummyManager.getActiveSession(player, System.currentTimeMillis());
        if (session == null) {
            player.sendMessage(Component.text("進行中のDPS計測はありません。", NamedTextColor.YELLOW));
            return;
        }
        long now = System.currentTimeMillis();
        player.sendMessage(Component.text("DPS: %.1f | Total: %.1f | Hits: %d".formatted(
                session.getAverageDps(now), session.getTotalDamage(), session.getHitCount()), NamedTextColor.GOLD));
    }

    private String formatCooldowns(Player player) {
        Map<String, Double> cooldowns = skillManager.getActiveCooldowns(player);
        if (cooldowns.isEmpty()) return "none";
        return cooldowns.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "%s %.1fs".formatted(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DevMenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission(PERMISSION)) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            boolean couldAffectMenu = event.isShiftClick()
                    || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR;
            if (couldAffectMenu) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        MenuAction action = holder.action(event.getRawSlot());
        if (action != null) action.run(player, event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DevMenuHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    public void removePlayer(Player player) {
        savedLocations.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
        inputListener.removePlayer(event.getPlayer());
    }

    enum Page {
        MAIN("ProjectS 開発メニュー"), WEAPONS("開発メニュー - 武器"), ITEMS("開発メニュー - アイテム"),
        DUMMIES("開発メニュー - 訓練ダミー"), COMMANDS("開発メニュー - コマンド一覧"),
        PLAYER("開発メニュー - プレイヤー操作"), COMBAT("開発メニュー - 戦闘テスト"),
        STATS("開発メニュー - ステータス調整"),
        CLASSES("開発メニュー - 職業テスト"), DEBUG("開発メニュー - デバッグ情報"),
        CONFIRM_CLEAR("確認 - インベントリ削除"), CONFIRM_REMOVE_ALL("確認 - ダミー全削除");
        private final String title;
        Page(String title) { this.title = title; }
    }

    @FunctionalInterface interface MenuAction { void run(Player player, org.bukkit.event.inventory.ClickType click); }
    @FunctionalInterface private interface EntryAction<T> { void run(T value, Player player, boolean shift); }
    private record CommandEntry(String path, String description, String note, boolean executable,
                                java.util.function.Consumer<Player> action) { }
}
