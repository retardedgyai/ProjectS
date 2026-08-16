package io.github.gyai.projects.beta.activation.track4;

import java.util.List;
import java.util.UUID;

/** Read-only/fallback command contribution; ProjectCommand is not edited by this Track. */
public interface BetaOperatorCommandContributor {
    String prefix();

    Response execute(Request request);

    record Request(UUID playerId, String worldName, boolean projectsDev,
                   boolean compatibleClient, List<String> arguments) {
        public Request {
            arguments = List.copyOf(arguments == null ? List.of() : arguments);
        }
    }

    record Response(boolean success, List<String> messages) {
        public Response {
            messages = List.copyOf(messages == null ? List.of() : messages);
            if (messages.size() > 24) throw new IllegalArgumentException("response oversized");
        }
    }
}
