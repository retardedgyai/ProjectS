package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track1.bukkit.BukkitEquipmentInventoryReader;
import io.github.gyai.projects.beta.activation.track1.bukkit.BukkitTrack1ListenerRegistrar;
import io.github.gyai.projects.beta.activation.track1.bukkit.BukkitTrack1PlayerListener;
import io.github.gyai.projects.beta.activation.track1.command.Track1OperatorCommandContributor;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.beta.activation.track1.module.EquipmentRuntimeModule;
import io.github.gyai.projects.beta.activation.track1.module.PlayerPersistenceRuntimeModule;
import io.github.gyai.projects.beta.activation.track1.module.Track1RuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressFileStore;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressService;
import io.github.gyai.projects.beta.activation.track1.spi.BetaOperatorSubject;
import io.github.gyai.projects.beta.activation.track2.BukkitTrainingDummyElementBoundary;
import io.github.gyai.projects.beta.activation.track2.CombatElementsRuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.beta.activation.track3.BoundedStagingOperationJournal;
import io.github.gyai.projects.beta.activation.track3.FileStagingTransactionAuditSink;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyOperatorContributor;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyPaths;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyService;
import io.github.gyai.projects.beta.activation.track3.StagingEnhancementOutcomeRegistry;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryTransactionAdapter;
import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;
import io.github.gyai.projects.beta.activation.track3.StagingTransactionJournalRepository;
import io.github.gyai.projects.beta.activation.track3.StagingTransactionRecoveryService;
import io.github.gyai.projects.beta.activation.track3.Track3RuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track3.infrastructure.BukkitStagingInventoryBridge;
import io.github.gyai.projects.beta.activation.track3.infrastructure.BukkitStagingInventoryPort;
import io.github.gyai.projects.beta.activation.track4.*;
import io.github.gyai.projects.dummy.TrainingDummyManager;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.manager.PlayerManager;
import io.github.gyai.projects.combat.damage.DamageService;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Policy;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Validator;
import io.github.gyai.projects.monster.definition.v2.reference.MobReferenceResolvers;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Policy;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Service;
import io.github.gyai.projects.monster.repository.MobDefinitionV2Codec;
import io.github.gyai.projects.monster.repository.MobDefinitionV2Repository;
import io.github.gyai.projects.network.beta.*;
import io.github.gyai.projects.party.PartyPolicy;
import io.github.gyai.projects.party.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Production composition root. Construction is inert; BetaRuntime owns module start/stop. */
public final class BetaActivationWave1CompositionRoot implements AutoCloseable {
    private final Track1RuntimeModuleProvider track1;
    private final CombatElementsRuntimeModuleProvider track2;
    private final Track3RuntimeModuleProvider track3;
    private final Track4RuntimeModuleProvider track4;
    private final List<BetaRuntimeModule> modules;
    private final Set<String> infrastructure;
    private final BetaOperatorContributorRegistry operators;
    private final StagingPlayerProgressService playerProgress;
    private final EquipmentInspectionService equipment;
    private final StagingTransactionJournalRepository transactionRepository;
    private final StagingTransactionRecoveryService recovery;
    private final FileStagingTransactionAuditSink auditSink;
    private final BoundedStagingOperationJournal operationJournal;
    private final FileStagingQuestProgressPort questProgress;
    private final FileStagingRewardClaimStore rewardClaims;
    private final ClientBetaProtocolRuntime protocol;
    private final ElementSnapshotProtocolPublisher elementPublisher;
    private final ConfirmedDamageHitObserver confirmedHitObserver;
    private boolean closed;

