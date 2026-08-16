package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.crafting.OutputProposal;
import io.github.gyai.projects.enhancement.v2.EnhancementAttempt;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;
import io.github.gyai.projects.enhancement.v2.EnhancementPolicy;
import io.github.gyai.projects.enhancement.v2.EnhancementPolicyRevision;
import io.github.gyai.projects.enhancement.v2.EnhancementResolver;
import io.github.gyai.projects.enhancement.v2.EnhancementTransition;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.equipment.operation.EquipmentExtensionSnapshot;
import io.github.gyai.projects.equipment.operation.EquipmentItems;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.EquipmentOperationPlan;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.equipment.operation.TierPromotionCarryPolicy;
import io.github.gyai.projects.equipment.operation.TierPromotionRequest;
import io.github.gyai.projects.equipment.operation.TierPromotionService;
import io.github.gyai.projects.repair.RepairPolicy;
import io.github.gyai.projects.repair.RepairRequest;
import io.github.gyai.projects.repair.RepairService;
import io.github.gyai.projects.transaction.TransactionAuditResult;
import io.github.gyai.projects.transaction.TransactionRequest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Staging-only operations. It is inert until its Runtime modules mark groups running. */
public final class StagingEconomyService implements StagingEconomyOperationPort,
        StagingItemDeliveryPort, AutoCloseable {
    private final StagingInventoryPort inventory;
    private final BoundedStagingOperationJournal journal;
    private final StagingInventoryTransactionAdapter transactions;
    private final StagingEnhancementOutcomeRegistry outcomes;
    private final BooleanSupplier moddedCraftEnabled;
    private final EnhancementResolver enhancementResolver = new EnhancementResolver();
    private boolean gatheringCraftingRunning;
    private boolean enhancementRepairRunning;
    private boolean closed;

    public StagingEconomyService(
            StagingInventoryPort inventory,
            BoundedStagingOperationJournal journal,
            StagingInventoryTransactionAdapter transactions,
            StagingEnhancementOutcomeRegistry outcomes
    ) {
        this(inventory, journal, transactions, outcomes, () -> true);
    }

    /**
     * The four-argument overload remains for isolated staging fixtures. Production composition
     * must supply the live feature policy through this constructor.
     */
    public StagingEconomyService(
            StagingInventoryPort inventory,
            BoundedStagingOperationJournal journal,
            StagingInventoryTransactionAdapter transactions,
            StagingEnhancementOutcomeRegistry outcomes,
            BooleanSupplier moddedCraftEnabled
    ) {
        if (inventory == null || journal == null || transactions == null || outcomes == null
                || moddedCraftEnabled == null) {
            throw new IllegalArgumentException("staging economy service input missing");
        }
        this.inventory = inventory;
        this.journal = journal;
        this.transactions = transactions;
        this.outcomes = outcomes;
        this.moddedCraftEnabled = moddedCraftEnabled;
    }

    public synchronized void setGroupRunning(OperationGroup group, boolean running) {
        requireOpen();
        if (group == OperationGroup.GATHERING_CRAFTING) gatheringCraftingRunning = running;
        else enhancementRepairRunning = running;
        if (!running && group == OperationGroup.ENHANCEMENT_REPAIR) outcomes.clear();
    }

    @Override
    public OperationResult execute(OperationRequest request) {
        return execute(request, StagingFailurePoint.NONE);
    }

    OperationResult execute(OperationRequest request, StagingFailurePoint failurePoint) {
        requireOpen();
        if (!request.access().allowed()) return OperationResult.rejected("staging-gate-denied");
        // This is the shared boundary for both command and GUI craft requests. Keep it before
        // session/inventory work so disabled feature combinations cannot reserve, roll, or mint.
        if (request.kind() == OperationKind.CRAFT && !moddedCraftEnabled.getAsBoolean()) {
            return OperationResult.rejected("equipment-mod-features-disabled");
        }
        if (!runningFor(request.kind())) return OperationResult.rejected("module-not-running");
        inventory.openSession(request.access().playerId());
        Optional<TransactionAuditResult> replay = journal.findTerminal(request.requestId());
        if (replay.isPresent()) {
            TransactionAuditResult terminal = replay.orElseThrow();
            if (!replayMatches(request, terminal)) {
                return OperationResult.rejected("request-id-reused-with-different-operation");
            }
            return fromExecution(new StagingInventoryTransactionAdapter.Execution(
                    terminal.asReplay(), journal.finalizedEquipment(request.requestId())));
        }
        try {
            return switch (request.kind()) {
                case GIVE -> give(request, failurePoint);
                case REFINE -> refine(request, failurePoint);
                case CRAFT -> craft(request, failurePoint);
                case PROMOTE -> promote(request, failurePoint);
                case ENHANCE -> enhance(request, failurePoint);
                case BREAK -> breakItem(request, failurePoint);
                case REPAIR -> repair(request, failurePoint);
            };
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return OperationResult.rejected(failure.getMessage());
        }
    }

    @Override
    public OperationResult deliver(
            StagingOperationAccess access,
            UUID requestId,
            String stagingItemId,
            long quantity
    ) {
        return execute(new OperationRequest(requestId, access, OperationKind.GIVE,
                Optional.ofNullable(stagingItemId), quantity));
    }

    @Override
    public synchronized void selectEnhancementOutcome(
            StagingOperationAccess access,
            EnhancementOutcome outcome
    ) {
        requireOpen();
        if (!access.allowed() || !enhancementRepairRunning) {
            throw new IllegalStateException("staging-gate-denied");
        }
        outcomes.set(access.playerId(), outcome);
    }

    @Override
    public StagingInventoryPort.InventorySnapshot status(UUID playerId) {
        return inventory.snapshot(playerId);
    }

    @Override
    public synchronized void logout(UUID playerId) {
        outcomes.logout(playerId);
        transactions.cancelForPlayer(playerId);
        inventory.logout(playerId);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        transactions.close();
        outcomes.close();
        journal.close();
        inventory.close();
        gatheringCraftingRunning = false;
        enhancementRepairRunning = false;
        closed = true;
    }

    private OperationResult give(OperationRequest request, StagingFailurePoint failurePoint) {
        String itemId = request.requestedItemId().orElseThrow(() ->
                new IllegalArgumentException("staging item ID is required"));
        if (!StagingEconomyCatalog.isStagingItem(itemId)
                || itemId.equals(StagingEconomyCatalog.TEST_BLADE_T1)
                || itemId.equals(StagingEconomyCatalog.TEST_BLADE_T2)
                || request.requestedQuantity() <= 0) {
            return OperationResult.rejected("invalid-staging-resource");
        }
        return resourceTransaction(request, "projects:staging-give",
                OperationResourcePlan.none(), new OutputProposal(
                        itemId, request.requestedQuantity(), false), failurePoint);
    }

    private OperationResult refine(OperationRequest request, StagingFailurePoint failurePoint) {
        OperationResourcePlan cost = new OperationResourcePlan(List.of(
                new OperationResourcePlan.MaterialCost(
                        StagingEconomyCatalog.IRON_ORE_TRANSACTION_KEY, 2)), 0);
        return resourceTransaction(request, StagingEconomyCatalog.REFINE_RECIPE_ID,
                cost, new OutputProposal(StagingEconomyCatalog.IRON_INGOT, 1, false),
                failurePoint);
    }

    private OperationResult craft(OperationRequest request, StagingFailurePoint failurePoint) {
        StagingInventoryPort.InventorySnapshot snapshot = inventory.snapshot(
                request.access().playerId());
        OperationResourcePlan costs = new OperationResourcePlan(List.of(
                new OperationResourcePlan.MaterialCost(
                        StagingEconomyCatalog.IRON_INGOT_TRANSACTION_KEY, 3)), 0);
        EquipmentItemV1 preview = StagingEconomyCatalog.previewBlade(EquipmentTier.T1);
        List<TransactionRequest.InputRevision> inputs = List.of(
                resourceInput(StagingEconomyCatalog.IRON_INGOT, snapshot.revision()));
        TransactionRequest transaction = new TransactionRequest(request.requestId(),
                request.access().playerId(), "projects:staging-craft",
                StagingEconomyCatalog.CRAFT_RECIPE_ID, snapshot.revision(), 1, inputs);
        // Resolver is invoked by EquipmentOperationParticipant after reservation.
        EquipmentOperationPlan plan = new EquipmentOperationPlan(transaction, costs, () -> mutation(
                request, "projects:staging-craft", StagingEconomyCatalog.CRAFT_RECIPE_ID,
                snapshot.revision(), transactions.resolveCraftMod(preview), costs, inputs));
        return equipmentTransaction(request, plan, failurePoint);
    }

    private OperationResult promote(OperationRequest request, StagingFailurePoint failurePoint) {
        StagingInventoryPort.InventorySnapshot snapshot = inventory.snapshot(
                request.access().playerId());
        EquipmentItemV1 source = snapshot.equipment().stream()
                .filter(item -> item.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T1))
                .findFirst().orElseThrow(() -> new IllegalStateException("T1 staging blade missing"));
        if (source.enhancementLevel() != 0 || source.broken()) {
            return OperationResult.rejected("staging-promotion-requires-unmodified-source");
        }
        OperationResourcePlan costs = new OperationResourcePlan(List.of(
                new OperationResourcePlan.MaterialCost(
                        StagingEconomyCatalog.IRON_INGOT_TRANSACTION_KEY, 2)), 0);
        EnumMap<TierPromotionCarryPolicy.CarryField,
                TierPromotionCarryPolicy.FieldDecision> decisions =
                new EnumMap<>(TierPromotionCarryPolicy.CarryField.class);
        for (TierPromotionCarryPolicy.CarryField field
                : TierPromotionCarryPolicy.CarryField.values()) {
            decisions.put(field, TierPromotionCarryPolicy.FieldDecision.USE_DESTINATION_VALUE);
        }
        TierPromotionCarryPolicy policy = new TierPromotionCarryPolicy(
                StagingEconomyCatalog.PROMOTION_RECIPE_ID, 1, decisions, costs);
        var proposal = new TierPromotionService().propose(new TierPromotionRequest(
                request.requestId(), request.access().playerId(),
                StagingEconomyCatalog.TEST_BLADE_FAMILY,
                StagingEconomyCatalog.TEST_BLADE_FAMILY,
                source, StagingEconomyCatalog.previewBlade(EquipmentTier.T2),
                snapshot.revision(), EquipmentExtensionSnapshot.empty()), policy);
        if (proposal.mutation().isEmpty()) return OperationResult.rejected(proposal.reason());
        return equipmentTransaction(request,
                EquipmentOperationPlan.fixed(proposal.mutation().orElseThrow()), failurePoint);
    }

    private OperationResult enhance(OperationRequest request, StagingFailurePoint failurePoint) {
        StagingInventoryPort.InventorySnapshot snapshot = inventory.snapshot(
                request.access().playerId());
        EquipmentItemV1 source = blade(snapshot, false);
        if (source.enhancementLevel() >= 30 || source.broken()) {
            return OperationResult.rejected("staging-enhancement-source-ineligible");
        }
        UUID instanceId = source.instanceId().orElseThrow();
        TransactionRequest transaction = new TransactionRequest(
                request.requestId(), request.access().playerId(),
                "projects:enhancement-v2", StagingEconomyCatalog.ENHANCEMENT_POLICY_ID,
                snapshot.revision(), 1, List.of(new TransactionRequest.InputRevision(
                        EquipmentMutationProposal.inputId(instanceId), snapshot.revision())));
        EnhancementAttempt attempt = new EnhancementAttempt(
                request.requestId(), request.access().playerId(),
                StagingEconomyCatalog.TEST_BLADE_FAMILY, source, snapshot.revision(),
                EquipmentExtensionSnapshot.empty());
        EquipmentOperationPlan plan = new EquipmentOperationPlan(
                transaction, OperationResourcePlan.none(), () -> {
                    EnhancementOutcome selected = outcomes.consume(request.access().playerId());
                    EnhancementPolicy policy = oneShotPolicy(source.enhancementLevel(), selected);
                    return enhancementResolver.resolve(attempt, policy, () -> 0.5)
                            .mutation().orElseThrow();
                });
        return equipmentTransaction(request, plan, failurePoint);
    }

    private OperationResult breakItem(OperationRequest request, StagingFailurePoint failurePoint) {
        StagingInventoryPort.InventorySnapshot snapshot = inventory.snapshot(
                request.access().playerId());
        EquipmentItemV1 source = blade(snapshot, false);
        if (source.broken()) return OperationResult.rejected("staging-blade-already-broken");
        EquipmentItemV1 broken = EquipmentItems.replaceMutableState(
                source, source.tier(), source.itemLevel(), source.quality(),
                source.modSlots(), source.enhancementLevel(), true, source.binding());
        EquipmentMutationProposal proposal = mutation(
                request, "projects:staging-break", "projects:staging-break",
                snapshot.revision(), broken, OperationResourcePlan.none(),
                List.of(new TransactionRequest.InputRevision(
                        EquipmentMutationProposal.inputId(source.instanceId().orElseThrow()),
                        snapshot.revision())));
        return equipmentTransaction(request, EquipmentOperationPlan.fixed(proposal), failurePoint);
    }

    private OperationResult repair(OperationRequest request, StagingFailurePoint failurePoint) {
        StagingInventoryPort.InventorySnapshot snapshot = inventory.snapshot(
                request.access().playerId());
        EquipmentItemV1 target = blade(snapshot, true);
        EquipmentItemV1 donor = snapshot.equipment().stream()
                .filter(item -> item.instanceId().isPresent()
                        && !item.instanceId().equals(target.instanceId())
                        && item.tier() == target.tier()
                        && item.enhancementLevel() == 0
                        && !item.broken()
                        && (item.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T1)
                        || item.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T2)))
                .findFirst().orElseThrow(() -> new IllegalStateException("repair donor missing"));
        var proposal = new RepairService().propose(new RepairRequest(
                request.requestId(), request.access().playerId(),
                StagingEconomyCatalog.TEST_BLADE_FAMILY,
                StagingEconomyCatalog.TEST_BLADE_FAMILY,
                target, snapshot.revision(), donor, snapshot.revision(),
                EquipmentExtensionSnapshot.empty()),
                new RepairPolicy(StagingEconomyCatalog.REPAIR_POLICY_ID, 1,
                        OperationResourcePlan.none()));
        if (proposal.mutation().isEmpty()) return OperationResult.rejected(proposal.reason());
        return equipmentTransaction(request,
                EquipmentOperationPlan.fixed(proposal.mutation().orElseThrow()), failurePoint);
    }

    private OperationResult resourceTransaction(
            OperationRequest request,
            String recipeId,
            OperationResourcePlan resources,
            OutputProposal output,
            StagingFailurePoint failurePoint
    ) {
        long revision = inventory.snapshot(request.access().playerId()).revision();
        List<TransactionRequest.InputRevision> inputs = resources.materials().isEmpty()
                ? List.of(resourceInput(output.outputId(), revision))
                : resources.materials().stream().map(material -> resourceInput(
                        StagingEconomyCatalog.itemIdForTransactionResource(material.materialId()),
                        revision)).toList();
        TransactionRequest transaction = new TransactionRequest(
                request.requestId(), request.access().playerId(),
                "projects:staging-resource", recipeId, revision, output.quantity(),
                inputs);
        return fromExecution(transactions.executeResource(
                request.access().playerId(), transaction, resources, output, failurePoint));
    }

    private OperationResult equipmentTransaction(
            OperationRequest request,
            EquipmentOperationPlan plan,
            StagingFailurePoint failurePoint
    ) {
        return fromExecution(transactions.executeEquipment(
                request.access().playerId(), plan, failurePoint));
    }

    private static OperationResult fromExecution(
            StagingInventoryTransactionAdapter.Execution execution
    ) {
        TransactionAuditResult result = execution.result();
        Status status = result.replayed() && result.outcome() == TransactionAuditResult.Outcome.COMMITTED
                ? Status.REPLAYED : switch (result.outcome()) {
            case COMMITTED -> Status.COMMITTED;
            case ROLLED_BACK -> Status.ROLLED_BACK;
            case COMMIT_UNCERTAIN -> Status.COMMIT_UNCERTAIN;
            case REJECTED, INPUT_CONFLICT, REPLAY_CONFLICT, DUPLICATE_ACTIVE,
                    ACTIVE_LIMIT, TERMINAL_LIMIT, CLOSED -> Status.REJECTED;
            case ROLLBACK_FAILED -> Status.FAILED;
        };
        return new OperationResult(status, result.reason(), Optional.of(result),
                execution.committedEquipment());
    }

    private static EnhancementPolicy oneShotPolicy(int level, EnhancementOutcome selected) {
        EnumMap<EnhancementOutcome, Double> probabilities = new EnumMap<>(EnhancementOutcome.class);
        EnumMap<EnhancementOutcome, EnhancementTransition> transitions =
                new EnumMap<>(EnhancementOutcome.class);
        for (EnhancementOutcome outcome : List.of(
                EnhancementOutcome.SUCCESS, EnhancementOutcome.NO_CHANGE,
                EnhancementOutcome.DOWNGRADE, EnhancementOutcome.BROKEN)) {
            probabilities.put(outcome, outcome == selected ? 1.0 : 0.0);
        }
        transitions.put(EnhancementOutcome.SUCCESS,
                new EnhancementTransition(level + 1, false));
        transitions.put(EnhancementOutcome.NO_CHANGE,
                new EnhancementTransition(level, false));
        transitions.put(EnhancementOutcome.DOWNGRADE,
                new EnhancementTransition(Math.max(0, level - 1), false));
        transitions.put(EnhancementOutcome.BROKEN,
                new EnhancementTransition(level, true));
        return new EnhancementPolicy(
                new EnhancementPolicyRevision(StagingEconomyCatalog.ENHANCEMENT_POLICY_ID, 1),
                level, probabilities, transitions, OperationResourcePlan.none());
    }

    private static EquipmentMutationProposal mutation(
            OperationRequest request,
            String operationId,
            String recipeId,
            long revision,
            EquipmentItemV1 item,
            OperationResourcePlan resources,
            List<TransactionRequest.InputRevision> inputs
    ) {
        return new EquipmentMutationProposal(
                request.requestId(), request.access().playerId(), operationId, recipeId,
                StagingEconomyCatalog.TEST_BLADE_FAMILY, revision, item,
                EquipmentExtensionSnapshot.empty(), resources, inputs);
    }

    private static TransactionRequest.InputRevision resourceInput(String id, long revision) {
        String safe = id.replace("projects:staging/", "").replace('/', '-');
        return new TransactionRequest.InputRevision("projects:resource-" + safe, revision);
    }

    private static EquipmentItemV1 blade(
            StagingInventoryPort.InventorySnapshot snapshot,
            boolean broken
    ) {
        return snapshot.equipment().stream()
                .filter(item -> item.broken() == broken
                        && (item.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T1)
                        || item.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T2)))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        broken ? "broken staging blade missing" : "staging blade missing"));
    }

    private synchronized boolean runningFor(OperationKind kind) {
        return switch (kind) {
            case GIVE, REFINE, CRAFT -> gatheringCraftingRunning;
            case PROMOTE, ENHANCE, BREAK, REPAIR -> enhancementRepairRunning;
        };
    }

    private static boolean replayMatches(
            OperationRequest request,
            TransactionAuditResult terminal
    ) {
        if (!terminal.playerId().equals(request.access().playerId())) return false;
        String operationId = switch (request.kind()) {
            case GIVE, REFINE -> "projects:staging-resource";
            case CRAFT -> "projects:staging-craft";
            case PROMOTE -> "projects:tier-promotion";
            case ENHANCE -> "projects:enhancement-v2";
            case BREAK -> "projects:staging-break";
            case REPAIR -> "projects:repair-v2";
        };
        String recipeId = switch (request.kind()) {
            case GIVE -> "projects:staging-give";
            case REFINE -> StagingEconomyCatalog.REFINE_RECIPE_ID;
            case CRAFT -> StagingEconomyCatalog.CRAFT_RECIPE_ID;
            case PROMOTE -> StagingEconomyCatalog.PROMOTION_RECIPE_ID;
            case ENHANCE -> StagingEconomyCatalog.ENHANCEMENT_POLICY_ID;
            case BREAK -> "projects:staging-break";
            case REPAIR -> StagingEconomyCatalog.REPAIR_POLICY_ID;
        };
        if (!terminal.operationId().equals(operationId)
                || !terminal.recipeId().equals(recipeId)) return false;
        if (request.kind() != OperationKind.GIVE) return terminal.expectedOutputUnits() == 1;
        if (terminal.expectedOutputUnits() != request.requestedQuantity()) return false;
        return terminal.output().map(output -> request.requestedItemId()
                .map(id -> id.equals(output.outputId())).orElse(false)).orElse(false);
    }

    private synchronized void requireOpen() {
        if (closed) throw new IllegalStateException("staging economy service is closed");
    }

    public enum OperationGroup {
        GATHERING_CRAFTING,
        ENHANCEMENT_REPAIR
    }
}
