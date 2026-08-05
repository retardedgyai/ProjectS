package io.github.gyai.projects.network.beta;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BetaCapabilitySessionService implements AutoCloseable {
    public enum AcknowledgeStatus {
        ACCEPTED,
        FEATURE_DISABLED,
        UNKNOWN_SESSION,
        EXPIRED,
        REVISION_MISMATCH,
        VERSION_MISMATCH,
        UNAVAILABLE_CAPABILITY,
        CLOSED
    }

    private BetaCapabilityPolicy policy;
    private final Clock clock;
    private final LinkedHashMap<UUID, MutableSession> sessions =
            new LinkedHashMap<>(16, 0.75f, true);
    private long nextRevision = 1;
    private boolean globallyEnabled;
    private boolean closed;

    public BetaCapabilitySessionService(BetaCapabilityPolicy policy, Clock clock) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized Optional<BetaCapabilityAdvertisement> advertise(
            UUID playerId,
            boolean clientBetaUiEnabled,
            BetaCapabilityAvailability availability
    ) {
        requirePlayer(playerId);
        if (closed) return Optional.empty();
        if (!clientBetaUiEnabled) {
            sessions.remove(playerId);
            return Optional.empty();
        }
        globallyEnabled = true;
        expire();
        var capabilities = policy.advertisedCapabilities().stream()
                .filter(value -> availability != null
                        && availability.isAvailable(playerId, value.id()))
                .toList();
        if (!sessions.containsKey(playerId) && sessions.size() >= policy.maximumSessions()) {
            UUID eldest = sessions.keySet().iterator().next();
            sessions.remove(eldest);
        }
        UUID sessionId = UUID.randomUUID();
        long revision = nextRevision++;
        Instant expiresAt = clock.instant().plus(policy.sessionTtl());
        sessions.put(playerId, new MutableSession(
                sessionId, revision, capabilities, new LinkedHashMap<>(), expiresAt));
        return Optional.of(new BetaCapabilityAdvertisement(
                BetaProtocolVersion.CURRENT, sessionId, revision, capabilities));
    }

    public synchronized AcknowledgeStatus acknowledge(
            UUID playerId,
            BetaCapabilityAcknowledgement acknowledgement,
            BetaCapabilityAvailability availability
    ) {
        requirePlayer(playerId);
        if (closed) return AcknowledgeStatus.CLOSED;
        if (!globallyEnabled) return AcknowledgeStatus.FEATURE_DISABLED;
        MutableSession session = sessions.get(playerId);
        if (session == null) return AcknowledgeStatus.UNKNOWN_SESSION;
        if (clock.instant().isAfter(session.expiresAt)) {
            sessions.remove(playerId);
            return AcknowledgeStatus.EXPIRED;
        }
        expire();
        if (!session.sessionId.equals(acknowledgement.sessionId())) {
            return AcknowledgeStatus.UNKNOWN_SESSION;
        }
        if (session.revision != acknowledgement.advertisementRevision()) {
            return AcknowledgeStatus.REVISION_MISMATCH;
        }
        Map<BetaCapabilityId, Integer> advertised = session.advertised.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        BetaCapabilityDescriptor::id,
                        BetaCapabilityDescriptor::payloadVersion));
        LinkedHashMap<BetaCapabilityId, Integer> accepted = new LinkedHashMap<>();
        for (BetaCapabilityDescriptor capability : acknowledgement.capabilities()) {
            Integer expected = advertised.get(capability.id());
            if (expected == null || availability == null
                    || !availability.isAvailable(playerId, capability.id())) {
                return AcknowledgeStatus.UNAVAILABLE_CAPABILITY;
            }
            if (expected != capability.payloadVersion()) {
                return AcknowledgeStatus.VERSION_MISMATCH;
            }
            accepted.put(capability.id(), capability.payloadVersion());
        }
        session.acknowledged.clear();
        session.acknowledged.putAll(accepted);
        return AcknowledgeStatus.ACCEPTED;
    }

    public synchronized BetaCapabilitySnapshot snapshot(UUID playerId) {
        requirePlayer(playerId);
        if (closed || !globallyEnabled) return BetaCapabilitySnapshot.oldClient(playerId);
        expire();
        MutableSession session = sessions.get(playerId);
        if (session == null) return BetaCapabilitySnapshot.oldClient(playerId);
        return new BetaCapabilitySnapshot(
                playerId, session.sessionId, session.revision,
                session.acknowledged, session.expiresAt, false);
    }

    public synchronized void reconnect(UUID playerId) {
        clear(playerId);
    }

    public synchronized void clear(UUID playerId) {
        if (playerId != null) sessions.remove(playerId);
    }

    public synchronized void reload(BetaCapabilityPolicy replacement, boolean enabled) {
        policy = java.util.Objects.requireNonNull(replacement);
        globallyEnabled = enabled;
        sessions.clear();
    }

    public synchronized void clear() {
        sessions.clear();
    }

    public synchronized int activeSessionCount() {
        expire();
        return sessions.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        globallyEnabled = false;
        sessions.clear();
    }

    private void expire() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));
    }

    private static void requirePlayer(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("Player ID is required");
    }

    private static final class MutableSession {
        private final UUID sessionId;
        private final long revision;
        private final java.util.List<BetaCapabilityDescriptor> advertised;
        private final LinkedHashMap<BetaCapabilityId, Integer> acknowledged;
        private final Instant expiresAt;

        private MutableSession(
                UUID sessionId,
                long revision,
                java.util.List<BetaCapabilityDescriptor> advertised,
                LinkedHashMap<BetaCapabilityId, Integer> acknowledged,
                Instant expiresAt
        ) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.advertised = java.util.List.copyOf(advertised);
            this.acknowledged = acknowledged;
            this.expiresAt = expiresAt;
        }
    }
}
