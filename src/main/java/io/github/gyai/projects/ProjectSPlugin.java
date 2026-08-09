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
import io.github.gyai.projects.combat.classsystem.WarriorEffectManager;
import io.github.gyai.projects.combat.classsystem.WarriorLoadoutManager;
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
import io.github.gyai.projects.listener.MonsterListener;
import io.github.gyai.projects.listener.HardControlTestToolListener;
import io.github.gyai.projects.listener.RangedWeaponListener;
import io.github.gyai.projects.manager.EnhancementManager;
import io.github.gyai.projects.manager.BalanceTuningManager;
import io.github.gyai.projects.manager.MonsterManager;
import io.github.gyai.projects.manager.TelegraphManager;
import io.github.gyai.projects.combat.skill.SkillEffectRenderer;
import io.github.gyai.projects.network.HudStatePacket;
import io.github.gyai.projects.network.WarriorLoadoutChannel;
import io.github.gyai.projects.network.WarriorLoadoutRequestPacket;
import io.github.gyai.projects.network.WarriorLoadoutSelectPacket;
import io.github.gyai.projects.network.WarriorLoadoutStatePacket;
import io.github.gyai.projects.network.BalanceStatePacket;
import io.github.gyai.projects.network.BalanceTuningChannel;
import io.github.gyai.projects.network.MonsterUiPacket;
import io.github.gyai.projects.network.MobEditorChannel;
import io.github.gyai.projects.network.MobEditorStatePacket;
import io.github.gyai.projects.monster.editor.MobEditorManager;
import io.github.gyai.projects.network.TelegraphPacket;
import io.github.gyai.projects.status.StatusEffectManager;
import io.github.gyai.projects.skill.warrior.WarriorAttackSkills;
import io.github.gyai.projects.skill.warrior.WarriorDefenseSkills;
import io.github.gyai.projects.skill.warrior.WarriorMobilitySkills;
import io.github.gyai.projects.skill.warrior.WarriorSkillSupport;
import io.github.gyai.projects.skill.warrior.WarriorUltimateSkills;
import io.github.gyai.projects.dev.HardControlTestTool;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.combat.damage.StarterSwordDamageShadow;
import io.github.gyai.projects.combat.damage.BukkitDamageShadowRuntimeContextResolver;
import io.github.gyai.projects.combat.damage.DamageShadowCommandService;
import io.github.gyai.projects.combat.damage.DamageShadowDispatcher;
import io.github.gyai.projects.combat.damage.DamageShadowValidationController;
import io.github.gyai.projects.combat.damage.DamageShadowValidationExporter;
import io.github.gyai.projects.combat.damage.DamageShadowValidationTracker;
import io.github.gyai.projects.combat.damage.DamageServiceStarterSwordRuntime;
import io.github.gyai.projects.combat.damage.StarterSwordDamageRoutePolicy;
import io.github.gyai.projects.combat.damage.StarterSwordDamageRouter;
import io.github.gyai.projects.combat.damage.StarterSwordRouteCommandService;
import io.github.gyai.projects.combat.damage.StarterSwordRouteController;
import io.github.gyai.projects.combat.damage.StarterSwordRouteTracker;
import io.github.gyai.projects.combat.damage.SpinSlashDamageShadow;
import io.github.gyai.projects.lifecycle.ShutdownSequence;
import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.BetaRuntime;
import io.github.gyai.projects.beta.activation.BetaRuntimeCommandService;
import io.github.gyai.projects.beta.activation.BetaActivationWave1CompositionRoot;
import io.github.gyai.projects.beta.activation.ConfirmedDamageHitObserver;
import io.github.gyai.projects.beta.activation.PreHitDamageModifier;
import io.github.gyai.projects.feature.FeatureFlagService;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.ability.BukkitAbilityRuntime;
import io.github.gyai.projects.ability.DevAbilityService;

