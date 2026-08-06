package io.github.gyai.projects.beta.activation.track3.infrastructure;

import io.github.gyai.projects.beta.activation.track3.BoundedStagingInventory;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyCatalog;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentCodec;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentDocument;
import io.github.gyai.projects.beta.activation.track3.StagingInventoryPort;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.operation.OperationResourcePlan;
import io.github.gyai.projects.transaction.InventoryCapacityProposal;
import io.github.gyai.projects.transaction.ReservationToken;
import io.github.gyai.projects.transaction.TransactionRequest;
import io.github.gyai.projects.transaction.TransactionStage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Transaction model backed by bounded immutable state, with each successful
 * consume/commit mirrored to a compare-and-swap Bukkit storage snapshot.
 * Only cloned ItemStacks are held during an active reservation.
 */
public final class BukkitStagingInventoryPort implements StagingInventoryPort {
    private static final int STAGING_RESOURCE_STACK_LIMIT = 64;
    private static final NamespacedKey STAGING_PAYLOAD = new NamespacedKey(
            "projects", "beta_staging_equipment_payload");
    private final BukkitStagingInventoryBridge bridge;
    private final BoundedStagingInventory transactions = new BoundedStagingInventory();
    private final StagingEquipmentCodec codec = new StagingEquipmentCodec();
    private final Function<StagingEquipmentDocument, ItemStack> equipmentStackFactory;
    private final BiFunction<Material, Integer, ItemStack> resourceStackFactory;
    private final HashMap<UUID, HeldLive> liveBefore = new HashMap<>();

    public BukkitStagingInventoryPort(BukkitStagingInventoryBridge bridge) {
        this(bridge, BukkitStagingInventoryPort::equipmentStack,
                (material, amount) -> new ItemStack(material, amount));
    }

    /** Production rendering path retains the staging envelope and visible lore. */
    public BukkitStagingInventoryPort(BukkitStagingInventoryBridge bridge,
                                      BukkitStagingEquipmentItemAdapter equipmentAdapter) {
        this(bridge, java.util.Objects.requireNonNull(equipmentAdapter)::committed,
                (material, amount) -> new ItemStack(material, amount));
    }

    /** Package-private stack seam keeps the live adapter verifiable without a Paper registry. */
    BukkitStagingInventoryPort(
            BukkitStagingInventoryBridge bridge,
            Function<StagingEquipmentDocument, ItemStack> equipmentStackFactory,
            BiFunction<Material, Integer, ItemStack> resourceStackFactory
    ) {
        this.bridge = java.util.Objects.requireNonNull(bridge);
        this.equipmentStackFactory = java.util.Objects.requireNonNull(equipmentStackFactory);
        this.resourceStackFactory = java.util.Objects.requireNonNull(resourceStackFactory);
    }

    @Override public synchronized void openSession(UUID playerId) {
        BukkitStagingInventoryBridge.InventorySnapshot live = bridge.snapshot(playerId)
                .orElseThrow(() -> new IllegalStateException("staging player offline"));
        transactions.openSession(playerId);
        synchronize(playerId, live.contents());
    }

    @Override public synchronized Optional<InventoryCapacityProposal> validate(UUID playerId,
            TransactionRequest request, OperationResourcePlan resources) {
        if (bridge.snapshot(playerId).isEmpty()) return Optional.empty();
        return transactions.validate(playerId, request, resources);
    }

    @Override public synchronized ReservationToken reserve(UUID playerId, TransactionRequest request,
            OperationResourcePlan resources, InventoryCapacityProposal capacity) {
        return transactions.reserve(playerId, request, resources, capacity);
    }

    @Override public synchronized void consume(UUID playerId, TransactionRequest request,
            OperationResourcePlan resources, ReservationToken reservation) {
        ItemStack[] before = bridge.snapshot(playerId).orElseThrow(() ->
                new IllegalStateException("staging player offline")).contents();
        ItemStack[] after = copy(before);
        for (OperationResourcePlan.MaterialCost cost : resources.materials()) {
            remove(after, material(StagingEconomyCatalog.itemIdForTransactionResource(cost.materialId())),
                    cost.quantity());
        }
        removeEquipmentInputs(after, request);
        BukkitStagingInventoryBridge.MutationResult mutation = bridge.replaceStorageAtomically(
                playerId, before, after);
        if (!mutation.committed()) throw new IllegalStateException("live-consume-" + mutation.status());
        try {
            transactions.consume(playerId, request, resources, reservation);
            liveBefore.put(request.requestId(), new HeldLive(playerId, copy(before)));
        } catch (RuntimeException failure) {
            BukkitStagingInventoryBridge.MutationResult compensation =
                    bridge.replaceStorageAtomically(playerId, after, before);
            if (!compensation.committed()) {
                throw new IllegalStateException("live-consume-compensation-" + compensation.status(), failure);
            }
            throw failure;
        }
    }

