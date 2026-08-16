package io.github.gyai.projects.beta.activation.track1.equipment;

import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentValidation;
import io.github.gyai.projects.item.compatibility.LegacyItemCompatibilityReader;
import io.github.gyai.projects.item.compatibility.LegacyItemReadResult;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pure, bounded read service. It never writes ItemStack/PDC or applies stats. */
public final class EquipmentInspectionService implements EquipmentInspectionPort, AutoCloseable {
    public static final int DEFAULT_MAXIMUM_PLAYERS = 512;

    private final Clock clock;
    private final int maximumPlayers;
    private final LegacyItemCompatibilityReader legacyReader = new LegacyItemCompatibilityReader();
    private final LegacyEquipmentV1Projector projector = new LegacyEquipmentV1Projector();
    private final LinkedHashMap<UUID, EquipmentInspectionSnapshot> latest = new LinkedHashMap<>();
    private boolean running;
    private boolean closed;

    public EquipmentInspectionService(Clock clock) {
        this(clock, DEFAULT_MAXIMUM_PLAYERS);
    }

    public EquipmentInspectionService(Clock clock, int maximumPlayers) {
        if (clock == null || maximumPlayers < 1 || maximumPlayers > 10_000) {
            throw new IllegalArgumentException("invalid equipment inspection service");
        }
        this.clock = clock;
        this.maximumPlayers = maximumPlayers;
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("service is closed");
        running = true;
    }

    @Override
    public synchronized EquipmentInspectionSnapshot inspect(
            UUID playerId, List<EquipmentScanEntry> entries) {
        if (!running || closed || playerId == null) throw new IllegalStateException("service is not running");
        EquipmentInspectionSnapshot snapshot = project(playerId, entries);
        latest.remove(playerId);
        latest.put(playerId, snapshot);
        while (latest.size() > maximumPlayers) latest.remove(latest.keySet().iterator().next());
        return snapshot;
    }

    /**
     * Projects an immutable inspection snapshot without requiring the runtime module to start.
     * It intentionally neither retains the result nor changes ItemStack/PDC, legacy data, or IDs.
     */
    public synchronized EquipmentInspectionSnapshot inspectReadOnly(
            UUID playerId, List<EquipmentScanEntry> entries) {
        if (closed || playerId == null) throw new IllegalStateException("service is closed");
        return project(playerId, entries);
    }

    private EquipmentInspectionSnapshot project(UUID playerId, List<EquipmentScanEntry> entries) {
        List<EquipmentScanEntry> source = entries == null ? List.of() : entries;
        if (source.size() > EquipmentInspectionSnapshot.MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("inventory scan is oversized");
        }
        ArrayList<EquipmentItemInspection> items = new ArrayList<>();
        int readable = 0;
        int valid = 0;
        int unknown = 0;
        for (EquipmentScanEntry entry : source) {
            LegacyItemReadResult legacy = legacyReader.read(entry.legacySource());
            Optional<EquipmentItemV1> projection = entry.equipmentV1();
            if (projection.isEmpty() && legacy.valid()) {
                try {
                    projection = Optional.of(projector.project(legacy.view().orElseThrow()));
                } catch (IllegalArgumentException ignored) {
                    projection = Optional.empty();
                }
            }
            EquipmentValidation validation = projection
                    .map(EquipmentValidation::validate)
                    .orElseGet(() -> new EquipmentValidation(false, List.of("projection")));
            if (legacy.valid()) readable++;
            if (validation.valid()) valid++;
            unknown += entry.unknownMods().size();
            items.add(new EquipmentItemInspection(entry.slot(), legacy, projection,
                    validation, entry.unknownMods().stream().map(value -> value.modId()).toList(),
                    sha256(entry.serializedBefore())));
        }
        return new EquipmentInspectionSnapshot(
                playerId, clock.instant(), items, readable, valid, unknown);
    }

    @Override
    public synchronized Optional<EquipmentInspectionSnapshot> latest(UUID playerId) {
        return Optional.ofNullable(latest.get(playerId));
    }

    public synchronized void remove(UUID playerId) { latest.remove(playerId); }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        running = false;
        latest.clear();
    }

    public synchronized boolean running() { return running && !closed; }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
