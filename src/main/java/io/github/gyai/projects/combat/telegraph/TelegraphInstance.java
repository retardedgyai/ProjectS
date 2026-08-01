package io.github.gyai.projects.combat.telegraph;

import java.util.Objects;
import java.util.UUID;

public final class TelegraphInstance {
    private final UUID id;
    private final UUID sourceId;
    private final int sourceNetworkId;
    private final TelegraphRequest request;
    private double centerX;
    private double centerY;
    private double centerZ;
    private boolean locked;
    private boolean detonated;
    private boolean cancelled;
    private boolean removed;
    private CancellationReason cancellationReason =
            CancellationReason.NONE;
    private long revision = 1L;
    private long cancelledAtTick = Long.MAX_VALUE;

    public TelegraphInstance(
            UUID id,
            UUID sourceId,
            int sourceNetworkId,
            TelegraphRequest request
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceId = Objects.requireNonNull(
                sourceId, "sourceId");
        if (sourceNetworkId < 0) {
            throw new IllegalArgumentException(
                    "Invalid source network id");
        }
        this.sourceNetworkId = sourceNetworkId;
        this.request = Objects.requireNonNull(
                request, "request");
        centerX = request.centerX();
        centerY = request.centerY();
        centerZ = request.centerZ();
        locked = request.trackingMode() == TrackingMode.FIXED;
    }

    public boolean updateCenter(
            double x,
            double y,
            double z
    ) {
        if (locked || detonated || cancelled || removed
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            return false;
        }
        double dx = x - centerX;
        double dy = y - centerY;
        double dz = z - centerZ;
        if (dx * dx + dy * dy + dz * dz < 0.0025) {
            return false;
        }
        centerX = x;
        centerY = y;
        centerZ = z;
        revision++;
        return true;
    }

    public boolean lock() {
        if (locked || detonated || cancelled || removed) {
            return false;
        }
        locked = true;
        revision++;
        return true;
    }

    public boolean detonate() {
        if (detonated || cancelled || removed) {
            return false;
        }
        detonated = true;
        locked = true;
        revision++;
        return true;
    }

    public boolean cancel(
            CancellationReason reason,
            long currentTick
    ) {
        if (detonated || cancelled || removed) {
            return false;
        }
        cancellationReason = Objects.requireNonNull(
                reason, "reason");
        if (reason == CancellationReason.NONE) {
            throw new IllegalArgumentException(
                    "Cancellation requires a reason");
        }
        cancelled = true;
        locked = true;
        cancelledAtTick = currentTick;
        revision++;
        return true;
    }

    public boolean markRemoved() {
        if (removed) {
            return false;
        }
        removed = true;
        revision++;
        return true;
    }

    public boolean contains(
            double x,
            double y,
            double z
    ) {
        return TelegraphGeometry.contains(
                request.shape(),
                centerX, centerY, centerZ,
                request.directionX(), request.directionZ(),
                request.radius(), request.innerRadius(),
                request.width(), request.length(),
                request.verticalTolerance(),
                x, y, z);
    }

    public UUID id() { return id; }
    public UUID sourceId() { return sourceId; }
    public int sourceNetworkId() { return sourceNetworkId; }
    public TelegraphRequest request() { return request; }
    public double centerX() { return centerX; }
    public double centerY() { return centerY; }
    public double centerZ() { return centerZ; }
    public boolean locked() { return locked; }
    public boolean detonated() { return detonated; }
    public boolean cancelled() { return cancelled; }
    public boolean removed() { return removed; }
    public CancellationReason cancellationReason() {
        return cancellationReason;
    }
    public long revision() { return revision; }
    public long cancelledAtTick() { return cancelledAtTick; }

    public enum Shape {
        CIRCLE,
        DONUT,
        LINE
    }

    public enum VisualTheme {
        DAMAGE,
        DEBUFF,
        POISON,
        SAFE,
        OPPORTUNITY
    }

    public enum VisualStyle {
        STANDARD,
        GROHM_STONE_TIDE
    }

    public enum TrackingMode {
        FIXED,
        TARGET
    }

    public enum CancellationReason {
        NONE,
        HARD_CONTROL,
        BOSS_RESET,
        SOURCE_REMOVED,
        TARGET_INVALID,
        WORLD_CHANGED,
        EXPIRED,
        PLUGIN_STOP
    }
}
