package io.github.gyai.projects.network.beta;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BetaProtocolFoundationTest {
    private BetaProtocolFoundationTest() {
    }

    public static void main(String[] args) {
        constantsAndCodecRoundTrip();
        malformedPacketsFailClosed();
        snapshotsAreBoundedAndFinite();
        capabilityLifecycleIsEphemeralAndExact();
        commandBoundaryRevalidatesAndDeduplicates();
        compatibilityMatrixHasSafeFallbacks();
        protocolApiIsPureJava();
    }

    private static void constantsAndCodecRoundTrip() {
        assert BetaProtocolVersion.CURRENT == 1;
        assert BetaChannels.CAPABILITIES.equals("projects:beta_caps_v1");
        assert BetaChannels.ACKNOWLEDGEMENT.equals("projects:beta_ack_v1");
        assert BetaChannels.STATE.equals("projects:beta_state_v1");
        assert BetaChannels.COMMAND.equals("projects:beta_command_v1");
        assert Arrays.stream(BetaCapabilityId.values()).map(BetaCapabilityId::id).toList()
                .equals(List.of("projects:hud", "projects:party", "projects:elements",
                        "projects:equipment", "projects:crafting", "projects:enhancement",
                        "projects:mob-editor-v2"));
        BetaProtocolCodec codec = new BetaProtocolCodec();
        UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001");
        BetaCapabilityAdvertisement advertisement = new BetaCapabilityAdvertisement(
                1, session, 9, List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.HUD)));
        assert codec.decodeAdvertisement(codec.encode(advertisement)).value().equals(advertisement);
        BetaCapabilityAcknowledgement acknowledgement = new BetaCapabilityAcknowledgement(
                1, session, 9, advertisement.capabilities());
        assert codec.decodeAcknowledgement(codec.encode(acknowledgement)).value()
                .equals(acknowledgement);
        BetaMessageEnvelope state = new BetaMessageEnvelope(
                1, BetaMessageKind.STATE, BetaCapabilityId.HUD, 1, session,
                new byte[] {1, 2, 3});
        BetaMessageEnvelope decodedState = codec.decodeMessage(codec.encode(state)).value();
        assert decodedState.aggregateVersion() == state.aggregateVersion();
        assert decodedState.kind() == state.kind();
        assert decodedState.capabilityId() == state.capabilityId();
        assert Arrays.equals(decodedState.payload(), state.payload());
        BetaCommandEnvelope command = new BetaCommandEnvelope(
                new BetaMessageEnvelope(1, BetaMessageKind.COMMAND,
                        BetaCapabilityId.CRAFTING, 1, session, new byte[] {4, 5}),
                7, 11, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        BetaCommandEnvelope decodedCommand = codec.decodeCommand(codec.encode(command)).value();
        assert decodedCommand.playerSessionRevision() == 7;
        assert decodedCommand.targetContentRevision() == 11;
        assert decodedCommand.idempotencyRequestId().equals(command.idempotencyRequestId());
        assert Arrays.equals(decodedCommand.message().payload(), command.message().payload());
        byte[] external = state.payload();
        external[0] = 99;
        assert state.payload()[0] == 1;
    }

    private static void malformedPacketsFailClosed() {
        BetaProtocolCodec codec = new BetaProtocolCodec();
        BetaMessageEnvelope state = new BetaMessageEnvelope(
                1, BetaMessageKind.STATE, BetaCapabilityId.HUD, 1, UUID.randomUUID(), new byte[0]);
        byte[] valid = codec.encode(state);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assert codec.decodeMessage(trailing).status() == BetaProtocolDecodeResult.Status.MALFORMED;
        byte[] wrongVersion = valid.clone();
        wrongVersion[0] = 2;
        assert codec.decodeMessage(wrongVersion).status()
                == BetaProtocolDecodeResult.Status.UNSUPPORTED_VERSION;
        byte[] unknownOpcode = valid.clone();
        unknownOpcode[1] = 99;
        assert codec.decodeMessage(unknownOpcode).status()
                == BetaProtocolDecodeResult.Status.UNKNOWN_OPCODE;
        byte[] unknownCapability = valid.clone();
        byte[] replacement = "unknowns:hud".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(replacement, 0, unknownCapability, 4, replacement.length);
        assert codec.decodeMessage(unknownCapability).status()
                == BetaProtocolDecodeResult.Status.UNKNOWN_CAPABILITY;
        byte[] invalidUtf8 = valid.clone();
        invalidUtf8[4] = (byte) 0xC3;
        invalidUtf8[5] = 0x28;
        assert codec.decodeMessage(invalidUtf8).status()
                == BetaProtocolDecodeResult.Status.MALFORMED;
        byte[] negativeLength = valid.clone();
        int lengthOffset = negativeLength.length - 4;
        Arrays.fill(negativeLength, lengthOffset, lengthOffset + 4, (byte) 0xFF);
        assert codec.decodeMessage(negativeLength).status()
                == BetaProtocolDecodeResult.Status.MALFORMED;
        byte[] oversized = new byte[BetaProtocolLimits.DEFAULTS.packetBytes() + 1];
        assert codec.decodeMessage(oversized).status() == BetaProtocolDecodeResult.Status.OVERSIZED;
        BetaCapabilityAdvertisement duplicate = null;
        try {
            duplicate = new BetaCapabilityAdvertisement(1, UUID.randomUUID(), 1,
                    List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.HUD),
                            BetaCapabilityDescriptor.v1(BetaCapabilityId.HUD)));
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains("Duplicate");
        }
        assert duplicate == null;
        for (int size = 0; size < 256; size++) {
            byte[] fuzz = new byte[size];
            new java.util.Random(size).nextBytes(fuzz);
            BetaProtocolDecodeResult<BetaMessageEnvelope> result = codec.decodeMessage(fuzz);
            assert result != null;
        }
    }

    private static void snapshotsAreBoundedAndFinite() {
        HudDisplaySnapshot hud = new HudDisplaySnapshot(
                12, 345, "projects:warrior", Map.of("projects:spirit", 42.0),
                HudDisplaySnapshot.EndgameUnlockState.UNKNOWN);
        assert hud.resourceSummaries().get("projects:spirit") == 42.0;
        assertThrows(() -> new HudDisplaySnapshot(1, 1, "projects:warrior",
                Map.of("projects:spirit", Double.NaN),
                HudDisplaySnapshot.EndgameUnlockState.UNKNOWN));
        assertThrows(() -> new ElementDisplaySnapshot(
                1, Double.POSITIVE_INFINITY, 0, 0, ElementDisplaySnapshot.ColdStage.NONE,
                false, 0));
        assertThrows(() -> new PartyDisplaySnapshot("projects:p", 1, UUID.randomUUID(),
                java.util.stream.IntStream.range(0, 129)
                        .mapToObj(index -> new PartyDisplaySnapshot.Member(
                                UUID.randomUUID(), "member", 1, true, true)).toList()));
        EnhancementDisplaySnapshot unavailable = new EnhancementDisplaySnapshot(
                UUID.randomUUID(), 1, 0, false,
                EnhancementDisplaySnapshot.PreviewStatus.UNAVAILABLE_BALANCE_DATA,
                Map.of(), List.of(), UUID.randomUUID(),
                EnhancementDisplaySnapshot.TerminalStatus.NONE);
        assert unavailable.costs().isEmpty();
        assertThrows(() -> new EnhancementDisplaySnapshot(
                UUID.randomUUID(), 1, 0, false,
                EnhancementDisplaySnapshot.PreviewStatus.UNAVAILABLE_BALANCE_DATA,
                Map.of("projects:gold", 1L), List.of(), UUID.randomUUID(),
                EnhancementDisplaySnapshot.TerminalStatus.NONE));
    }

    private static void capabilityLifecycleIsEphemeralAndExact() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        BetaCapabilityPolicy policy = new BetaCapabilityPolicy(
                2, Duration.ofSeconds(5),
                List.of(BetaCapabilityDescriptor.v1(BetaCapabilityId.HUD),
                        BetaCapabilityDescriptor.v1(BetaCapabilityId.PARTY)));
        BetaCapabilitySessionService service = new BetaCapabilitySessionService(policy, clock);
        UUID player = UUID.randomUUID();
        assert service.advertise(player, false, (ignored, id) -> true).isEmpty();
        BetaCapabilityAdvertisement advertisement = service.advertise(
                player, true, (ignored, id) -> id == BetaCapabilityId.HUD).orElseThrow();
        assert advertisement.capabilities().size() == 1;
        assert service.snapshot(player).acknowledgedCapabilities().isEmpty();
        var mismatch = new BetaCapabilityAcknowledgement(1, advertisement.sessionId(),
                advertisement.advertisementRevision(),
                List.of(new BetaCapabilityDescriptor(BetaCapabilityId.HUD, 2)));
        assert service.acknowledge(player, mismatch, (ignored, id) -> true)
                == BetaCapabilitySessionService.AcknowledgeStatus.VERSION_MISMATCH;
        var acknowledgement = new BetaCapabilityAcknowledgement(1, advertisement.sessionId(),
                advertisement.advertisementRevision(), advertisement.capabilities());
        assert service.acknowledge(player, acknowledgement, (ignored, id) -> true)
                == BetaCapabilitySessionService.AcknowledgeStatus.ACCEPTED;
        assert service.snapshot(player).supports(BetaCapabilityId.HUD, 1);
        service.reconnect(player);
        assert service.snapshot(player).oldClient();
        advertisement = service.advertise(player, true, (ignored, id) -> true).orElseThrow();
        clock.advance(Duration.ofSeconds(6));
        assert service.snapshot(player).oldClient();
        service.reload(policy, true);
        assert service.activeSessionCount() == 0;
        service.close();
        service.close();
        assert service.advertise(player, true, (ignored, id) -> true).isEmpty();
    }

    private static void commandBoundaryRevalidatesAndDeduplicates() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        UUID player = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        BetaCapabilitySnapshot capability = new BetaCapabilitySnapshot(
                player, session, 3, Map.of(BetaCapabilityId.CRAFTING, 1),
                clock.instant().plusSeconds(60), false);
        UUID request = UUID.randomUUID();
        BetaCommandEnvelope command = new BetaCommandEnvelope(
                new BetaMessageEnvelope(1, BetaMessageKind.COMMAND,
                        BetaCapabilityId.CRAFTING, 1, session, new byte[0]),
                3, 8, request);
        BetaCommandContext context = new BetaCommandContext(
                player, capability, 3, 8, true, true, true, true,
                BetaCommandContext.CommandClass.PERSISTENT_MUTATION);
        BetaCommandRouter router = new BetaCommandRouter(
                new BetaRateLimiter(32, clock),
                (ignored, ignoredCommand) -> BetaCommandAuthorization.Decision.allow(), 32);
        int[] calls = {0};
        BetaCommandResult accepted = router.route(context, command, decoder(), (ignored, delivered) -> {
            calls[0]++;
            return new BetaCommandResult(BetaCommandResult.Status.ACCEPTED,
                    delivered.requestId(), "", true);
        });
        assert accepted.status() == BetaCommandResult.Status.ACCEPTED;
        assert router.route(context, command, decoder(), (ignored, delivered) -> {
            calls[0]++;
            throw new AssertionError("duplicate must not reach destination");
        }).status() == BetaCommandResult.Status.DUPLICATE;
        assert calls[0] == 1;
        BetaCommandContext denied = new BetaCommandContext(
                player, capability, 3, 8, false, true, true, true,
                BetaCommandContext.CommandClass.READ);
        BetaCommandEnvelope deniedCommand = new BetaCommandEnvelope(
                command.message(), 3, 8, UUID.randomUUID());
        assert router.route(denied, deniedCommand, decoder(), (a, b) -> null).status()
                == BetaCommandResult.Status.PERMISSION_DENIED;
        BetaCommandEnvelope stale = new BetaCommandEnvelope(
                command.message(), 3, 7, UUID.randomUUID());
        assert router.route(context, stale, decoder(), (a, b) -> null).status()
                == BetaCommandResult.Status.STALE_REVISION;
        BetaCommandEnvelope wrongSession = new BetaCommandEnvelope(
                new BetaMessageEnvelope(1, BetaMessageKind.COMMAND,
                        BetaCapabilityId.CRAFTING, 1, UUID.randomUUID(), new byte[0]),
                3, 8, UUID.randomUUID());
        assert router.route(context, wrongSession, decoder(), (a, b) -> null).status()
                == BetaCommandResult.Status.CAPABILITY_DENIED;
        BetaRateLimiter limiter = new BetaRateLimiter(4, clock);
        assert limiter.tryAcquire("player", new BetaRateLimitPolicy(1, 2));
        assert limiter.tryAcquire("player", new BetaRateLimitPolicy(1, 2));
        assert !limiter.tryAcquire("player", new BetaRateLimitPolicy(1, 2));
        clock.advance(Duration.ofSeconds(1));
        assert limiter.tryAcquire("player", new BetaRateLimitPolicy(1, 2));
        router.close();
        router.close();
    }

    private static BetaCommandDecoder decoder() {
        return envelope -> BetaCommandDecoder.DecodeResult.success(new BetaDecodedCommand(
                envelope.message().capabilityId(), "projects:test-command",
                envelope.idempotencyRequestId(), envelope.playerSessionRevision(),
                envelope.targetContentRevision(), Map.of(), List.of()));
    }

    private static void compatibilityMatrixHasSafeFallbacks() {
        // old/old and old server/new client use existing channels and no Beta session.
        UUID player = UUID.randomUUID();
        BetaCapabilitySnapshot oldClient = BetaCapabilitySnapshot.oldClient(player);
        assert oldClient.oldClient() && !oldClient.supports(BetaCapabilityId.HUD, 1);
        // new server/old client sends no state until acknowledgement.
        assert oldClient.acknowledgedCapabilities().isEmpty();
        // new/new exact v1 is covered by the codec/session round trips.
        // malformed and unsupported clients are rejected without throwing above.
        assert !io.github.gyai.projects.feature.FeatureFlagSnapshot.allDisabled()
                .isEnabled(io.github.gyai.projects.feature.FeatureKey.CLIENT_BETA_UI);
        assert Arrays.stream(io.github.gyai.projects.ProjectSPlugin.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().startsWith(
                        "io.github.gyai.projects.network.beta"));
    }

    private static void protocolApiIsPureJava() {
        List<Class<?>> types = List.of(
                BetaProtocolVersion.class, BetaChannels.class, BetaCapabilityId.class,
                BetaCapabilityDescriptor.class, BetaCapabilityAdvertisement.class,
                BetaCapabilityAcknowledgement.class, BetaMessageEnvelope.class,
                BetaCommandEnvelope.class, BetaProtocolCodec.class,
                BetaCapabilitySessionService.class, BetaCommandRouter.class,
                HudDisplaySnapshot.class, PartyDisplaySnapshot.class,
                ElementDisplaySnapshot.class, EquipmentDisplaySnapshot.class,
                CraftingDisplaySnapshot.class, EnhancementDisplaySnapshot.class,
                MobEditorDisplaySnapshot.class, MobEditorDisplayPort.class,
                MobEditorCommandPort.class);
        for (Class<?> type : types) {
            for (Field field : type.getFields()) assertPure(field.getType());
            for (Method method : type.getMethods()) {
                if (method.getDeclaringClass() == Object.class) continue;
                assertPure(method.getReturnType());
                Arrays.stream(method.getParameterTypes()).forEach(BetaProtocolFoundationTest::assertPure);
            }
            Arrays.stream(type.getConstructors()).map(Executable::getParameterTypes)
                    .flatMap(Arrays::stream).forEach(BetaProtocolFoundationTest::assertPure);
        }
    }

    private static void assertPure(Class<?> type) {
        assert !type.getName().startsWith("org.bukkit") : type;
        assert !type.getName().startsWith("net.minecraft") : type;
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
