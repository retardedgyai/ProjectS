package io.github.gyai.projects.equipment;

public record BaseStatRoll(String statId, double value) {
    public BaseStatRoll {
        statId = MetadataIds.requireCanonical("statId", statId);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
    }
}
