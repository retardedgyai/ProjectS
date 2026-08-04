package io.github.gyai.projects.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartySnapshot(PartyId partyId, UUID leaderId, List<PartyMember> members) {
    public PartySnapshot {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        members = members == null ? List.of() : List.copyOf(members);
        if (members.isEmpty()) throw new IllegalArgumentException("Party has no members");
        if (members.stream().map(PartyMember::playerId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("Duplicate party member");
        }
        if (members.stream().noneMatch(member -> member.playerId().equals(leaderId))) {
            throw new IllegalArgumentException("Leader must be a member");
        }
    }
}