    private BetaActivationWave1CompositionRoot(
            Track1RuntimeModuleProvider track1,
            CombatElementsRuntimeModuleProvider track2,
            Track3RuntimeModuleProvider track3,
            Track4RuntimeModuleProvider track4,
            Set<String> infrastructure,
            BetaOperatorContributorRegistry operators,
            StagingPlayerProgressService playerProgress,
            EquipmentInspectionService equipment,
            StagingTransactionJournalRepository transactionRepository,
            StagingTransactionRecoveryService recovery,
            FileStagingTransactionAuditSink auditSink,
            BoundedStagingOperationJournal operationJournal,
            FileStagingQuestProgressPort questProgress,
            FileStagingRewardClaimStore rewardClaims,
            ClientBetaProtocolRuntime protocol,
            ElementSnapshotProtocolPublisher elementPublisher,
            ConfirmedDamageHitObserver confirmedHitObserver
    ) {
        this.track1 = track1; this.track2 = track2; this.track3 = track3; this.track4 = track4;
        this.infrastructure = Set.copyOf(infrastructure); this.operators = operators;
        this.playerProgress = playerProgress; this.equipment = equipment;
        this.transactionRepository = transactionRepository; this.recovery = recovery;
        this.auditSink = auditSink; this.questProgress = questProgress;
        this.operationJournal = operationJournal;
        this.rewardClaims = rewardClaims; this.protocol = protocol;
        this.elementPublisher = elementPublisher; this.confirmedHitObserver = confirmedHitObserver;
        modules = new BetaActivationWave1ProviderRegistry(
                track1, track2, track3, track4).modules();
    }

