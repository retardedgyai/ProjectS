package io.github.gyai.projects.equipment;

public record ImmutableEquipmentStatContribution(
        String statId, double value, String sourceId
) implements EquipmentStatContribution {
    public ImmutableEquipmentStatContribution {
        statId = MetadataIds.requireCanonical("statId", statId);
        sourceId = MetadataIds.requireCanonical("sourceId", sourceId);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
    }
}
