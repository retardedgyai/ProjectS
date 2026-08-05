package io.github.gyai.projects.beta.activation.track2;

import java.util.List;
import java.util.UUID;

/** Pure command contribution. ProjectCommand registration belongs to the Integration Gate. */
public interface BetaOperatorCommandContributor {
    String route();

    List<String> execute(boolean permitted, UUID actorId, List<String> arguments);
}
