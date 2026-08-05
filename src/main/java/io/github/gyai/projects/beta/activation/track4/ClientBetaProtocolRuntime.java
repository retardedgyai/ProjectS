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
    private final List<ViewerStateLifecycle> viewerStateLifecycles = new ArrayList<>();
    private boolean running;
    private boolean closed;

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
        if (closed) throw new IllegalStateException("protocol runtime is closed");
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

    public synchronized boolean running() { return running; }

    public synchronized boolean closed() { return closed; }

    public synchronized int activeSessionCount() {
        return sessions.activeSessionCount();
    }

    public synchronized int retainedSessionCount() {
        return sessions.retainedSessionCount();
    }

    public synchronized void clearAllConnectionState() {
        sessions.clear();
        for (int index = viewerStateLifecycles.size() - 1; index >= 0; index--) {
            try { viewerStateLifecycles.get(index).clearAll(); }
            catch (RuntimeException ignored) { }
        }
    }

    public synchronized void addViewerStateLifecycle(ViewerStateLifecycle lifecycle) {
        if (closed) throw new IllegalStateException("protocol runtime is closed");
        if (lifecycle == null || viewerStateLifecycles.contains(lifecycle)) return;
        if (viewerStateLifecycles.size() >= 8) {
            throw new IllegalStateException("too many viewer state lifecycles");
        }
        viewerStateLifecycles.add(lifecycle);
    }

    public synchronized void disconnect(UUID playerId) {
        if (playerId == null) return;
        sessions.clear(playerId);
        clearViewerState(playerId);
    }

    public synchronized void reconnect(UUID playerId) {
        if (playerId == null) return;
        sessions.reconnect(playerId);
        clearViewerState(playerId);
    }

    public synchronized BetaCapabilitySnapshot capabilitySnapshot(UUID playerId) {
        return running ? sessions.snapshot(playerId)
                : BetaCapabilitySnapshot.oldClient(playerId);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        running = false;
        unregisterAll();
        sessions.close();
        commands.close();
        for (int index = viewerStateLifecycles.size() - 1; index >= 0; index--) {
            try { viewerStateLifecycles.get(index).clearAll(); }
            catch (RuntimeException ignored) { }
        }
        viewerStateLifecycles.clear();
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

    private void clearViewerState(UUID playerId) {
        for (int index = viewerStateLifecycles.size() - 1; index >= 0; index--) {
            try { viewerStateLifecycles.get(index).clear(playerId); }
            catch (RuntimeException ignored) { }
        }
    }

    public interface ViewerStateLifecycle {
        void clear(UUID playerId);

        void clearAll();
    }

    private record Registration(String channel, BetaChannelRegistrar.Direction direction) { }
}
