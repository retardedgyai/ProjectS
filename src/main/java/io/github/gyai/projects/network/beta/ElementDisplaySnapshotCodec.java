package io.github.gyai.projects.network.beta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Protocol-v1 payload codec for the server-authoritative elements snapshot. */
public final class ElementDisplaySnapshotCodec {
    public byte[] encode(ElementDisplaySnapshot value) {
        if (value == null) throw new IllegalArgumentException("element snapshot is required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(value.targetNetworkId());
                out.writeLong(value.stateRevision());
                out.writeDouble(value.fireFractionalGauge());
                out.writeInt(value.fireStacks());
                out.writeDouble(value.fireThreshold());
                out.writeDouble(value.fireFractionalProgress());
                out.writeBoolean(value.fireDecayActive());
                out.writeLong(value.fireDecayStartsInMillis());
                out.writeLong(value.detonationPulseRevision());
                out.writeLong(value.snapshotExpiresAtMillis());
                out.writeDouble(value.coldGauge());
                out.writeByte(value.coldStage().ordinal());
                out.writeBoolean(value.frozen());
                out.writeLong(value.refreezeImmunityMillis());
            }
            byte[] result = bytes.toByteArray();
            if (result.length > BetaProtocolLimits.DEFAULTS.packetBytes()) {
                throw new IllegalArgumentException("element payload is oversized");
            }
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot encode element payload", failure);
        }
    }

    public ElementDisplaySnapshot decode(byte[] payload) {
        if (payload == null || payload.length > BetaProtocolLimits.DEFAULTS.packetBytes()) {
            throw new IllegalArgumentException("invalid element payload");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            ElementDisplaySnapshot.ColdStage[] stages = ElementDisplaySnapshot.ColdStage.values();
            int target = in.readInt();
            long revision = in.readLong();
            double gauge = in.readDouble();
            int stacks = in.readInt();
            double threshold = in.readDouble();
            double progress = in.readDouble();
            boolean decay = in.readBoolean();
            long decayStarts = in.readLong();
            long pulse = in.readLong();
            long expiry = in.readLong();
            double cold = in.readDouble();
            int stage = in.readUnsignedByte();
            boolean frozen = in.readBoolean();
            long immunity = in.readLong();
            if (stage >= stages.length || in.available() != 0) {
                throw new IllegalArgumentException("malformed element payload");
            }
            return new ElementDisplaySnapshot(target, revision, gauge, stacks,
                    threshold, progress, decay, decayStarts, pulse, expiry,
                    cold, stages[stage], frozen, immunity);
        } catch (IOException failure) {
            throw new IllegalArgumentException("malformed element payload", failure);
        }
    }
}
