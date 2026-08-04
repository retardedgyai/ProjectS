package io.github.gyai.projects.party;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PartyService implements AutoCloseable {
    private final PartyPolicy policy;
    private final Clock clock;
    private final Map<PartyId, MutableParty> parties = new LinkedHashMap<>();
    private final Map<UUID, PartyId> membership = new LinkedHashMap<>();
    private final Map<UUID, PartyInvite> invites = new LinkedHashMap<>();
    private final Map<UUID, ArrayDeque<Instant>> inviteHistory = new LinkedHashMap<>();
    private long nextJoinSequence;
    private boolean closed;

    public PartyService(PartyPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized PartyCommandResult create(PartyId partyId, UUID leaderId) {
        requireOpen();
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        if (parties.size() >= policy.maximumParties()) return rejected("party-capacity");
        if (parties.containsKey(partyId)) return rejected("party-id-in-use");
        if (membership.containsKey(leaderId)) return rejected("already-in-party");
        if (!joinSequenceAvailable()) return rejected("join-sequence-exhausted");
        MutableParty party = new MutableParty(partyId, leaderId);
        party.members.put(leaderId, connected(leaderId));
        parties.put(partyId, party);
        membership.put(leaderId, partyId);
        return result(PartyCommandResult.Status.CREATED, party, null, "", false);
    }

    public synchronized PartyCommandResult invite(
            UUID inviteId, PartyId partyId, UUID inviterId, UUID inviteeId
    ) {
        requireOpen();
        Objects.requireNonNull(inviteId, "inviteId");
        MutableParty party = parties.get(partyId);
        if (party == null || !party.members.containsKey(inviterId)) {
            return rejected("inviter-not-member");
        }
        if (invites.containsKey(inviteId)) {
            PartyInvite existing = invites.get(inviteId);
            if (!existing.partyId().equals(partyId)
                    || !existing.inviterId().equals(inviterId)
                    || !existing.inviteeId().equals(inviteeId)) {
                return rejected("invite-id-conflict");
            }
            return result(PartyCommandResult.Status.INVITED, party, existing,
                    "invite-id-replay", true);
        }
        if (membership.containsKey(inviteeId)) return rejected("invitee-already-in-party");
        if (party.members.size() >= policy.maximumPartySize()) return rejected("party-full");
        if (invites.size() >= policy.maximumInviteRecords()) return rejected("invite-capacity");
        Instant now = clock.instant();
        if (!allowInvite(inviterId, now)) return rejected("invite-rate-limit");
        PartyInvite invite = new PartyInvite(inviteId, partyId, inviterId, inviteeId,
                now, now.plus(policy.inviteExpiry()), PartyInvite.Status.PENDING);
        invites.put(inviteId, invite);
        return result(PartyCommandResult.Status.INVITED, party, invite, "", false);
    }

    public synchronized PartyCommandResult accept(UUID inviteId) {
        requireOpen();
        PartyInvite invite = invites.get(inviteId);
        if (invite == null) return rejected("unknown-invite");
        MutableParty party = parties.get(invite.partyId());
        if (invite.terminal()) {
            return result(statusFor(invite.status()), party, invite, "terminal-replay", true);
        }
        if (!clock.instant().isBefore(invite.expiresAt())) return expire(inviteId);
        if (party == null) return terminal(invite, PartyInvite.Status.CANCELLED, null,
                PartyCommandResult.Status.REJECTED, "party-missing");
        if (membership.containsKey(invite.inviteeId())) {
            return terminal(invite, PartyInvite.Status.CANCELLED, party,
                    PartyCommandResult.Status.REJECTED, "already-in-party");
        }
        if (party.members.size() >= policy.maximumPartySize()) {
            return terminal(invite, PartyInvite.Status.CANCELLED, party,
                    PartyCommandResult.Status.REJECTED, "party-full");
        }
        if (!joinSequenceAvailable()) {
            return terminal(invite, PartyInvite.Status.CANCELLED, party,
                    PartyCommandResult.Status.REJECTED, "join-sequence-exhausted");
        }
        party.members.put(invite.inviteeId(), connected(invite.inviteeId()));
        membership.put(invite.inviteeId(), invite.partyId());
        return terminal(invite, PartyInvite.Status.ACCEPTED, party,
                PartyCommandResult.Status.ACCEPTED, "");
    }

    public synchronized PartyCommandResult decline(UUID inviteId) {
        requireOpen();
        PartyInvite invite = invites.get(inviteId);
        if (invite == null) return rejected("unknown-invite");
        MutableParty party = parties.get(invite.partyId());
        if (invite.terminal()) {
            return result(statusFor(invite.status()), party, invite, "terminal-replay", true);
        }
        return terminal(invite, PartyInvite.Status.DECLINED, party,
                PartyCommandResult.Status.DECLINED, "");
    }

    public synchronized PartyCommandResult expire(UUID inviteId) {
        requireOpen();
        PartyInvite invite = invites.get(inviteId);
        if (invite == null) return rejected("unknown-invite");
        MutableParty party = parties.get(invite.partyId());
        if (invite.terminal()) {
            return result(statusFor(invite.status()), party, invite, "terminal-replay", true);
        }
        if (clock.instant().isBefore(invite.expiresAt())) return rejected("invite-not-expired");
        return terminal(invite, PartyInvite.Status.EXPIRED, party,
                PartyCommandResult.Status.EXPIRED, "");
    }

    public synchronized PartyCommandResult leave(UUID playerId) {
        requireOpen();
        return remove(playerId, playerId, false);
    }

    public synchronized PartyCommandResult kick(UUID actorId, UUID memberId) {
        requireOpen();
        PartyId partyId = membership.get(actorId);
        MutableParty party = parties.get(partyId);
        if (party == null || !party.leaderId.equals(actorId)) return rejected("not-leader");
        if (actorId.equals(memberId)) return rejected("leader-must-leave");
        return remove(actorId, memberId, true);
    }

    public synchronized PartyCommandResult disconnect(UUID playerId) {
        requireOpen();
        MutableParty party = partyFor(playerId);
        if (party == null) return rejected("not-in-party");
        PartyMember current = party.members.get(playerId);
        if (!current.connected()) {
            return result(PartyCommandResult.Status.DISCONNECTED, party, null,
                    "disconnect-replay", true);
        }
        party.members.put(playerId, new PartyMember(playerId, current.joinSequence(),
                false, Optional.of(clock.instant().plus(policy.reconnectGrace()))));
        return result(PartyCommandResult.Status.DISCONNECTED, party, null, "", false);
    }

    public synchronized PartyCommandResult reconnect(UUID playerId) {
        requireOpen();
        MutableParty party = partyFor(playerId);
        if (party == null) return rejected("not-in-party");
        PartyMember member = party.members.get(playerId);
        if (member.connected()) {
            return result(PartyCommandResult.Status.RECONNECTED, party, null,
                    "reconnect-replay", true);
        }
        if (!clock.instant().isBefore(member.reconnectDeadline().orElseThrow())) {
            return remove(playerId, playerId, false);
        }
        party.members.put(playerId, connected(playerId, member.joinSequence()));
        return result(PartyCommandResult.Status.RECONNECTED, party, null, "", false);
    }

    public synchronized int expireDisconnectedMembers() {
        requireOpen();
        Instant now = clock.instant();
        List<UUID> expired = parties.values().stream()
                .flatMap(party -> party.members.values().stream())
                .filter(member -> !member.connected())
                .filter(member -> !now.isBefore(member.reconnectDeadline().orElseThrow()))
                .map(PartyMember::playerId).toList();
        expired.forEach(player -> remove(player, player, false));
        return expired.size();
    }

    public synchronized Optional<PartySnapshot> party(PartyId partyId) {
        MutableParty party = parties.get(partyId);
        return party == null ? Optional.empty() : Optional.of(snapshot(party));
    }

    public synchronized List<UUID> chatRecipients(PartyId partyId, UUID senderId) {
        MutableParty party = parties.get(partyId);
        if (party == null || !party.members.containsKey(senderId)) return List.of();
        return party.members.values().stream().filter(PartyMember::connected)
                .map(PartyMember::playerId).toList();
    }

    public synchronized PartyHealthSummary healthSummary(
            PartyId partyId, List<PartyHealthSummary.MemberHealth> inputs
    ) {
        MutableParty party = parties.get(partyId);
        if (party == null) throw new IllegalArgumentException("Unknown party");
        List<PartyHealthSummary.MemberHealth> filtered = List.copyOf(inputs).stream()
                .filter(value -> party.members.containsKey(value.playerId())).toList();
        return new PartyHealthSummary(partyId, filtered);
    }

    public synchronized int partyCount() { return parties.size(); }
    public synchronized int inviteRecordCount() { return invites.size(); }
    public synchronized boolean closed() { return closed; }

    public synchronized void clear() {
        parties.clear();
        membership.clear();
        invites.clear();
        inviteHistory.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        clear();
        closed = true;
    }

    private PartyCommandResult remove(UUID actor, UUID memberId, boolean kicked) {
        MutableParty party = partyFor(memberId);
        if (party == null) return rejected("not-in-party");
        if (kicked && !party.leaderId.equals(actor)) return rejected("not-leader");
        party.members.remove(memberId);
        membership.remove(memberId);
        inviteHistory.remove(memberId);
        if (party.members.isEmpty()) {
            parties.remove(party.partyId);
            cancelInvites(party.partyId);
            return new PartyCommandResult(PartyCommandResult.Status.DISBANDED,
                    Optional.empty(), Optional.empty(), "", false);
        }
        if (party.leaderId.equals(memberId)) {
            party.leaderId = party.members.values().stream()
                    .min(Comparator.comparingLong(PartyMember::joinSequence))
                    .orElseThrow().playerId();
        }
        return result(kicked ? PartyCommandResult.Status.KICKED
                : PartyCommandResult.Status.LEFT, party, null, "", false);
    }

    private void cancelInvites(PartyId partyId) {
        invites.replaceAll((id, invite) -> invite.partyId().equals(partyId)
                && !invite.terminal() ? invite.withStatus(PartyInvite.Status.CANCELLED) : invite);
    }

    private boolean allowInvite(UUID inviter, Instant now) {
        ArrayDeque<Instant> history = inviteHistory.computeIfAbsent(
                inviter, ignored -> new ArrayDeque<>());
        Instant threshold = now.minus(policy.inviteRateWindow());
        while (!history.isEmpty() && !history.peekFirst().isAfter(threshold)) {
            history.removeFirst();
        }
        if (history.size() >= policy.maximumInvitesPerWindow()) return false;
        history.addLast(now);
        return true;
    }

    private PartyMember connected(UUID playerId) {
        return connected(playerId, nextJoinSequence++);
    }

    private boolean joinSequenceAvailable() {
        return nextJoinSequence < Long.MAX_VALUE;
    }

    private static PartyMember connected(UUID playerId, long sequence) {
        return new PartyMember(playerId, sequence, true, Optional.empty());
    }

    private MutableParty partyFor(UUID playerId) {
        return parties.get(membership.get(playerId));
    }

    private PartyCommandResult terminal(
            PartyInvite invite, PartyInvite.Status inviteStatus, MutableParty party,
            PartyCommandResult.Status status, String reason
    ) {
        PartyInvite replacement = invite.withStatus(inviteStatus);
        invites.put(invite.inviteId(), replacement);
        return result(status, party, replacement, reason, false);
    }

    private static PartyCommandResult.Status statusFor(PartyInvite.Status status) {
        return switch (status) {
            case ACCEPTED -> PartyCommandResult.Status.ACCEPTED;
            case DECLINED -> PartyCommandResult.Status.DECLINED;
            case EXPIRED -> PartyCommandResult.Status.EXPIRED;
            case CANCELLED, PENDING -> PartyCommandResult.Status.REJECTED;
        };
    }

    private static PartyCommandResult result(
            PartyCommandResult.Status status, MutableParty party, PartyInvite invite,
            String reason, boolean replayed
    ) {
        return new PartyCommandResult(status,
                party == null ? Optional.empty() : Optional.of(snapshot(party)),
                Optional.ofNullable(invite), reason, replayed);
    }

    private static PartyCommandResult rejected(String reason) {
        return new PartyCommandResult(PartyCommandResult.Status.REJECTED,
                Optional.empty(), Optional.empty(), reason, false);
    }

    private static PartySnapshot snapshot(MutableParty party) {
        return new PartySnapshot(party.partyId, party.leaderId,
                new ArrayList<>(party.members.values()));
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Party service is closed");
    }

    private static final class MutableParty {
        private final PartyId partyId;
        private UUID leaderId;
        private final LinkedHashMap<UUID, PartyMember> members = new LinkedHashMap<>();

        private MutableParty(PartyId partyId, UUID leaderId) {
            this.partyId = partyId;
            this.leaderId = leaderId;
        }
    }
}
