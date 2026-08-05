package io.github.gyai.projects.beta.activation.track3.spi;

import io.github.gyai.projects.beta.activation.track3.StagingOperationAccess;

import java.util.List;
import java.util.Set;

/** Track-local command contribution; it performs no Bukkit command registration. */
public interface BetaOperatorCommandContributor {
    String contributorId();

    Set<String> commandPaths();

    CommandResponse execute(StagingOperationAccess access, List<String> arguments);

    record CommandResponse(boolean success, String message) {
        public CommandResponse {
            message = message == null ? "" : message;
            if (message.length() > 256) message = message.substring(0, 256);
        }
    }
}
