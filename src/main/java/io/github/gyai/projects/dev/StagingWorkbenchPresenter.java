package io.github.gyai.projects.dev;

import io.github.gyai.projects.beta.activation.BetaActivationAudience;
import io.github.gyai.projects.beta.activation.BetaMutationPolicy;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyOperationPort;
import io.github.gyai.projects.beta.activation.track3.StagingEquipmentInspectionFormatter;
import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;
import io.github.gyai.projects.equipment.EquipmentItemV1;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Thin, Bukkit-free workbench action/view boundary. It owns no player or inventory handle. */
public final class StagingWorkbenchPresenter {
    private final StagingEconomyOperationPort operations;
    private final BooleanSupplier gatheringRunning;
    private final BooleanSupplier enhancementRunning;
    private final BooleanSupplier craftFeatureAllowed;

    public StagingWorkbenchPresenter(StagingEconomyOperationPort operations,
                                     BooleanSupplier gatheringRunning,
                                     BooleanSupplier enhancementRunning,
                                     BooleanSupplier craftFeatureAllowed) {
        this.operations = java.util.Objects.requireNonNull(operations);
        this.gatheringRunning = java.util.Objects.requireNonNull(gatheringRunning);
        this.enhancementRunning = java.util.Objects.requireNonNull(enhancementRunning);
        this.craftFeatureAllowed = java.util.Objects.requireNonNull(craftFeatureAllowed);
    }

    public View view(StagingOperationAccess access, Optional<UUID> selected) {
        var snapshot = operations.status(access.playerId());
        return new View(snapshot, selected == null ? Optional.empty() : selected,
                gatheringRunning.getAsBoolean(), enhancementRunning.getAsBoolean(),
                craftFeatureAllowed.getAsBoolean(), denial(access, null));
    }

    public Action action(UUID requestId, StagingOperationAccess access,
                         StagingEconomyOperationPort.OperationKind kind,
                         Optional<String> item, long quantity) {
        String denial = denial(access, kind);
        if (!denial.isBlank()) return new Action(denial, Optional.empty());
        return new Action("", Optional.of(operations.execute(new StagingEconomyOperationPort.OperationRequest(
                requestId, access, kind, item, quantity))));
    }

    public String inspect(StagingOperationAccess access, Optional<UUID> selected) {
        List<EquipmentItemV1> equipment = operations.status(access.playerId()).equipment();
        EquipmentItemV1 item = selected.flatMap(id -> equipment.stream().filter(value ->
                value.instanceId().filter(id::equals).isPresent()).findFirst()).orElseGet(() ->
                equipment.isEmpty() ? null : equipment.getFirst());
        return StagingEquipmentInspectionFormatter.format(item);
    }

    public String denial(StagingOperationAccess access, StagingEconomyOperationPort.OperationKind kind) {
        if (!access.projectsDev()) return "projects.dev required";
        if (access.activationPolicy().audience() != BetaActivationAudience.ALLOWLIST
                || !access.activationPolicy().allowlistedPlayerUuids().contains(access.playerId())) return "audience/allowlist denied";
        if (!access.activationPolicy().allowsWorld(access.worldName())) return "world denied";
        if (access.activationPolicy().mutationPolicy() != BetaMutationPolicy.STAGING_WRITE) return "mutation policy denied";
        boolean gathering = kind == null || kind == StagingEconomyOperationPort.OperationKind.GIVE
                || kind == StagingEconomyOperationPort.OperationKind.REFINE || kind == StagingEconomyOperationPort.OperationKind.CRAFT;
        if (gathering && !gatheringRunning.getAsBoolean()) return "GATHERING_CRAFTING not RUNNING";
        if (!gathering && !enhancementRunning.getAsBoolean()) return "ENHANCEMENT_REPAIR not RUNNING";
        if (kind == StagingEconomyOperationPort.OperationKind.CRAFT && !craftFeatureAllowed.getAsBoolean()) return "craft feature denied";
        return "";
    }

    public record View(io.github.gyai.projects.beta.activation.track3.StagingInventoryPort.InventorySnapshot snapshot,
                       Optional<UUID> selected, boolean gatheringRunning, boolean enhancementRunning,
                       boolean craftFeatureAllowed, String readOnlyReason) { }
    public record Action(String denial, Optional<StagingEconomyOperationPort.OperationResult> result) {
        public boolean accepted() { return result.isPresent(); }
    }
}
