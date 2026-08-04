package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.UUID;

public record PartyDisplaySnapshot(
        String partyId,
        long revision,
        UUID leaderId,
        List<Member> members
) {
    public PartyDisplaySnapshot {
        partyId = BetaDisplayValidation.id(partyId, "partyId");
        if (revision < 0 || leaderId == null) throw new IllegalArgumentException("Invalid party identity");
        members = BetaDisplayValidation.list(members, 128, "party members");
    }

    public record Member(UUID playerId, String displayName, double healthRatio,
                         boolean nearby, boolean connected) {
        public Member {
            if (playerId == null) throw new IllegalArgumentException("Player ID is required");
            displayName = BetaDisplayValidation.string(displayName, "displayName");
            BetaDisplayValidation.finite(healthRatio, "healthRatio");
            if (healthRatio < 0 || healthRatio > 1) {
                throw new IllegalArgumentException("healthRatio is out of range");
            }
        }
    }
}
