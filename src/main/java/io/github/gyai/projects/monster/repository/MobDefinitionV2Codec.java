package io.github.gyai.projects.monster.repository;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;
import io.github.gyai.projects.combat.element.ElementTargetCategory;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Policy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic UTF-8 YAML envelope with a bounded, non-Java-serialized payload. */
public final class MobDefinitionV2Codec {
    private static final int PAYLOAD_FORMAT = 1;
    private final MobDefinitionV2Policy policy;

    public MobDefinitionV2Codec(MobDefinitionV2Policy policy) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    public byte[] encode(MobDefinitionV2 value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(PAYLOAD_FORMAT);
            writeString(out, value.display().name());
            writeString(out, value.display().nameplatePolicy());
            writeMap(out, value.display().values());
            writeString(out, value.entityType());
            writeString(out, value.category().name());
            writeDoubleMap(out, value.attributes());
            writeAttacks(out, value.attacks());
            writeSkills(out, value.skills());
            writePhases(out, value.phases());
            writeStrings(out, value.dropReferences());
            writeSpawns(out, value.spawnRules());
            writeWeaknesses(out, value.weaknesses());
            writeString(out, value.fireCategory().category().name());
            writeDoubleMap(out, value.fireCategory().explicitOverrides());
            writeString(out, value.iceCategory().category().name());
            writeDoubleMap(out, value.iceCategory().explicitOverrides());
            writeStrings(out, value.rewardReferences());
            writeString(out, value.participationPolicyReference());
            writeMap(out, value.extensions());
        }
        String payload = Base64.getEncoder().encodeToString(bytes.toByteArray());
        String yaml = "schema-version: 2\n"
                + "mob-id: " + value.mobId() + "\n"
                + "revision: " + value.revision() + "\n"
                + "payload-format: " + PAYLOAD_FORMAT + "\n"
                + "payload-base64: " + payload + "\n";
        byte[] encoded = yaml.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > policy.maximumFileBytes()) throw new IOException("definition oversized");
        return encoded;
    }

    public MobDefinitionV2 decode(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length > policy.maximumFileBytes()) {
            throw new IOException("definition oversized");
        }
        String yaml = strictUtf8(encoded);
        Map<String, String> fields = topLevel(yaml);
        if (!Set.of("schema-version", "mob-id", "revision", "payload-format",
                "payload-base64").equals(fields.keySet())) {
            throw new IOException("unknown, missing, or duplicate envelope field");
        }
        int schema = integer(fields.get("schema-version"), "schema-version");
        if (schema != 2) throw new IOException("unsupported schema version");
        int payloadFormat = integer(fields.get("payload-format"), "payload-format");
        if (payloadFormat != PAYLOAD_FORMAT) throw new IOException("unknown payload format");
        long revision = longValue(fields.get("revision"), "revision");
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(fields.get("payload-base64"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid base64 payload", exception);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != PAYLOAD_FORMAT) throw new IOException("payload format mismatch");
            var display = new MobDefinitionV2.DisplayMetadata(
                    readString(in), readString(in), readMap(in));
            String entityType = readString(in);
            var category = enumValue(MobDefinitionV2.MobCategory.class, readString(in));
            Map<String, Double> attributes = readDoubleMap(in);
            List<MobDefinitionV2.AttackDefinition> attacks = readAttacks(in);
            List<MobDefinitionV2.SkillReference> skills = readSkills(in);
            List<MobDefinitionV2.PhaseDefinition> phases = readPhases(in);
            List<String> drops = readStrings(in);
            List<MobDefinitionV2.SpawnRule> spawns = readSpawns(in);
            List<MobDefinitionV2.ElementWeakness> weaknesses = readWeaknesses(in);
            var fireCategory = new MobDefinitionV2.ElementCategorySettings(
                    enumValue(ElementTargetCategory.class, readString(in)), readDoubleMap(in));
            var iceCategory = new MobDefinitionV2.ElementCategorySettings(
                    enumValue(ElementTargetCategory.class, readString(in)), readDoubleMap(in));
            List<String> rewards = readStrings(in);
            String participation = readString(in);
            Map<String, String> extensions = readMap(in);
            if (in.available() != 0) throw new IOException("trailing payload bytes");
            return new MobDefinitionV2(schema, fields.get("mob-id"), revision,
                    display, entityType, category, attributes, attacks, skills,
                    phases, drops, spawns, weaknesses, fireCategory, iceCategory,
                    rewards, participation, extensions);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("invalid Mob v2 payload", exception);
        }
    }

    public static int inspectSchemaVersion(byte[] bytes) throws IOException {
        String value = topLevel(strictUtf8(bytes)).get("schema-version");
        if (value == null) throw new IOException("schema-version missing");
        return integer(value, "schema-version");
    }

    public static LegacyHeader inspectLegacyHeader(byte[] bytes) throws IOException {
        Map<String, String> fields = topLevel(strictUtf8(bytes));
        int schema = integer(required(fields, "schema-version"), "schema-version");
        String id = required(fields, "id");
        long revision = longValue(required(fields, "revision"), "revision");
        return new LegacyHeader(schema, id, revision);
    }

    private void writeAttacks(DataOutputStream out,
                              List<MobDefinitionV2.AttackDefinition> values) throws IOException {
        writeCount(out, values.size());
        for (var value : values) {
            writeString(out, value.attackId());
            writeString(out, value.damageFamily().name());
            writeString(out, value.classification().name());
            List<String> tags = value.metadata().tags().stream().map(Enum::name).sorted().toList();
            writeStrings(out, tags);
            writeElementMap(out, value.metadata().elements().values());
            writeElementMap(out, value.metadata().elements().scalingRates());
            out.writeDouble(value.coefficient());
        }
    }

    private List<MobDefinitionV2.AttackDefinition> readAttacks(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<MobDefinitionV2.AttackDefinition> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = readString(in);
            DamageType family = enumValue(DamageType.class, readString(in));
            var classification = enumValue(MobDefinitionV2.AttackClassification.class,
                    readString(in));
            EnumSet<AttackTag> tags = EnumSet.noneOf(AttackTag.class);
            for (String tag : readStrings(in)) {
                if (!tags.add(enumValue(AttackTag.class, tag))) throw new IOException("duplicate tag");
            }
            ElementProfile elements = new ElementProfile(readElementMap(in), readElementMap(in));
            double coefficient = finite(in.readDouble());
            values.add(new MobDefinitionV2.AttackDefinition(id, family, classification,
                    new AttackMetadata(tags, elements), coefficient));
        }
        return List.copyOf(values);
    }

    private void writeSkills(DataOutputStream out,
                             List<MobDefinitionV2.SkillReference> values) throws IOException {
        writeCount(out, values.size());
        for (var value : values) {
            writeString(out, value.skillId()); out.writeLong(value.revision());
            writeString(out, value.trigger()); writeString(out, value.cooldownReference());
            writeString(out, value.targetSelectionReference());
            writeString(out, value.attackMetadataReference());
            out.writeDouble(value.coefficient()); writeString(out, value.enabledCondition());
        }
    }

    private List<MobDefinitionV2.SkillReference> readSkills(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<MobDefinitionV2.SkillReference> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(new MobDefinitionV2.SkillReference(readString(in), in.readLong(),
                    readString(in), readString(in), readString(in), readString(in),
                    finite(in.readDouble()), readString(in)));
        }
        return List.copyOf(values);
    }

    private void writePhases(DataOutputStream out,
                             List<MobDefinitionV2.PhaseDefinition> values) throws IOException {
        writeCount(out, values.size());
        for (var value : values) {
            writeString(out, value.phaseId()); out.writeBoolean(value.entry());
            writeString(out, value.entryCondition()); writeString(out, value.exitCondition());
            writeStrings(out, value.allowedSkills().stream().sorted().toList());
            writeStrings(out, value.transitionTargets().stream().sorted().toList());
            writeStrings(out, value.cleanupActions());
            writeString(out, value.invulnerabilityPolicyReference());
        }
    }

    private List<MobDefinitionV2.PhaseDefinition> readPhases(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<MobDefinitionV2.PhaseDefinition> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(new MobDefinitionV2.PhaseDefinition(readString(in), in.readBoolean(),
                    readString(in), readString(in), new LinkedHashSet<>(readStrings(in)),
                    new LinkedHashSet<>(readStrings(in)), readStrings(in), readString(in)));
        }
        return List.copyOf(values);
    }

    private void writeSpawns(DataOutputStream out,
                             List<MobDefinitionV2.SpawnRule> values) throws IOException {
        writeCount(out, values.size());
        for (var value : values) {
            writeString(out, value.spawnId()); writeString(out, value.regionLocationKey());
            writeString(out, value.maximumActiveReference());
            writeString(out, value.respawnPolicyReference());
            writeMap(out, value.conditions());
            writeString(out, value.schedulerLifecycleReference());
        }
    }

    private List<MobDefinitionV2.SpawnRule> readSpawns(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<MobDefinitionV2.SpawnRule> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(new MobDefinitionV2.SpawnRule(readString(in), readString(in),
                    readString(in), readString(in), readMap(in), readString(in)));
        }
        return List.copyOf(values);
    }

    private void writeWeaknesses(DataOutputStream out,
                                 List<MobDefinitionV2.ElementWeakness> values) throws IOException {
        writeCount(out, values.size());
        for (var value : values) { writeString(out, value.element().name()); out.writeDouble(value.multiplier()); }
    }

    private List<MobDefinitionV2.ElementWeakness> readWeaknesses(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<MobDefinitionV2.ElementWeakness> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(new MobDefinitionV2.ElementWeakness(
                enumValue(DamageElement.class, readString(in)), finite(in.readDouble())));
        return List.copyOf(values);
    }

    private void writeDoubleMap(DataOutputStream out, Map<String, Double> values) throws IOException {
        writeCount(out, values.size());
        for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(out, entry.getKey()); out.writeDouble(entry.getValue());
        }
    }

    private Map<String, Double> readDoubleMap(DataInputStream in) throws IOException {
        int count = readMapCount(in); Map<String, Double> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            if (values.putIfAbsent(readString(in), finite(in.readDouble())) != null) {
                throw new IOException("duplicate map key");
            }
        }
        return Map.copyOf(values);
    }

    private void writeElementMap(DataOutputStream out, Map<DamageElement, Double> values) throws IOException {
        writeCount(out, values.size());
        for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(out, entry.getKey().name()); out.writeDouble(entry.getValue());
        }
    }

    private Map<DamageElement, Double> readElementMap(DataInputStream in) throws IOException {
        int count = readMapCount(in); EnumMap<DamageElement, Double> values = new EnumMap<>(DamageElement.class);
        for (int i = 0; i < count; i++) {
            if (values.put(enumValue(DamageElement.class, readString(in)), finite(in.readDouble())) != null) {
                throw new IOException("duplicate element key");
            }
        }
        return Map.copyOf(values);
    }

    private void writeMap(DataOutputStream out, Map<String, String> values) throws IOException {
        writeCount(out, values.size());
        for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(out, entry.getKey()); writeString(out, entry.getValue());
        }
    }

    private Map<String, String> readMap(DataInputStream in) throws IOException {
        int count = readMapCount(in); Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            if (values.putIfAbsent(readString(in), readString(in)) != null) {
                throw new IOException("duplicate map key");
            }
        }
        return Map.copyOf(values);
    }

    private void writeStrings(DataOutputStream out, List<String> values) throws IOException {
        writeCount(out, values.size()); for (String value : values) writeString(out, value);
    }

    private List<String> readStrings(DataInputStream in) throws IOException {
        int count = readCount(in); List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(readString(in)); return List.copyOf(values);
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > policy.maximumStringBytes()) throw new IOException("string oversized");
        out.writeInt(bytes.length); out.write(bytes);
    }

    private String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > policy.maximumStringBytes()) throw new IOException("invalid string length");
        return strictUtf8(in.readNBytes(length), length);
    }

    private void writeCount(DataOutputStream out, int count) throws IOException {
        if (count < 0 || count > policy.maximumCollectionEntries()) throw new IOException("collection oversized");
        out.writeInt(count);
    }

    private int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > policy.maximumCollectionEntries()) throw new IOException("invalid collection length");
        return count;
    }

    private int readMapCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > policy.maximumMapEntries()) throw new IOException("invalid map length");
        return count;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) throws IOException {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw new IOException("unknown enum: " + value, exception); }
    }

    private static double finite(double value) throws IOException {
        if (!Double.isFinite(value)) throw new IOException("non-finite number");
        return value;
    }

    private static String strictUtf8(byte[] bytes) throws IOException { return strictUtf8(bytes, bytes.length); }

    private static String strictUtf8(byte[] bytes, int expected) throws IOException {
        if (bytes.length != expected) throw new IOException("truncated UTF-8 value");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("invalid UTF-8", exception);
        }
    }

    private static Map<String, String> topLevel(String yaml) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : yaml.split("\\R", -1)) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            if (Character.isWhitespace(line.charAt(0))) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("invalid YAML envelope");
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (fields.putIfAbsent(key, value) != null) throw new IOException("duplicate YAML key");
        }
        return fields;
    }

    private static String required(Map<String, String> fields, String key) throws IOException {
        String value = fields.get(key); if (value == null || value.isBlank()) throw new IOException(key + " missing"); return value;
    }

    private static int integer(String value, String name) throws IOException {
        try { return Integer.parseInt(value); } catch (RuntimeException e) { throw new IOException("invalid " + name, e); }
    }

    private static long longValue(String value, String name) throws IOException {
        try { long parsed = Long.parseLong(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (RuntimeException e) { throw new IOException("invalid " + name, e); }
    }

    public record LegacyHeader(int schemaVersion, String mobId, long revision) { }
}