import java.time.Clock;
import java.util.logging.Level;

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
    private StatusEffectManager statusEffectManager;
    private WarriorCombatManager warriorCombatManager;
    private WarriorEffectManager warriorEffectManager;
    private WarriorLoadoutManager warriorLoadoutManager;
    private WarriorLoadoutChannel warriorLoadoutChannel;
    private BalanceTuningManager balanceTuningManager;
    private BalanceTuningChannel balanceTuningChannel;
    private MonsterManager monsterManager;
    private TelegraphManager telegraphManager;
    private DamageService damageService;
    private DevAbilityService devAbilityService;
    private StarterSwordDamageShadow starterSwordDamageShadow;
    private SpinSlashDamageShadow spinSlashDamageShadow;
    private MobEditorManager mobEditorManager;
    private MobEditorChannel mobEditorChannel;
    private ShutdownSequence shutdownSequence;
    private BetaRuntime betaRuntime;
    private BetaActivationWave1CompositionRoot betaComposition;
    private BetaActivationPolicy betaActivationPolicy = BetaActivationPolicy.defaults();
    private FeatureFlagSnapshot betaFeatureFlags = FeatureFlagSnapshot.allDisabled();
    private ConfirmedDamageHitObserver betaConfirmedHitObserver =
            ConfirmedDamageHitObserver.NO_OP;
    private PreHitDamageModifier betaPreHitDamageModifier = PreHitDamageModifier.NO_OP;

    @Override
    public void onEnable() {
        shutdownSequence = null;
        saveDefaultConfig();
        snapshotBetaConfiguration();
        playerManager = new PlayerManager();
        crowdControlManager = new CrowdControlManager(this);
        statusEffectManager = new StatusEffectManager(this);
        telegraphManager = new TelegraphManager(this);
        monsterManager = new MonsterManager(
                this,
                crowdControlManager,
                statusEffectManager,
                playerManager,
                telegraphManager);
        monsterManager.initialize();
        boolean debugEnabled = getConfig().getBoolean(
                "debug.enabled", getConfig().getBoolean("debug", false));
        boolean allowCreativeSkillTest = getConfig().getBoolean(
                "debug.allow-creative-skill-test", false);
        boolean painterMageEnabled = getConfig().getBoolean(
                "classes.painter-mage.enabled", false);
        ItemManager itemManager = new ItemManager(this);
        itemManager.initialize(painterMageEnabled);
        HardControlTestTool hardControlTestTool = new HardControlTestTool(
                this, itemManager);
        balanceTuningManager = new BalanceTuningManager(this, itemManager);
        EnhancementManager enhancementManager = new EnhancementManager(
                this, itemManager, balanceTuningManager);
        balanceTuningManager.setEnhancementManager(enhancementManager);
        itemManager.setItemInitializer(enhancementManager::refreshWeapon);
        EnhancementListener enhancementListener = new EnhancementListener(
                this, itemManager, enhancementManager);
        skillManager = new SkillManager(playerManager);
        trainingDummyManager = new TrainingDummyManager(this);
        damageService = new DamageService(
                playerManager, itemManager, enhancementManager,
                trainingDummyManager);
        devAbilityService = new DevAbilityService(
                new BukkitAbilityRuntime(this, damageService, telegraphManager, monsterManager),
                monsterManager);
        initializeBetaComposition();
        Clock damageShadowClock = Clock.systemUTC();
        BukkitDamageShadowRuntimeContextResolver damageShadowContextResolver =
                new BukkitDamageShadowRuntimeContextResolver(
                        trainingDummyManager,
                        monsterManager,
                        enhancementManager,
                        itemManager,
                        damageShadowClock);
        DamageShadowValidationController damageShadowController =
                new DamageShadowValidationController(
                        getConfig().getBoolean(
                                "combat.damage-foundation.starter-sword-shadow-enabled",
                                false),
                        new DamageShadowValidationTracker(),
                        new DamageShadowValidationExporter(),
                        getDataFolder().toPath()
                                .resolve("debug")
                                .resolve("damage-shadow"),
                        damageShadowClock,
                        getLogger());
        starterSwordDamageShadow =
                new StarterSwordDamageShadow(
                        damageService,
                        damageShadowController,
                        damageShadowContextResolver,
                        debugEnabled,
                        getLogger());
        DamageShadowCommandService damageShadowCommandService =
                new DamageShadowCommandService(damageShadowController);
        DamageShadowValidationController spinSlashShadowController =
                new DamageShadowValidationController(
                        getConfig().getBoolean(
                                "combat.damage-foundation.warrior-spin-slash-shadow-enabled",
                                false),
                        new DamageShadowValidationTracker(),
                        new DamageShadowValidationExporter(
                                "spin-slash-shadow"),
                        getDataFolder().toPath()
                                .resolve("debug")
                                .resolve("damage-shadow")
                                .resolve("spin-slash"),
                        damageShadowClock,
                        getLogger());
        spinSlashDamageShadow = new SpinSlashDamageShadow(
                damageService,
                spinSlashShadowController,
                damageShadowContextResolver,
                debugEnabled,
                getLogger());
        DamageShadowCommandService spinSlashShadowCommandService =
                new DamageShadowCommandService(
                        spinSlashShadowController,
                        "spin_slash",
                        "spin-slash");
        DamageShadowDispatcher damageShadowDispatcher =
                new DamageShadowDispatcher(
                        damageService::apply,
                        java.util.List.of(spinSlashDamageShadow));
        StarterSwordRouteController damageRouteController =
                new StarterSwordRouteController(
                        getConfig().getBoolean(
                                "combat.damage-foundation.starter-sword-authoritative-enabled",
                                false),
                        new StarterSwordRouteTracker(),
                        damageShadowClock);
        StarterSwordDamageRouter starterSwordDamageRouter =
                new StarterSwordDamageRouter(
                        new DamageServiceStarterSwordRuntime(damageService),
                        starterSwordDamageShadow,
                        damageRouteController,
                        new StarterSwordDamageRoutePolicy());
        StarterSwordRouteCommandService damageRouteCommandService =
                new StarterSwordRouteCommandService(damageRouteController);
        mobEditorManager = new MobEditorManager(
                this, monsterManager, itemManager, damageService);
        mobEditorChannel = new MobEditorChannel(
                this, mobEditorManager, monsterManager);
        resourceManager = new ResourceManager(playerManager);
        warriorCombatManager = new WarriorCombatManager(
                this,
                itemManager,
                resourceManager,
                trainingDummyManager,
                enhancementManager,
                damageService,
                getConfig().getDouble(
                        "classes.warrior.fighting-spirit-retention-seconds", 10.0),
                getConfig().getInt(
                        "classes.warrior.fighting-spirit-decay-per-second", 5),
                getConfig().getDouble(
                        "classes.warrior.damage-percent-per-fighting-spirit", 0.1),
                getConfig().getDouble(
                        "classes.warrior.maximum-spirit-healing-per-hit", 1.0));
        WarriorSkillSupport warriorSkillSupport = new WarriorSkillSupport(
                this, trainingDummyManager, enhancementManager,
                warriorCombatManager, balanceTuningManager,
                damageShadowDispatcher, betaPreHitDamageModifier, betaConfirmedHitObserver);
        warriorEffectManager = new WarriorEffectManager(
                this, warriorCombatManager, enhancementManager,
                trainingDummyManager, skillManager, damageService);
        warriorLoadoutManager = new WarriorLoadoutManager(
                warriorCombatManager, skillManager);
        warriorLoadoutChannel = new WarriorLoadoutChannel(
                this, warriorLoadoutManager, warriorCombatManager);
        warriorEffectManager.setLoadoutManager(warriorLoadoutManager);
        warriorLoadoutManager.setEffectManager(warriorEffectManager);
        warriorCombatManager.setEffectManager(warriorEffectManager);
        skillManager.register(new SpinSlashSkill(warriorSkillSupport));
        WarriorAttackSkills.register(skillManager, warriorSkillSupport);
        WarriorMobilitySkills.register(
                skillManager, warriorSkillSupport, warriorCombatManager,
                warriorEffectManager);
        WarriorDefenseSkills.register(
                skillManager, warriorSkillSupport, warriorEffectManager);
        WarriorUltimateSkills.register(
                skillManager, warriorSkillSupport, warriorEffectManager,
                playerManager);
        balanceTuningManager.loadOnEnable();
        balanceTuningChannel = new BalanceTuningChannel(
                this, balanceTuningManager);
        ClassRegistry classRegistry = new ClassRegistry();
        classRegistry.register(new ClassDefinition(
                        "warrior", "ウォーリアー", WarriorCombatManager.WARRIOR_WEAPON_ID,
                        ResourceDefinition.FIGHTING_SPIRIT,
                        Material.IRON_SWORD, "闘気を高めて戦う近接クラス"),
                new WarriorController(
                        skillManager, warriorCombatManager,
                        warriorEffectManager, warriorLoadoutManager));
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
                    this, damageService, painterPassiveManager);
            painterPassiveManager.setDamageService(painterDamageService);
            painterSkillExecutor = new PainterSkillExecutor(
                this, resourceManager, painterMana, skillManager,
                    targetingService, painterDamageService,
                    crowdControlManager, statusEffectManager, effectRenderer);
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
        warriorLoadoutChannel.setClassManager(classManager);
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
                resourceManager, enhancementManager, hardControlTestTool);
        clientInputListener.setDevMenuOpener(devMenuManager::open);

        getServer().getMessenger().registerIncomingPluginChannel(
                this, ClientInputListener.CHANNEL, clientInputListener);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, ClientInputListener.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, HudStatePacket.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, WarriorLoadoutRequestPacket.CHANNEL,
                warriorLoadoutChannel);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, WarriorLoadoutSelectPacket.CHANNEL,
                warriorLoadoutChannel);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, WarriorLoadoutStatePacket.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, BalanceTuningChannel.REQUEST_CHANNEL,
                balanceTuningChannel);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, BalanceTuningChannel.UPDATE_CHANNEL,
                balanceTuningChannel);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, BalanceTuningChannel.ACTION_CHANNEL,
                balanceTuningChannel);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, BalanceStatePacket.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, MonsterUiPacket.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, MobEditorChannel.REQUEST_CHANNEL, mobEditorChannel);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, MobEditorStatePacket.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, TelegraphPacket.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this,
                TelegraphPacket.HELLO_CHANNEL,
                telegraphManager);

        getServer().getOnlinePlayers().forEach(playerManager::initializePlayer);
        getServer().getOnlinePlayers().forEach(enhancementManager::refreshInventory);
        getServer().getPluginManager().registerEvents(damageService, this);
        getServer().getPluginManager().registerEvents(mobEditorManager, this);
        getServer().getPluginManager().registerEvents(mobEditorChannel, this);
        getServer().getPluginManager().registerEvents(warriorCombatManager, this);
        getServer().getPluginManager().registerEvents(warriorEffectManager, this);
        getServer().getPluginManager().registerEvents(
                new CombatListener(
                        itemManager, combatInputManager, combatHudManager,
                        trainingDummyManager, enhancementManager,
                        damageService, starterSwordDamageRouter,
                        betaPreHitDamageModifier, betaConfirmedHitObserver), this);
        getServer().getPluginManager().registerEvents(
                new HardControlTestToolListener(
                        hardControlTestTool,
                        crowdControlManager,
                        monsterManager,
                        damageService), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(playerManager, skillManager, combatHudManager, trainingDummyManager,
                        classManager, resourceManager,
                        warriorLoadoutManager, enhancementManager), this);
        getServer().getPluginManager().registerEvents(
                new TrainingDummyListener(
                        trainingDummyManager, warriorCombatManager), this);
        getServer().getPluginManager().registerEvents(devMenuManager, this);
        getServer().getPluginManager().registerEvents(
                new ClassEquipmentListener(this, classManager, resourceManager), this);
        if (painterSkillExecutor != null && painterDamageService != null) {
            getServer().getPluginManager().registerEvents(
                    new PainterCombatListener(
                            itemManager, painterSkillExecutor,
                            painterDamageService, damageService), this);
        }
        getServer().getPluginManager().registerEvents(enhancementListener, this);
        getServer().getPluginManager().registerEvents(rangedWeaponListener, this);
        getServer().getPluginManager().registerEvents(scoutController, this);
        getServer().getPluginManager().registerEvents(
                new MonsterListener(
                        monsterManager,
                        crowdControlManager,
                        statusEffectManager), this);
        getServer().getPluginManager().registerEvents(
                telegraphManager, this);
        trainingDummyManager.start();
        warriorCombatManager.start();
        warriorEffectManager.start();
        combatHudManager.start();
        telegraphManager.start();
        monsterManager.start();

        if (getCommand("projects") != null) {
            getCommand("projects").setExecutor(new ProjectCommand(
                    itemManager, trainingDummyManager, devMenuManager,
                    enhancementListener, monsterManager,
                    crowdControlManager, statusEffectManager,
                    playerManager, damageShadowCommandService,
                    spinSlashShadowCommandService,
                    damageRouteCommandService,
                    betaRuntime == null || betaComposition == null ? null
                            : new BetaRuntimeCommandService(
                            betaRuntime, betaComposition.operators()),
                    devAbilityService));
        }

        getLogger().info("ProjectS has started!");
    }

    @Override
    public void onDisable() {
        shutdownSequence().run();
    }

    private void snapshotBetaConfiguration() {
        try {
            org.bukkit.configuration.ConfigurationSection featureSection =
                    getConfig().getConfigurationSection("features");
            betaFeatureFlags = new FeatureFlagService(
                    featureSection == null ? java.util.Map.of()
                            : featureSection.getValues(false)).snapshot();
            org.bukkit.configuration.ConfigurationSection activationSection =
                    getConfig().getConfigurationSection("beta.activation");
            betaActivationPolicy = BetaActivationPolicy.parse(
                    activationSection == null ? java.util.Map.of()
                            : activationSection.getValues(false),
                    message -> getLogger().warning(
                            "Beta activation config: " + message));
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE,
                    "Beta configuration snapshot failed; Beta remains disabled",
                    exception);
            betaActivationPolicy = BetaActivationPolicy.defaults();
            betaFeatureFlags = FeatureFlagSnapshot.allDisabled();
        }
    }

    private void initializeBetaComposition() {
        BetaActivationWave1CompositionRoot partial = null;
        try {
            Clock clock = Clock.systemUTC();
            partial = BetaActivationWave1CompositionRoot.create(
                    this, betaActivationPolicy, betaFeatureFlags,
                    playerManager, trainingDummyManager, damageService, clock);
            BetaRuntime runtime = partial.createRuntime(
                    betaActivationPolicy, betaFeatureFlags, clock,
                    (message, exception) -> getLogger().log(
                            Level.WARNING, "Beta runtime: " + message, exception));
            runtime.start();
            betaComposition = partial;
            betaRuntime = runtime;
            betaPreHitDamageModifier = partial.preHitDamageModifier();
            betaConfirmedHitObserver = partial.confirmedHitObserver();
        } catch (RuntimeException exception) {
            if (partial != null) partial.close();
            betaComposition = null;
            betaRuntime = null;
            betaPreHitDamageModifier = PreHitDamageModifier.NO_OP;
            betaConfirmedHitObserver = ConfirmedDamageHitObserver.NO_OP;
            getLogger().log(Level.SEVERE,
                    "Beta composition failed safely; legacy startup will continue",
                    exception);
        }
    }

    private synchronized ShutdownSequence shutdownSequence() {
        if (shutdownSequence != null) {
            return shutdownSequence;
        }
        ShutdownSequence sequence = new ShutdownSequence(
                (name, exception) -> getLogger().log(
                        Level.WARNING,
                        "ProjectS cleanup failed: " + name,
                        exception));

        sequence.addIfPresent("betaRuntime.close",
                betaRuntime, BetaRuntime::close);
        sequence.addIfPresent("betaComposition.close",
                betaComposition, BetaActivationWave1CompositionRoot::close);
        sequence.addIfPresent("abilityRuntime.close",
                devAbilityService, DevAbilityService::close);
        sequence.add("scheduler.cancelTasks",
                () -> getServer().getScheduler().cancelTasks(this));
        sequence.addIfPresent("monsterManager.stop",
                monsterManager, MonsterManager::stop);
        sequence.addIfPresent("telegraphManager.stop",
                telegraphManager, TelegraphManager::stop);
        sequence.addIfPresent("combatHudManager.stop",
                combatHudManager, CombatHudManager::stop);
        sequence.addIfPresent("warriorCombatManager.stop",
                warriorCombatManager, WarriorCombatManager::stop);
        sequence.addIfPresent("warriorEffectManager.stop",
                warriorEffectManager, WarriorEffectManager::stop);
        sequence.addIfPresent("trainingDummyManager.stop",
                trainingDummyManager, TrainingDummyManager::stop);

        sequence.add("pluginChannels.unregister", () -> {
            getServer().getMessenger().unregisterIncomingPluginChannel(this);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        });

        sequence.addIfPresent("combatInputManager.clear",
                combatInputManager, CombatInputManager::clear);
        sequence.addIfPresent("warriorLoadoutManager.clear",
                warriorLoadoutManager, WarriorLoadoutManager::clear);
        sequence.addIfPresent("warriorLoadoutChannel.clear",
                warriorLoadoutChannel, WarriorLoadoutChannel::clear);
        sequence.addIfPresent("balanceTuningChannel.clear",
                balanceTuningChannel, BalanceTuningChannel::clear);
        sequence.addIfPresent("playerManager.clear",
                playerManager, PlayerManager::clear);
        sequence.addIfPresent("skillManager.clear",
                skillManager, SkillManager::clear);
        sequence.addIfPresent("resourceManager.clear",
                resourceManager, ResourceManager::clear);
        sequence.addIfPresent("mobEditorChannel.clear",
                mobEditorChannel, MobEditorChannel::clear);
        sequence.addIfPresent("mobEditorManager.clear",
                mobEditorManager, MobEditorManager::clear);
        sequence.addIfPresent("damageService.clear",
                damageService, DamageService::clear);
        sequence.addIfPresent("starterSwordDamageShadow.close",
                starterSwordDamageShadow, StarterSwordDamageShadow::close);
        sequence.addIfPresent("spinSlashDamageShadow.close",
                spinSlashDamageShadow, SpinSlashDamageShadow::close);
        sequence.addIfPresent("painterSkillExecutor.clearAll",
                painterSkillExecutor, PainterSkillExecutor::clearAll);
        sequence.addIfPresent("painterPassiveManager.clear",
                painterPassiveManager, PainterPassiveManager::clear);
        sequence.addIfPresent("crowdControlManager.clear",
                crowdControlManager, CrowdControlManager::clear);
        sequence.addIfPresent("statusEffectManager.clear",
                statusEffectManager, StatusEffectManager::clear);
        sequence.add("shutdown.log",
                () -> getLogger().info("ProjectS has stopped!"));
        shutdownSequence = sequence;
        return sequence;
    }
}
