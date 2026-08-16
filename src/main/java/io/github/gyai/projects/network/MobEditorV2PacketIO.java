package io.github.gyai.projects.network;

import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.transaction.DomainId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Strict continuation codec.  The v1 codec deliberately remains untouched. */
public final class MobEditorV2PacketIO {
    public static final int VERSION = 2;
    public static final int MAX_PAYLOAD_BYTES = 48 * 1024;
    public static final int MAX_CATALOG_ENTRIES = 128;
    public static final int MAX_ABILITY_ID_BYTES = 96;
    public static final int MAX_DISPLAY_NAME_BYTES = 128;

    private MobEditorV2PacketIO() { }

    public static MobDefinition readMob(DataInputStream input) throws IOException {
        MobDefinition base;
        try {
            base = MobEditorPacketIO.readMob(input);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed mob", exception);
        }
        requireSchema(base);
        requireUnique(base.tags(), "Duplicate mob tag");
        requireUnique(base.appearance().variants().keySet(), "Duplicate variant");
        int count = input.readUnsignedByte();
        if (count > MobDefinition.MAX_ABILITY_IDS) throw new IOException("Too many abilities");
        ArrayList<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = MobEditorPacketIO.readString(input, MAX_ABILITY_ID_BYTES);
            try { DomainId.requireNamespaced(id, "ability id"); }
            catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Malformed ability id", e); }
            ids.add(id);
        }
        requireUnique(ids, "Duplicate ability id");
        return base.withAbilityIds(ids);
    }

    public static void writeMob(DataOutputStream output, MobDefinition value) throws IOException {
        requireSchema(value);
        requireUnique(value.tags(), "Duplicate mob tag");
        requireUnique(value.appearance().variants().keySet(), "Duplicate variant");
        MobEditorPacketIO.writeMob(output, value);
        if (value.abilityIds().size() > MobDefinition.MAX_ABILITY_IDS) throw new IOException("Too many abilities");
        output.writeByte(value.abilityIds().size());
        for (String id : value.abilityIds()) {
            DomainId.requireNamespaced(id, "ability id");
            MobEditorPacketIO.writeString(output, id, MAX_ABILITY_ID_BYTES);
        }
    }

    public static HeadDefinition readHead(DataInputStream input) throws IOException {
        HeadDefinition head = MobEditorPacketIO.readHead(input);
        if (head.schemaVersion() != HeadDefinition.SCHEMA_VERSION) {
            throw new IOException("Unsupported head schema");
        }
        requireUnique(head.tags(), "Duplicate head tag");
        return head;
    }

    public static void writeHead(DataOutputStream output, HeadDefinition value) throws IOException {
        if (value.schemaVersion() != HeadDefinition.SCHEMA_VERSION) {
            throw new IOException("Unsupported head schema");
        }
        requireUnique(value.tags(), "Duplicate head tag");
        MobEditorPacketIO.writeHead(output, value);
    }

    static void requireUniqueTags(Iterable<String> values, String error) throws IOException {
        requireUnique(values, error);
    }

    public static List<CatalogEntry> readCatalog(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        if (count > MAX_CATALOG_ENTRIES) throw new IOException("Too many catalog entries");
        ArrayList<CatalogEntry> result = new ArrayList<>(count);
        HashSet<String> ids = new HashSet<>();
        String prior = null;
        for (int i = 0; i < count; i++) {
            String id = MobEditorPacketIO.readString(input, MAX_ABILITY_ID_BYTES);
            String name = MobEditorPacketIO.readString(input, MAX_DISPLAY_NAME_BYTES);
            try { DomainId.requireNamespaced(id, "ability id"); }
            catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Malformed catalog id", e); }
            if (name.isBlank() || !ids.add(id) || (prior != null && prior.compareTo(id) >= 0)) {
                throw new IOException("Invalid catalog");
            }
            prior = id;
            result.add(new CatalogEntry(id, name));
        }
        return List.copyOf(result);
    }

    public static void writeCatalog(DataOutputStream output, List<CatalogEntry> catalog) throws IOException {
        if (catalog.size() > MAX_CATALOG_ENTRIES) throw new IOException("Too many catalog entries");
        output.writeShort(catalog.size());
        String prior = null;
        HashSet<String> ids = new HashSet<>();
        for (CatalogEntry entry : catalog) {
            DomainId.requireNamespaced(entry.id(), "ability id");
            if (entry.displayName().isBlank() || !ids.add(entry.id())
                    || (prior != null && prior.compareTo(entry.id()) >= 0)) throw new IOException("Invalid catalog");
            MobEditorPacketIO.writeString(output, entry.id(), MAX_ABILITY_ID_BYTES);
            MobEditorPacketIO.writeString(output, entry.displayName(), MAX_DISPLAY_NAME_BYTES);
            prior = entry.id();
        }
    }

    private static void requireUnique(Iterable<String> values, String error) throws IOException {
        HashSet<String> unique = new HashSet<>();
        for (String value : values) if (!unique.add(value)) throw new IOException(error);
    }

    private static void requireSchema(MobDefinition value) throws IOException {
        if (value.schemaVersion() != MobDefinition.SCHEMA_VERSION) {
            throw new IOException("Unsupported mob schema");
        }
    }

    public record CatalogEntry(String id, String displayName) { }
}
