package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.network.beta.BetaCapabilitySnapshot;
import io.github.gyai.projects.network.beta.BetaChannels;
import io.github.gyai.projects.network.beta.BetaMessageEnvelope;
import io.github.gyai.projects.network.beta.BetaMessageKind;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolVersion;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshotCodec;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Lifecycle-owned publisher for newer, negotiated projects:elements snapshots. */
public final class ElementSnapshotProtocolPublisher implements AutoCloseable {
    private static final long PERIOD_MILLIS = 100L;
    private final ElementSnapshotProtocolAdapter adapter;
    private final BetaStateTransport transport;
    private final ElementSnapshotProtocolAdapter.CapabilityPort capabilities;
    private final Clock clock;
    private final BetaProtocolCodec protocolCodec = new BetaProtocolCodec();
    private final ElementDisplaySnapshotCodec payloadCodec = new ElementDisplaySnapshotCodec();
    private BetaStateTransport.Cancellable task;
    private boolean running;

    public ElementSnapshotProtocolPublisher(
            ElementSnapshotProtocolAdapter adapter,
            BetaStateTransport transport,
            ElementSnapshotProtocolAdapter.CapabilityPort capabilities,
            Clock clock
    ) {
        this.adapter = java.util.Objects.requireNonNull(adapter);
        this.transport = java.util.Objects.requireNonNull(transport);
        this.capabilities = java.util.Objects.requireNonNull(capabilities);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized void start() {
        if (running) return;
        task = java.util.Objects.requireNonNull(
                transport.schedule(this::publishOnce, PERIOD_MILLIS));
        running = true;
    }

    public void publishOnce() {
        List<UUID> viewers;
        synchronized (this) { if (!running) return; }
        try { viewers = transport.viewers(); }
        catch (RuntimeException ignored) { return; }
        if (viewers == null) return;
        adapter.retainViewers(viewers);
        int examined = 0;
        for (UUID viewer : viewers) {
            if (viewer == null || examined++ >= ElementSnapshotProtocolAdapter.MAXIMUM_VIEWERS) break;
            UUID target;
            try { target = transport.visibleTarget(viewer); }
            catch (RuntimeException ignored) { continue; }
            if (target == null) { adapter.clearViewer(viewer); continue; }
            try {
                var next = adapter.next(viewer, target, clock.millis());
                if (next.isEmpty()) continue;
                BetaCapabilitySnapshot session = capabilities.snapshot(viewer);
                if (session == null || session.oldClient() || session.sessionId() == null) continue;
                byte[] packet = protocolCodec.encode(new BetaMessageEnvelope(
                        BetaProtocolVersion.CURRENT, BetaMessageKind.STATE,
                        io.github.gyai.projects.network.beta.BetaCapabilityId.ELEMENTS,
                        1, session.sessionId(), payloadCodec.encode(next.orElseThrow())));
                transport.send(viewer, BetaChannels.STATE, packet);
            } catch (RuntimeException ignored) {
                // One viewer cannot disrupt the publisher or gameplay.
            }
        }
    }

    public synchronized boolean running() { return running; }

    @Override public synchronized void close() {
        if (!running && task == null) { adapter.clear(); return; }
        running = false;
        if (task != null) try { task.cancel(); } catch (RuntimeException ignored) { }
        task = null;
        adapter.clear();
    }
}
