package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.EquipmentMutationProposal;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded staging inventory implementation used before Bukkit wiring exists.
 * It retains UUIDs and immutable values only, never Player or ItemStack references.
 */
public final class BoundedStagingInventory implements StagingInventoryPort {
    public static final int MAXIMUM_PLAYERS = 128;
    public static final int DEFAULT_EQUIPMENT_CAPACITY = 36;
    private static final int MAXIMUM_RESERVATIONS = 256;

    private final int equipmentCapacity;
    private final LinkedHashMap<UUID, PlayerState> players = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, HeldReservation> reservations = new LinkedHashMap<>();
    private boolean closed;

    public BoundedStagingInventory() {
        this(DEFAULT_EQUIPMENT_CAPACITY);
    }

    public BoundedStagingInventory(int equipmentCapacity) {
        if (equipmentCapacity <= 0 || equipmentCapacity > 256) {
            throw new IllegalArgumentException("invalid staging equipment capacity");
        }
        this.equipmentCapacity = equipmentCapacity;
    }

    @Override
    public synchronized void openSession(UUID playerId) {
        requireOpen();
        player(playerId);
    }

    @Override
    public synchronized Optional<InventoryCapacityProposal> validate(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources
    ) {
        requireOpen();
        PlayerState state = players.get(playerId);
        if (state == null || state.revision != request.expectedRevision()) return Optional.empty();
        if (!hasResources(state, resources) || !hasInputs(state, request.inputs())) {
            return Optional.empty();
        }
        if (!request.operationId().equals("projects:staging-resource")) {
            long consumedEquipment = request.inputs().stream()
                    .filter(input -> input.inputId().startsWith("projects:item-"))
                    .count();
            if (state.equipment.size() - consumedEquipment + request.expectedOutputUnits()
                    > equipmentCapacity) {
                return Optional.empty();
            }
        }
        return Optional.of(InventoryCapacityProposal.reservedInventory(
                request.expectedOutputUnits()));
    }

    @Override
    public synchronized ReservationToken reserve(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            InventoryCapacityProposal capacity
    ) {
        requireOpen();
        if (reservations.size() >= MAXIMUM_RESERVATIONS
                || reservations.containsKey(request.requestId())
                || validate(playerId, request, resources).isEmpty()) {
            throw new IllegalStateException("staging reservation rejected");
        }
        PlayerState state = requirePlayer(playerId);
        Snapshot before = state.copy();
        ReservationToken token = new ReservationToken(
                "staging-" + request.requestId().toString());
        reservations.put(request.requestId(), new HeldReservation(
                playerId, request, resources, token, before));
        return token;
    }

    @Override
    public synchronized void consume(
            UUID playerId,
            TransactionRequest request,
            OperationResourcePlan resources,
            ReservationToken reservation
    ) {
        HeldReservation held = requireReservation(playerId, request.requestId(), reservation);
        if (held.consumed) throw new IllegalStateException("staging reservation already consumed");
        PlayerState state = requirePlayer(playerId);
        for (OperationResourcePlan.MaterialCost material : resources.materials()) {
            subtract(state.resources,
                    StagingEconomyCatalog.itemIdForTransactionResource(material.materialId()),
                    material.quantity());
        }
        for (TransactionRequest.InputRevision input : request.inputs()) {
            if (!input.inputId().startsWith("projects:item-")) continue;
            UUID instanceId = parseInputId(input.inputId());
            if (state.equipment.remove(instanceId) == null) {
                throw new IllegalStateException("reserved equipment disappeared");
            }
        }
        held.consumed = true;
    }

    @Override
    public synchronized CommitResult commitEquipment(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            long expectedRevision,
            StagingEquipmentDocument document
    ) {
        HeldReservation held = requireReservation(playerId, requestId, reservation);
        PlayerState state = requirePlayer(playerId);
        if (!held.consumed || held.request.expectedRevision() != expectedRevision
                || state.revision != expectedRevision) {
            return new CommitResult(false, state.revision, Optional.empty(), "revision-conflict");
        }
        EquipmentItemV1 item = document.item();
        UUID instanceId = item.instanceId().orElseThrow(() ->
                new IllegalArgumentException("committed staging equipment requires UUID"));
        if (state.equipment.size() >= equipmentCapacity
                || state.equipment.containsKey(instanceId)) {
            return new CommitResult(false, state.revision, Optional.empty(), "full-or-duplicate");
        }
        state.equipment.put(instanceId, item);
        state.revision = Math.addExact(state.revision, 1);
        reservations.remove(requestId);
        return new CommitResult(true, state.revision, Optional.of(item), "committed");
    }

    @Override
    public synchronized CommitResult commitResource(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            String resourceId,
            long quantity
    ) {
        HeldReservation held = requireReservation(playerId, requestId, reservation);
        PlayerState state = requirePlayer(playerId);
        if (!held.consumed || quantity <= 0 || !StagingEconomyCatalog.isStagingItem(resourceId)) {
            return new CommitResult(false, state.revision, Optional.empty(), "invalid-resource-output");
        }
        state.resources.merge(resourceId, quantity, Math::addExact);
        state.revision = Math.addExact(state.revision, 1);
        reservations.remove(requestId);
        return new CommitResult(true, state.revision, Optional.empty(), "committed");
    }

    @Override
    public synchronized void rollback(
            UUID playerId,
            UUID requestId,
            ReservationToken reservation,
            TransactionStage lastCompletedStage
    ) {
        HeldReservation held = requireReservation(playerId, requestId, reservation);
        players.put(playerId, PlayerState.from(held.before));
        reservations.remove(requestId);
    }