    public static BetaActivationWave1CompositionRoot create(
            JavaPlugin plugin,
            BetaActivationPolicy policy,
            FeatureFlagSnapshot flags,
            PlayerManager playerManager,
            TrainingDummyManager dummies,
            DamageService damageService,
            Clock clock
    ) {
        java.util.Objects.requireNonNull(plugin); java.util.Objects.requireNonNull(policy);
        java.util.Objects.requireNonNull(flags); java.util.Objects.requireNonNull(playerManager);
        java.util.Objects.requireNonNull(dummies); java.util.Objects.requireNonNull(damageService);
        java.util.Objects.requireNonNull(clock);
        Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();

        ClientBetaProtocolRuntime[] protocolHolder = new ClientBetaProtocolRuntime[1];
        StagingPlayerProgressService progress = new StagingPlayerProgressService(policy,
                new StagingPlayerProgressFileStore(
                        data.resolve("beta-staging").resolve("players"), Set.of()), clock);
        EquipmentInspectionService equipment = new EquipmentInspectionService(clock);
        BukkitEquipmentInventoryReader equipmentReader = new BukkitEquipmentInventoryReader();
        BukkitTrack1PlayerListener listener = new BukkitTrack1PlayerListener(
                playerManager, progress, equipment,
                playerId -> protocolHolder[0] != null
                        && !protocolHolder[0].capabilitySnapshot(playerId).oldClient());
        Track1RuntimeModuleProvider track1 = new Track1RuntimeModuleProvider(
                new PlayerPersistenceRuntimeModule(progress,
                        new BukkitTrack1ListenerRegistrar(plugin), listener),
                new EquipmentRuntimeModule(equipment));
        Track1OperatorCommandContributor track1Commands = new Track1OperatorCommandContributor(
                policy, progress, equipment, playerId -> {
                    Player player = Bukkit.getPlayer(playerId);
                    return player == null ? List.of() : equipmentReader.scan(player);
                });

        CombatElementsRuntimeModuleProvider track2 = new CombatElementsRuntimeModuleProvider(
                new BukkitTrainingDummyElementBoundary(plugin, dummies, damageService), clock);

        StagingEconomyPaths economyPaths = StagingEconomyPaths.under(data);
        StagingTransactionJournalRepository transactionRepository =
                new StagingTransactionJournalRepository(economyPaths.transactionsDirectory());
        FileStagingTransactionAuditSink auditSink =
                new FileStagingTransactionAuditSink(economyPaths, transactionRepository);
        BukkitStagingInventoryPort inventory = new BukkitStagingInventoryPort(
                new BukkitStagingInventoryBridge(Bukkit::getPlayer));
        BoundedStagingOperationJournal operationJournal =
                new BoundedStagingOperationJournal(512, auditSink);
        StagingTransactionRecoveryService recovery =
                new StagingTransactionRecoveryService(transactionRepository, operationJournal);
        StagingInventoryTransactionAdapter transactions =
                new StagingInventoryTransactionAdapter(
                        inventory, operationJournal, clock, UUID::randomUUID);
        StagingEconomyService economy = new StagingEconomyService(
                inventory, operationJournal, transactions,
                new StagingEnhancementOutcomeRegistry());
        Track3RuntimeModuleProvider track3 = new Track3RuntimeModuleProvider(economy, recovery);
        StagingEconomyOperatorContributor economyCommands =
                new StagingEconomyOperatorContributor(economy);

        FileStagingQuestProgressPort questProgress = new FileStagingQuestProgressPort(
                data.resolve("beta-staging").resolve("players").resolve("quests"));
        Track1QuestProgressPort questProgressPort =
                new Track1QuestProgressPort(progress, questProgress);
        FileStagingRewardClaimStore rewardClaims = new FileStagingRewardClaimStore(
                data.resolve("beta-staging").resolve("reward-claims"));
        StagingPartyRuntime party = new StagingPartyRuntime(new PartyService(
                new PartyPolicy(8, 512, 2048, 16, Duration.ofSeconds(10),
                        Duration.ofMinutes(2), Duration.ofMinutes(5)), clock), policy);
        java.util.function.BooleanSupplier economyRunning = () ->
                track3.track3Modules().stream().anyMatch(module ->
                        module.state() == BetaRuntimeModuleState.RUNNING);
        StagingTrainingDummyQuestRuntime quest = new StagingTrainingDummyQuestRuntime(
                policy, clock, questProgressPort, rewardClaims,
                Track3ToTrack4Ports.delivery(economy, policy, economyRunning));
        TrainingDummyParticipationRelay participationRelay =
                new TrainingDummyParticipationRelay(plugin, track2.participation(), quest,
                        playerId -> protocolHolder[0] != null
                                && !protocolHolder[0].capabilitySnapshot(playerId).oldClient());
        StagingEconomyOperationPort economyPort = Track3ToTrack4Ports.economy(economyRunning);
        PartyQuestRewardRuntimeModule partyModule =
                new PartyQuestRewardRuntimeModule(party, quest, participationRelay, economyPort);

        Path mobRoot = data.resolve("beta-staging").resolve("mobs");
        MobDefinitionV2Policy mobPolicy = MobDefinitionV2Policy.SAFE_DEFAULTS;
        MobReferenceResolvers resolvers = new MobReferenceResolvers(
                (id, revision) -> false, id -> false, id -> false,
                id -> false, id -> false, id -> false);
        MobDefinitionV2Validator validator = new MobDefinitionV2Validator(resolvers, mobPolicy);
        MobDefinitionV2Repository mobRepository = new MobDefinitionV2Repository(
                mobRoot, new MobDefinitionV2Codec(mobPolicy), validator, mobPolicy);
        BukkitMobEditorV2TestSpawnPort mobTestSpawns = new BukkitMobEditorV2TestSpawnPort();
        MobEditorV2Service mobService = new MobEditorV2Service(mobRepository, validator,
                (playerId, action) -> true,
                mobTestSpawns,
                () -> flags.isEnabled(io.github.gyai.projects.feature.FeatureKey.MOB_EDITOR_V2),
                MobEditorV2Policy.SAFE_DEFAULTS, clock);
        MobEditorV2RuntimeModule mobModule = new MobEditorV2RuntimeModule(
                new StagingMobEditorRuntime(mobService, policy, mobRoot));

        EnumMap<BetaCapabilityId, BetaRuntimeModule> producers =
                new EnumMap<>(BetaCapabilityId.class);
        producers.put(BetaCapabilityId.HUD, track1.modules().get(0));
        producers.put(BetaCapabilityId.PARTY, partyModule);
        producers.put(BetaCapabilityId.ELEMENTS, track2.module());
        producers.put(BetaCapabilityId.EQUIPMENT, track1.modules().get(1));
        producers.put(BetaCapabilityId.CRAFTING, track3.modules().get(0));
        producers.put(BetaCapabilityId.ENHANCEMENT, track3.modules().get(1));
        producers.put(BetaCapabilityId.MOB_EDITOR_V2, mobModule);
        RunningCapabilityRegistry availability = new RunningCapabilityRegistry(producers);
        BetaCapabilitySessionService sessions = new BetaCapabilitySessionService(
                BetaCapabilityPolicy.wave3Defaults(), clock);
        BetaCommandRouter router = new BetaCommandRouter(new BetaRateLimiter(512, clock),
                (context, command) -> context.permissionGranted()
                        ? BetaCommandAuthorization.Decision.allow()
                        : BetaCommandAuthorization.Decision.deny("projects.dev required"), 512);
        PluginMessageListener incoming = (channel, player, message) -> {
            ClientBetaProtocolRuntime value = protocolHolder[0];
            if (value == null || !BetaChannels.ACKNOWLEDGEMENT.equals(channel)) return;
            var decoded = new BetaProtocolCodec().decodeAcknowledgement(message);
            if (decoded.status() == BetaProtocolDecodeResult.Status.SUCCESS) {
                value.acknowledge(player.getUniqueId(), decoded.value());
            }
        };
        BukkitBetaChannelRegistrar channels = new BukkitBetaChannelRegistrar(plugin, incoming);
        ClientBetaProtocolRuntime protocol = new ClientBetaProtocolRuntime(
                channels, sessions, router, availability);
        protocolHolder[0] = protocol;
        ClientBetaProtocolRuntimeModule[] protocolModuleHolder =
                new ClientBetaProtocolRuntimeModule[1];
        ElementSnapshotProtocolAdapter elementAdapter = new ElementSnapshotProtocolAdapter(
                track2.snapshots(),
                () -> protocolModuleHolder[0] == null ? BetaRuntimeModuleState.NOT_INSTALLED
                        : protocolModuleHolder[0].state(),
                () -> track2.combatElementsModule().state(),
                protocol::capabilitySnapshot, visibility -> true);
        ElementSnapshotProtocolPublisher publisher = new ElementSnapshotProtocolPublisher(
                elementAdapter, new BukkitBetaStateTransport(plugin, dummies),
                protocol::capabilitySnapshot, clock);
        ClientBetaProtocolRuntimeModule protocolModule =
                new ClientBetaProtocolRuntimeModule(protocol, publisher);
        protocolModuleHolder[0] = protocolModule;
        Track4RuntimeModuleProvider track4 = new Track4RuntimeModuleProvider(
                partyModule, mobModule, protocolModule);
        Track4OperatorCommandContributor track4Commands =
                new Track4OperatorCommandContributor(track4);

        BetaOperatorContributorRegistry operators = new BetaOperatorContributorRegistry(List.of(
                entry("player", BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                        (context, args) -> track1Result(track1Commands, context, "player", args)),
                entry("equipment", BetaRuntimeModuleId.EQUIPMENT,
                        (context, args) -> track1Result(track1Commands, context, "equipment", args)),
                entry("element", BetaRuntimeModuleId.COMBAT_ELEMENTS,
                        (context, args) -> new BetaOperatorContributorRegistry.Result(true,
                                track2.operatorCommands().execute(context.projectsDev(), context.actorId(), args))),
                entry("economy", BetaRuntimeModuleId.GATHERING_CRAFTING,
                        (context, args) -> economyResult(economyCommands, policy, context, args)),
                entry("party", BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                        (context, args) -> track4Result(track4Commands, context)),
                entry("quest", BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                        (context, args) -> track4Result(track4Commands, context)),
                entry("reward", BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                        (context, args) -> track4Result(track4Commands, context)),
                entry("mob", BetaRuntimeModuleId.MOB_EDITOR_V2,
                        (context, args) -> track4Result(track4Commands, context))));

        ConfirmedDamageHitObserver observer = track2.confirmedHitObserver(
                track2.combatElementsModule()::state, dummies, clock);
        return new BetaActivationWave1CompositionRoot(track1, track2, track3, track4,
                Set.of("track1.bukkit-listener", "track1.staging-player-store",
                        "track1.inventory-reader", "training-dummy-boundary",
                        "damage-service-secondary", "track3.staging-inventory",
                        "track3.staging-transaction-journal", "track1-progress-port",
                        "track3-item-delivery-port", "beta-staging-mob-repository",
                        "minecraft-plugin-messaging"),
                operators, progress, equipment, transactionRepository, recovery,
                auditSink, operationJournal, questProgress, rewardClaims, protocol, publisher, observer);
    }

