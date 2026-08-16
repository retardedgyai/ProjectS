package io.github.gyai.projects.mod;

import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentStatContribution;
import io.github.gyai.projects.equipment.ImmutableEquipmentStatContribution;

import java.util.List;
import java.util.Optional;

public record ModValidation(boolean valid, List<String> issues,
                            Optional<EquipmentStatContribution> contribution) {
    public ModValidation { issues = List.copyOf(issues); contribution = contribution == null ? Optional.empty() : contribution; }
    public static ModValidation validate(ModSlotEntry slotEntry, ModDefinition definition,
                                         EquipmentSlot equipmentSlot) {
        if (!(slotEntry instanceof ModEntry entry)) {
            return new ModValidation(false, List.of("unsupported MOD entry"), Optional.empty());
        }
        if (definition == null) return new ModValidation(false, List.of("unknown MOD definition"), Optional.empty());
        java.util.ArrayList<String> issues = new java.util.ArrayList<>();
        if (!entry.modId().equals(definition.modId())) issues.add("modId");
        if (entry.rank() != definition.rank()) issues.add("rank");
        if (!definition.allowedSlots().contains(equipmentSlot)) issues.add("equipmentSlot");
        if (entry.definitionRevision() != definition.definitionRevision()) issues.add("definitionRevision");
        if (entry.rolledValue() < definition.minimumValue()
                || entry.rolledValue() > definition.maximumValue()) issues.add("rolledValue");
        if (!issues.isEmpty()) return new ModValidation(false, issues, Optional.empty());
        return new ModValidation(true, List.of(), Optional.of(
                new ImmutableEquipmentStatContribution(definition.statId(), entry.rolledValue(), entry.modId())));
    }
}
