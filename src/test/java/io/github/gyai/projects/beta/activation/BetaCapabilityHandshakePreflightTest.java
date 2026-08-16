package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track2.ElementRuntimeSnapshotPort;
import io.github.gyai.projects.beta.activation.track2.CombatElementsRuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track2.CompatibleElementsClientPort;
import io.github.gyai.projects.beta.activation.track2.StagingElementProfile;
import io.github.gyai.projects.beta.activation.track2.TrainingDummyElementBoundary;
import io.github.gyai.projects.beta.activation.track4.BetaCapabilityAdvertisementPublisher;
import io.github.gyai.projects.beta.activation.track4.BetaCapabilityAdvertisementTransport;
import io.github.gyai.projects.beta.activation.track4.BetaCapabilityLifecycleListener;
import io.github.gyai.projects.beta.activation.track4.BetaCapabilityLifecycleRegistrar;
import io.github.gyai.projects.beta.activation.track4.BetaChannelRegistrar;
import io.github.gyai.projects.beta.activation.track4.BetaStateTransport;
import io.github.gyai.projects.beta.activation.track4.BetaStagingPlayerLifecyclePort;
import io.github.gyai.projects.beta.activation.track4.ClientBetaProtocolRuntime;
import io.github.gyai.projects.beta.activation.track4.ClientBetaProtocolRuntimeModule;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolAdapter;
import io.github.gyai.projects.beta.activation.track4.ElementSnapshotProtocolPublisher;
import io.github.gyai.projects.beta.activation.track4.RunningCapabilityRegistry;
import io.github.gyai.projects.combat.element.ice.IceElementEngine;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.network.beta.BetaCapabilityAcknowledgement;
import io.github.gyai.projects.network.beta.BetaCapabilityDescriptor;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaCapabilityPolicy;
import io.github.gyai.projects.network.beta.BetaCapabilitySessionService;
import io.github.gyai.projects.network.beta.BetaChannels;
import io.github.gyai.projects.network.beta.BetaCommandAuthorization;
import io.github.gyai.projects.network.beta.BetaCommandRouter;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolVersion;
import io.github.gyai.projects.network.beta.BetaRateLimiter;
import io.github.gyai.projects.network.beta.ElementDisplaySnapshotCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class BetaCapabilityHandshakePreflightTest {
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    public static void main(String[] args) {
        defaultSafetyStartsNothing();
        liveHandshakeGatesElementStateUntilAcceptedAck();
        wrongCapabilityDoesNotSatisfyElements();
        ttlRenewalIsBoundedAndAckGated();
        diagnosticsArePureReads();
        duplicateLifecycleReusesSessionAndCleansUp();
        productionPlayerLifecycleClearsTemporaryProfiles();
        admissionAndBoundsFailClosed();
        partialStartupFailureCleansEveryBoundary();
        productionCompositionUsesLivePublisherWithoutPlayerRetention();
        System.out.println("BetaCapabilityHandshakePreflightTest passed");
    }

    private static void defaultSafetyStartsNothing() {
        MutableClock clock = new MutableClock(NOW);
        Harness harness = new Harness(BetaActivationPolicy.defaults(),
                FeatureFlagSnapshot.allDisabled(), clock,
                BetaCapabilityPolicy.wave3Defaults());
        BetaRuntime runtime = BetaRuntimeFactory.create(
                BetaActivationPolicy.defaults(), FeatureFlagSnapshot.allDisabled(),
                List.of(harness.module), Set.of("minecraft-plugin-messaging"),
                clock, (message, failure) -> { });
        BetaRuntimeHealthSnapshot health = runtime.start();
        assert health.status() == BetaRuntimeHealthStatus.DISABLED;
        assert harness.channels.registerCount == 0;
        assert harness.lifecycle.registerCount == 0;
        assert harness.advertisements.scheduleCount == 0;
        assert harness.advertisements.maintenanceScheduleCount == 0;
        assert harness.states.scheduleCount == 0;
        assert harness.advertisements.packets.isEmpty();
        assert harness.states.packets.isEmpty();
        assert harness.protocol.activeSessionCount() == 0;
        for (FeatureKey key : FeatureKey.values()) {
            assert !FeatureFlagSnapshot.allDisabled().isEnabled(key);
        }
        runtime.close();
    }

    private static void liveHandshakeGatesElementStateUntilAcceptedAck() {
        UUID player = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        BetaActivationPolicy policy = allowlistPolicy(Set.of(player), Set.of("beta_world"));
        Harness harness = new Harness(policy, enabledFlags(), clock,
                BetaCapabilityPolicy.wave3Defaults());
        harness.advertisements.online = List.of(player);
        harness.advertisements.worlds.put(player, "beta_world");
        harness.states.viewers = List.of(player);
        harness.start();
        CompatibleElementsClientPort acknowledgedElements = playerId ->
                harness.protocol.capabilitySnapshot(playerId)
                        .supports(BetaCapabilityId.ELEMENTS, 1);
        assert !acknowledgedElements.supportsElements(player)
                : "session absent must not satisfy Elements compatibility";
        assert harness.module.state() == BetaRuntimeModuleState.RUNNING;
        assert harness.channels.active.size() == 4;
        assert harness.lifecycle.registerCount == 1;
        assert harness.advertisements.scheduleCount == 1;
        assert harness.advertisements.maintenanceScheduleCount == 1;
        assert harness.states.scheduleCount == 1;

        harness.advertisements.runInitialCheck();
        assert harness.advertisements.packets.isEmpty()
                : "join/online presence without channel registration must not advertise";
        harness.states.runPublisher();
        assert harness.states.packets.isEmpty() : "state before advertisement must be zero";

        harness.advertisements.listen(player, BetaChannels.CAPABILITIES);
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.size() == 1;
        assert !acknowledgedElements.supportsElements(player)
                : "advertisement without ACK must not satisfy Elements compatibility";
        FakeAdvertisementTransport.Packet sent = harness.advertisements.packets.getFirst();
        assert sent.channel().equals(BetaChannels.CAPABILITIES);
        var decodedAdvertisement = new BetaProtocolCodec()
                .decodeAdvertisement(sent.bytes()).value();
        assert decodedAdvertisement.aggregateVersion() == BetaProtocolVersion.CURRENT;
        assert decodedAdvertisement.capabilities().equals(List.of(
                BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));

        harness.states.runPublisher();
        assert harness.states.packets.isEmpty() : "state before ack must be zero";
        byte[] acknowledgement = new BetaProtocolCodec().encode(
                new BetaCapabilityAcknowledgement(
                        BetaProtocolVersion.CURRENT,
                        decodedAdvertisement.sessionId(),
                        decodedAdvertisement.advertisementRevision(),
                        decodedAdvertisement.capabilities()));
        assert harness.advertisementPublisher.onAcknowledgement(player, acknowledgement)
                == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
        assert acknowledgedElements.supportsElements(player)
                : "accepted Elements v1 ACK must satisfy compatibility";

        harness.states.runPublisher();
        assert harness.states.packets.size() == 1 : "accepted ack must admit state";
        var envelope = new BetaProtocolCodec()
                .decodeMessage(harness.states.packets.getFirst().bytes()).value();
        assert envelope.capabilityId() == BetaCapabilityId.ELEMENTS;
        var fire = new ElementDisplaySnapshotCodec().decode(envelope.payload());
        assert fire.targetNetworkId() == 77;
        assert fire.stateRevision() == 1;
        assert fire.fireStacks() == 3;
        assert fire.fireThreshold() == 100;

        var diagnostics = harness.advertisementPublisher.diagnostics();
        assert diagnostics.advertisementSentCount() == 1;
        assert diagnostics.ackAcceptedCount() == 1;
        assert diagnostics.activeCapabilitySessionCount() == 1;
        harness.lifecycle.fireQuit(player);
        assert !acknowledgedElements.supportsElements(player)
                : "disconnect must revoke Elements compatibility";
        harness.stop();
    }

    private static void ttlRenewalIsBoundedAndAckGated() {
        UUID player = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        BetaCapabilityPolicy shortTtl = new BetaCapabilityPolicy(
                8, Duration.ofSeconds(5),
                List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));
        Harness harness = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), clock, shortTtl);
        harness.advertisements.online = List.of(player);
        harness.advertisements.worlds.put(player, "beta_world");
        harness.advertisements.listen(player, BetaChannels.CAPABILITIES);
        harness.states.viewers = List.of(player);
        harness.start();
        assert harness.module.start().success();
        assert harness.advertisements.maintenanceScheduleCount == 1;
        assert harness.advertisementPublisher.maintenanceRunning();
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        var current = decodeAdvertisement(harness.advertisements.packets.getLast().bytes());
        byte[] currentAck = acknowledgement(current);
        assert harness.advertisementPublisher.onAcknowledgement(player, currentAck)
                == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
        harness.states.runPublisher();
        assert harness.states.packets.size() == 1;

        int advertisements = harness.advertisements.packets.size();
        for (int run = 0; run < 100; run++) harness.advertisements.runMaintenance();
        assert harness.advertisements.packets.size() == advertisements;
        assert harness.advertisementPublisher.diagnostics().sessionRenewalCount() == 0;

        for (int renewal = 1; renewal <= 3; renewal++) {
            UUID oldSession = current.sessionId();
            long oldRevision = current.advertisementRevision();
            int statesBeforeRenewal = harness.states.packets.size();
            clock.advance(Duration.ofSeconds(6));
            harness.advertisements.runMaintenance();
            assert harness.advertisements.packets.size() == ++advertisements;
            current = decodeAdvertisement(harness.advertisements.packets.getLast().bytes());
            assert !current.sessionId().equals(oldSession);
            assert current.advertisementRevision() > oldRevision;
            assert harness.advertisementPublisher.onAcknowledgement(player, currentAck)
                    == BetaCapabilitySessionService.AcknowledgeStatus.UNKNOWN_SESSION;
            harness.states.runPublisher();
            assert harness.states.packets.size() == statesBeforeRenewal
                    : "state before renewed ack must be zero";
            currentAck = acknowledgement(current);
            assert harness.advertisementPublisher.onAcknowledgement(player, currentAck)
                    == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
            harness.states.runPublisher();
            assert harness.states.packets.size() == statesBeforeRenewal + 1;
        }
        var diagnostics = harness.advertisementPublisher.diagnostics();
        assert diagnostics.sessionRenewalCount() == 3;
        assert diagnostics.expiredSessionCount() == 3;
        assert diagnostics.maintenanceRunCount() == 103;
        assert diagnostics.maintenanceFailureCount() == 0;
        assert diagnostics.pendingAdvertisementCount() == 1;
        assert diagnostics.activeCapabilitySessionCount() == 1;
        harness.stop();
        assert harness.advertisements.maintenanceCancelled;

        Harness noAck = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), new MutableClock(NOW), shortTtl);
        noAck.advertisements.online = List.of(player);
        noAck.advertisements.worlds.put(player, "beta_world");
        noAck.advertisements.listen(player, BetaChannels.CAPABILITIES);
        noAck.start();
        noAck.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        byte[] first = noAck.advertisements.packets.getLast().bytes();
        noAck.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        assert java.util.Arrays.equals(first, noAck.advertisements.packets.getLast().bytes());
        int beforeExpiry = noAck.advertisements.packets.size();
        for (int run = 0; run < 100; run++) noAck.advertisements.runMaintenance();
        assert noAck.advertisements.packets.size() == beforeExpiry;
        noAck.advertisements.online = List.of();
        noAck.clock.advance(Duration.ofSeconds(6));
        noAck.advertisements.runMaintenance();
        assert noAck.advertisements.packets.size() == beforeExpiry;
        assert noAck.advertisementPublisher.diagnostics().sessionRenewalCount() == 0;
        assert noAck.advertisementPublisher.pendingCount() == 1;
        noAck.advertisements.failOnline = true;
        noAck.advertisements.runMaintenance();
        assert noAck.advertisements.packets.size() == beforeExpiry;
        assert noAck.advertisementPublisher.diagnostics().maintenanceFailureCount() == 1;
        noAck.advertisements.failOnline = false;
        noAck.advertisements.online = List.of(player);
        noAck.advertisements.runMaintenance();
        assert noAck.advertisements.packets.size() == beforeExpiry + 1;
        for (int run = 0; run < 100; run++) noAck.advertisements.runMaintenance();
        assert noAck.advertisements.packets.size() == beforeExpiry + 1;
        noAck.stop();
    }

    private static void wrongCapabilityDoesNotSatisfyElements() {
        UUID player = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        Harness harness = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), clock, BetaCapabilityPolicy.wave3Defaults());
        harness.advertisements.online = List.of(player);
        harness.advertisements.worlds.put(player, "beta_world");
        harness.advertisements.listen(player, BetaChannels.CAPABILITIES);
        harness.start();
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        var advertisement = decodeAdvertisement(
                harness.advertisements.packets.getLast().bytes());
        byte[] wrongAck = new BetaProtocolCodec().encode(
                new BetaCapabilityAcknowledgement(
                        BetaProtocolVersion.CURRENT,
                        advertisement.sessionId(), advertisement.advertisementRevision(),
                        List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.HUD))));
        assert harness.advertisementPublisher.onAcknowledgement(player, wrongAck)
                == BetaCapabilitySessionService.AcknowledgeStatus.UNAVAILABLE_CAPABILITY;
        assert !harness.protocol.capabilitySnapshot(player)
                .supports(BetaCapabilityId.ELEMENTS, 1);
        harness.stop();
    }

    private static void diagnosticsArePureReads() {
        UUID player = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        BetaCapabilityPolicy shortTtl = new BetaCapabilityPolicy(
                8, Duration.ofSeconds(5),
                List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));
        Harness harness = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), clock, shortTtl);
        harness.advertisements.online = List.of(player);
        harness.advertisements.worlds.put(player, "beta_world");
        harness.advertisements.listen(player, BetaChannels.CAPABILITIES);
        harness.start();
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        var first = decodeAdvertisement(harness.advertisements.packets.getLast().bytes());
        int packetCount = harness.advertisements.packets.size();
        clock.advance(Duration.ofSeconds(6));
        for (int read = 0; read < 100; read++) {
            var diagnostics = harness.advertisementPublisher.diagnostics();
            assert diagnostics.pendingAdvertisementCount() == 1;
            assert diagnostics.activeCapabilitySessionCount() == 1;
            assert diagnostics.maintenanceRunCount() == 0;
            assert harness.advertisementPublisher.pendingCount() == 1;
            assert harness.advertisementPublisher.diagnosticLines().size() == 6;
        }
        assert harness.advertisements.packets.size() == packetCount;
        var retained = decodeAdvertisement(harness.advertisements.packets.getLast().bytes());
        assert retained.sessionId().equals(first.sessionId());
        assert retained.advertisementRevision() == first.advertisementRevision();
        harness.stop();
    }

    private static void productionPlayerLifecycleClearsTemporaryProfiles() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        CombatElementsRuntimeModuleProvider track2 =
                new CombatElementsRuntimeModuleProvider(
                        new NoopElementBoundary(), Clock.fixed(NOW, ZoneOffset.UTC));
        Harness harness = new Harness(
                allowlistPolicy(Set.of(playerA, playerB), Set.of("beta_world")),
                enabledFlags(), new MutableClock(NOW), BetaCapabilityPolicy.wave3Defaults(),
                protocol -> BetaActivationWave1CompositionRoot.playerLifecycle(track2, protocol));
        track2.operatorCommands().execute(true, playerA, List.of("fire"));
        track2.operatorCommands().execute(true, playerB, List.of("ice"));
        harness.start();
        harness.lifecycle.fireQuit(playerA);
        assert track2.snapshots().playerProfile(playerA) == StagingElementProfile.NONE;
        assert track2.snapshots().playerProfile(playerB) == StagingElementProfile.ICE;
        harness.lifecycle.fireKick(playerB);
        assert track2.snapshots().playerProfile(playerB) == StagingElementProfile.NONE;
        track2.operatorCommands().execute(true, playerB, List.of("ice"));
        harness.lifecycle.fireJoin(playerB);
        assert track2.snapshots().playerProfile(playerB) == StagingElementProfile.NONE;
        track2.operatorCommands().execute(true, playerA, List.of("fire"));
        harness.stop();
        harness.stop();
        harness.advertisementPublisher.close();
        assert track2.snapshots().playerProfile(playerA) == StagingElementProfile.NONE;
    }

    private static void duplicateLifecycleReusesSessionAndCleansUp() {
        UUID player = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        Harness harness = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), clock, BetaCapabilityPolicy.wave3Defaults());
        harness.advertisements.worlds.put(player, "beta_world");
        harness.advertisements.listen(player, BetaChannels.CAPABILITIES);
        harness.states.viewers = List.of(player);
        harness.start();

        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        byte[] firstPacket = harness.advertisements.packets.getFirst().bytes();
        var first = new BetaProtocolCodec().decodeAdvertisement(firstPacket).value();
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.size() == 2;
        byte[] resentPacket = harness.advertisements.packets.getLast().bytes();
        assert java.util.Arrays.equals(firstPacket, resentPacket);
        var resent = new BetaProtocolCodec().decodeAdvertisement(resentPacket).value();
        assert resent.sessionId().equals(first.sessionId());
        assert resent.advertisementRevision() == first.advertisementRevision();
        assert harness.protocol.activeSessionCount() == 1;

        byte[] oldAck = new BetaProtocolCodec().encode(
                new BetaCapabilityAcknowledgement(1, first.sessionId(),
                        first.advertisementRevision(), first.capabilities()));
        assert harness.advertisementPublisher.onAcknowledgement(player, oldAck)
                == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.size() == 2
                : "duplicate registration after ack must not create or resend a session";

        harness.states.runPublisher();
        assert harness.elementAdapter.retainedRevisionCount() == 1;
        harness.lifecycle.fireQuit(player);
        assert harness.protocol.activeSessionCount() == 0;
        assert harness.advertisementPublisher.pendingCount() == 0;
        assert harness.elementAdapter.retainedRevisionCount() == 0;

        harness.lifecycle.fireJoin(player);
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        var reconnected = new BetaProtocolCodec().decodeAdvertisement(
                harness.advertisements.packets.getLast().bytes()).value();
        assert !reconnected.sessionId().equals(first.sessionId());
        assert reconnected.advertisementRevision() > first.advertisementRevision();
        assert harness.advertisementPublisher.onAcknowledgement(player, oldAck)
                == BetaCapabilitySessionService.AcknowledgeStatus.UNKNOWN_SESSION;
        harness.lifecycle.fireKick(player);
        assert harness.protocol.activeSessionCount() == 0;

        harness.lifecycle.fireJoin(player);
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        var expiring = new BetaProtocolCodec().decodeAdvertisement(
                harness.advertisements.packets.getLast().bytes()).value();
        byte[] expiredAck = new BetaProtocolCodec().encode(
                new BetaCapabilityAcknowledgement(1, expiring.sessionId(),
                        expiring.advertisementRevision(), expiring.capabilities()));
        clock.advance(Duration.ofMinutes(6));
        assert harness.advertisementPublisher.onAcknowledgement(player, expiredAck)
                == BetaCapabilitySessionService.AcknowledgeStatus.EXPIRED;

        int advertisementsBeforeStop = harness.advertisements.packets.size();
        harness.stop();
        harness.stop();
        assert harness.lifecycle.unregisterCount == 1;
        assert harness.channels.active.isEmpty();
        assert harness.states.cancelled;
        assert harness.protocol.activeSessionCount() == 0;
        harness.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.size() == advertisementsBeforeStop;
        harness.states.runPublisher();
        assert harness.states.packets.size() == 1;

        var diagnostics = harness.advertisementPublisher.diagnostics();
        assert diagnostics.advertisementResendCount() == 1;
        assert diagnostics.ackRejectedCount() >= 2;
    }

    private static void admissionAndBoundsFailClosed() {
        UUID allowed = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        MutableClock clock = new MutableClock(NOW);
        Harness harness = new Harness(
                allowlistPolicy(Set.of(allowed), Set.of("beta_world")),
                enabledFlags(), clock, BetaCapabilityPolicy.wave3Defaults());
        harness.start();
        harness.advertisements.worlds.put(denied, "beta_world");
        harness.advertisements.listen(denied, BetaChannels.CAPABILITIES);
        harness.lifecycle.fireRegister(denied, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.isEmpty();

        harness.advertisements.worlds.put(allowed, "wrong_world");
        harness.advertisements.listen(allowed, BetaChannels.CAPABILITIES);
        harness.lifecycle.fireRegister(allowed, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.isEmpty();
        harness.advertisements.worlds.put(allowed, "beta_world");
        harness.advertisements.channels.remove(allowed);
        harness.lifecycle.fireRegister(allowed, BetaChannels.CAPABILITIES);
        assert harness.advertisements.packets.isEmpty();
        harness.stop();

        Harness disabledProducer = new Harness(
                allowlistPolicy(Set.of(allowed), Set.of("beta_world")),
                enabledFlags(), new MutableClock(NOW),
                BetaCapabilityPolicy.wave3Defaults(), BetaRuntimeModuleState.DISABLED);
        disabledProducer.advertisements.worlds.put(allowed, "beta_world");
        disabledProducer.advertisements.listen(allowed, BetaChannels.CAPABILITIES);
        disabledProducer.start();
        disabledProducer.lifecycle.fireRegister(allowed, BetaChannels.CAPABILITIES);
        var empty = new BetaProtocolCodec().decodeAdvertisement(
                disabledProducer.advertisements.packets.getFirst().bytes()).value();
        assert empty.capabilities().isEmpty();
        assert empty.capabilities().stream().noneMatch(capability ->
                capability.id() == BetaCapabilityId.PARTY
                        || capability.id() == BetaCapabilityId.MOB_EDITOR_V2);
        disabledProducer.stop();

        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        BetaCapabilityPolicy boundedPolicy = new BetaCapabilityPolicy(
                2, Duration.ofMinutes(5),
                List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.ELEMENTS)));
        Harness bounded = new Harness(
                allowlistPolicy(Set.of(first, second, third), Set.of("beta_world")),
                enabledFlags(), new MutableClock(NOW), boundedPolicy);
        bounded.start();
        for (UUID player : List.of(first, second, third)) {
            bounded.advertisements.worlds.put(player, "beta_world");
            bounded.advertisements.listen(player, BetaChannels.CAPABILITIES);
            bounded.lifecycle.fireRegister(player, BetaChannels.CAPABILITIES);
        }
        assert bounded.advertisementPublisher.pendingCount() <= 2;
        assert bounded.protocol.activeSessionCount() <= 2;
        bounded.stop();
    }

    private static void partialStartupFailureCleansEveryBoundary() {
        UUID player = UUID.randomUUID();
        Harness harness = new Harness(
                allowlistPolicy(Set.of(player), Set.of("beta_world")),
                enabledFlags(), new MutableClock(NOW),
                BetaCapabilityPolicy.wave3Defaults());
        harness.states.failSchedule = true;
        BetaRuntimeModuleContext context = new BetaRuntimeModuleContext(
                harness.policy, harness.flags, Set.of("minecraft-plugin-messaging"),
                harness.advertisements.clock, true);
        assert harness.module.prepare(context).success();
        assert !harness.module.start().success();
        assert harness.module.state() == BetaRuntimeModuleState.FAILED;
        assert harness.channels.active.isEmpty();
        assert harness.lifecycle.unregisterCount == 1;
        assert harness.advertisements.initialCancelled;
        assert harness.advertisements.maintenanceCancelled;
        assert !harness.advertisementPublisher.running();
        assert harness.protocol.closed();
        assert harness.protocol.activeSessionCount() == 0;
    }

    private static void productionCompositionUsesLivePublisherWithoutPlayerRetention() {
        String composition = read("src/main/java/io/github/gyai/projects/beta/activation/"
                + "BetaActivationWave1CompositionRoot.java");
        assert composition.contains("new BukkitBetaCapabilityAdvertisementTransport(plugin)");
        assert composition.contains("new BukkitBetaCapabilityLifecycleRegistrar(plugin)");
        assert composition.contains("value.onAcknowledgement(player.getUniqueId(), message)");
        assert composition.contains("playerLifecycle(track2, protocol)");
        assert composition.contains("track2.playerDisconnected(playerId)");
        assert composition.contains("track2::clearPlayerProfiles");
        String publisher = read("src/main/java/io/github/gyai/projects/beta/activation/track4/"
                + "BetaCapabilityAdvertisementPublisher.java");
        String transport = read("src/main/java/io/github/gyai/projects/beta/activation/track4/"
                + "BukkitBetaCapabilityAdvertisementTransport.java");
        String registrar = read("src/main/java/io/github/gyai/projects/beta/activation/track4/"
                + "BukkitBetaCapabilityLifecycleRegistrar.java");
        assert !publisher.contains("Player player");
        assert !publisher.contains("Map<UUID, Player");
        assert publisher.contains("scheduleRepeating(");
        assert !transport.contains("private final Player");
        assert !registrar.contains("private final Player");
    }

    private static BetaActivationPolicy allowlistPolicy(
            Set<UUID> players,
            Set<String> worlds
    ) {
        return new BetaActivationPolicy(BetaActivationAudience.ALLOWLIST,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.READ_ONLY, players, worlds, true, true);
    }

    private static FeatureFlagSnapshot enabledFlags() {
        return FeatureFlagSnapshot.of(Map.of(
                FeatureKey.CLIENT_BETA_UI, true,
                FeatureKey.FIRE_SYSTEM, true));
    }

    private static io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement
            decodeAdvertisement(byte[] packet) {
        return new BetaProtocolCodec().decodeAdvertisement(packet).value();
    }

    private static byte[] acknowledgement(
            io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement advertisement
    ) {
        return new BetaProtocolCodec().encode(new BetaCapabilityAcknowledgement(
                BetaProtocolVersion.CURRENT,
                advertisement.sessionId(),
                advertisement.advertisementRevision(),
                advertisement.capabilities()));
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class Harness {
        private final BetaActivationPolicy policy;
        private final FeatureFlagSnapshot flags;
        private final StateModule elements;
        private final RecordingChannels channels = new RecordingChannels();
        private final FakeAdvertisementTransport advertisements =
                new FakeAdvertisementTransport();
        private final FakeLifecycleRegistrar lifecycle = new FakeLifecycleRegistrar();
        private final FakeStateTransport states = new FakeStateTransport();
        private final ClientBetaProtocolRuntime protocol;
        private final ElementSnapshotProtocolAdapter elementAdapter;
        private final ElementSnapshotProtocolPublisher elementPublisher;
        private final BetaCapabilityAdvertisementPublisher advertisementPublisher;
        private final ClientBetaProtocolRuntimeModule module;
        private final MutableClock clock;

        private Harness(
                BetaActivationPolicy policy,
                FeatureFlagSnapshot flags,
                MutableClock clock,
                BetaCapabilityPolicy capabilityPolicy
        ) {
            this(policy, flags, clock, capabilityPolicy,
                    BetaRuntimeModuleState.RUNNING, null);
        }

        private Harness(
                BetaActivationPolicy policy,
                FeatureFlagSnapshot flags,
                MutableClock clock,
                BetaCapabilityPolicy capabilityPolicy,
                BetaRuntimeModuleState elementState
        ) {
            this(policy, flags, clock, capabilityPolicy, elementState, null);
        }

        private Harness(
                BetaActivationPolicy policy,
                FeatureFlagSnapshot flags,
                MutableClock clock,
                BetaCapabilityPolicy capabilityPolicy,
                Function<ClientBetaProtocolRuntime, BetaStagingPlayerLifecyclePort>
                        playerLifecycleFactory
        ) {
            this(policy, flags, clock, capabilityPolicy,
                    BetaRuntimeModuleState.RUNNING, playerLifecycleFactory);
        }

        private Harness(
                BetaActivationPolicy policy,
                FeatureFlagSnapshot flags,
                MutableClock clock,
                BetaCapabilityPolicy capabilityPolicy,
                BetaRuntimeModuleState elementState,
                Function<ClientBetaProtocolRuntime, BetaStagingPlayerLifecyclePort>
                        playerLifecycleFactory
        ) {
            this.policy = policy;
            this.flags = flags;
            this.clock = clock;
            elements = new StateModule(BetaRuntimeModuleId.COMBAT_ELEMENTS, elementState);
            EnumMap<BetaCapabilityId, BetaRuntimeModule> producers =
                    new EnumMap<>(BetaCapabilityId.class);
            producers.put(BetaCapabilityId.ELEMENTS, elements);
            protocol = new ClientBetaProtocolRuntime(channels,
                    new BetaCapabilitySessionService(capabilityPolicy, clock),
                    new BetaCommandRouter(new BetaRateLimiter(16, clock),
                            (context, command) -> BetaCommandAuthorization.Decision.allow(), 16),
                    new RunningCapabilityRegistry(producers));
            ClientBetaProtocolRuntimeModule[] holder =
                    new ClientBetaProtocolRuntimeModule[1];
            UUID target = UUID.randomUUID();
            states.target = target;
            elementAdapter = new ElementSnapshotProtocolAdapter(
                    new FixedSnapshots(target, snapshot(target)),
                    () -> holder[0] == null ? BetaRuntimeModuleState.NOT_INSTALLED
                            : holder[0].state(),
                    elements::state, protocol::capabilitySnapshot, ignored -> true);
            elementPublisher = new ElementSnapshotProtocolPublisher(
                    elementAdapter, states, protocol::capabilitySnapshot, clock);
            BetaStagingPlayerLifecyclePort playerLifecycle = playerLifecycleFactory == null
                    ? null : playerLifecycleFactory.apply(protocol);
            advertisementPublisher = playerLifecycle == null
                    ? new BetaCapabilityAdvertisementPublisher(
                    protocol, advertisements, lifecycle, capabilityPolicy, clock,
                    () -> holder[0] == null ? BetaRuntimeModuleState.NOT_INSTALLED
                            : holder[0].state())
                    : new BetaCapabilityAdvertisementPublisher(
                    protocol, advertisements, lifecycle, capabilityPolicy, clock,
                    () -> holder[0] == null ? BetaRuntimeModuleState.NOT_INSTALLED
                            : holder[0].state(), playerLifecycle);
            protocol.addViewerStateLifecycle(new ClientBetaProtocolRuntime.ViewerStateLifecycle() {
                @Override public void clear(UUID playerId) {
                    advertisementPublisher.clearPlayerState(playerId);
                }
                @Override public void clearAll() {
                    advertisementPublisher.clearAllState();
                }
            });
            protocol.addViewerStateLifecycle(new ClientBetaProtocolRuntime.ViewerStateLifecycle() {
                @Override public void clear(UUID playerId) {
                    elementPublisher.clearViewer(playerId);
                }
                @Override public void clearAll() {
                    elementPublisher.clearViewerState();
                }
            });
            module = new ClientBetaProtocolRuntimeModule(
                    protocol, advertisementPublisher, elementPublisher);
            holder[0] = module;
        }

        private void start() {
            BetaRuntimeModuleContext context = new BetaRuntimeModuleContext(
                    policy, flags, Set.of("minecraft-plugin-messaging"),
                    advertisements.clock, true);
            assert module.prepare(context).success();
            assert module.start().success();
        }

        private void stop() {
            module.stop();
        }
    }

    private static ElementRuntimeSnapshotPort.TargetSnapshot snapshot(UUID target) {
        return new ElementRuntimeSnapshotPort.TargetSnapshot(
                target, 77, 1, 3, 25, 100, .25,
                true, 500, 1, NOW.plusSeconds(30).toEpochMilli(),
                0, IceElementEngine.Stage.NONE, false, 0,
                NOW.toEpochMilli(), 1);
    }

    private static final class FixedSnapshots implements ElementRuntimeSnapshotPort {
        private final UUID target;
        private final TargetSnapshot snapshot;

        private FixedSnapshots(UUID target, TargetSnapshot snapshot) {
            this.target = target;
            this.snapshot = snapshot;
        }

        @Override public Optional<TargetSnapshot> target(UUID id) {
            return target.equals(id) ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public Map<UUID, TargetSnapshot> targets() {
            return Map.of(target, snapshot);
        }
        @Override public StagingElementProfile playerProfile(UUID playerId) {
            return StagingElementProfile.FIRE;
        }
    }

    private static final class FakeAdvertisementTransport
            implements BetaCapabilityAdvertisementTransport {
        private final MutableClock clock = new MutableClock(NOW);
        private final Map<UUID, Set<String>> channels = new HashMap<>();
        private final Map<UUID, String> worlds = new HashMap<>();
        private final List<Packet> packets = new ArrayList<>();
        private List<UUID> online = List.of();
        private Runnable initialCheck;
        private Runnable maintenance;
        private boolean initialCancelled;
        private boolean maintenanceCancelled;
        private boolean failOnline;
        private int scheduleCount;
        private int maintenanceScheduleCount;

        @Override public List<UUID> onlinePlayers() { return online; }
        @Override public boolean online(UUID playerId) {
            if (failOnline) throw new IllegalStateException("online check failed");
            return online.contains(playerId);
        }
        @Override public Set<String> listeningChannels(UUID playerId) {
            return channels.getOrDefault(playerId, Set.of());
        }
        @Override public String worldName(UUID playerId) { return worlds.get(playerId); }
        @Override public void send(UUID playerId, String channel, byte[] packet) {
            packets.add(new Packet(playerId, channel, packet.clone()));
        }
        @Override public Cancellable scheduleMainThread(Runnable task) {
            scheduleCount++;
            initialCheck = task;
            return new Cancellable() {
                @Override public void cancel() { initialCancelled = true; }
                @Override public boolean cancelled() { return initialCancelled; }
            };
        }
        @Override public Cancellable scheduleRepeating(Runnable task, long periodMillis) {
            assert periodMillis == BetaCapabilityAdvertisementPublisher
                    .MAINTENANCE_PERIOD_MILLIS;
            maintenanceScheduleCount++;
            maintenance = task;
            return new Cancellable() {
                @Override public void cancel() { maintenanceCancelled = true; }
                @Override public boolean cancelled() { return maintenanceCancelled; }
            };
        }
        private void listen(UUID player, String channel) {
            channels.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(channel);
        }
        private void runInitialCheck() {
            if (!initialCancelled && initialCheck != null) {
                Runnable task = initialCheck;
                initialCheck = null;
                task.run();
            }
        }
        private void runMaintenance() {
            if (!maintenanceCancelled && maintenance != null) maintenance.run();
        }
        private record Packet(UUID playerId, String channel, byte[] bytes) { }
    }

    private static final class FakeLifecycleRegistrar
            implements BetaCapabilityLifecycleRegistrar {
        private BetaCapabilityLifecycleListener listener;
        private int registerCount;
        private int unregisterCount;

        @Override public void register(BetaCapabilityLifecycleListener value) {
            if (listener != null) return;
            listener = value;
            registerCount++;
        }
        @Override public void unregister() {
            if (listener == null) return;
            listener = null;
            unregisterCount++;
        }
        private void fireJoin(UUID player) {
            if (listener != null) listener.onJoin(player);
        }
        private void fireRegister(UUID player, String channel) {
            if (listener != null) listener.onChannelRegistered(player, channel);
        }
        private void fireQuit(UUID player) {
            if (listener != null) listener.onQuit(player);
        }
        private void fireKick(UUID player) {
            if (listener != null) listener.onKick(player);
        }
    }

    private static final class RecordingChannels implements BetaChannelRegistrar {
        private final Set<String> active = new LinkedHashSet<>();
        private int registerCount;
        @Override public void register(String channel, Direction direction) {
            active.add(channel + ":" + direction);
            registerCount++;
        }
        @Override public void unregister(String channel, Direction direction) {
            active.remove(channel + ":" + direction);
        }
    }

    private static final class FakeStateTransport implements BetaStateTransport {
        private final List<Packet> packets = new ArrayList<>();
        private List<UUID> viewers = List.of();
        private UUID target;
        private Runnable publisher;
        private boolean cancelled;
        private boolean failSchedule;
        private int scheduleCount;
        @Override public List<UUID> viewers() { return viewers; }
        @Override public UUID visibleTarget(UUID viewerId) { return target; }
        @Override public void send(UUID viewerId, String channel, byte[] packet) {
            packets.add(new Packet(viewerId, channel, packet.clone()));
        }
        @Override public Cancellable schedule(Runnable task, long periodMillis) {
            scheduleCount++;
            if (failSchedule) throw new IllegalStateException("scheduled failure");
            publisher = task;
            return new Cancellable() {
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
        private void runPublisher() {
            if (!cancelled && publisher != null) publisher.run();
        }
        private record Packet(UUID playerId, String channel, byte[] bytes) { }
    }

    private static final class StateModule implements BetaRuntimeModule {
        private final BetaRuntimeModuleId id;
        private final BetaRuntimeModuleState state;
        private StateModule(BetaRuntimeModuleId id, BetaRuntimeModuleState state) {
            this.id = id;
            this.state = state;
        }
        @Override public BetaRuntimeModuleId id() { return id; }
        @Override public Set<BetaRuntimeModuleId> dependencies() { return Set.of(); }
        @Override public BetaRuntimeModuleResult prepare(BetaRuntimeModuleContext context) {
            return BetaRuntimeModuleResult.ready();
        }
        @Override public BetaRuntimeModuleResult start() { return BetaRuntimeModuleResult.running(); }
        @Override public BetaRuntimeModuleResult stop() { return BetaRuntimeModuleResult.stopped(); }
        @Override public BetaRuntimeModuleState state() { return state; }
    }

    private static final class NoopElementBoundary implements TrainingDummyElementBoundary {
        @Override public boolean isLiveTrainingDummy(UUID targetId) { return false; }
        @Override public int targetRuntimeId(UUID targetId) { return -1; }
        @Override public List<UUID> nearbyTrainingDummies(
                UUID centerId, double radius, int limit
        ) { return List.of(); }
        @Override public void applySecondaryDamage(SecondaryDamage damage) { }
        @Override public void publishVisual(VisualEvent event) { }
        @Override public Cancellable scheduleCleanup(Runnable task, long periodMillis) {
            return new Cancellable() {
                private boolean cancelled;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean cancelled() { return cancelled; }
            };
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
