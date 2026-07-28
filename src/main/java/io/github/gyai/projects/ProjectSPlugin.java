package io.github.gyai.projects;

import io.github.gyai.projects.command.ProjectCommand;
import io.github.gyai.projects.manager.ItemManager;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.manager.CombatHudManager;
import io.github.gyai.projects.listener.CombatListener;
import io.github.gyai.projects.listener.PlayerListener;
import io.github.gyai.projects.skill.SkillManager;
import io.github.gyai.projects.skill.SpinSlashSkill;
import io.github.gyai.projects.dummy.TrainingDummyListener;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.input.CombatInputManager;
import io.github.gyai.projects.network.ClientInputListener;
import io.github.gyai.projects.dev.DevMenuManager;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.gyai.projects.combat.classsystem.ClassDefinition;
import io.github.gyai.projects.combat.classsystem.ClassManager;
import io.github.gyai.projects.combat.classsystem.ClassRegistry;
import io.github.gyai.projects.combat.classsystem.PainterMageController;
import io.github.gyai.projects.combat.classsystem.ScoutController;
import io.github.gyai.projects.combat.classsystem.WarriorCombatManager;
import io.github.gyai.projects.combat.classsystem.WarriorController;
import io.github.gyai.projects.combat.resource.ResourceDefinition;
import io.github.gyai.projects.combat.resource.ResourceManager;
import io.github.gyai.projects.combat.resource.ResourceType;
import org.bukkit.Material;
import io.github.gyai.projects.listener.ClassEquipmentListener;
import io.github.gyai.projects.combat.skill.TargetingService;
import io.github.gyai.projects.combat.skill.PainterPassiveManager;
import io.github.gyai.projects.combat.skill.SkillDamageService;
import io.github.gyai.projects.combat.skill.CrowdControlManager;
import io.github.gyai.projects.combat.skill.PainterSkillExecutor;
import io.github.gyai.projects.listener.PainterCombatListener;
import io.github.gyai.projects.listener.EnhancementListener;
import io.github.gyai.projects.listener.RangedWeaponListener;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.combat.skill.SkillEffectRenderer;
import io.github.gyai.projects.network.HudStatePacket;

public final class ProjectSPlugin extends JavaPlugin {
    private PlayerManager playerManager;
    private SkillManager skillManager;
    private CombatHudManager combatHudManager;
    private TrainingDummyManager trainingDummyManager;
    private CombatInputManager combatInputManager;
    private ClientInputListener clientInputListener;
    private DevMenuManager devMenuManager;
    private ResourceManager resourceManager;
    private ClassManager classManager;
    private PainterSkillExecutor painterSkillExecutor;
    private PainterPassiveManager painterPassiveManager;
    private CrowdControlManager crowdControlManager;
    private WarriorCombatManager warriorCombatManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean debugEnabled = getConfig().getBoolean(
                "debug.enabled", getConfig().getBoolean("debug", false));
        boolean allowCreativeSkillTest = getConfig().getBoolean(
                "debug.allow-creative-skill-test", false);
        boolean painterMageEnabled = getConfig().getBoolean(
                "classes.painter-mage.enabled", false);
        ItemManager itemManager = new ItemManager(this);
        itemManager.initialize(painterMageEnabled);
        playerManager = new PlayerManager();
        EnhancementManager enhancementManager = new EnhancementManager(this, itemManager);
        EnhancementListener enhancementListener = new EnhancementListener(
                this, itemManager, enhancementManager);
        skillManager = new SkillManager(playerManager);
        trainingDummyManager = new TrainingDummyManager(this);
        resourceManager = new ResourceManager(playerManager);
        warriorCombatManager = new WarriorCombatManager(
                this,
                itemManager,
                resourceManager,
                trainingDummyManager,
                getConfig().getDouble(
                        "classes.warrior.fighting-spirit-retention-seconds", 10.0),
                getConfig().getInt(
                        "classes.warrior.fighting-spirit-decay-per-second", 5),
                getConfig().getDouble(
                        "classes.warrior.damage-percent-per-fighting-spirit", 0.1),
                getConfig().getDouble(
                        "classes.warrior.maximum-spirit-healing-per-hit", 1.0));
        skillManager.register(new SpinSlashSkill(
                trainingDummyManager, enhancementManager, warriorCombatManager));
        ClassRegistry classRegistry = new ClassRegistry();
        classRegistry.register(new ClassDefinition(
                        "warrior", "ウォーリアー", WarriorCombatManager.WARRIOR_WEAPON_ID,
                        ResourceDefinition.FIGHTING_SPIRIT,
                        Material.IRON_SWORD, "闘気を高めて戦う近接クラス"),
                new WarriorController(skillManager, warriorCombatManager));
        SkillDamageService painterDamageService = null;
        if (painterMageEnabled) {
            ResourceDefinition painterMana = new ResourceDefinition(
                    ResourceType.MANA,
                    getConfig().getInt("classes.painter-mage.maximum-mana", 400),
                    getConfig().getDouble("classes.painter-mage.mana-regeneration-per-second", 8.0));
            TargetingService targetingService = new TargetingService(trainingDummyManager);
            SkillEffectRenderer effectRenderer = new SkillEffectRenderer(getConfig());
            painterPassiveManager = new PainterPassiveManager(
                    this, targetingService, effectRenderer,
                    getConfig().getBoolean("debug.painter-skills", false));
            painterPassiveManager.configure(
                    getConfig().getDouble("skills.painter.passive.record-window", 4),
                    getConfig().getDouble("skills.painter.passive.explosion-delay", .6),
                    getConfig().getDouble("skills.painter.passive.radius", 2.5),
                    getConfig().getDouble("skills.painter.passive.base-damage", 6));
            painterDamageService = new SkillDamageService(
                    this, trainingDummyManager, painterPassiveManager);
            painterPassiveManager.setDamageService(painterDamageService);
            crowdControlManager = new CrowdControlManager(this);
            painterSkillExecutor = new PainterSkillExecutor(
                    this, resourceManager, painterMana, skillManager,
                    targetingService, painterDamageService, crowdControlManager, effectRenderer);
            painterDamageService.setExecutor(painterSkillExecutor);
            classRegistry.register(new ClassDefinition(
                            "painter_mage", "画術師", "painter_staff", painterMana,
                            Material.BLAZE_ROD, "画題を選び、二段階入力で術を描く魔法職"),
                    new PainterMageController(
                            painterSkillExecutor, painterPassiveManager, skillManager));
        }
        ScoutController scoutController = new ScoutController(
                this, itemManager, enhancementManager, skillManager, trainingDummyManager);
        classRegistry.register(new ClassDefinition(
                        "scout", "Scout", "starter_bow", ResourceDefinition.NONE,
                        Material.BOW, "機動力と連射に優れたレンジドクラス"),
                scoutController);
        classManager = new ClassManager(itemManager, classRegistry);
        combatHudManager = new CombatHudManager(
                this, itemManager, playerManager, skillManager, trainingDummyManager,
                classManager, resourceManager);
        skillManager.setHudManager(combatHudManager);
        combatInputManager = new CombatInputManager(
                itemManager, skillManager, combatHudManager, allowCreativeSkillTest,
                debugEnabled, getLogger(), classManager);
        RangedWeaponListener rangedWeaponListener = new RangedWeaponListener(
                this, itemManager, enhancementManager, scoutController);
        clientInputListener = new ClientInputListener(
                combatInputManager, rangedWeaponListener, getLogger(), debugEnabled);
        devMenuManager = new DevMenuManager(
                this, itemManager, playerManager, skillManager, trainingDummyManager,
                combatInputManager, clientInputListener, classManager, classRegistry,
                resourceManager, enhancementManager);
        clientInputListener.setDevMenuOpener(devMenuManager::open);

