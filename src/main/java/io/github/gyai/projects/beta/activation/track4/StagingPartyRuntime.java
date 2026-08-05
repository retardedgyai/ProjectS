package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.party.PartyCommandResult;
import io.github.gyai.projects.party.PartyHealthSummary;
import io.github.gyai.projects.party.PartyId;
import io.github.gyai.projects.party.PartyService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StagingPartyRuntime implements AutoCloseable {
    private final PartyService parties;
    private final BetaActivationPolicy policy;
    private boolean closed;

    public StagingPartyRuntime(PartyService parties, BetaActivationPolicy policy) {
        this.parties = java.util.Objects.requireNonNull(parties);
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    public synchronized PartyCommandResult create(
            Context context, PartyId partyId, UUID leaderId) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.create(partyId, leaderId)
                : rejected(admission.reason());
    }

    public synchronized PartyCommandResult invite(
            Context context, UUID inviteId, PartyId partyId,
            UUID inviterId, UUID inviteeId) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.invite(
                inviteId, partyId, inviterId, inviteeId) : rejected(admission.reason());
    }

    public synchronized PartyCommandResult accept(Context context, UUID inviteId) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.accept(inviteId) : rejected(admission.reason());
    }

    public synchronized PartyCommandResult leave(Context context) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.leave(context.playerId())
                : rejected(admission.reason());
    }

    public synchronized PartyCommandResult onJoin(Context context) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.reconnect(context.playerId())
                : rejected(admission.reason());
    }

    public synchronized PartyCommandResult onQuit(Context context) {
        StagingAdmission.Decision admission = admit(context);
        return admission.allowed() ? parties.disconnect(context.playerId())
                : rejected(admission.reason());
    }

    public synchronized List<UUID> chatRecipients(
            Context context, PartyId partyId) {
        return admit(context).allowed()
                ? parties.chatRecipients(partyId, context.playerId()) : List.of();
    }

    public synchronized Optional<PartyHealthSummary> healthSummary(
            Context context,
            PartyId partyId,
            List<PartyHealthSummary.MemberHealth> health) {
        if (!admit(context).allowed()) return Optional.empty();
        return Optional.of(parties.healthSummary(partyId, health));
    }

    public synchronized int partyCount() {
        return parties.partyCount();
    }

    public synchronized int inviteRecordCount() {
        return parties.inviteRecordCount();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        parties.close();
    }

    private StagingAdmission.Decision admit(Context context) {
        if (closed || context == null) {
            return new StagingAdmission.Decision(false, "party-runtime-closed");
        }
        return StagingAdmission.read(policy, context.playerId(), context.worldName(),
                context.projectsDev(), context.compatibleClient());
    }

    private static PartyCommandResult rejected(String reason) {
        return new PartyCommandResult(PartyCommandResult.Status.REJECTED,
                Optional.empty(), Optional.empty(), reason, false);
    }

    public record Context(UUID playerId, String worldName,
                          boolean projectsDev, boolean compatibleClient) {
    }
}