    private static BetaOperatorContributorRegistry.Entry entry(
            String subject, BetaRuntimeModuleId id,
            BetaOperatorContributorRegistry.Contributor contributor
    ) { return new BetaOperatorContributorRegistry.Entry(subject, id, contributor); }

    private static BetaOperatorContributorRegistry.Result track1Result(
            Track1OperatorCommandContributor contributor,
            BetaOperatorContributorRegistry.Context context,
            String subject,
            List<String> arguments
    ) {
        ArrayList<String> supplied = new ArrayList<>(); supplied.add(subject); supplied.addAll(arguments);
        var result = contributor.execute(context.projectsDev(),
                new BetaOperatorSubject(context.actorId(), context.worldName(), context.compatibleClient()), supplied);
        return new BetaOperatorContributorRegistry.Result(result.accepted(), result.messages());
    }

    private static BetaOperatorContributorRegistry.Result economyResult(
            StagingEconomyOperatorContributor contributor, BetaActivationPolicy policy,
            BetaOperatorContributorRegistry.Context context, List<String> arguments
    ) {
        if (context.actorId() == null) return new BetaOperatorContributorRegistry.Result(false, List.of("player required"));
        var result = contributor.execute(new StagingOperationAccess(
                context.actorId(), context.worldName(), context.projectsDev(), policy), arguments);
        return new BetaOperatorContributorRegistry.Result(result.success(), List.of(result.message()));
    }

