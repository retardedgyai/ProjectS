package io.github.gyai.projects.ability.editor;

import io.github.gyai.projects.ability.AbilityDefinition;
import io.github.gyai.projects.ability.AbilityLifecycleEvent;
import io.github.gyai.projects.ability.AbilityRuntime;
import io.github.gyai.projects.ability.AbilityVisualDefinition;
import io.github.gyai.projects.ability.AbilityVisualRegistry;
import io.github.gyai.projects.ability.AnchorFrame;
import io.github.gyai.projects.ability.DevAbilityDefinitions;
import io.github.gyai.projects.ability.MotionDirection;
import io.github.gyai.projects.ability.MotionEasing;
import io.github.gyai.projects.ability.MotionMode;
import io.github.gyai.projects.ability.MotionSpec;
import io.github.gyai.projects.network.AbilityVfxPacket;
import io.github.gyai.projects.network.AbilityVfxPacketV2;
import io.github.gyai.projects.network.SkillVfxEditorChannel;
import io.github.gyai.projects.network.SkillVfxEditorChannelV2;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Focused server-side Motion domain, hidden merge, v3 transport, and runtime tests. */
public final class SkillVfxMotionFoundationTest {
    private static final String ABILITY = "projects:motion-test";
    private static final String VISUAL = "projects:vfx/motion-test";

    public static void main(String[] ignored) {
        legacyDefaultsAndCapabilityMatrix();
        boundsEasingAndModeRules();
        visualWidePrimitiveIdValidation();
        hiddenMotionMergeAndTypeRejection();
        hiddenMotionNewIdsAndChannelApply();
        editorV3RoundTripAndStrictness();
        editorV3StateAndFetchRoundTrip();
        runtimeV3RoundTripAndLegacyFiltering();
        System.out.println("skill vfx motion foundation assertions=67");
    }

