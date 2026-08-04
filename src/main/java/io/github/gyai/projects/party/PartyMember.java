package io.github.gyai.projects.party;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PartyMember(
        UUID playerId,
        long joinSequence,
        boolean connected,
        Optional<Instant> reconnectDeadline
) {
    public PartyMember {
        Objects.requireNonNull(playerId, "playerId");
        if (joinSequence < 0) throw new IllegalArgumentException("Negative join sequence");
        reconnectDeadline = reconnectDeadline == null ? Optional.empty() : reconnectDeadline;
        if (connected && reconnectDeadline.isPresent()) {
            throw new IllegalArgumentException("Connected member cannot have reconnect deadline");
        }
    }
}