    private static BetaOperatorContributorRegistry.Result track4Result(
            Track4OperatorCommandContributor contributor,
            BetaOperatorContributorRegistry.Context context
    ) {
        var result = contributor.execute(new io.github.gyai.projects.beta.activation.track4.BetaOperatorCommandContributor.Request(
                context.actorId(), context.worldName(), context.projectsDev(),
                context.compatibleClient(), List.of("status")));
        return new BetaOperatorContributorRegistry.Result(result.success(), result.messages());
    }

    public BetaRuntime createRuntime(
            BetaActivationPolicy policy, FeatureFlagSnapshot flags, Clock clock,
            java.util.function.BiConsumer<String, RuntimeException> exceptionLogger
    ) {
        return BetaRuntimeFactory.create(policy, flags, modules, infrastructure, clock, exceptionLogger);
    }
    public List<BetaRuntimeModule> modules() { return modules; }
    public BetaOperatorContributorRegistry operators() { return operators; }
    public ConfirmedDamageHitObserver confirmedHitObserver() { return confirmedHitObserver; }
    public ElementRuntimeSnapshotPort elementSnapshots() { return track2.snapshots(); }
    public io.github.gyai.projects.beta.activation.track2.TrainingDummyParticipationPort participation() { return track2.participation(); }
    public StagingEconomyService economy() { return track3.service(); }
    public Inspection inspection() { return new Inspection(track1, track2, track3, track4,
            transactionRepository, recovery, auditSink, protocol, elementPublisher); }
    public RecoveryGraphSnapshot recoveryGraph() {
        return new RecoveryGraphSnapshot(
                recovery.usesRepository(transactionRepository),
                auditSink.usesRecoveryJournal(transactionRepository),
                recovery.restoresInto(operationJournal));
    }

    @Override public synchronized void close() {
        if (closed) return; closed = true;
        closeSafely(elementPublisher);
        closeSafely(protocol);
        for (int index = track4.modules().size() - 1; index >= 0; index--) stopSafely(track4.modules().get(index));
        closeSafely(track3); closeSafely(recovery); closeSafely(rewardClaims);
        closeSafely(questProgress); stopSafely(track2.module());
        for (int index = track1.modules().size() - 1; index >= 0; index--) stopSafely(track1.modules().get(index));
    }
    private static void stopSafely(BetaRuntimeModule module) { try { module.stop(); } catch (RuntimeException ignored) { } }
    private static void closeSafely(AutoCloseable value) { try { value.close(); } catch (Exception ignored) { } }

    public record Inspection(
            Track1RuntimeModuleProvider track1,
            CombatElementsRuntimeModuleProvider track2,
            Track3RuntimeModuleProvider track3,
            Track4RuntimeModuleProvider track4,
            StagingTransactionJournalRepository transactionRepository,
            StagingTransactionRecoveryService recovery,
            FileStagingTransactionAuditSink auditSink,
            ClientBetaProtocolRuntime protocol,
            ElementSnapshotProtocolPublisher publisher
    ) { }
    public record RecoveryGraphSnapshot(boolean recoveryUsesRepository,
                                        boolean auditUsesRepository,
                                        boolean recoveryRestoresTerminalJournal) { }
}
