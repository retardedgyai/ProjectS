package io.github.gyai.projects.beta;

import io.github.gyai.projects.ProjectSPlugin;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.monster.definition.v2.MobV2FoundationTest;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Service;
import io.github.gyai.projects.network.beta.BetaCapabilityAcknowledgement;
import io.github.gyai.projects.network.beta.BetaCapabilityAdvertisement;
import io.github.gyai.projects.network.beta.BetaCapabilityDescriptor;
import io.github.gyai.projects.network.beta.BetaCapabilityId;
import io.github.gyai.projects.network.beta.BetaChannels;
import io.github.gyai.projects.network.beta.BetaCommandEnvelope;
import io.github.gyai.projects.network.beta.BetaCommandResult;
import io.github.gyai.projects.network.beta.BetaDecodedCommand;
import io.github.gyai.projects.network.beta.BetaMessageEnvelope;
import io.github.gyai.projects.network.beta.BetaMessageKind;
import io.github.gyai.projects.network.beta.BetaProtocolCodec;
import io.github.gyai.projects.network.beta.BetaProtocolDecodeResult;
import io.github.gyai.projects.network.beta.BetaProtocolFoundationTest;
import io.github.gyai.projects.network.beta.BetaProtocolLimits;
import io.github.gyai.projects.network.beta.BetaProtocolVersion;
import io.github.gyai.projects.network.beta.MobEditorCommandPort;
import io.github.gyai.projects.network.beta.MobEditorDisplayPort;
import io.github.gyai.projects.network.beta.MobEditorDisplaySnapshot;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Wave3IntegratedFoundationTest {
    private static final UUID SESSION =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Pattern VECTOR = Pattern.compile(
            "\\\"name\\\": \\\"([^\\\"]+)\\\"\\s*,\\s*"
                    + "\\\"classification\\\": \\\"([^\\\"]+)\\\"\\s*,\\s*"
                    + "\\\"packetHex\\\": \\\"([0-9a-f]+)\\\"");

    private Wave3IntegratedFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        MobV2FoundationTest.main(args);
        BetaProtocolFoundationTest.main(args);
        schemaAndFlagsRemainDisabled();
        manifestMatchesServerConstants();
        goldenVectorsMatchServerCodec();
        mobEditorPortsRemainAdapterOnly();
        gameplayRemainsDisconnected();
    }

    private static void schemaAndFlagsRemainDisabled() {
        assert SchemaVersions.currentVersion(SchemaId.MOB_DEFINITION).orElseThrow() == 2;
        assert SchemaVersions.supportedReadVersions(SchemaId.MOB_DEFINITION)
                .equals(Set.of(1, 2));
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.currentVersion(SchemaId.CLIENT_PROTOCOL).orElseThrow() == 1;
        FeatureFlagSnapshot disabled = FeatureFlagSnapshot.allDisabled();
        for (FeatureKey key : FeatureKey.values()) assert !disabled.isEnabled(key);
        assert !disabled.isEnabled(FeatureKey.MOB_EDITOR_V2);
        assert !disabled.isEnabled(FeatureKey.CLIENT_BETA_UI);
    }

    private static void manifestMatchesServerConstants() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("docs/protocol/beta-protocol-v1.json"));
        String manifest = new String(bytes, StandardCharsets.UTF_8);
        assert bytes.length > 0 && bytes[bytes.length - 1] == '\n';
        assert manifest.indexOf('\r') < 0;
        assert manifest.equals(canonicalManifest());
        assert BetaProtocolVersion.CURRENT == integer(manifest, "aggregateProtocolVersion");
        assert BetaChannels.CAPABILITIES.equals(string(manifest, "capabilities"));
        assert BetaChannels.ACKNOWLEDGEMENT.equals(string(manifest, "acknowledgement"));
        assert BetaChannels.STATE.equals(string(manifest, "state"));
        assert BetaChannels.COMMAND.equals(string(manifest, "command"));
        for (BetaCapabilityId capability : BetaCapabilityId.values()) {
            assert integer(manifest, capability.id()) == 1;
        }
        BetaProtocolLimits limits = BetaProtocolLimits.DEFAULTS;
        assert limits.handshakeBytes() == integer(manifest, "handshakeBytes");
        assert limits.packetBytes() == integer(manifest, "packetBytes");
        assert limits.stringBytes() == integer(manifest, "stringBytes");
        assert limits.canonicalIdBytes() == integer(manifest, "canonicalIdBytes");
        assert limits.listEntries() == integer(manifest, "listEntries");
        assert limits.mapEntries() == integer(manifest, "mapEntries");
        assert limits.mobEditorPageEntries() == integer(manifest, "mobEditorListPage");
    }

    private static void goldenVectorsMatchServerCodec() throws Exception {
        Map<String, Vector> vectors = loadVectors();
        assert vectors.size() == 13;
        BetaProtocolCodec codec = new BetaProtocolCodec();
        List<BetaCapabilityDescriptor> capabilities = Arrays.stream(BetaCapabilityId.values())
                .map(BetaCapabilityDescriptor::v1).toList();
        BetaCapabilityAdvertisement advertisement = new BetaCapabilityAdvertisement(
                1, SESSION, 7, capabilities);
        assertPacket(vectors, "capability-advertisement", codec.encode(advertisement));
        assert codec.decodeAdvertisement(packet(vectors, "capability-advertisement"))
                .value().equals(advertisement);
        BetaCapabilityAcknowledgement acknowledgement = new BetaCapabilityAcknowledgement(
                1, SESSION, 7, capabilities);
        assertPacket(vectors, "capability-acknowledgement", codec.encode(acknowledgement));
        assert codec.decodeAcknowledgement(packet(vectors, "capability-acknowledgement"))
                .value().equals(acknowledgement);

        verifyState(codec, vectors, "hud-state", BetaCapabilityId.HUD,
                document(1, 1, "hud", fields("level", "12", "xp", "34",
                        "class-id", "warrior"), List.of()));
        verifyState(codec, vectors, "party-state", BetaCapabilityId.PARTY,
                document(2, 1, "party", fields("party-id", "projects:test-party",
                        "leader", "player-a", "members", "2"), List.of()));
        verifyState(codec, vectors, "element-state", BetaCapabilityId.ELEMENTS,
                document(3, 1, "elements", fields("target-network-id", "42",
                        "fire-gauge", "0.5", "fire-stacks", "1",
                        "cold-gauge", "0.25"), List.of()));
        verifyState(codec, vectors, "equipment-state", BetaCapabilityId.EQUIPMENT,
                document(4, 1, "equipment", fields("tier", "T1", "item-level", "3",
                        "rarity", "COMMON", "quality", "1.0"), List.of()));
        verifyState(codec, vectors, "crafting-unavailable-balance-state",
                BetaCapabilityId.CRAFTING, document(5, 3, "UNAVAILABLE_BALANCE_DATA",
                        fields("preview-status", "UNAVAILABLE_BALANCE_DATA"), List.of()));
        verifyState(codec, vectors, "enhancement-unavailable-balance-state",
                BetaCapabilityId.ENHANCEMENT, document(6, 3, "UNAVAILABLE_BALANCE_DATA",
                        fields("preview-status", "UNAVAILABLE_BALANCE_DATA"), List.of()));
        verifyState(codec, vectors, "mob-editor-list-page", BetaCapabilityId.MOB_EDITOR_V2,
                document(7, 1, "mob-list", fields("page", "0", "schema-version", "2"),
                        List.of("projects:test-mob", "projects:test-boss")));

        byte[] conflictPayload = document(8, 4, "revision-conflict",
                fields("base-revision", "7", "current-revision", "8",
                        "conflict", "true"), List.of());
        BetaMessageEnvelope conflict = new BetaMessageEnvelope(1,
                BetaMessageKind.COMMAND_RESULT, BetaCapabilityId.MOB_EDITOR_V2, 1,
                REQUEST, conflictPayload);
        assertPacket(vectors, "mob-editor-conflict-result", codec.encode(conflict));
        assert codec.decodeMessage(packet(vectors, "mob-editor-conflict-result"))
                .value().kind() == BetaMessageKind.COMMAND_RESULT;

        verifyCommand(codec, vectors, "valid-command", 8);
        verifyCommand(codec, vectors, "stale-revision-command", 7);
        assert codec.decodeMessage(packet(vectors, "malformed-trailing-byte")).status()
                == BetaProtocolDecodeResult.Status.MALFORMED;

        byte[] unknown = packet(vectors, "hud-state").clone();
        byte[] knownId = "projects:hud".getBytes(StandardCharsets.UTF_8);
        byte[] unknownId = "projects:bad".getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(unknown, knownId);
        assert offset >= 0;
        System.arraycopy(unknownId, 0, unknown, offset, unknownId.length);
        assert codec.decodeMessage(unknown).status()
                == BetaProtocolDecodeResult.Status.UNKNOWN_CAPABILITY;
    }

    private static void verifyState(
            BetaProtocolCodec codec,
            Map<String, Vector> vectors,
            String name,
            BetaCapabilityId capability,
            byte[] payload
    ) {
        BetaMessageEnvelope expected = new BetaMessageEnvelope(
                1, BetaMessageKind.STATE, capability, 1, SESSION, payload);
        assertPacket(vectors, name, codec.encode(expected));
        BetaMessageEnvelope decoded = codec.decodeMessage(packet(vectors, name)).value();
        assert decoded.kind() == BetaMessageKind.STATE;
        assert decoded.capabilityId() == capability;
        assert decoded.requestOrSessionId().equals(SESSION);
        assert Arrays.equals(decoded.payload(), payload);
    }

    private static void verifyCommand(
            BetaProtocolCodec codec,
            Map<String, Vector> vectors,
            String name,
            long targetRevision
    ) {
        BetaCommandEnvelope expected = new BetaCommandEnvelope(
                new BetaMessageEnvelope(1, BetaMessageKind.COMMAND,
                        BetaCapabilityId.MOB_EDITOR_V2, 1, SESSION,
                        "save".getBytes(StandardCharsets.UTF_8)),
                7, targetRevision, REQUEST);
        assertPacket(vectors, name, codec.encode(expected));
        BetaCommandEnvelope decoded = codec.decodeCommand(packet(vectors, name)).value();
        assert decoded.playerSessionRevision() == 7;
        assert decoded.targetContentRevision() == targetRevision;
        assert decoded.idempotencyRequestId().equals(REQUEST);
    }

    private static void mobEditorPortsRemainAdapterOnly() {
        MobEditorV2Service.ListResult empty = new MobEditorV2Service.ListResult(
                new MobEditorV2Service.Result(MobEditorV2Service.Status.OK, "ok"),
                List.of(), 0);
        MobEditorAdapter adapter = new MobEditorAdapter(empty);
        MobEditorDisplayPort displayPort = adapter;
        MobEditorCommandPort commandPort = adapter;
        MobEditorDisplaySnapshot snapshot = displayPort.snapshot(UUID.randomUUID()).orElseThrow();
        assert snapshot.schemaVersion() == 2 && snapshot.entries().isEmpty();
        AtomicInteger repositoryMutations = new AtomicInteger();
        BetaDecodedCommand decoded = new BetaDecodedCommand(
                BetaCapabilityId.MOB_EDITOR_V2, "save", REQUEST, 7, 8,
                Map.of("mob-id", "projects:test-mob"), List.of());
        BetaCommandResult result = commandPort.handle(null, decoded);
        assert result.status() == BetaCommandResult.Status.PERMISSION_DENIED;
        assert repositoryMutations.get() == 0;
        assert MobEditorDisplayPort.class.getPackageName()
                .equals("io.github.gyai.projects.network.beta");
        assert MobEditorV2Service.class.getPackageName()
                .equals("io.github.gyai.projects.monster.editor.v2");
    }

    private static void gameplayRemainsDisconnected() throws IOException {
        String plugin = Files.readString(Path.of(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java"));
        for (String forbidden : List.of(
                "BetaProtocolCodec", "BetaCapabilitySessionService", "MobEditorV2Service",
                "projects:beta_caps_v1", "projects:beta_state_v1")) {
            assert !plugin.contains(forbidden);
        }
        assert ProjectSPlugin.class.getDeclaredFields().length > 0;
    }

    private static Map<String, Vector> loadVectors() throws IOException {
        String json = Files.readString(Path.of(
                "src/test/resources/protocol/beta-protocol-v1-vectors.json"),
                StandardCharsets.UTF_8);
        assert json.indexOf('\r') < 0 && json.endsWith("\n");
        LinkedHashMap<String, Vector> result = new LinkedHashMap<>();
        Matcher matcher = VECTOR.matcher(json);
        while (matcher.find()) {
            Vector vector = new Vector(matcher.group(2),
                    HexFormat.of().parseHex(matcher.group(3)));
            assert result.putIfAbsent(matcher.group(1), vector) == null;
        }
        return Map.copyOf(result);
    }

    private static byte[] document(
            long revision,
            int status,
            String message,
            Map<String, String> fields,
            List<String> entries
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeLong(revision);
                output.writeByte(status);
                writeString(output, message);
                output.writeShort(fields.size());
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    writeString(output, entry.getKey());
                    writeString(output, entry.getValue());
                }
                output.writeShort(entries.size());
                for (String entry : entries) writeString(output, entry);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static Map<String, String> fields(String... values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static byte[] packet(Map<String, Vector> vectors, String name) {
        return vectors.get(name).packet().clone();
    }

    private static void assertPacket(Map<String, Vector> vectors, String name, byte[] actual) {
        assert Arrays.equals(packet(vectors, name), actual) : name;
    }

    private static int indexOf(byte[] source, byte[] target) {
        outer: for (int index = 0; index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) continue outer;
            }
            return index;
        }
        return -1;
    }

    private static String string(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        assert matcher.find() : key;
        return matcher.group(1);
    }

    private static int integer(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        assert matcher.find() : key;
        return Integer.parseInt(matcher.group(1));
    }

    private static String canonicalManifest() {
        return """
                {
                  "aggregateProtocolVersion": 1,
                  "channels": {
                    "capabilities": "projects:beta_caps_v1",
                    "acknowledgement": "projects:beta_ack_v1",
                    "state": "projects:beta_state_v1",
                    "command": "projects:beta_command_v1"
                  },
                  "capabilities": {
                    "projects:hud": 1,
                    "projects:party": 1,
                    "projects:elements": 1,
                    "projects:equipment": 1,
                    "projects:crafting": 1,
                    "projects:enhancement": 1,
                    "projects:mob-editor-v2": 1
                  },
                  "bounds": {
                    "handshakeBytes": 8192,
                    "packetBytes": 32768,
                    "stringBytes": 256,
                    "canonicalIdBytes": 128,
                    "listEntries": 128,
                    "mapEntries": 64,
                    "mobEditorListPage": 50
                  },
                  "opcodes": {
                    "advertisement": 1,
                    "acknowledgement": 2,
                    "state": 3,
                    "command": 4,
                    "commandResult": 5
                  }
                }
                """;
    }

    private record Vector(String classification, byte[] packet) {
        private Vector {
            packet = packet.clone();
        }

        @Override public byte[] packet() {
            return packet.clone();
        }
    }

    private static final class MobEditorAdapter
            implements MobEditorDisplayPort, MobEditorCommandPort {
        private final MobEditorV2Service.ListResult list;

        private MobEditorAdapter(MobEditorV2Service.ListResult list) {
            this.list = list;
        }

        @Override public Optional<MobEditorDisplaySnapshot> snapshot(UUID playerId) {
            List<MobEditorDisplaySnapshot.Summary> entries = list.definitions().stream()
                    .map(value -> new MobEditorDisplaySnapshot.Summary(
                            value.mobId(), value.revision(), value.mobId()))
                    .toList();
            return Optional.of(new MobEditorDisplaySnapshot(
                    2, 0, list.page(), entries, null, List.of(),
                    MobEditorDisplaySnapshot.OperationStatus.IDLE));
        }

        @Override public BetaCommandResult handle(
                io.github.gyai.projects.network.beta.BetaCommandContext context,
                BetaDecodedCommand command
        ) {
            return new BetaCommandResult(BetaCommandResult.Status.PERMISSION_DENIED,
                    command.requestId(), "integration adapter requires router authorization", true);
        }
    }
}
