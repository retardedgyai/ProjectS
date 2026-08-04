package io.github.gyai.projects.party;

import java.util.Optional;

public record PartyCommandResult(
        Status status,
        Optional<PartySnapshot> party,
        Optional<PartyInvite> invite,
        String reason,
        boolean replayed
) {
    public PartyCommandResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        party = party == null ? Optional.empty() : party;
        invite = invite == null ? Optional.empty() : invite;
        reason = reason == null ? "" : reason;
        if (reason.length() > 256) reason = reason.substring(0, 256);
    }

    public enum Status {
        CREATED, INVITED, ACCEPTED, DECLINED, EXPIRED, LEFT, KICKED,
        DISCONNECTED, RECONNECTED, DISBANDED, REJECTED, CLOSED
    }
}
