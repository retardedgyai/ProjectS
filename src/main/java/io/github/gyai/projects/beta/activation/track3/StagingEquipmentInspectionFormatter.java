package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.equipment.BaseStatRoll;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.TradePolicy;
import io.github.gyai.projects.mod.ModEntry;
import io.github.gyai.projects.mod.ModSlotEntry;
import io.github.gyai.projects.mod.UnknownModEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Read-only inspection rendering shared by the staging GUI and operator boundary.
 * The full form is deliberately finite; command callers can use the bounded
 * overload or {@link #formatLines(EquipmentItemV1)} to paginate complete fields.
 */
public final class StagingEquipmentInspectionFormatter {
    public static final int MAXIMUM_RENDERED_CHARACTERS = 4_096;
    private static final int MAXIMUM_BASE_STAT_ROLLS = 4;

    private StagingEquipmentInspectionFormatter() { }

    /**
     * Returns the complete, deterministic staging inspection form for normal
     * fixtures. It never creates an item instance or UUID.
     */
    public static String format(EquipmentItemV1 item) {
        return format(item, MAXIMUM_RENDERED_CHARACTERS);
    }

    /**
     * Bounds transport text without changing the source item. Consumers that
     * need every field in a smaller response should page {@link #formatLines}.
     */
    public static String format(EquipmentItemV1 item, int maximumCharacters) {
        if (maximumCharacters <= 0) throw new IllegalArgumentException("maximumCharacters must be positive");
        String formatted = String.join(" ", formatLines(item));
        if (formatted.length() <= maximumCharacters) return formatted;
        if (maximumCharacters == 1) return "…";
        return formatted.substring(0, maximumCharacters - 1) + "…";
    }

    /**
     * Predictably ordered complete field groups for small response/page limits.
     * Base rolls are intentionally capped at four and report the remaining count.
     */
    public static List<String> formatLines(EquipmentItemV1 item) {
        if (item == null) return List.of("装備なし");
        ArrayList<String> lines = new ArrayList<>();
        lines.add("EquipmentSchema=" + item.schemaVersion()
                + " ID=" + item.itemId()
                + " UUID=" + item.instanceId().map(Object::toString).orElse("none"));
        lines.add("Tier=" + item.tier() + " ILv=" + item.itemLevel()
                + " Rarity=" + item.rarity() + " Quality=" + item.quality()
                + " Category=" + item.category() + " Slot=" + item.slot());
        lines.add("Enhancement=" + item.enhancementLevel() + " Broken=" + item.broken()
                + " Binding=" + item.binding() + " Trade=" + tradePolicy(item.tradePolicy())
                + " MOD slots=" + item.modSlots().size());
        lines.add(baseStats(item.baseStatRolls()));
        item.crafter().ifPresent(value -> lines.add("CrafterUUID=" + value.playerId()
                + " Crafter=" + value.displaySnapshot()));
        item.modSlots().forEach(slot -> lines.add(slot.entry()
                .map(entry -> mod(slot.index(), entry))
                .orElse("MOD slot=" + slot.index() + " empty")));
        return List.copyOf(lines);
    }

    private static String baseStats(List<BaseStatRoll> rolls) {
        StringJoiner values = new StringJoiner(", ", "BaseStats=[", "]");
        rolls.stream().limit(MAXIMUM_BASE_STAT_ROLLS)
                .forEach(roll -> values.add(roll.statId() + "=" + roll.value()));
        if (rolls.size() > MAXIMUM_BASE_STAT_ROLLS) {
            values.add("+" + (rolls.size() - MAXIMUM_BASE_STAT_ROLLS) + " more");
        }
        return values.toString();
    }

    private static String mod(int slotIndex, ModSlotEntry entry) {
        if (entry instanceof ModEntry known) {
            return "MOD slot=" + slotIndex + " ID=" + known.modId()
                    + " Rank=" + known.rank() + " value=" + known.rolledValue()
                    + " Display=" + knownDisplayName(known)
                    + " Schema=" + known.schemaVersion()
                    + " DefinitionRevision=" + known.definitionRevision()
                    + " SourceCatalog=" + known.source().definitionPackId()
                    + " SourceId=" + known.source().operationSourceId();
        }
        if (entry instanceof UnknownModEntry unknown) {
            return "UnknownMOD slot=" + slotIndex + " ID=" + unknown.modId()
                    + " Schema=" + unknown.schemaId() + "/" + unknown.schemaVersion()
                    + " UNKNOWN / 効果無効";
        }
        return "UnknownMOD slot=" + slotIndex + " UNKNOWN / 効果無効";
    }

    private static String tradePolicy(TradePolicy policy) {
        if (TradePolicy.DENY_ALL.equals(policy)) return "DENY_ALL";
        return "direct=" + policy.directTradeAllowed() + ",market=" + policy.marketAllowed()
                + ",dismantle=" + policy.dismantleAllowed();
    }

    /** Resolves only declared staging fixture metadata; no production catalog is implied. */
    public static String knownDisplayName(ModEntry entry) {
        return StagingModRollService.defaultCandidates().stream()
                .map(StagingModRollService.Candidate::definition)
                .filter(definition -> definition.modId().equals(entry.modId())
                        && definition.definitionRevision() == entry.definitionRevision())
                .map(definition -> definition.display().localizationKey())
                .findFirst()
                .orElse(entry.modId());
    }
}
