package io.github.gyai.projects.network;

import io.github.gyai.projects.ability.AbilityRegistry;
import io.github.gyai.projects.ability.AbilityRuntime;
import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobAppearanceDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobEditorManager;
import org.bukkit.entity.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import sun.misc.Unsafe;

/** Executable production codec regression suite; assertions run under {@code -ea}. */
public final class MobEditorV2ProtocolTest {
    private static final Path FIXTURE = Path.of(
            "src/test/resources/protocol/mob-editor-v2-fixture.txt");

    public static void main(String[] args) throws Exception {
        MobDefinition detail = MobDefinition.create("projects:test")
                .withRevision(7)
                .withAbilityIds(List.of("projects:stale", "projects:arcane_burst"));
        byte[] v1Save = v1Save(detail);
        byte[] v2Save = v2Save(detail);
        byte[] state = new MobEditorV2StatePacket(
                true, true, false, "ok", List.of(), detail, List.of(), null,
                List.of(
                        new MobEditorV2PacketIO.CatalogEntry(
                                "projects:arcane_burst", "Arcane"),
                        new MobEditorV2PacketIO.CatalogEntry("projects:zeta", "Zeta")))
                .encode();

        verifyFixture(v1Save, state, v2Save);
        requestBoundaryRejects(v2Save);
        mobRejects(detail);
        headRejects();
        catalogRejects();
        summaryTagsAreCappedAtEight();
        stateAggregateBoundRejects();
        closeThenReopenDoesNotLeakV2State();
        System.out.println("MobEditorV2ProtocolTest passed");
    }

