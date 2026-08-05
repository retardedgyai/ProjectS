package io.github.gyai.projects.beta.activation.track1.spi;

import java.util.List;

/** Read-only operator contribution. It cannot enable flags or mutate policy. */
public interface BetaOperatorCommandContributor {
    String route();

    BetaOperatorCommandResult execute(
            boolean hasProjectsDevPermission,
            BetaOperatorSubject subject,
            List<String> arguments);
}
