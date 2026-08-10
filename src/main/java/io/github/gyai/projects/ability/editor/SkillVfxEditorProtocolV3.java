package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.AbilityLifecycleEvent;
import io.github.gyai.projects.ability.AbilityVisualDefinition;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Additive Motion-aware editor transport.  The v1 body is retained byte-for-byte inside
 * the frame; the bounded table is the only v3 addition and is keyed by stable primitive id.
 */
public final class SkillVfxEditorProtocolV3 {
    public static final int VERSION = 3;
    public static final int MAX_PACKET = SkillVfxEditorProtocol.MAX_PACKET;
    public static final int MAX_STRING = SkillVfxEditorProtocol.MAX_STRING;
    private static final int MAX_TABLES = 2;
    private static final int MAX_TABLE_ENTRIES = SkillVfxEditorProtocol.MAX_HOOKS
            * SkillVfxEditorProtocol.MAX_EMISSIONS * SkillVfxEditorProtocol.MAX_PRIMITIVES;

    private SkillVfxEditorProtocolV3() { }

    public static byte[] encodeRequest(SkillVfxEditorProtocol.Request request) {
        return frame(SkillVfxEditorProtocol.encodeRequest(request),
                request.visual() == null ? List.of() : List.of(request.visual()));
    }

    public static SkillVfxEditorProtocol.Request decodeRequest(byte[] bytes) {
        Frame frame = unframe(bytes);
        SkillVfxEditorProtocol.Request request = SkillVfxEditorProtocol.decodeRequest(frame.body());
        int expected = request.visual() == null ? 0 : 1;
        if (frame.tables().size() != expected) throw bad("request table count");
        if (request.visual() == null) return request;
        return withVisual(request, patch(request.visual(), frame.tables().getFirst()));
    }

    public static byte[] encodeState(SkillVfxEditorProtocol.State state) {
        List<AbilityVisualDefinition> visuals = state.snapshot() == null
                ? List.of() : List.of(state.snapshot().base(), state.snapshot().effective());
        return frame(SkillVfxEditorProtocol.encodeState(state), visuals);
    }

    public static SkillVfxEditorProtocol.State decodeState(byte[] bytes) {
        Frame frame = unframe(bytes);
        SkillVfxEditorProtocol.State state = SkillVfxEditorProtocol.decodeState(frame.body());
        if (state.snapshot() == null) {
            if (!frame.tables().isEmpty()) throw bad("state table count");
            return state;
        }
        if (frame.tables().size() != 2) throw bad("state table count");
        SkillVfxEditorService.Snapshot old = state.snapshot();
        SkillVfxEditorService.Snapshot snapshot = new SkillVfxEditorService.Snapshot(
                old.ability(), old.visualId(), patch(old.base(), frame.tables().get(0)),
                patch(old.effective(), frame.tables().get(1)), old.revision(),
                old.baseFingerprint(), old.effectiveFingerprint(), old.sessionOverride());
        return new SkillVfxEditorProtocol.State(state.status(), state.correlation(), state.session(),
                state.catalog(), snapshot, state.previewAllowed(), state.message());
    }

    public static byte[] encodeVisual(AbilityVisualDefinition visual) {
        return frame(SkillVfxEditorProtocol.encodeVisual(visual), List.of(visual));
    }

    public static AbilityVisualDefinition decodeVisual(byte[] bytes) {
        Frame frame = unframe(bytes);
        if (frame.tables().size() != 1) throw bad("visual table count");
        return patch(decodeBaseVisual(frame.body()), frame.tables().getFirst());
    }

    private record Entry(AbilityVisualDefinition.Appearance appearance, MotionSpec motion) { }
    private record Frame(byte[] body, List<Map<String, Entry>> tables) { }

    private static SkillVfxEditorProtocol.Request withVisual(SkillVfxEditorProtocol.Request request,
                                                              AbilityVisualDefinition visual) {
        return new SkillVfxEditorProtocol.Request(request.operation(), request.correlation(), request.session(),
                request.abilityId(), request.revision(), request.baseFingerprint(), request.effectiveFingerprint(), visual);
    }

