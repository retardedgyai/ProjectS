package io.github.gyai.projects.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartyHealthSummary(PartyId partyId, List<MemberHealth> members) {
    public PartyHealthSummary {
        Objects.requireNonNull(partyId, "partyId");
        members = members == null ? List.of() : List.copyOf(members);
    }

    public record MemberHealth(UUID playerId, double health, double maximumHealth) {
        public MemberHealth {
            Objects.requireNonNull(playerId, "playerId");
            if (!Double.isFinite(health) || !Double.isFinite(maximumHealth)
                    || health < 0.0 || maximumHealth <= 0.0 || health > maximumHealth) {
                throw new IllegalArgumentException("Invalid health summary");
            }
        }
    }
}
