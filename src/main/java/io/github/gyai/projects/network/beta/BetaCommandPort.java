package io.github.gyai.projects.network.beta;

@FunctionalInterface
public interface BetaCommandPort {
    BetaCommandResult handle(BetaCommandContext context, BetaCommandEnvelope command);
}