    @Override public synchronized CommitResult commitEquipment(UUID playerId, UUID requestId,
            ReservationToken reservation, long expectedRevision, StagingEquipmentDocument document) {
        ItemStack[] before = bridge.snapshot(playerId).orElseThrow(() ->
                new IllegalStateException("staging player offline")).contents();
        ItemStack[] after = copy(before);
        if (!insert(after, equipmentStackFactory.apply(document))) {
            return rollbackFailedCommit(playerId, requestId, reservation, "full");
        }
        BukkitStagingInventoryBridge.MutationResult live = bridge.replaceStorageAtomically(playerId, before, after);
        if (!live.committed()) return rollbackFailedCommit(playerId, requestId, reservation, live.status());
        CommitResult result = transactions.commitEquipment(playerId, requestId, reservation,
                expectedRevision, document);
        if (!result.committed()) rollbackFailedCommit(playerId, requestId, reservation, result.status());
        else liveBefore.remove(requestId);
        return result;
    }

    @Override public synchronized CommitResult commitResource(UUID playerId, UUID requestId,
            ReservationToken reservation, String resourceId, long quantity) {
        ItemStack[] before = bridge.snapshot(playerId).orElseThrow(() ->
                new IllegalStateException("staging player offline")).contents();
        ItemStack[] after = copy(before);
        if (!add(after, material(resourceId), quantity)) {
            return rollbackFailedCommit(playerId, requestId, reservation, "full");
        }
        BukkitStagingInventoryBridge.MutationResult live = bridge.replaceStorageAtomically(playerId, before, after);
        if (!live.committed()) return rollbackFailedCommit(playerId, requestId, reservation, live.status());
        CommitResult result = transactions.commitResource(playerId, requestId, reservation, resourceId, quantity);
        if (!result.committed()) rollbackFailedCommit(playerId, requestId, reservation, result.status());
        else liveBefore.remove(requestId);
        return result;
    }

    @Override public synchronized void rollback(UUID playerId, UUID requestId,
            ReservationToken reservation, TransactionStage lastCompletedStage) {
        HeldLive original = liveBefore.get(requestId);
        if (original != null) {
            BukkitStagingInventoryBridge.InventorySnapshot current = bridge.snapshot(playerId)
                    .orElseThrow(() -> new IllegalStateException("staging player offline"));
            BukkitStagingInventoryBridge.MutationResult restored = bridge.replaceStorageAtomically(
                    playerId, current.contents(), original.contents);
            if (!restored.committed()) {
                throw new IllegalStateException("live-rollback-" + restored.status());
            }
            liveBefore.remove(requestId);
        }
        transactions.rollback(playerId, requestId, reservation, lastCompletedStage);
    }

    @Override public synchronized InventorySnapshot snapshot(UUID playerId) {
        bridge.snapshot(playerId).ifPresent(live -> synchronize(playerId, live.contents()));
        return transactions.snapshot(playerId);
    }
    @Override public synchronized void logout(UUID playerId) {
        liveBefore.entrySet().removeIf(entry -> entry.getValue().playerId.equals(playerId));
        transactions.logout(playerId);
    }
    @Override public synchronized void close() { liveBefore.clear(); transactions.close(); }
    public BukkitStagingInventoryBridge bridge() { return bridge; }