    private static void verifyFixture(byte[] v1Save, byte[] state, byte[] v2Save)
            throws Exception {
        String fixture = Files.readString(FIXTURE);
        assert hex(v1Save).equals(value(fixture, "v1-mob-save-request"));
        assert hex(state).equals(value(fixture, "v2-server-state"));
        assert hex(v2Save).equals(value(fixture, "v2-client-save-request"));

        MobEditorV2RequestDecoder.Request decoded =
                MobEditorV2RequestDecoder.decode(v2Save);
        assert decoded.operation() == MobEditorChannel.SAVE_DRAFT;
        assert decoded.mob().revision() == 7;
        assert decoded.mob().abilityIds().equals(
                List.of("projects:stale", "projects:arcane_burst"));
        assert !decoded.cursor();

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(Arrays.copyOfRange(v1Save, 2, v1Save.length)))) {
            MobDefinition v1Decoded = MobEditorPacketIO.readMob(input);
            assert v1Decoded.abilityIds().isEmpty();
            assert input.available() == 0;
        }
    }

    private static void requestBoundaryRejects(byte[] valid) {
        expectRejected(() -> MobEditorV2RequestDecoder.decode(new byte[0]));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(new byte[] {2}));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(new byte[] {2, 5}));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(
                Arrays.copyOf(valid, valid.length - 1)));
        byte[] unsupportedVersion = valid.clone();
        unsupportedVersion[0] = 1;
        expectRejected(() -> MobEditorV2RequestDecoder.decode(unsupportedVersion));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(new byte[] {2, 99}));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        expectRejected(() -> MobEditorV2RequestDecoder.decode(trailing));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(
                new byte[MobEditorV2PacketIO.MAX_PAYLOAD_BYTES + 1]));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(
                new byte[] {2, (byte) MobEditorChannel.CONTROL_TEST_MOBS, (byte) 127}));
        expectRejected(() -> MobEditorV2RequestDecoder.decode(updateHeadFavorite("bad")));
    }

    private static void mobRejects(MobDefinition detail) throws Exception {
        byte[] validMob = v2Body(detail);
        byte[] invalidUtf8 = replace(validMob, "projects:stale".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte) 0xff, 'r', 'o', 'j', 'e', 'c', 't', 's', ':', 's', 't', 'a', 'l', 'e'});
        expectRejected(() -> readMob(invalidUtf8));

        byte[] invalidEnum = replace(validMob, "NORMAL".getBytes(StandardCharsets.UTF_8),
                "BAD\u0000\u0000\u0000".getBytes(StandardCharsets.UTF_8));
        expectRejected(() -> readMob(invalidEnum));

        byte[] invalidSchema = validMob.clone();
        invalidSchema[0] = 2;
        expectRejected(() -> readMob(invalidSchema));

        MobDefinition tagged = copy(detail, List.of("tag", "tag"), detail.appearance());
        expectRejected(() -> readMob(v1BodyThenAbilities(tagged, List.of())));

        LinkedHashMap<String, String> variants = new LinkedHashMap<>();
        variants.put("first", "one");
        variants.put("other", "two");
        MobAppearanceDefinition appearance = new MobAppearanceDefinition(
                detail.appearance().scale(), detail.appearance().age(),
                detail.appearance().glowing(), detail.appearance().glowingColor(), variants,
                detail.appearance().equipment());
        byte[] duplicateVariant = replace(v1BodyThenAbilities(
                copy(detail, detail.tags(), appearance), List.of()),
                "other".getBytes(StandardCharsets.UTF_8),
                "first".getBytes(StandardCharsets.UTF_8));
        expectRejected(() -> readMob(duplicateVariant));

        expectRejected(() -> readMob(v1BodyThenCount(detail, 65)));
        expectRejected(() -> readMob(v1BodyThenAbilities(detail,
                List.of("projects:stale", "projects:stale"))));
        expectRejected(() -> readMob(v1BodyThenAbilities(detail, List.of("bad"))));
        expectRejected(() -> readMob(Arrays.copyOf(validMob, validMob.length - 1)));
    }

    private static void catalogRejects() throws Exception {
        List<MobEditorV2PacketIO.CatalogEntry> valid = List.of(
                new MobEditorV2PacketIO.CatalogEntry("projects:arcane_burst", "Arcane"),
                new MobEditorV2PacketIO.CatalogEntry("projects:zeta", "Zeta"));
        byte[] encoded = catalog(valid);
        assert readCatalog(encoded).equals(valid);
        assert Arrays.equals(encoded, catalog(valid));

        expectRejected(() -> MobEditorV2PacketIO.writeCatalog(new DataOutputStream(
                new ByteArrayOutputStream()), List.of(
                new MobEditorV2PacketIO.CatalogEntry("projects:zeta", "Zeta"),
                new MobEditorV2PacketIO.CatalogEntry("projects:arcane_burst", "Arcane"))));
        expectRejected(() -> readCatalog(rawCatalog(2,
                List.of("projects:arcane_burst", "projects:arcane_burst"))));
        expectRejected(() -> readCatalog(rawCatalog(2,
                List.of("projects:zeta", "projects:arcane_burst"))));
        expectRejected(() -> readCatalog(rawCatalog(1, List.of("bad"))));
        expectRejected(() -> readCatalog(rawCatalog(129, List.of())));
    }

    private static void headRejects() throws IOException {
        HeadDefinition duplicateTags = new HeadDefinition(1, 0, "projects:head",
                "Head", HeadDefinition.SourceType.VANILLA_HEAD, "", "", "",
                List.of("tag", "tag"), false, "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(2);
            output.writeByte(MobEditorChannel.CREATE_HEAD);
            MobEditorPacketIO.writeHead(output, duplicateTags);
        }
        expectRejected(() -> MobEditorV2RequestDecoder.decode(bytes.toByteArray()));
        expectRejected(() -> new MobEditorV2StatePacket(true, true, false, "",
                List.of(), null, List.of(duplicateTags), null, List.of()).encode());
    }

    private static void stateAggregateBoundRejects() {
        List<MobDefinition> summaries = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            tags.add(String.format("tag%029d", index));
        }
        for (int index = 0; index < MobEditorPacketIO.MAX_MOBS; index++) {
            summaries.add(largeSummary(tags));
        }
        expectRejected(() -> new MobEditorV2StatePacket(
                true, true, false, "", summaries, null, List.of(), null, List.of()).encode());
    }

    private static void closeThenReopenDoesNotLeakV2State() throws Exception {
        assertV2StateRace("save", true);
        assertV2StateRace("reload", false);
    }

    private static void assertV2StateRace(String oldOperation,
                                          boolean oldRateLimited) throws Exception {
        MobEditorChannel channel = new MobEditorChannel(
                null, emptyManager(), null,
                new AbilityRegistry(AbilityRuntime.standardActions()));
        List<byte[]> sent = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = racePlayer(playerId, sent);
        long oldToken = beginV2(channel, player, oldRateLimited);
        assert oldToken != 0 : oldOperation + " v2 request did not start";

        channel.onPluginMessageReceived(MobEditorChannel.REQUEST_CHANNEL_V2, player,
                new byte[]{(byte) MobEditorV2PacketIO.VERSION,
                        (byte) MobEditorChannel.CLOSE});

        long newToken = beginV2(channel, player, !oldRateLimited);
        assert newToken != 0 && newToken != oldToken
                : oldOperation + " v2 reopen did not get an independent token";
        finishV2(channel, player, oldToken, snapshot("old " + oldOperation));
        assert sent.isEmpty() : "stale v2 " + oldOperation + " response leaked";
        assert inFlight(channel, playerId) == newToken
                : "stale v2 " + oldOperation + " completion removed the new token";

        finishV2(channel, player, newToken, snapshot("new " + oldOperation));
        assert sent.size() == 1 : "valid v2 response was lost";
        assert stateMessage(sent.getFirst()).equals("new " + oldOperation);
        assert inFlight(channel, playerId) == null : "v2 token was not retired";
    }

    private static long beginV2(MobEditorChannel channel, Player player,
                                boolean rateLimited) throws Exception {
        Method method = MobEditorChannel.class.getDeclaredMethod(
                "beginStateIo", Player.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (long) method.invoke(channel, player, rateLimited, true);
    }

    private static void finishV2(MobEditorChannel channel, Player player,
                                 long token, MobEditorManager.Snapshot snapshot)
            throws Exception {
        Method method = MobEditorChannel.class.getDeclaredMethod(
                "finishStateIoV2", Player.class, long.class, MobEditorManager.Snapshot.class);
        method.setAccessible(true);
        method.invoke(channel, player, token, snapshot);
    }

    private static MobEditorManager.Snapshot snapshot(String message) {
        return new MobEditorManager.Snapshot(true, false, message,
                List.of(), null, List.of(), null);
    }

    @SuppressWarnings("unchecked")
    private static Long inFlight(MobEditorChannel channel, UUID playerId)
            throws Exception {
        Field field = MobEditorChannel.class.getDeclaredField("stateIoInFlight");
        field.setAccessible(true);
        return ((java.util.Map<UUID, Long>) field.get(channel)).get(playerId);
    }

    private static MobEditorManager emptyManager() throws Exception {
        MobEditorManager manager = (MobEditorManager) unsafe().allocateInstance(
                MobEditorManager.class);
        Field sessions = MobEditorManager.class.getDeclaredField("sessions");
        sessions.setAccessible(true);
        sessions.set(manager, new HashMap<>());
        return manager;
    }

    private static Player racePlayer(UUID playerId, List<byte[]> sent) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> true;
                    case "isOnline" -> true;
                    case "sendPluginMessage" -> {
                        sent.add((byte[]) arguments[2]);
                        yield null;
                    }
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "MobEditorV2RacePlayer";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static String stateMessage(byte[] packet) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet))) {
            assert input.readUnsignedByte() == MobEditorV2PacketIO.VERSION;
            input.readBoolean();
            input.readBoolean();
            input.readBoolean();
            return MobEditorPacketIO.readString(input, 256);
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void summaryTagsAreCappedAtEight() throws IOException {
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            tags.add("tag" + index);
        }
        MobDefinition mob = copy(MobDefinition.create("projects:summary"), tags,
                MobDefinition.create("projects:summary").appearance());
        HeadDefinition head = new HeadDefinition(1, 0, "projects:summary_head",
                "Summary head", HeadDefinition.SourceType.VANILLA_HEAD, "", "", "",
                tags, false, "");
        byte[] state = new MobEditorV2StatePacket(true, true, false, "", List.of(mob),
                null, List.of(head), null, List.of()).encode();

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(state))) {
            assert input.readUnsignedByte() == MobEditorV2PacketIO.VERSION;
            input.readBoolean();
            input.readBoolean();
            input.readBoolean();
            MobEditorPacketIO.readString(input, 256);
            assert MobEditorV2PacketIO.readCatalog(input).isEmpty();
            assert input.readUnsignedShort() == 1;
            MobEditorPacketIO.readString(input, 64);
            MobEditorPacketIO.readString(input, 128);
            MobEditorPacketIO.readString(input, 64);
            MobEditorPacketIO.readString(input, 32);
            input.readBoolean();
            input.readLong();
            assertSummaryTags(input, tags);
            assert !input.readBoolean();
            assert input.readUnsignedByte() == 1;
            MobEditorPacketIO.readString(input, 64);
            MobEditorPacketIO.readString(input, 128);
            MobEditorPacketIO.readString(input, 32);
            input.readBoolean();
            assertSummaryTags(input, tags);
            assert !input.readBoolean();
            assert input.available() == 0;
        }
    }

    private static void assertSummaryTags(DataInputStream input, List<String> expected)
            throws IOException {
        assert input.readUnsignedByte() == 8;
        for (int index = 0; index < 8; index++) {
            assert MobEditorPacketIO.readString(input, 32).equals(expected.get(index));
        }
    }

    private static MobDefinition largeSummary(List<String> tags) {
        MobDefinition defaults = MobDefinition.create("projects:aggregate");
        return new MobDefinition(defaults.schemaVersion(), defaults.revision(), "m".repeat(64),
                "d".repeat(128), "e".repeat(64), defaults.category(), defaults.enabled(),
                defaults.level(), defaults.nameplateMode(), tags, defaults.stats(),
                defaults.basicAttack(), defaults.ai(), defaults.appearance(), defaults.abilityIds());
    }

    private static byte[] v1Save(MobDefinition mob) throws IOException {
        return request(1, MobEditorChannel.SAVE_DRAFT, mob, false);
    }

    private static byte[] v2Save(MobDefinition mob) throws IOException {
        return request(2, MobEditorChannel.SAVE_DRAFT, mob, true);
    }

    private static byte[] request(int version, int operation, MobDefinition mob, boolean v2)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(version);
            output.writeByte(operation);
            if (v2) {
                MobEditorV2PacketIO.writeMob(output, mob);
            } else {
                MobEditorPacketIO.writeMob(output, mob);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] v2Body(MobDefinition mob) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            MobEditorV2PacketIO.writeMob(output, mob);
        }
        return bytes.toByteArray();
    }

    private static byte[] v1BodyThenAbilities(MobDefinition mob, List<String> abilities)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            MobEditorPacketIO.writeMob(output, mob);
            output.writeByte(abilities.size());
            for (String ability : abilities) {
                MobEditorPacketIO.writeString(output, ability, 96);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] v1BodyThenCount(MobDefinition mob, int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            MobEditorPacketIO.writeMob(output, mob);
            output.writeByte(count);
        }
        return bytes.toByteArray();
    }

    private static byte[] catalog(List<MobEditorV2PacketIO.CatalogEntry> entries)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            MobEditorV2PacketIO.writeCatalog(output, entries);
        }
        return bytes.toByteArray();
    }

    private static List<MobEditorV2PacketIO.CatalogEntry> readCatalog(byte[] bytes)
            throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            List<MobEditorV2PacketIO.CatalogEntry> result = MobEditorV2PacketIO.readCatalog(input);
            assert input.available() == 0;
            return result;
        }
    }

    private static byte[] rawCatalog(int count, List<String> ids) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeShort(count);
            for (String id : ids) {
                MobEditorPacketIO.writeString(output, id, 96);
                MobEditorPacketIO.writeString(output, "Name", 128);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] updateHeadFavorite(String revision) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(2);
            output.writeByte(MobEditorChannel.UPDATE_HEAD_FAVORITE);
            MobEditorPacketIO.writeString(output, "head", 64);
            MobEditorPacketIO.writeString(output, revision, 32);
            output.writeBoolean(true);
        }
        return bytes.toByteArray();
    }

    private static MobDefinition copy(MobDefinition source, List<String> tags,
                                      MobAppearanceDefinition appearance) {
        return new MobDefinition(source.schemaVersion(), source.revision(), source.id(),
                source.displayName(), source.entityType(), source.category(), source.enabled(),
                source.level(), source.nameplateMode(), tags, source.stats(), source.basicAttack(),
                source.ai(), appearance, source.abilityIds());
    }

    private static void readMob(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            MobEditorV2PacketIO.readMob(input);
            if (input.available() != 0) {
                throw new IOException("Trailing bytes");
            }
        }
    }

    private static byte[] replace(byte[] source, byte[] from, byte[] to) {
        assert from.length == to.length;
        byte[] result = source.clone();
        for (int index = 0; index <= result.length - from.length; index++) {
            if (Arrays.equals(Arrays.copyOfRange(result, index, index + from.length), from)) {
                System.arraycopy(to, 0, result, index, to.length);
                return result;
            }
        }
        throw new AssertionError("fixture token was not found");
    }

    private static void expectRejected(ThrowingAction action) {
        try {
            action.run();
            throw new AssertionError("Expected rejection");
        } catch (IOException | IllegalArgumentException | IllegalStateException expected) {
            // Expected protocol or encoder rejection.
        } catch (Exception exception) {
            throw new AssertionError("Unexpected checked exception", exception);
        }
    }

    private static String value(String fixture, String key) {
        return fixture.lines().filter(line -> line.startsWith(key + "="))
                .findFirst().orElseThrow().substring(key.length() + 1);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
