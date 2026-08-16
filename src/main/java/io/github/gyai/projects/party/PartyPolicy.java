package io.github.gyai.projects.party;

import java.time.Duration;
import java.util.Objects;

public record PartyPolicy(
        int maximumPartySize,
        int maximumParties,
        int maximumInviteRecords,
        int maximumInvitesPerWindow,
        Duration inviteRateWindow,
        Duration inviteExpiry,
        Duration reconnectGrace
) {
    public PartyPolicy {
        if (maximumPartySize <= 0 || maximumParties <= 0
                || maximumInviteRecords <= 0 || maximumInvitesPerWindow <= 0) {
            throw new IllegalArgumentException("Party policy bounds must be positive");
        }
        inviteRateWindow = positive(inviteRateWindow, "inviteRateWindow");
        inviteExpiry = positive(inviteExpiry, "inviteExpiry");
        reconnectGrace = positive(reconnectGrace, "reconnectGrace");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