    @Override
    public synchronized InventorySnapshot snapshot(UUID playerId) {
        PlayerState state = players.get(playerId);
        if (state == null) return new InventorySnapshot(0, Map.of(), List.of(), 0);
        int active = (int) reservations.values().stream()
                .filter(value -> value.playerId.equals(playerId)).count();
        return new InventorySnapshot(state.revision, state.resources,
                List.copyOf(state.equipment.values()), active);
    }

    public synchronized void seedResource(UUID playerId, String resourceId, long quantity) {
        requireOpen();
        if (!StagingEconomyCatalog.isStagingItem(resourceId) || quantity < 0) {
            throw new IllegalArgumentException("invalid staging seed resource");
        }
        PlayerState state = player(playerId);
        state.resources.put(resourceId, quantity);
    }

    public synchronized void seedEquipment(UUID playerId, EquipmentItemV1 item) {
        requireOpen();
        if (!StagingEconomyCatalog.isStagingItem(item.itemId()) || item.instanceId().isEmpty()) {
            throw new IllegalArgumentException("staging equipment seed requires identity");
        }
        PlayerState state = player(playerId);
        if (state.equipment.size() >= equipmentCapacity
                || state.equipment.putIfAbsent(item.instanceId().orElseThrow(), item) != null) {
            throw new IllegalStateException("staging equipment seed rejected");
        }
    }

    @Override
    public synchronized void logout(UUID playerId) {
        if (playerId == null) return;
        List<HeldReservation> active = reservations.values().stream()
                .filter(value -> value.playerId.equals(playerId)).toList();
        for (HeldReservation held : active) {
            players.put(playerId, PlayerState.from(held.before));
            reservations.remove(held.request.requestId());
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        for (HeldReservation held : List.copyOf(reservations.values())) {
            players.put(held.playerId, PlayerState.from(held.before));
        }
        reservations.clear();
        players.clear();
        closed = true;
    }

    private PlayerState player(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        PlayerState existing = players.get(playerId);
        if (existing != null) return existing;
        if (players.size() >= MAXIMUM_PLAYERS) {
            throw new IllegalStateException("staging player capacity reached");
        }
        PlayerState created = new PlayerState();
        players.put(playerId, created);
        return created;
    }

    private PlayerState requirePlayer(UUID playerId) {
        PlayerState state = players.get(playerId);
        if (state == null) throw new IllegalStateException("staging player inventory missing");
        return state;
    }

    private boolean hasResources(PlayerState state, OperationResourcePlan plan) {
        for (OperationResourcePlan.MaterialCost material : plan.materials()) {
            String itemId = StagingEconomyCatalog.itemIdForTransactionResource(
                    material.materialId());
            if (state.resources.getOrDefault(itemId, 0L) < material.quantity()) {
                return false;
            }
        }
        return plan.currencyCost() == 0;
    }

    private boolean hasInputs(PlayerState state, List<TransactionRequest.InputRevision> inputs) {
        for (TransactionRequest.InputRevision input : inputs) {
            if (input.revision() != state.revision) return false;
            if (input.inputId().startsWith("projects:item-")) {
                if (!state.equipment.containsKey(parseInputId(input.inputId()))) return false;
            } else if (!input.inputId().startsWith("projects:resource-")) {
                return false;
            }
        }
        return true;
    }

    private HeldReservation requireReservation(
            UUID playerId,
            UUID requestId,
            ReservationToken token
    ) {
        HeldReservation held = reservations.get(requestId);
        if (held == null || !held.playerId.equals(playerId) || !held.token.equals(token)) {
            throw new IllegalStateException("staging reservation missing");
        }
        return held;
    }

    private static UUID parseInputId(String inputId) {
        String value = inputId.substring("projects:item-".length());
        if (value.length() != 32) throw new IllegalArgumentException("invalid equipment input ID");
        String hyphenated = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-" + value.substring(16, 20) + "-"
                + value.substring(20);
        return UUID.fromString(hyphenated);
    }

    private static void subtract(Map<String, Long> values, String id, long quantity) {
        long current = values.getOrDefault(id, 0L);
        if (current < quantity) throw new IllegalStateException("reserved resource disappeared");
        long remaining = current - quantity;
        if (remaining == 0) values.remove(id); else values.put(id, remaining);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("staging inventory is closed");
    }

    private static final class PlayerState {
        private long revision;
        private final LinkedHashMap<String, Long> resources = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, EquipmentItemV1> equipment = new LinkedHashMap<>();

        private Snapshot copy() {
            return new Snapshot(revision, Map.copyOf(resources), Map.copyOf(equipment));
        }

        private static PlayerState from(Snapshot snapshot) {
            PlayerState state = new PlayerState();
            state.revision = snapshot.revision;
            state.resources.putAll(snapshot.resources);
            state.equipment.putAll(snapshot.equipment);
            return state;
        }
    }

    private static final class HeldReservation {
        private final UUID playerId;
        private final TransactionRequest request;
        private final OperationResourcePlan resources;
        private final ReservationToken token;
        private final Snapshot before;
        private boolean consumed;

        private HeldReservation(UUID playerId, TransactionRequest request,
                                OperationResourcePlan resources, ReservationToken token,
                                Snapshot before) {
            this.playerId = playerId;
            this.request = request;
            this.resources = resources;
            this.token = token;
            this.before = before;
        }
    }

    private record Snapshot(
            long revision,
            Map<String, Long> resources,
            Map<UUID, EquipmentItemV1> equipment
    ) {
    }
}