    private void synchronize(UUID playerId, ItemStack[] contents) {
        HashMap<String, Long> resources = new HashMap<>();
        long ore = count(contents, Material.RAW_IRON), ingot = count(contents, Material.IRON_INGOT);
        if (ore > 0) resources.put(StagingEconomyCatalog.IRON_ORE, ore);
        if (ingot > 0) resources.put(StagingEconomyCatalog.IRON_INGOT, ingot);
        java.util.ArrayList<EquipmentItemV1> equipment = new java.util.ArrayList<>();
        for (ItemStack item : contents) staging(item).ifPresent(equipment::add);
        transactions.synchronizePlayerSnapshot(playerId, resources, equipment);
    }
    private CommitResult rollbackFailedCommit(UUID playerId, UUID requestId,
                                               ReservationToken reservation, String status) {
        rollback(playerId, requestId, reservation, TransactionStage.CONSUME);
        return new CommitResult(false, transactions.snapshot(playerId).revision(), Optional.empty(), status);
    }
    private Optional<EquipmentItemV1> staging(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        byte[] payload = item.getItemMeta().getPersistentDataContainer().get(STAGING_PAYLOAD,
                PersistentDataType.BYTE_ARRAY);
        try { return payload == null ? Optional.empty() : Optional.of(codec.decode(payload).item()); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    /**
     * Mirrors BoundedStagingInventory's UUID-input removal against the same
     * cloned storage snapshot as material consumption.  A staging payload is
     * an authoritative physical representation, so an invalid payload or an
     * ambiguous UUID is rejected before the live CAS is attempted.
     */
    private void removeEquipmentInputs(ItemStack[] values, TransactionRequest request) {
        HashSet<UUID> requested = new HashSet<>();
        for (TransactionRequest.InputRevision input : request.inputs()) {
            if (!input.inputId().startsWith("projects:item-")) continue;
            UUID instanceId = equipmentInputId(input.inputId());
            if (!requested.add(instanceId)) {
                throw new IllegalStateException("duplicate live equipment input");
            }
        }
        if (requested.isEmpty()) return;

        HashMap<UUID, Integer> matches = new HashMap<>();
        for (int slot = 0; slot < values.length; slot++) {
            ItemStack stack = values[slot];
            if (stack == null || !stack.hasItemMeta()) continue;
            byte[] payload = stack.getItemMeta().getPersistentDataContainer().get(STAGING_PAYLOAD,
                    PersistentDataType.BYTE_ARRAY);
            if (payload == null) continue;
            EquipmentItemV1 item;
            try {
                item = codec.decode(payload).item();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("malformed live staging equipment", invalid);
            }
            UUID instanceId = item.instanceId().orElseThrow(() ->
                    new IllegalStateException("live staging equipment lacks UUID"));
            if (!requested.contains(instanceId)) continue;
            if (stack.getAmount() != 1) {
                throw new IllegalStateException("malformed live equipment stack");
            }
            if (matches.putIfAbsent(instanceId, slot) != null) {
                throw new IllegalStateException("ambiguous live equipment input");
            }
        }
        for (UUID instanceId : requested) {
            Integer slot = matches.get(instanceId);
            if (slot == null) throw new IllegalStateException("live equipment input missing");
            values[slot] = null;
        }
    }
    private static UUID equipmentInputId(String inputId) {
        String value = inputId.substring("projects:item-".length());
        if (value.length() != 32) throw new IllegalArgumentException("invalid equipment input ID");
        String hyphenated = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-" + value.substring(16, 20) + "-"
                + value.substring(20);
        try {
            return UUID.fromString(hyphenated);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid equipment input ID", invalid);
        }
    }
    private static Material material(String id) {
        return switch (id) {
            case StagingEconomyCatalog.IRON_ORE -> Material.RAW_IRON;
            case StagingEconomyCatalog.IRON_INGOT -> Material.IRON_INGOT;
            default -> throw new IllegalArgumentException("unsupported live staging resource");
        };
    }
    private static void remove(ItemStack[] values, Material material, long amount) {
        long remaining = amount;
        for (int index = 0; index < values.length; index++) {
            ItemStack item = values[index];
            if (item == null || item.getType() != material) continue;
            int used = (int) Math.min(remaining, item.getAmount()); item.setAmount(item.getAmount() - used);
            remaining -= used;
            if (item.getAmount() == 0) values[index] = null;
            if (remaining == 0) return;
        }
        throw new IllegalStateException("live resource disappeared");
    }
    private boolean add(ItemStack[] values, Material material, long amount) {
        long remaining = amount;
        for (ItemStack item : values) if (item != null && item.getType() == material && item.getAmount() < STAGING_RESOURCE_STACK_LIMIT) {
            int add = (int) Math.min(remaining, STAGING_RESOURCE_STACK_LIMIT - item.getAmount()); item.setAmount(item.getAmount() + add); remaining -= add;
            if (remaining == 0) return true;
        }
        for (int i = 0; i < values.length && remaining > 0; i++) if (values[i] == null || values[i].getType() == Material.AIR) {
            int add = (int) Math.min(remaining, STAGING_RESOURCE_STACK_LIMIT); values[i] = resourceStackFactory.apply(material, add); remaining -= add;
        }
        return remaining == 0;
    }
    private static boolean insert(ItemStack[] values, ItemStack item) { for (int i = 0; i < values.length; i++) if (values[i] == null || values[i].getType() == Material.AIR) { values[i] = item; return true; } return false; }
    private static long count(ItemStack[] values, Material material) { return Arrays.stream(values).filter(item -> item != null && item.getType() == material).mapToLong(ItemStack::getAmount).sum(); }
    private static ItemStack[] copy(ItemStack[] values) { return Arrays.stream(values).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new); }
    private static ItemStack equipmentStack(StagingEquipmentDocument document) {
        ItemStack result = new ItemStack(Material.IRON_SWORD); var meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(STAGING_PAYLOAD, PersistentDataType.BYTE_ARRAY, document.payload()); result.setItemMeta(meta); return result;
    }
    private record HeldLive(UUID playerId, ItemStack[] contents) { }
}
