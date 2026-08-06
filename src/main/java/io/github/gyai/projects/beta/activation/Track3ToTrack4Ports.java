package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track3.StagingEconomyService;
import io.github.gyai.projects.beta.activation.track3.StagingEconomyOperationPort;
import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;
import io.github.gyai.projects.beta.activation.track4.StagingItemDeliveryPort;
import io.github.gyai.projects.reward.RewardClaimRequest;
import io.github.gyai.projects.reward.RewardDeliveryReceipt;

/** Explicit adapters from the real Track 3 service to Track 4 consumer ports. */
public final class Track3ToTrack4Ports {
    private Track3ToTrack4Ports() { }

    public static StagingItemDeliveryPort delivery(
            StagingEconomyService service,
            BetaActivationPolicy policy,
            java.util.function.BooleanSupplier running
    ) {
        java.util.Objects.requireNonNull(service);
        java.util.Objects.requireNonNull(policy);
        java.util.Objects.requireNonNull(running);
        return new StagingItemDeliveryPort() {
            @Override public RewardDeliveryReceipt deliver(
                    RewardClaimRequest claim, String item, int quantity
            ) {
                return rejected("delivery-context-required");
            }

            @Override public RewardDeliveryReceipt deliver(
                    RewardClaimRequest claim, String item, int quantity,
                    DeliveryContext context
            ) {
                if (!available() || claim == null || context == null) return rejected("module-not-running");
                StagingOperationAccess access = new StagingOperationAccess(
                        claim.key().playerId(), context.worldName(), context.projectsDev(), policy);
                var result = service.deliver(access, claim.requestId(), item, quantity);
                return switch (result.status()) {
                    case COMMITTED, REPLAYED -> new RewardDeliveryReceipt(
                            RewardDeliveryReceipt.Status.DELIVERED, result.detail(), true);
                    case COMMIT_UNCERTAIN -> knownSafeFullInventory(result)
                            ? new RewardDeliveryReceipt(RewardDeliveryReceipt.Status.FULL_INVENTORY,
                            result.detail(), false)
                            : new RewardDeliveryReceipt(RewardDeliveryReceipt.Status.COMMIT_UNCERTAIN,
                            result.detail(), false);
                    case ROLLED_BACK -> new RewardDeliveryReceipt(
                            RewardDeliveryReceipt.Status.PERSIST_FAILURE, result.detail(), false);
                    case REJECTED, FAILED -> rejected(result.detail());
                };
            }

            @Override public boolean available() { return running.getAsBoolean(); }
        };
    }

    public static io.github.gyai.projects.beta.activation.track4.StagingEconomyOperationPort economy(
            java.util.function.BooleanSupplier running
    ) {
        return new io.github.gyai.projects.beta.activation.track4.StagingEconomyOperationPort() {
            @Override public boolean available() { return running.getAsBoolean(); }
            @Override public String healthDetail() {
                return available() ? "Track 3 staging economy running" : "Track 3 staging economy disabled";
            }
        };
    }

    private static RewardDeliveryReceipt rejected(String reason) {
        return new RewardDeliveryReceipt(RewardDeliveryReceipt.Status.REJECTED,
                reason == null ? "" : reason, false);
    }

    /**
     * Bukkit storage reports this before any output is exposed and restores the
     * reservation snapshot. It is therefore a deterministic capacity result,
     * not an acknowledgement ambiguity. Keep this deliberately exact: every
     * other commit uncertainty remains non-retryable custody.
     */
    private static boolean knownSafeFullInventory(
            StagingEconomyOperationPort.OperationResult result
    ) {
        return result.transaction().map(transaction ->
                transaction.operationId().equals("projects:staging-resource")
                        && transaction.reason().equals("commit=full")).orElse(false);
    }
}