    private static void legacyDefaultsAndCapabilityMatrix() {
        AbilityVisualDefinition.PrimitiveSpec legacy = primitive(AbilityVisualDefinition.PrimitiveType.SPIRAL,
                MotionSpec.LEGACY_DEFAULT);
        check(legacy.motion().equals(MotionSpec.LEGACY_DEFAULT), "old PrimitiveSpec constructor has legacy Motion");
        check(DevAbilityDefinitions.sharedArcaneBurst().id().equals("projects:dev-shared-arcane-burst"), "existing DSL definition remains available");
        for (AbilityVisualDefinition.PrimitiveType type : AbilityVisualDefinition.PrimitiveType.values()) {
            primitive(type, new MotionSpec(MotionMode.STATIC, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, 0));
            primitive(type, new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, .5, 0));
        }
        for (AbilityVisualDefinition.PrimitiveType type : new AbilityVisualDefinition.PrimitiveType[]{
                AbilityVisualDefinition.PrimitiveType.LINE, AbilityVisualDefinition.PrimitiveType.ARC,
                AbilityVisualDefinition.PrimitiveType.CIRCLE, AbilityVisualDefinition.PrimitiveType.SPIRAL,
                AbilityVisualDefinition.PrimitiveType.WAVE, AbilityVisualDefinition.PrimitiveType.BEZIER}) {
            primitive(type, new MotionSpec(MotionMode.REVEAL, MotionDirection.REVERSE, MotionEasing.EASE_OUT, .25, 0));
        }
        for (AbilityVisualDefinition.PrimitiveType type : new AbilityVisualDefinition.PrimitiveType[]{
                AbilityVisualDefinition.PrimitiveType.LINE, AbilityVisualDefinition.PrimitiveType.ARC,
                AbilityVisualDefinition.PrimitiveType.SPIRAL, AbilityVisualDefinition.PrimitiveType.WAVE,
                AbilityVisualDefinition.PrimitiveType.BEZIER}) {
            primitive(type, new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.EASE_IN_OUT, .75, .5));
            primitive(type, new MotionSpec(MotionMode.TRAVEL, MotionDirection.REVERSE, MotionEasing.LINEAR, 0, 1));
        }
        for (AbilityVisualDefinition.PrimitiveType type : new AbilityVisualDefinition.PrimitiveType[]{
                AbilityVisualDefinition.PrimitiveType.POINT, AbilityVisualDefinition.PrimitiveType.CONE,
                AbilityVisualDefinition.PrimitiveType.SPHERE, AbilityVisualDefinition.PrimitiveType.BURST}) {
            expect(IllegalArgumentException.class, () -> primitive(type,
                    new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, 0)));
        }
    }

    private static void boundsEasingAndModeRules() {
        for (double phase : new double[]{0, .25, .5, .75, 1}) {
            for (MotionEasing easing : MotionEasing.values()) {
                primitive(AbilityVisualDefinition.PrimitiveType.LINE,
                        new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, easing, phase, 0));
            }
        }
        for (double trail : new double[]{0, .5, 1}) {
            primitive(AbilityVisualDefinition.PrimitiveType.LINE,
                    new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, trail));
        }
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, Double.NaN, 0));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, Double.POSITIVE_INFINITY, 0));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, Double.NEGATIVE_INFINITY, 0));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, -.01, 0));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, -0.01));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, Double.NaN));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, Double.POSITIVE_INFINITY));
        expect(IllegalArgumentException.class, () -> new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, 1.01));
        expect(IllegalArgumentException.class, () -> primitive(AbilityVisualDefinition.PrimitiveType.LINE,
                new MotionSpec(MotionMode.STATIC, MotionDirection.REVERSE, MotionEasing.LINEAR, 0, 0)));
        expect(IllegalArgumentException.class, () -> primitive(AbilityVisualDefinition.PrimitiveType.LINE,
                new MotionSpec(MotionMode.REVEAL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, .1)));

        AbilityVisualDefinition legacy = visual(primitive(AbilityVisualDefinition.PrimitiveType.LINE, MotionSpec.LEGACY_DEFAULT));
        AbilityVisualDefinition moved = visual(primitive(AbilityVisualDefinition.PrimitiveType.LINE,
                new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, .25, .25)));
        check(!SkillVfxEditorService.fingerprint(legacy).equals(SkillVfxEditorService.fingerprint(moved)),
                "Motion-only change affects fingerprint");
    }

    private static void visualWidePrimitiveIdValidation() {
        expect(IllegalArgumentException.class, () -> duplicateAcrossEmissions());
        expect(IllegalArgumentException.class, () -> duplicateAcrossHooks());
        AbilityVisualDefinition unique = uniqueVisual();
        AbilityVisualRegistry registry = new AbilityVisualRegistry();
        registry.register(unique);
        check(registry.find(VISUAL).orElseThrow().equals(unique), "registry accepts unique visual-wide primitive ids");
        check(SkillVfxEditorProtocolV3.decodeVisual(SkillVfxEditorProtocolV3.encodeVisual(unique)).equals(unique),
                "unique ids survive v3 visual/FETCH encoding boundary");
    }

    private static void hiddenMotionMergeAndTypeRejection() {
        SkillVfxEditorService service = service();
        SkillVfxEditorService.Snapshot before = service.snapshot(ABILITY);
        AbilityVisualDefinition seeded = visual(primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                new MotionSpec(MotionMode.REVEAL, MotionDirection.REVERSE, MotionEasing.EASE_IN, .25, 0),
                AbilityVisualDefinition.Appearance.particle("minecraft:flame")));
        SkillVfxEditorService.Snapshot first = service.apply(service.serverSession(), ABILITY, before.revision(),
                before.baseFingerprint(), before.effectiveFingerprint(), seeded);
        AbilityVisualDefinition v1 = visual(primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD));
        SkillVfxEditorService.Snapshot second = service.applyV1(service.serverSession(), ABILITY, first.revision(),
                first.baseFingerprint(), first.effectiveFingerprint(), v1);
        check(p(second.effective()).motion().equals(seeded.bindings().getFirst().emissions().getFirst().primitives().getFirst().motion()),
                "v1 preserves hidden Motion");
        check(p(second.effective()).appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:flame")),
                "v1 preserves hidden Appearance");
        AbilityVisualDefinition v2 = visual(primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.particle("minecraft:cloud")));
        SkillVfxEditorService.Snapshot third = service.applyV2(service.serverSession(), ABILITY, second.revision(),
                second.baseFingerprint(), second.effectiveFingerprint(), v2);
        check(p(third.effective()).motion().equals(p(second.effective()).motion()), "v2 preserves hidden Motion");
        check(p(third.effective()).appearance().equals(AbilityVisualDefinition.Appearance.particle("minecraft:cloud")),
                "v2 accepts visible Appearance");

        AbilityVisualDefinition incompatible = visual(primitive("stable", AbilityVisualDefinition.PrimitiveType.POINT,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD));
        expect(IllegalArgumentException.class, () -> SkillVfxEditorService.mergeV1Appearance(third.effective(), incompatible));
    }

    private static void hiddenMotionNewIdsAndChannelApply() {
        SkillVfxEditorService v1Service = service();
        SkillVfxEditorService.Snapshot v1Before = v1Service.snapshot(ABILITY);
        AbilityVisualDefinition v1Candidate = visualWithPrimitives(List.of(
                primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE, MotionSpec.LEGACY_DEFAULT,
                        AbilityVisualDefinition.Appearance.DEBUG_QUAD),
                primitive("new-v1", AbilityVisualDefinition.PrimitiveType.LINE,
                        new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, .5),
                        AbilityVisualDefinition.Appearance.DEBUG_QUAD)));
        byte[][] v1Response = new byte[1][];
        SkillVfxEditorChannel.Sender sender = sender(v1Response);
        SkillVfxEditorChannel.dispatch(SkillVfxEditorProtocol.encodeRequest(new SkillVfxEditorProtocol.Request(
                SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION, 41, v1Service.serverSession(), ABILITY,
                v1Before.revision(), v1Before.baseFingerprint(), v1Before.effectiveFingerprint(), v1Candidate)), sender, v1Service);
        check(v1Response[0] != null, "v1 channel returns apply response");
        SkillVfxEditorProtocol.State v1State = SkillVfxEditorProtocol.decodeState(v1Response[0]);
        check(v1State.snapshot() != null && find(v1State.snapshot().effective(), "new-v1").motion().isLegacyDefault(),
                "v1 channel gives new primitive the legacy Motion default");

        SkillVfxEditorService v2Service = service();
        SkillVfxEditorService.Snapshot v2Before = v2Service.snapshot(ABILITY);
        AbilityVisualDefinition v2Candidate = visualWithPrimitives(List.of(
                primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE, MotionSpec.LEGACY_DEFAULT,
                        AbilityVisualDefinition.Appearance.DEBUG_QUAD),
                primitive("new-v2", AbilityVisualDefinition.PrimitiveType.LINE,
                        new MotionSpec(MotionMode.TRAVEL, MotionDirection.REVERSE, MotionEasing.EASE_OUT, .25, 1),
                        AbilityVisualDefinition.Appearance.particle("minecraft:cloud"))));
        byte[][] v2Response = new byte[1][];
        SkillVfxEditorChannel.Sender senderV2 = sender(v2Response);
        SkillVfxEditorChannelV2.dispatch(SkillVfxEditorProtocolV2.encodeRequest(new SkillVfxEditorProtocol.Request(
                SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION, 42, v2Service.serverSession(), ABILITY,
                v2Before.revision(), v2Before.baseFingerprint(), v2Before.effectiveFingerprint(), v2Candidate)), senderV2, v2Service);
        check(v2Response[0] != null, "v2 channel returns apply response");
        SkillVfxEditorProtocol.State v2State = SkillVfxEditorProtocolV2.decodeState(v2Response[0]);
        check(v2State.snapshot() != null && find(v2State.snapshot().effective(), "new-v2").motion().isLegacyDefault(),
                "v2 channel gives new primitive the legacy Motion default");
    }

    private static void editorV3RoundTripAndStrictness() {
        AbilityVisualDefinition value = visual(primitive("line", AbilityVisualDefinition.PrimitiveType.LINE,
                new MotionSpec(MotionMode.TRAVEL, MotionDirection.REVERSE, MotionEasing.EASE_IN_OUT, .5, .25),
                AbilityVisualDefinition.Appearance.particle("minecraft:flame")));
        byte[] encoded = SkillVfxEditorProtocolV3.encodeVisual(value);
        AbilityVisualDefinition decoded = SkillVfxEditorProtocolV3.decodeVisual(encoded);
        check(decoded.equals(value), "Editor v3 visual round trip");
        SkillVfxEditorProtocol.Request request = new SkillVfxEditorProtocol.Request(
                SkillVfxEditorProtocol.Operation.APPLY_VISUAL_SESSION, 7, UUID.randomUUID(), ABILITY, 0,
                "base", "effective", value);
        check(SkillVfxEditorProtocolV3.decodeRequest(SkillVfxEditorProtocolV3.encodeRequest(request)).visual().equals(value),
                "Editor v3 request round trip");
        expect(IllegalArgumentException.class, () -> SkillVfxEditorProtocolV3.decodeVisual(Arrays.copyOf(encoded, encoded.length + 1)));
        byte[] unknown = encoded.clone();
        unknown[motionModeOffset(unknown)] = (byte) 127;
        expect(IllegalArgumentException.class, () -> SkillVfxEditorProtocolV3.decodeVisual(unknown));
    }

    private static void editorV3StateAndFetchRoundTrip() {
        SkillVfxEditorService service = service(uniqueVisual());
        SkillVfxEditorProtocol.Request fetch = new SkillVfxEditorProtocol.Request(
                SkillVfxEditorProtocol.Operation.FETCH, 8, service.serverSession(), ABILITY, 0, "", "", null);
        SkillVfxEditorProtocol.Request fetchRoundTrip = SkillVfxEditorProtocolV3.decodeRequest(
                SkillVfxEditorProtocolV3.encodeRequest(fetch));
        check(fetchRoundTrip.operation() == SkillVfxEditorProtocol.Operation.FETCH && fetchRoundTrip.visual() == null,
                "v3 FETCH keeps a zero-table request");
        SkillVfxEditorService.Snapshot snapshot = service.snapshot(ABILITY);
        SkillVfxEditorProtocol.State state = new SkillVfxEditorProtocol.State(
                SkillVfxEditorProtocol.Status.OK, 8, service.serverSession(), service.catalog(), snapshot, true, "ok");
        SkillVfxEditorProtocol.State roundTrip = SkillVfxEditorProtocolV3.decodeState(
                SkillVfxEditorProtocolV3.encodeState(state));
        check(roundTrip.snapshot() != null && roundTrip.snapshot().base().equals(snapshot.base())
                        && roundTrip.snapshot().effective().equals(snapshot.effective()),
                "v3 state preserves unique primitive ids and Motion tables");
    }

    private static void runtimeV3RoundTripAndLegacyFiltering() {
        AnchorFrame anchor = new AnchorFrame(UUID.randomUUID(), "minecraft:overworld", 1, 2, 3, 0, 0, 1, 0, 1, 0);
        AbilityVfxPacket.Primitive legacy = new AbilityVfxPacket.Primitive(
                AbilityVisualDefinition.PrimitiveType.LINE, 0, 10, 0xff00ffff, 1, 4, 11,
                new AbilityVisualDefinition.Vec(0, 0, 0), 0, 0, 0, 2, 0, 0, 0, 0, 0, 0,
                List.of(), AbilityVisualDefinition.Appearance.DEBUG_QUAD, MotionSpec.LEGACY_DEFAULT);
        AbilityVfxPacket.Primitive moved = new AbilityVfxPacket.Primitive(
                AbilityVisualDefinition.PrimitiveType.LINE, 0, 10, 0xffff00ff, 1, 4, 12,
                new AbilityVisualDefinition.Vec(0, 0, 0), 0, 0, 0, 2, 0, 0, 0, 0, 0, 0,
                List.of(), AbilityVisualDefinition.Appearance.particle("minecraft:flame"),
                new MotionSpec(MotionMode.TRAVEL, MotionDirection.FORWARD, MotionEasing.LINEAR, 0, .5));
        AbilityVfxPacket.Cue cue = new AbilityVfxPacket.Cue(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                "projects:vfx/motion-test", AbilityLifecycleEvent.Hook.CAST, -1, 0, 1, 1, 10, anchor,
                List.of(legacy, moved));
        byte[] bytes = AbilityVfxPacketV2.encode(cue);
        AbilityVfxPacket.Cue decoded = AbilityVfxPacketV2.decode(bytes);
        check(decoded.primitives().size() == 2 && decoded.primitives().get(1).motion().equals(moved.motion()),
                "Runtime v3 primitive Motion round trip");
        check(bytes[0] == AbilityVfxPacketV2.VERSION && bytes[bytes.length - 1] == bytes[bytes.length - 1],
                "Runtime v2 channel/version exists");
        check(AbilityVfxPacket.legacyOnly(cue).orElseThrow().primitives().size() == 1,
                "old-client fallback filters nonlegacy Motion");
        AbilityVfxPacket.Cue onlyMoved = new AbilityVfxPacket.Cue(cue.session(), cue.sequence(), cue.cueId(), cue.castId(),
                cue.visualId(), cue.hook(), cue.actionIndex(), cue.emissionIndex(), cue.serverTickAtSend(), cue.startTick(),
                cue.cueDurationTicks(), cue.anchor(), List.of(moved));
        check(AbilityVfxPacket.legacyOnly(onlyMoved).isEmpty(), "empty legacy fallback cue is omitted");
    }

    private static SkillVfxEditorService service() {
        return service(visual(primitive("stable", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD)));
    }

    private static SkillVfxEditorService service(AbilityVisualDefinition visual) {
        io.github.gyai.projects.ability.AbilityRegistry abilities = new io.github.gyai.projects.ability.AbilityRegistry(AbilityRuntime.standardActions());
        abilities.register(new AbilityDefinition(1, ABILITY, "Motion test", List.of(new AbilityDefinition.Wait(1))));
        AbilityVisualRegistry visuals = new AbilityVisualRegistry();
        visuals.register(visual);
        visuals.bind(new io.github.gyai.projects.ability.AbilityVisualBinding(ABILITY, VISUAL));
        return new SkillVfxEditorService(abilities, visuals);
    }

    private static AbilityVisualDefinition visual(AbilityVisualDefinition.PrimitiveSpec primitive) {
        return visualWithPrimitives(List.of(primitive));
    }

    private static AbilityVisualDefinition visualWithPrimitives(List<AbilityVisualDefinition.PrimitiveSpec> primitives) {
        return new AbilityVisualDefinition(1, VISUAL, List.of(new AbilityVisualDefinition.HookBinding(
                AbilityLifecycleEvent.Hook.CAST, List.of(new AbilityVisualDefinition.Emission("motion", -1, primitives)))));
    }

    private static AbilityVisualDefinition duplicateAcrossEmissions() {
        AbilityVisualDefinition.PrimitiveSpec duplicate = primitive("duplicate", AbilityVisualDefinition.PrimitiveType.LINE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD);
        return new AbilityVisualDefinition(1, VISUAL, List.of(new AbilityVisualDefinition.HookBinding(
                AbilityLifecycleEvent.Hook.CAST, List.of(
                        new AbilityVisualDefinition.Emission("first", -1, List.of(duplicate)),
                        new AbilityVisualDefinition.Emission("second", -1, List.of(duplicate))))));
    }

    private static AbilityVisualDefinition duplicateAcrossHooks() {
        AbilityVisualDefinition.PrimitiveSpec duplicate = primitive("duplicate", AbilityVisualDefinition.PrimitiveType.LINE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD);
        return new AbilityVisualDefinition(1, VISUAL, List.of(
                new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.CAST,
                        List.of(new AbilityVisualDefinition.Emission("cast", -1, List.of(duplicate)))),
                new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.HIT,
                        List.of(new AbilityVisualDefinition.Emission("hit", -1, List.of(duplicate))))));
    }

    private static AbilityVisualDefinition uniqueVisual() {
        AbilityVisualDefinition.PrimitiveSpec cast = primitive("unique-cast", AbilityVisualDefinition.PrimitiveType.LINE,
                MotionSpec.LEGACY_DEFAULT, AbilityVisualDefinition.Appearance.DEBUG_QUAD);
        AbilityVisualDefinition.PrimitiveSpec hit = primitive("unique-hit", AbilityVisualDefinition.PrimitiveType.CIRCLE,
                new MotionSpec(MotionMode.REVEAL, MotionDirection.REVERSE, MotionEasing.LINEAR, .5, 0),
                AbilityVisualDefinition.Appearance.particle("minecraft:flame"));
        return new AbilityVisualDefinition(1, VISUAL, List.of(
                new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.CAST,
                        List.of(new AbilityVisualDefinition.Emission("cast", -1, List.of(cast)))),
                new AbilityVisualDefinition.HookBinding(AbilityLifecycleEvent.Hook.HIT,
                        List.of(new AbilityVisualDefinition.Emission("hit", -1, List.of(hit))))));
    }

    private static AbilityVisualDefinition.PrimitiveSpec find(AbilityVisualDefinition visual, String id) {
        return visual.bindings().stream().flatMap(h -> h.emissions().stream())
                .flatMap(e -> e.primitives().stream()).filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static SkillVfxEditorChannel.Sender sender(byte[][] response) {
        return new SkillVfxEditorChannel.Sender() {
            @Override public boolean hasPermission(String permission) { return true; }
            @Override public void send(byte[] payload) { response[0] = payload; }
        };
    }

    private static AbilityVisualDefinition.PrimitiveSpec p(AbilityVisualDefinition visual) {
        return visual.bindings().getFirst().emissions().getFirst().primitives().getFirst();
    }

    private static AbilityVisualDefinition.PrimitiveSpec primitive(AbilityVisualDefinition.PrimitiveType type, MotionSpec motion) {
        return primitive(type.name().toLowerCase(), type, motion, AbilityVisualDefinition.Appearance.DEBUG_QUAD);
    }

    private static AbilityVisualDefinition.PrimitiveSpec primitive(String id, AbilityVisualDefinition.PrimitiveType type,
                                                                    MotionSpec motion, AbilityVisualDefinition.Appearance appearance) {
        AbilityVisualDefinition.Scalar size = null, radius = null, length = null, height = null, angle = null,
                start = null, sweep = null, turns = null;
        int count = 0;
        List<AbilityVisualDefinition.Vec> points = List.of();
        switch (type) {
            case POINT -> size = new AbilityVisualDefinition.Literal(1);
            case LINE -> length = new AbilityVisualDefinition.Literal(2);
            case ARC -> { radius = new AbilityVisualDefinition.Literal(1); sweep = new AbilityVisualDefinition.Literal(1); }
            case CIRCLE, SPHERE -> radius = new AbilityVisualDefinition.Literal(1);
            case CONE -> { length = new AbilityVisualDefinition.Literal(2); angle = new AbilityVisualDefinition.Literal(.5); }
            case SPIRAL -> { radius = new AbilityVisualDefinition.Literal(1); height = new AbilityVisualDefinition.Literal(0); turns = new AbilityVisualDefinition.Literal(1); }
            case WAVE -> { length = new AbilityVisualDefinition.Literal(2); radius = new AbilityVisualDefinition.Literal(1); height = new AbilityVisualDefinition.Literal(0); }
            case BEZIER -> points = List.of(new AbilityVisualDefinition.Vec(0, 0, 0), new AbilityVisualDefinition.Vec(1, 0, 0), new AbilityVisualDefinition.Vec(2, 0, 0));
            case BURST -> { radius = new AbilityVisualDefinition.Literal(1); count = 2; }
        }
        return new AbilityVisualDefinition.PrimitiveSpec(id, type, 0, 10, 0xffffffff, 1, 1, 1,
                new AbilityVisualDefinition.Vec(0, 0, 0), 0, size, radius, length, height, angle, start, sweep,
                turns, count, points, appearance, motion);
    }

    private static int motionModeOffset(byte[] encoded) {
        int body = ByteBuffer.wrap(encoded, 1, 2).getShort() & 0xffff;
        int offset = 3 + body + 1 + 2;
        int id = ByteBuffer.wrap(encoded, offset, 2).getShort() & 0xffff;
        offset += 2 + id + 1;
        int appearance = ByteBuffer.wrap(encoded, offset, 2).getShort() & 0xffff;
        return offset + 2 + appearance;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); throw new AssertionError("expected " + type.getSimpleName()); }
        catch (Throwable error) { if (!type.isInstance(error)) throw new AssertionError("expected " + type.getSimpleName(), error); }
    }
}