    private static byte[] frame(byte[] body, List<AbilityVisualDefinition> visuals) {
        if (body == null || body.length > 0xffff || visuals == null || visuals.size() > MAX_TABLES) {
            throw bad("frame bounds");
        }
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(VERSION);
            out.writeShort(body.length);
            out.write(body);
            out.writeByte(visuals.size());
            for (AbilityVisualDefinition visual : visuals) writeTable(out, visual);
            out.flush();
            if (raw.size() > MAX_PACKET) throw bad("packet");
            return raw.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Frame unframe(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_PACKET) throw bad("packet");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IOException("version");
            int bodyLength = in.readUnsignedShort();
            byte[] body = in.readNBytes(bodyLength);
            if (body.length != bodyLength) throw new EOFException();
            int count = in.readUnsignedByte();
            if (count > MAX_TABLES) throw new IOException("table count");
            List<Map<String, Entry>> tables = new ArrayList<>();
            for (int i = 0; i < count; i++) tables.add(readTable(in));
            if (in.available() != 0) throw new IOException("trailing");
            return new Frame(body, List.copyOf(tables));
        } catch (Exception e) {
            throw bad("malformed v3 editor packet", e);
        }
    }

    private static void writeTable(DataOutputStream out, AbilityVisualDefinition visual) throws IOException {
        List<AbilityVisualDefinition.PrimitiveSpec> primitives = primitives(visual);
        if (primitives.size() > MAX_TABLE_ENTRIES) throw new IOException("table entries");
        out.writeShort(primitives.size());
        Set<String> ids = new HashSet<>();
        for (AbilityVisualDefinition.PrimitiveSpec primitive : primitives) {
            if (!ids.add(primitive.id())) throw new IOException("duplicate primitive id");
            string(out, primitive.id());
            out.writeByte(primitive.appearance().kind().ordinal());
            string(out, primitive.appearance().id());
            out.writeByte(primitive.motion().mode().ordinal());
            out.writeByte(primitive.motion().direction().ordinal());
            out.writeByte(primitive.motion().easing().ordinal());
            out.writeDouble(primitive.motion().phase());
            out.writeDouble(primitive.motion().trailFraction());
        }
    }

    private static Map<String, Entry> readTable(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        if (count > MAX_TABLE_ENTRIES) throw new IOException("table entries");
        Map<String, Entry> result = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String id = string(in);
            AbilityVisualDefinition.AppearanceKind kind = enumValue(
                    AbilityVisualDefinition.AppearanceKind.values(), in.readUnsignedByte());
            AbilityVisualDefinition.Appearance appearance =
                    new AbilityVisualDefinition.Appearance(kind, string(in));
            MotionMode mode = enumValue(MotionMode.values(), in.readUnsignedByte());
            MotionDirection direction = enumValue(MotionDirection.values(), in.readUnsignedByte());
            MotionEasing easing = enumValue(MotionEasing.values(), in.readUnsignedByte());
            MotionSpec motion = new MotionSpec(mode, direction, easing, in.readDouble(), in.readDouble());
            if (result.put(id, new Entry(appearance, motion)) != null) throw new IOException("duplicate id");
        }
        return Map.copyOf(result);
    }

    private static AbilityVisualDefinition patch(AbilityVisualDefinition base, Map<String, Entry> entries) {
        List<AbilityVisualDefinition.PrimitiveSpec> all = primitives(base);
        Set<String> ids = all.stream().map(AbilityVisualDefinition.PrimitiveSpec::id).collect(Collectors.toSet());
        if (entries.size() != all.size() || !entries.keySet().equals(ids)) throw bad("primitive ids");
        List<AbilityVisualDefinition.HookBinding> bindings = new ArrayList<>();
        for (AbilityVisualDefinition.HookBinding hook : base.bindings()) {
            List<AbilityVisualDefinition.Emission> emissions = new ArrayList<>();
            for (AbilityVisualDefinition.Emission emission : hook.emissions()) {
                List<AbilityVisualDefinition.PrimitiveSpec> primitives = new ArrayList<>();
                for (AbilityVisualDefinition.PrimitiveSpec primitive : emission.primitives()) {
                    Entry entry = entries.get(primitive.id());
                    primitives.add(new AbilityVisualDefinition.PrimitiveSpec(
                            primitive.id(), primitive.type(), primitive.delayTicks(), primitive.durationTicks(),
                            primitive.argb(), primitive.width(), primitive.density(), primitive.seed(),
                            primitive.localOffset(), primitive.yawRadians(), primitive.size(), primitive.radius(),
                            primitive.length(), primitive.height(), primitive.angle(), primitive.startAngle(),
                            primitive.sweepAngle(), primitive.turns(), primitive.count(), primitive.controlPoints(),
                            entry.appearance(), entry.motion()));
                }
                emissions.add(new AbilityVisualDefinition.Emission(emission.id(), emission.actionIndex(), primitives));
            }
            bindings.add(new AbilityVisualDefinition.HookBinding(hook.hook(), emissions));
        }
        return new AbilityVisualDefinition(base.schemaVersion(), base.id(), bindings);
    }

    private static AbilityVisualDefinition decodeBaseVisual(byte[] body) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(1);
            out.writeByte(SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION.ordinal());
            out.writeLong(1);
            out.writeLong(0);
            out.writeLong(0);
            string(out, "projects:v3");
            out.writeLong(0);
            string(out, "base");
            string(out, "effective");
            out.writeBoolean(true);
            out.write(body);
            out.flush();
            return SkillVfxEditorProtocol.decodeRequest(raw.toByteArray()).visual();
        } catch (IOException e) {
            throw bad("visual body", e);
        }
    }

    private static List<AbilityVisualDefinition.PrimitiveSpec> primitives(AbilityVisualDefinition visual) {
        List<AbilityVisualDefinition.PrimitiveSpec> result = new ArrayList<>();
        for (AbilityVisualDefinition.HookBinding hook : visual.bindings()) {
            for (AbilityVisualDefinition.Emission emission : hook.emissions()) result.addAll(emission.primitives());
        }
        return result;
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        if (value == null) throw new IOException("string");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) throw new IOException("string");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String string(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING) throw new IOException("string");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException("utf8");
        return value;
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
