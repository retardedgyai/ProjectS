package io.github.gyai.projects.network.beta;

import java.util.LinkedHashMap;

public final class BetaCommandRouter implements AutoCloseable {
    private final BetaRateLimiter rateLimiter;
    private final BetaCommandAuthorization authorization;
    private final int maximumTerminalResults;
    private final LinkedHashMap<String, BetaCommandResult> terminalResults =
            new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public BetaCommandRouter(
            BetaRateLimiter rateLimiter,
            BetaCommandAuthorization authorization,
            int maximumTerminalResults
    ) {
        this.rateLimiter = java.util.Objects.requireNonNull(rateLimiter);
        this.authorization = java.util.Objects.requireNonNull(authorization);
        if (maximumTerminalResults <= 0) {
            throw new IllegalArgumentException("maximumTerminalResults must be positive");
        }
        this.maximumTerminalResults = maximumTerminalResults;
    }

    public synchronized BetaCommandResult route(
            BetaCommandContext context,
            BetaCommandEnvelope command,
            BetaCommandDecoder decoder,
            BetaCommandPort destination
    ) {
        java.util.Objects.requireNonNull(context);
        java.util.Objects.requireNonNull(command);
        java.util.Objects.requireNonNull(decoder);
        java.util.Objects.requireNonNull(destination);
        String key = context.playerId() + ":" + command.idempotencyRequestId();
        BetaCommandResult previous = terminalResults.get(key);
        if (previous != null) return previous.asReplay();
        if (closed) return result(key, BetaCommandResult.Status.CLOSED, command, "Router is closed", true);
        if (!context.producerFeatureEnabled()) {
            return result(key, BetaCommandResult.Status.FEATURE_DISABLED, command, "Feature is disabled", true);
        }
        if (!context.permissionGranted()) {
            return result(key, BetaCommandResult.Status.PERMISSION_DENIED, command, "Permission denied", true);
        }
        if (!context.capabilitySession().playerId().equals(context.playerId())
                || context.capabilitySession().sessionId() == null
                || !context.capabilitySession().sessionId().equals(
                        command.message().requestOrSessionId())
                || !context.capabilitySession().supports(
                        command.message().capabilityId(), command.message().capabilityPayloadVersion())) {
            return result(key, BetaCommandResult.Status.CAPABILITY_DENIED, command, "Capability not negotiated", true);
        }
        if (context.currentPlayerSessionRevision() != command.playerSessionRevision()
                || context.currentTargetContentRevision() != command.targetContentRevision()) {
            return result(key, BetaCommandResult.Status.STALE_REVISION, command, "Revision mismatch", true);
        }
        if (!context.currentStateValid()) {
            return result(key, BetaCommandResult.Status.INVALID_CURRENT_STATE, command, "Current state rejected", true);
        }
        if (!context.transactionAdmitted()) {
            return result(key, BetaCommandResult.Status.TRANSACTION_REJECTED, command, "Transaction not admitted", true);
        }
        BetaCommandAuthorization.Decision decision = authorization.authorize(context, command);
        if (decision == null || !decision.allowed()) {
            return result(key, BetaCommandResult.Status.PERMISSION_DENIED, command,
                    decision == null ? "Authorization unavailable" : decision.reason(), true);
        }
        BetaRateLimitPolicy policy = switch (context.commandClass()) {
            case READ -> BetaRateLimitPolicy.READ;
            case PERSISTENT_MUTATION -> BetaRateLimitPolicy.PERSISTENT;
            case MOB_EDITOR_SAVE_APPLY -> BetaRateLimitPolicy.MOB_EDITOR_SAVE_APPLY;
        };
        String rateKey = context.playerId() + ":" + context.commandClass();
        if (!rateLimiter.tryAcquire(rateKey, policy)) {
            return result(key, BetaCommandResult.Status.RATE_LIMITED, command, "Rate limit exceeded", false);
        }
        BetaCommandDecoder.DecodeResult decoded;
        try {
            decoded = decoder.decode(command);
        } catch (RuntimeException exception) {
            decoded = BetaCommandDecoder.DecodeResult.failure("Command decoder failed");
        }
        if (decoded == null || !decoded.successful()
                || decoded.command().capabilityId() != command.message().capabilityId()
                || !decoded.command().requestId().equals(command.idempotencyRequestId())
                || decoded.command().playerSessionRevision() != command.playerSessionRevision()
                || decoded.command().targetContentRevision() != command.targetContentRevision()) {
            return result(key, BetaCommandResult.Status.MALFORMED, command,
                    decoded == null ? "Command decoder unavailable" : decoded.detail(), true);
        }
        BetaCommandResult delivered = destination.handle(context, decoded.command());
        if (delivered == null || !delivered.requestId().equals(command.idempotencyRequestId())) {
            return result(key, BetaCommandResult.Status.MALFORMED, command, "Invalid destination result", true);
        }
        if (delivered.terminal()) remember(key, delivered);
        return delivered;
    }

    public synchronized void clear() {
        terminalResults.clear();
        rateLimiter.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        terminalResults.clear();
        rateLimiter.close();
    }

    private BetaCommandResult result(
            String key,
            BetaCommandResult.Status status,
            BetaCommandEnvelope command,
            String detail,
            boolean terminal
    ) {
        BetaCommandResult result = new BetaCommandResult(
                status, command.idempotencyRequestId(), detail, terminal);
        if (terminal) remember(key, result);
        return result;
    }

    private void remember(String key, BetaCommandResult result) {
        if (terminalResults.size() >= maximumTerminalResults && !terminalResults.containsKey(key)) {
            terminalResults.remove(terminalResults.keySet().iterator().next());
        }
        terminalResults.put(key, result);
    }
}
