package io.github.gyai.projects.party;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PartyInvite(
        UUID inviteId,
        PartyId partyId,
        UUID inviterId,
        UUID inviteeId,
        Instant createdAt,
        Instant expiresAt,
        Status status
) {
    public PartyInvite {
        Objects.requireNonNull(inviteId, "inviteId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Invite expiry must follow creation");
        }
    }

    public boolean terminal() {
        return status != Status.PENDING;
    }

    public PartyInvite withStatus(Status replacement) {
        if (terminal()) return this;
        if (replacement == Status.PENDING) throw new IllegalArgumentException("Terminal status required");
        return new PartyInvite(inviteId, partyId, inviterId, inviteeId,
                createdAt, expiresAt, replacement);
    }

    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }
}
