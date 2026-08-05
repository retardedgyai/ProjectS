package io.github.gyai.projects.beta.activation.track3;

import java.util.UUID;

/** Cross-Track port consumed by Activation Track 4 after the Integration Gate. */
public interface StagingItemDeliveryPort {
    StagingEconomyOperationPort.OperationResult deliver(
            StagingOperationAccess access,
            UUID requestId,
            String stagingItemId,
            long quantity);
}
