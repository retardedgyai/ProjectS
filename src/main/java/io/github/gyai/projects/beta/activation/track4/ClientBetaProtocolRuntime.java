package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.network.beta.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Four-channel lifecycle plus server-authoritative command admission. */
public final class ClientBetaProtocolRuntime implements AutoCloseable {
    private final BetaChannelRegistrar channels;
    private final BetaCapabilitySessionService sessions;
    private final BetaCommandRouter commands;
    private final BetaCapabilityAvailability availability;
    private final List<Registration> registrations = new ArrayList<>();
    private boolean running;

    public ClientBetaProtocolRuntime(BetaChannelRegistrar channels,
                                     BetaCapabilitySessionService sessions,
                                     BetaCommandRouter commands,
                                     BetaCapabilityAvailability availability) {
        this.channels = java.util.Objects.requireNonNull(channels);
        this.sessions = java.util.Objects.requireNonNull(sessions);
        this.commands = java.util.Objects.requireNonNull(commands);
        this.availability = java.util.Objects.requireNonNull(availability);
    }

    public synchronized void start() {
        if (running) return;
        try {
            register(BetaChannels.CAPABILITIES, BetaChannelRegistrar.Direction.OUTGOING);
            register(BetaChannels.ACKNOWLEDGEMENT, BetaChannelRegistrar.Direction.INCOMING);
            register(BetaChannels.STATE, BetaChannelRegistrar.Direction.OUTGOING);
            register(BetaChannels.COMMAND, BetaChannelRegistrar.Direction.INCOMING);
            running = true;
        } catch (RuntimeException failure) {
            unregisterAll();
            throw failure;
        }
    }

    public synchronized Optional<BetaCapabilityAdvertisement> advertise(UUID playerId,
                                                                         boolean featureEnabled) {
        return running ? sessions.advertise(playerId, featureEnabled, availability) : Optional.empty();
    }

    public synchronized BetaCapabilitySessionService.AcknowledgeStatus acknowledge(
            UUID playerId, BetaCapabilityAcknowledgement value) {
        if (!running) return BetaCapabilitySessionService.AcknowledgeStatus.FEATURE_DISABLED;
        return sessions.acknowledge(playerId, value, availability);
    }

    public synchronized BetaCommandResult route(BetaCommandContext context,
                                                BetaCommandEnvelope envelope,
                                                BetaCommandDecoder decoder,
                                                BetaCommandPort destination) {
        if (!running) return new BetaCommandResult(BetaCommandResult.Status.FEATURE_DISABLED,
                envelope.idempotencyRequestId(), "protocol module is stopped", true);
        return commands.route(context, envelope, decoder, destination);
    }

    public synchronized int registrationCount() { return registrations.size(); }

    @Override public synchronized void close() {
        unregisterAll();
        sessions.close();
        commands.close();
        running = false;
    }

    private void register(String channel, BetaChannelRegistrar.Direction direction) {
        channels.register(channel, direction);
        registrations.add(new Registration(channel, direction));
    }

    private void unregisterAll() {
        for (int i = registrations.size() - 1; i >= 0; i--) {
            Registration value = registrations.get(i);
            try { channels.unregister(value.channel(), value.direction()); }
            catch (RuntimeException ignored) { }
        }
        registrations.clear();
    }

    private record Registration(String channel, BetaChannelRegistrar.Direction direction) { }
}
