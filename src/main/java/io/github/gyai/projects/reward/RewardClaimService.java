package io.github.gyai.projects.reward;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public final class RewardClaimService {
    private final RewardClaimStore store;
    private final RewardDeliveryPort delivery;
    private final RewardRetryPolicy retryPolicy;
    private final Clock clock;

    public RewardClaimService(
            RewardClaimStore store,
            RewardDeliveryPort delivery,
            RewardRetryPolicy retryPolicy,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized RewardClaimResult claim(RewardClaimRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<RewardClaimResult> existing;
        try {
            existing = Objects.requireNonNull(
                    store.findTerminal(request.key()), "terminal lookup");
        } catch (RuntimeException failure) {
            return storeFailure(request, failure);
        }
        if (existing.isPresent()) return existing.orElseThrow().asReplay();

        try {
            return store.executeExclusive(request.key(), request.requestId(), () -> {
                RewardDeliveryReceipt receipt;
                try {
                    receipt = Objects.requireNonNull(
                            delivery.deliver(request), "delivery receipt");
                } catch (RuntimeException failure) {
                    return new RewardClaimResult(request.key(),
                            RewardClaimResult.Status.PERSIST_FAILURE,
                            "delivery=" + bounded(failure), false, false,
                            clock.instant());
                }
                boolean terminal = retryPolicy.terminal(receipt);
                return new RewardClaimResult(
                        request.key(), map(receipt.status()), receipt.reason(), terminal,
                        false, clock.instant());
            });
        } catch (RuntimeException failure) {
            return storeFailure(request, failure);
        }
    }

    private RewardClaimResult storeFailure(
            RewardClaimRequest request, RuntimeException failure
    ) {
        return new RewardClaimResult(request.key(),
                RewardClaimResult.Status.CLAIM_STORE_FAILURE,
                bounded(failure), false, false, clock.instant());
    }

    private static RewardClaimResult.Status map(RewardDeliveryReceipt.Status status) {
        return switch (status) {
            case DELIVERED -> RewardClaimResult.Status.DELIVERED;
            case FULL_INVENTORY -> RewardClaimResult.Status.FULL_INVENTORY;
            case PERSIST_FAILURE -> RewardClaimResult.Status.PERSIST_FAILURE;
            case COMMIT_UNCERTAIN -> RewardClaimResult.Status.COMMIT_UNCERTAIN;
            case REJECTED -> RewardClaimResult.Status.REJECTED;
        };
    }

    private static String bounded(RuntimeException failure) {
        String value = failure.getMessage();
        if (value == null || value.isBlank()) value = failure.getClass().getSimpleName();
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
