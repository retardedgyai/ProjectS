package io.github.gyai.projects.dev;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DevMenuHolder implements InventoryHolder {
    private final DevMenuManager.Page page;
    private final int pageNumber;
    private final Map<Integer, DevMenuManager.MenuAction> actions = new HashMap<>();
    private final Map<String, UUID> requestIds = new HashMap<>();
    private UUID selectedEquipment;
    private final java.util.HashSet<String> inFlight = new java.util.HashSet<>();
    private String stagingOutcome = "";
    private boolean stagingRefresh;
    private Inventory inventory;

    DevMenuHolder(DevMenuManager.Page page, int pageNumber) {
        this.page = page;
        this.pageNumber = pageNumber;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void action(int slot, DevMenuManager.MenuAction action) {
        actions.put(slot, action);
    }

    DevMenuManager.MenuAction action(int slot) {
        return actions.get(slot);
    }

    DevMenuManager.Page page() {
        return page;
    }

    int pageNumber() {
        return pageNumber;
    }

    UUID requestId(String action) {
        return requestIds.computeIfAbsent(action, ignored -> UUID.randomUUID());
    }

    /** Releases a completed request so the next real click is a new operation. */
    void completeRequest(String action, io.github.gyai.projects.beta.activation.track3.StagingEconomyOperationPort.Status status) {
        if (status == null) return;
        switch (status) {
            case COMMITTED, REPLAYED, ROLLED_BACK, REJECTED -> requestIds.remove(action);
            case COMMIT_UNCERTAIN, FAILED -> {
                // A retry must retain its id until a terminal receipt is known.
            }
        }
    }

    UUID selectedEquipment() { return selectedEquipment; }
    void selectEquipment(UUID value) { selectedEquipment = value; }
    boolean begin(String action) { return inFlight.add(action); }
    void finish(String action) { inFlight.remove(action); }
    String stagingOutcome() { return stagingOutcome; }
    void stagingOutcome(String value) {
        stagingOutcome = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (stagingOutcome.length() > 256) stagingOutcome = stagingOutcome.substring(0, 256);
    }

    /** Transfers only bounded, UUID/text staging state; never a Bukkit handle. */
    void copyStagingStateFrom(DevMenuHolder source) {
        if (source == null) return;
        selectedEquipment = source.selectedEquipment;
        requestIds.putAll(source.requestIds);
        inFlight.addAll(source.inFlight);
        stagingOutcome(source.stagingOutcome);
    }

    void beginStagingRefresh() { stagingRefresh = true; }
    boolean consumeStagingRefresh() {
        boolean value = stagingRefresh;
        stagingRefresh = false;
        return value;
    }
    void clearStagingState() {
        selectedEquipment = null; requestIds.clear(); inFlight.clear(); stagingOutcome = "";
        stagingRefresh = false;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
