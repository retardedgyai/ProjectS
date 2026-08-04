package io.github.gyai.projects.gathering;

import io.github.gyai.projects.transaction.DomainId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GatheringNode implements AutoCloseable {
    private final String nodeId;
    private final String resourceId;
    private final String worldKey;
    private final String locationKey;
    private final RespawnPolicy respawnPolicy;
    private State state = State.AVAILABLE;
    private Reservation reservation;
    private Instant depletedAt;
    private boolean closed;

    public GatheringNode(
            String nodeId,
            String resourceId,
            String worldKey,
            String locationKey,
            RespawnPolicy respawnPolicy
    ) {
        this.nodeId = DomainId.requireNamespaced(nodeId, "node ID");
        this.resourceId = DomainId.requireNamespaced(resourceId, "resource ID");
        this.worldKey = DomainId.requireKey(worldKey, "world key");
        this.locationKey = DomainId.requireKey(locationKey, "location key");
        this.respawnPolicy = Objects.requireNonNull(respawnPolicy, "respawnPolicy");
    }

    public synchronized boolean reserve(UUID reservationId, UUID playerId) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(playerId, "playerId");
        if (closed || state != State.AVAILABLE) return false;
        reservation = new Reservation(reservationId, playerId);
        state = State.RESERVED;
        return true;
    }

    public synchronized boolean cancel(UUID reservationId) {
        if (!matches(reservationId)) return false;
        reservation = null;
        state = State.AVAILABLE;
        return true;
    }

    public synchronized boolean deplete(UUID reservationId, Instant now) {
        Objects.requireNonNull(now, "now");
        if (!matches(reservationId)) return false;
        reservation = null;
        depletedAt = now;
        state = State.DEPLETED;
        return true;
    }

    public synchronized boolean refresh(Instant now) {
        Objects.requireNonNull(now, "now");
        if (closed || state != State.DEPLETED
                || !respawnPolicy.canRespawn(depletedAt, now)) {
            return false;
        }
        depletedAt = null;
        state = State.AVAILABLE;
        return true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                nodeId, resourceId, worldKey, locationKey, state,
                Optional.ofNullable(reservation),
                Optional.ofNullable(depletedAt), closed);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        reservation = null;
    }

    private boolean matches(UUID reservationId) {
        return reservationId != null && state == State.RESERVED
                && reservation != null
                && reservation.reservationId().equals(reservationId);
    }

    public enum State {
        AVAILABLE,
        RESERVED,
        DEPLETED
    }

    public record Reservation(UUID reservationId, UUID playerId) {
        public Reservation {
            Objects.requireNonNull(reservationId, "reservationId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    public record Snapshot(
            String nodeId,
            String resourceId,
            String worldKey,
            String locationKey,
            State state,
            Optional<Reservation> reservation,
            Optional<Instant> depletedAt,
            boolean closed
    ) {
        public Snapshot {
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(locationKey, "locationKey");
            Objects.requireNonNull(state, "state");
            reservation = reservation == null ? Optional.empty() : reservation;
            depletedAt = depletedAt == null ? Optional.empty() : depletedAt;
        }
    }
}
