package io.github.gyai.projects.network;

import io.github.gyai.projects.ability.AbilityLifecycleEvent;
import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.ability.AnchorFrame;
import io.github.gyai.projects.ability.MotionDirection;
import io.github.gyai.projects.ability.MotionEasing;
import io.github.gyai.projects.ability.MotionMode;
import io.github.gyai.projects.ability.MotionSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Additive runtime channel whose primitive envelope is version 3 and carries Motion. */
public final class AbilityVfxPacketV2 {
    public static final String CHANNEL = "projects:ability_vfx_v2";
    public static final int VERSION = 2;
    public static final int PRIMITIVE_VERSION = 3;
    public static final int MAX_PACKET_BYTES = AbilityVfxPacket.MAX_PACKET_BYTES;

    private AbilityVfxPacketV2() { }

    public static byte[] encode(AbilityVfxPacket.Cue cue) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(VERSION);
            uuid(out, cue.session());
            out.writeLong(cue.sequence());
            uuid(out, cue.cueId());
            uuid(out, cue.castId());
            string(out, cue.visualId(), 96);
            out.writeByte(cue.hook().ordinal());
            out.writeInt(cue.actionIndex());
            out.writeInt(cue.emissionIndex());
            AnchorFrame anchor = cue.anchor().normalized();
            uuid(out, anchor.worldId());
            string(out, anchor.dimension(), 128);
            doubles(out, anchor.x(), anchor.y(), anchor.z(), anchor.forwardX(), anchor.forwardY(), anchor.forwardZ(),
                    anchor.upX(), anchor.upY(), anchor.upZ());
            out.writeLong(cue.serverTickAtSend());
            out.writeLong(cue.startTick());
            out.writeInt(cue.cueDurationTicks());
            out.writeByte(cue.primitives().size());
            for (AbilityVfxPacket.Primitive primitive : cue.primitives()) writePrimitive(out, primitive);
            out.flush();
            if (raw.size() > MAX_PACKET_BYTES) throw new IllegalArgumentException("Vfx packet too large");
            return raw.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Strict server-side fixture decoder used to prove the additive layout and fail closed. */
    public static AbilityVfxPacket.Cue decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_PACKET_BYTES) throw bad("packet");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IOException("version");
            UUID session = uuid(in);
            long sequence = in.readLong();
            UUID cueId = uuid(in);
            UUID castId = uuid(in);
            String visualId = string(in, 96);
            AbilityLifecycleEvent.Hook hook = enumValue(AbilityLifecycleEvent.Hook.values(), in.readUnsignedByte());
            int actionIndex = in.readInt();
            int emissionIndex = in.readInt();
            AnchorFrame anchor = new AnchorFrame(uuid(in), string(in, 128),
                    in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                    in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
            long serverTick = in.readLong();
            long startTick = in.readLong();
            int duration = in.readInt();
            int count = in.readUnsignedByte();
            if (count == 0 || count > 16) throw new IOException("primitive count");
            List<AbilityVfxPacket.Primitive> primitives = new ArrayList<>();
            for (int i = 0; i < count; i++) primitives.add(readPrimitive(in));
            if (in.available() != 0) throw new IOException("trailing");
            return new AbilityVfxPacket.Cue(session, sequence, cueId, castId, visualId, hook, actionIndex,
                    emissionIndex, serverTick, startTick, duration, anchor, primitives);
        } catch (Exception e) {
            throw bad("Malformed motion VFX packet", e);
        }
    }

    private static void writePrimitive(DataOutputStream out, AbilityVfxPacket.Primitive primitive) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(payload);
        body.writeShort(primitive.delayTicks());
        body.writeShort(primitive.durationTicks());
        body.writeByte(primitive.argb() >>> 16);
        body.writeByte(primitive.argb() >>> 8);
        body.writeByte(primitive.argb());
        body.writeByte(primitive.argb() >>> 24);
        body.writeDouble(primitive.width());
        body.writeShort(primitive.density());
        body.writeLong(primitive.seed());
        doubles(body, primitive.localOffset().x(), primitive.localOffset().y(), primitive.localOffset().z(),
                primitive.yawRadians(), primitive.size(), primitive.radius(), primitive.length(), primitive.height(),
                primitive.angle(), primitive.startAngle(), primitive.sweepAngle(), primitive.turns());
        body.writeShort(primitive.count());
        body.writeByte(primitive.controlPoints().size());
        for (AbilityVisualDefinition.Vec point : primitive.controlPoints()) doubles(body, point.x(), point.y(), point.z());
        body.writeByte(primitive.appearance().kind().ordinal());
        string(body, primitive.appearance().id(), 96);
        body.writeByte(primitive.motion().mode().ordinal());
        body.writeByte(primitive.motion().direction().ordinal());
        body.writeByte(primitive.motion().easing().ordinal());
        body.writeDouble(primitive.motion().phase());
        body.writeDouble(primitive.motion().trailFraction());
        body.flush();
        if (payload.size() > 0xffff) throw new IOException("primitive");
        out.writeByte(primitive.type().ordinal());
        out.writeByte(PRIMITIVE_VERSION);
        out.writeShort(payload.size());
        out.write(payload.toByteArray());
    }

    private static AbilityVfxPacket.Primitive readPrimitive(DataInputStream in) throws IOException {
        AbilityVisualDefinition.PrimitiveType type = enumValue(AbilityVisualDefinition.PrimitiveType.values(), in.readUnsignedByte());
        if (in.readUnsignedByte() != PRIMITIVE_VERSION) throw new IOException("primitive version");
        int length = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        DataInputStream body = new DataInputStream(new ByteArrayInputStream(bytes));
        int delay = body.readUnsignedShort();
        int duration = body.readUnsignedShort();
        int argb = body.readUnsignedByte() << 16 | body.readUnsignedByte() << 8
                | body.readUnsignedByte() | body.readUnsignedByte() << 24;
        double width = body.readDouble();
        int density = body.readUnsignedShort();
        long seed = body.readLong();
        AbilityVisualDefinition.Vec offset = vec(body);
        double yaw = body.readDouble();
        double size = body.readDouble();
        double radius = body.readDouble();
        double lineLength = body.readDouble();
        double height = body.readDouble();
        double angle = body.readDouble();
        double start = body.readDouble();
        double sweep = body.readDouble();
        double turns = body.readDouble();
        int count = body.readUnsignedShort();
        int points = body.readUnsignedByte();
        if (points > 8) throw new IOException("points");
        List<AbilityVisualDefinition.Vec> controlPoints = new ArrayList<>();
        for (int i = 0; i < points; i++) controlPoints.add(vec(body));
        AbilityVisualDefinition.AppearanceKind kind = enumValue(AbilityVisualDefinition.AppearanceKind.values(), body.readUnsignedByte());
        AbilityVisualDefinition.Appearance appearance = new AbilityVisualDefinition.Appearance(kind, string(body, 96));
        MotionMode mode = enumValue(MotionMode.values(), body.readUnsignedByte());
        MotionDirection direction = enumValue(MotionDirection.values(), body.readUnsignedByte());
        MotionEasing easing = enumValue(MotionEasing.values(), body.readUnsignedByte());
        MotionSpec motion = new MotionSpec(mode, direction, easing, body.readDouble(), body.readDouble());
        if (body.available() != 0) throw new IOException("primitive trailing");
        return new AbilityVfxPacket.Primitive(type, delay, duration, argb, width, density, seed, offset, yaw,
                size, radius, lineLength, height, angle, start, sweep, turns, count, controlPoints, appearance, motion);
    }

    private static AbilityVisualDefinition.Vec vec(DataInputStream in) throws IOException {
        return new AbilityVisualDefinition.Vec(in.readDouble(), in.readDouble(), in.readDouble());
    }

    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void string(DataOutputStream out, String value, int max) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > max) throw new IOException("string");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String string(DataInputStream in, int max) throws IOException {
        int length = in.readUnsignedShort();
        if (length > max) throw new IOException("string");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException("utf8");
        return value;
    }

    private static void doubles(DataOutputStream out, double... values) throws IOException {
        for (double value : values) out.writeDouble(value);
    }

    private static <T> T enumValue(T[] values, int ordinal) throws IOException {
        if (ordinal < 0 || ordinal >= values.length) throw new IOException("enum");
        return values[ordinal];
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException bad(String message, Exception cause) {
        return new IllegalArgumentException(message, cause);
    }
}