        getServer().getMessenger().registerIncomingPluginChannel(
                this, ClientInputListener.CHANNEL, clientInputListener);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, ClientInputListener.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, HudStatePacket.CHANNEL);

        getServer().getOnlinePlayers().forEach(playerManager::initializePlayer);
        getServer().getPluginManager().registerEvents(warriorCombatManager, this);
        getServer().getPluginManager().registerEvents(
                new CombatListener(
                        itemManager, combatInputManager, combatHudManager,
                        trainingDummyManager, enhancementManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(playerManager, skillManager, combatHudManager, trainingDummyManager,
                        classManager, resourceManager), this);
        getServer().getPluginManager().registerEvents(
                new TrainingDummyListener(
                        trainingDummyManager, warriorCombatManager), this);
        getServer().getPluginManager().registerEvents(devMenuManager, this);
        getServer().getPluginManager().registerEvents(
                new ClassEquipmentListener(this, classManager, resourceManager), this);
        if (painterSkillExecutor != null && painterDamageService != null) {
            getServer().getPluginManager().registerEvents(
                    new PainterCombatListener(
                            itemManager, painterSkillExecutor, painterDamageService), this);
        }
        getServer().getPluginManager().registerEvents(enhancementListener, this);
        getServer().getPluginManager().registerEvents(rangedWeaponListener, this);
        getServer().getPluginManager().registerEvents(scoutController, this);
        trainingDummyManager.start();
        warriorCombatManager.start();
        combatHudManager.start();

        if (getCommand("projects") != null) {
            getCommand("projects").setExecutor(new ProjectCommand(
                    itemManager, trainingDummyManager, devMenuManager, enhancementListener));
        }

        getLogger().info("ProjectS has started!");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, ClientInputListener.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ClientInputListener.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, HudStatePacket.CHANNEL);
        if (combatInputManager != null) {
            combatInputManager.clear();
        }
        if (combatHudManager != null) {
            combatHudManager.stop();
        }
        if (warriorCombatManager != null) {
            warriorCombatManager.stop();
        }
        if (trainingDummyManager != null) {
            trainingDummyManager.stop();
        }
        if (playerManager != null) {
            playerManager.clear();
        }
        if (skillManager != null) {
            skillManager.clear();
        }
        if (resourceManager != null) {
            resourceManager.clear();
        }
        if (painterSkillExecutor != null) painterSkillExecutor.clearAll();
        if (painterPassiveManager != null) painterPassiveManager.clear();
        if (crowdControlManager != null) crowdControlManager.clear();
        getLogger().info("ProjectS has stopped!");
    }
}
