package io.github.gyai.projects.network.beta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BetaProtocolCodec {
    private static final int ADVERTISEMENT = 1;
    private static final int ACKNOWLEDGEMENT = 2;
    private static final int STATE = 3;
    private static final int COMMAND = 4;
    private static final int COMMAND_RESULT = 5;

    private final BetaProtocolLimits limits;

    public BetaProtocolCodec() {
        this(BetaProtocolLimits.DEFAULTS);
    }

    public BetaProtocolCodec(BetaProtocolLimits limits) {
        this.limits = java.util.Objects.requireNonNull(limits);
    }

    public byte[] encode(BetaCapabilityAdvertisement value) {
        return writeHandshake(ADVERTISEMENT, value.aggregateVersion(), value.sessionId(),
                value.advertisementRevision(), value.capabilities());
    }

    public byte[] encode(BetaCapabilityAcknowledgement value) {
        return writeHandshake(ACKNOWLEDGEMENT, value.aggregateVersion(), value.sessionId(),
                value.advertisementRevision(), value.capabilities());
    }

    public byte[] encode(BetaMessageEnvelope value) {
        if (value.kind() == BetaMessageKind.COMMAND) {
            throw new IllegalArgumentException("Commands require command metadata");
        }
        return writeMessage(value, null);
    }

    public byte[] encode(BetaCommandEnvelope value) {
        return writeMessage(value.message(), value);
    }

    public BetaProtocolDecodeResult<BetaCapabilityAdvertisement> decodeAdvertisement(byte[] packet) {
        BetaProtocolDecodeResult<Handshake> result = readHandshake(packet, ADVERTISEMENT);
        if (result.status() != BetaProtocolDecodeResult.Status.SUCCESS) {
            return BetaProtocolDecodeResult.failure(result.status(), result.detail());
        }
        Handshake value = result.value();
        return BetaProtocolDecodeResult.success(new BetaCapabilityAdvertisement(
                value.version(), value.sessionId(), value.revision(), value.capabilities()));
    }

    public BetaProtocolDecodeResult<BetaCapabilityAcknowledgement> decodeAcknowledgement(byte[] packet) {
        BetaProtocolDecodeResult<Handshake> result = readHandshake(packet, ACKNOWLEDGEMENT);
        if (result.status() != BetaProtocolDecodeResult.Status.SUCCESS) {
            return BetaProtocolDecodeResult.failure(result.status(), result.detail());
        }
        Handshake value = result.value();
        return BetaProtocolDecodeResult.success(new BetaCapabilityAcknowledgement(
                value.version(), value.sessionId(), value.revision(), value.capabilities()));
    }

    public BetaProtocolDecodeResult<BetaMessageEnvelope> decodeMessage(byte[] packet) {
        BetaProtocolDecodeResult<DecodedMessage> result = readMessage(packet, false);
        if (result.status() != BetaProtocolDecodeResult.Status.SUCCESS) {
            return BetaProtocolDecodeResult.failure(result.status(), result.detail());
        }
        return BetaProtocolDecodeResult.success(result.value().message());
    }

    public BetaProtocolDecodeResult<BetaCommandEnvelope> decodeCommand(byte[] packet) {
        BetaProtocolDecodeResult<DecodedMessage> result = readMessage(packet, true);
        if (result.status() != BetaProtocolDecodeResult.Status.SUCCESS) {
            return BetaProtocolDecodeResult.failure(result.status(), result.detail());
        }
        if (result.value().command() == null) {
            return BetaProtocolDecodeResult.failure(
                    BetaProtocolDecodeResult.Status.UNKNOWN_OPCODE, "Not a command packet");
        }
        return BetaProtocolDecodeResult.success(result.value().command());
    }

    private byte[] writeHandshake(
            int opcode,
            int version,
            UUID sessionId,
            long revision,
            List<BetaCapabilityDescriptor> capabilities
    ) {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
                payload.writeLong(revision);
                payload.writeShort(capabilities.size());
                for (BetaCapabilityDescriptor capability : capabilities) {
                    writeString(payload, capability.id().id(), limits.canonicalIdBytes());
                    payload.writeInt(capability.payloadVersion());
                }
            }
            byte[] encodedPayload = payloadBytes.toByteArray();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(version);
                output.writeByte(opcode);
                writeUuid(output, sessionId);
                output.writeInt(encodedPayload.length);
                output.write(encodedPayload);
            }
            return bounded(bytes.toByteArray(), limits.handshakeBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot encode handshake", exception);
        }
    }

    private BetaProtocolDecodeResult<Handshake> readHandshake(byte[] packet, int expectedOpcode) {
        if (packet == null) return malformed("Missing packet");
        if (packet.length > limits.handshakeBytes()) return oversized();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet))) {
            int version = input.readUnsignedByte();
            if (version != BetaProtocolVersion.CURRENT) return unsupported();
            if (input.readUnsignedByte() != expectedOpcode) return unknownOpcode();
            UUID sessionId = readUuid(input);
            int payloadLength = input.readInt();
            if (payloadLength < 0) return malformed("Negative payload length");
            if (payloadLength > limits.handshakeBytes() || payloadLength > input.available()) {
                return oversized();
            }
            byte[] payloadBytes = input.readNBytes(payloadLength);
            if (input.available() != 0) return malformed("Trailing bytes");
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(payloadBytes));
            long revision = payload.readLong();
            if (revision < 0) return malformed("Negative revision");
            int count = payload.readUnsignedShort();
            if (count > limits.listEntries() || count > BetaCapabilityId.values().length) {
                return oversized();
            }
            ArrayList<BetaCapabilityDescriptor> capabilities = new ArrayList<>(count);
            java.util.HashSet<BetaCapabilityId> ids = new java.util.HashSet<>();
            for (int index = 0; index < count; index++) {
                String canonical = readString(payload, limits.canonicalIdBytes());
                BetaCapabilityId id = BetaCapabilityId.fromId(canonical).orElse(null);
                if (id == null) return unknownCapability();
                if (!ids.add(id)) return malformed("Duplicate capability");
                int payloadVersion = payload.readInt();
                if (payloadVersion <= 0) return malformed("Invalid payload version");
                capabilities.add(new BetaCapabilityDescriptor(id, payloadVersion));
            }
            if (payload.available() != 0) return malformed("Trailing payload bytes");
            return BetaProtocolDecodeResult.success(new Handshake(
                    version, sessionId, revision, List.copyOf(capabilities)));
        } catch (EOFException exception) {
            return malformed("Truncated packet");
        } catch (IOException | IllegalArgumentException exception) {
            return malformed(exception.getMessage());
        }
    }

    private byte[] writeMessage(BetaMessageEnvelope message, BetaCommandEnvelope command) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(message.aggregateVersion());
                output.writeByte(opcode(message.kind()));
                writeString(output, message.capabilityId().id(), limits.canonicalIdBytes());
                output.writeInt(message.capabilityPayloadVersion());
                writeUuid(output, message.requestOrSessionId());
                if (command != null) {
                    output.writeLong(command.playerSessionRevision());
                    output.writeLong(command.targetContentRevision());
                    writeUuid(output, command.idempotencyRequestId());
                }
                byte[] payload = message.payload();
                output.writeInt(payload.length);
                output.write(payload);
            }
            return bounded(bytes.toByteArray(), limits.packetBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot encode message", exception);
        }
    }

    private BetaProtocolDecodeResult<DecodedMessage> readMessage(byte[] packet, boolean requireCommand) {
        if (packet == null) return malformed("Missing packet");
        if (packet.length > limits.packetBytes()) return oversized();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet))) {
            int version = input.readUnsignedByte();
            if (version != BetaProtocolVersion.CURRENT) return unsupported();
            int opcode = input.readUnsignedByte();
            BetaMessageKind kind = kind(opcode);
            if (kind == null || requireCommand != (kind == BetaMessageKind.COMMAND)) {
                return unknownOpcode();
            }
            String canonical = readString(input, limits.canonicalIdBytes());
            BetaCapabilityId capability = BetaCapabilityId.fromId(canonical).orElse(null);
            if (capability == null) return unknownCapability();
            int payloadVersion = input.readInt();
            if (payloadVersion <= 0) return malformed("Invalid payload version");
            UUID requestId = readUuid(input);
            long sessionRevision = 0;
            long contentRevision = 0;
            UUID idempotencyId = null;
            if (kind == BetaMessageKind.COMMAND) {
                sessionRevision = input.readLong();
                contentRevision = input.readLong();
                idempotencyId = readUuid(input);
                if (sessionRevision < 0 || contentRevision < 0) {
                    return malformed("Negative command revision");
                }
            }
            int length = input.readInt();
            if (length < 0) return malformed("Negative payload length");
            if (length > limits.packetBytes() || length > input.available()) return oversized();
            byte[] payload = input.readNBytes(length);
            if (input.available() != 0) return malformed("Trailing bytes");
            BetaMessageEnvelope message = new BetaMessageEnvelope(
                    version, kind, capability, payloadVersion, requestId, payload);
            BetaCommandEnvelope command = kind == BetaMessageKind.COMMAND
                    ? new BetaCommandEnvelope(message, sessionRevision, contentRevision, idempotencyId)
                    : null;
            return BetaProtocolDecodeResult.success(new DecodedMessage(message, command));
        } catch (EOFException exception) {
            return malformed("Truncated packet");
        } catch (IOException | IllegalArgumentException exception) {
            return malformed(exception.getMessage());
        }
    }

    public static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private int opcode(BetaMessageKind kind) {
        return switch (kind) {
            case STATE -> STATE;
            case COMMAND -> COMMAND;
            case COMMAND_RESULT -> COMMAND_RESULT;
        };
    }

    private BetaMessageKind kind(int opcode) {
        return switch (opcode) {
            case STATE -> BetaMessageKind.STATE;
            case COMMAND -> BetaMessageKind.COMMAND;
            case COMMAND_RESULT -> BetaMessageKind.COMMAND_RESULT;
            default -> null;
        };
    }

    private void writeString(DataOutputStream output, String value, int maximumBytes)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) throw new IOException("String is oversized");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private String readString(DataInputStream input, int maximumBytes) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes || length > input.available()) {
            throw new IOException("String is oversized");
        }
        byte[] bytes = input.readNBytes(length);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private byte[] bounded(byte[] bytes, int maximum) {
        if (bytes.length > maximum) throw new IllegalArgumentException("Packet is oversized");
        return bytes;
    }

    private static <T> BetaProtocolDecodeResult<T> malformed(String detail) {
        return BetaProtocolDecodeResult.failure(BetaProtocolDecodeResult.Status.MALFORMED, detail);
    }

    private static <T> BetaProtocolDecodeResult<T> oversized() {
        return BetaProtocolDecodeResult.failure(BetaProtocolDecodeResult.Status.OVERSIZED, "Packet is oversized");
    }

    private static <T> BetaProtocolDecodeResult<T> unsupported() {
        return BetaProtocolDecodeResult.failure(
                BetaProtocolDecodeResult.Status.UNSUPPORTED_VERSION, "Unsupported aggregate version");
    }

    private static <T> BetaProtocolDecodeResult<T> unknownCapability() {
        return BetaProtocolDecodeResult.failure(
                BetaProtocolDecodeResult.Status.UNKNOWN_CAPABILITY, "Unknown capability");
    }

    private static <T> BetaProtocolDecodeResult<T> unknownOpcode() {
        return BetaProtocolDecodeResult.failure(
                BetaProtocolDecodeResult.Status.UNKNOWN_OPCODE, "Unknown opcode");
    }

    private record Handshake(
            int version,
            UUID sessionId,
            long revision,
            List<BetaCapabilityDescriptor> capabilities
    ) {
    }

    private record DecodedMessage(BetaMessageEnvelope message, BetaCommandEnvelope command) {
    }
}
