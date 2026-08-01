package io.github.gyai.projects.network;

import io.github.gyai.projects.combat.telegraph.TelegraphInstance;
import io.github.gyai.projects.combat.telegraph.TelegraphOperation;
import io.github.gyai.projects.combat.telegraph.TelegraphRequest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record TelegraphPacket(
        TelegraphOperation operation,
        long sequence,
        long serverTick,
        Snapshot snapshot
) {
    public static final String CHANNEL =
            "projects:telegraph_v1";
    public static final String HELLO_CHANNEL =
            "projects:telegraph_hello_v1";
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PACKET_BYTES = 4_096;

    public TelegraphPacket {
        Objects.requireNonNull(operation, "operation");
        if (operation == TelegraphOperation.CLEAR) {
            if (snapshot != null) {
                throw new IllegalArgumentException(
                        "Clear cannot contain a telegraph");
            }
        } else {
            Objects.requireNonNull(snapshot, "snapshot");
            snapshot.validate();
        }
    }

    public static TelegraphPacket from(
            TelegraphOperation operation,
            long sequence,
            long serverTick,
            TelegraphInstance instance
    ) {
        TelegraphRequest request = instance.request();
        return new TelegraphPacket(
                operation,
                sequence,
                serverTick,
                new Snapshot(
                        instance.id(),
                        instance.sourceId(),
                        instance.sourceNetworkId(),
                        request.attackId(),
                        request.worldId(),
                        request.dimension(),
                        request.shape(),
                        request.theme(),
                        request.style(),
                        instance.centerX(),
                        instance.centerY(),
                        instance.centerZ(),
                        request.directionX(),
                        request.directionZ(),
                        request.radius(),
                        request.innerRadius(),
                        request.width(),
                        request.length(),
                        request.startTick(),
                        request.totalWarningTicks(),
                        remaining(request.lockTick(), serverTick),
                        remaining(request.detonateTick(), serverTick),
                        remaining(request.expireTick(), serverTick),
                        request.verticalTolerance(),
                        request.trackingMode(),
                        request.targetId(),
                        instance.revision(),
                        instance.locked(),
                        instance.detonated(),
                        instance.cancelled(),
                        instance.cancellationReason()));
    }

    public static TelegraphPacket clear(
            long sequence,
            long serverTick
    ) {
        return new TelegraphPacket(
                TelegraphOperation.CLEAR,
                sequence,
                serverTick,
                null);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes =
                    new ByteArrayOutputStream();
            DataOutputStream output =
                    new DataOutputStream(bytes);
            output.writeByte(PROTOCOL_VERSION);
            output.writeByte(operation.ordinal());
            output.writeLong(sequence);
            output.writeLong(serverTick);
            output.writeBoolean(snapshot != null);
            if (snapshot != null) {
                writeUuid(output, snapshot.id());
                writeUuid(output, snapshot.sourceId());
                output.writeInt(snapshot.sourceNetworkId());
                writeString(
                        output,
                        snapshot.attackId(),
                        TelegraphRequest.MAX_ATTACK_ID_BYTES);
                writeUuid(output, snapshot.worldId());
                writeString(
                        output,
                        snapshot.dimension(),
                        TelegraphRequest.MAX_DIMENSION_BYTES);
                output.writeByte(snapshot.shape().ordinal());
                output.writeByte(snapshot.theme().ordinal());
                output.writeByte(snapshot.style().ordinal());
                output.writeDouble(snapshot.centerX());
                output.writeDouble(snapshot.centerY());
                output.writeDouble(snapshot.centerZ());
                output.writeDouble(snapshot.directionX());
                output.writeDouble(snapshot.directionZ());
                output.writeDouble(snapshot.radius());
                output.writeDouble(snapshot.innerRadius());
                output.writeDouble(snapshot.width());
                output.writeDouble(snapshot.length());
                output.writeLong(snapshot.startTick());
                output.writeInt(snapshot.totalWarningTicks());
                output.writeInt(snapshot.remainingLockTicks());
                output.writeInt(snapshot.remainingDetonationTicks());
                output.writeInt(snapshot.remainingExpireTicks());
                output.writeDouble(snapshot.verticalTolerance());
                output.writeByte(snapshot.trackingMode().ordinal());
                output.writeBoolean(snapshot.targetId() != null);
                if (snapshot.targetId() != null) {
                    writeUuid(output, snapshot.targetId());
                }
                output.writeLong(snapshot.revision());
                output.writeBoolean(snapshot.locked());
                output.writeBoolean(snapshot.detonated());
                output.writeBoolean(snapshot.cancelled());
                output.writeByte(
                        snapshot.cancellationReason().ordinal());
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException(
                        "Telegraph packet is too large");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not encode telegraph packet",
                    exception);
        }
    }

    private static int remaining(
            long targetTick,
            long currentTick
    ) {
        return (int) Math.clamp(
                targetTick - currentTick,
                0L,
                TelegraphRequest.MAX_DURATION_TICKS);
    }

    private static void writeUuid(
            DataOutputStream output,
            UUID id
    ) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            int maximumBytes
    ) throws IOException {
        byte[] encoded =
                value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException(
                    "Telegraph string is too long");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    public record Snapshot(
            UUID id,
            UUID sourceId,
            int sourceNetworkId,
            String attackId,
            UUID worldId,
            String dimension,
            TelegraphInstance.Shape shape,
            TelegraphInstance.VisualTheme theme,
            TelegraphInstance.VisualStyle style,
            double centerX,
            double centerY,
            double centerZ,
            double directionX,
            double directionZ,
            double radius,
            double innerRadius,
            double width,
            double length,
            long startTick,
            int totalWarningTicks,
            int remainingLockTicks,
            int remainingDetonationTicks,
            int remainingExpireTicks,
            double verticalTolerance,
            TelegraphInstance.TrackingMode trackingMode,
            UUID targetId,
            long revision,
            boolean locked,
            boolean detonated,
            boolean cancelled,
            TelegraphInstance.CancellationReason
                    cancellationReason
    ) {
        private void validate() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(
                    trackingMode, "trackingMode");
            Objects.requireNonNull(
                    cancellationReason,
                    "cancellationReason");
            if (sourceNetworkId < 0
                    || revision < 1L
                    || totalWarningTicks <= 0
                    || totalWarningTicks
                    > TelegraphRequest.MAX_DURATION_TICKS
                    || !validRemaining(remainingLockTicks)
                    || !validRemaining(
                    remainingDetonationTicks)
                    || !validRemaining(
                    remainingExpireTicks)
                    || !allFinite(
                    centerX, centerY, centerZ,
                    directionX, directionZ,
                    radius, innerRadius, width, length,
                    verticalTolerance)) {
                throw new IllegalArgumentException(
                        "Invalid telegraph snapshot");
            }
        }

        private static boolean validRemaining(int value) {
            return value >= 0
                    && value
                    <= TelegraphRequest.MAX_DURATION_TICKS;
        }

        private static boolean allFinite(
                double... values
        ) {
            for (double value : values) {
                if (!Double.isFinite(value)) {
                    return false;
                }
            }
            return true;
        }
    }
}
