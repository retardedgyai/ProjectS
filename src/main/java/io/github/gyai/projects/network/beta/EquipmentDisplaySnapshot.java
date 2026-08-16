package io.github.gyai.projects.network.beta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EquipmentDisplaySnapshot(
        ReadStatus readStatus,
        List<Slot> slots
) {
    public enum ReadStatus { SUPPORTED, UNSUPPORTED, UNKNOWN_VERSION, CORRUPT }

    public EquipmentDisplaySnapshot {
        if (readStatus == null) throw new IllegalArgumentException("Read status is required");
        slots = BetaDisplayValidation.list(slots, 128, "equipment slots");
    }

    public record Slot(
            String slotId,
            UUID itemInstanceId,
            long revision,
            int tier,
            int itemLevel,
            String rarity,
            double quality,
            Map<String, Double> baseRolls,
            List<String> modEntries,
            String binding,
            String tradePolicy,
            int enhancementLevel,
            boolean broken
    ) {
        public Slot {
            slotId = BetaDisplayValidation.id(slotId, "slotId");
            rarity = BetaDisplayValidation.id(rarity, "rarity");
            binding = BetaDisplayValidation.id(binding, "binding");
            tradePolicy = BetaDisplayValidation.id(tradePolicy, "tradePolicy");
            BetaDisplayValidation.finite(quality, "quality");
            if (itemInstanceId == null || revision < 0 || tier < 0 || itemLevel < 0
                    || quality < 0 || enhancementLevel < 0) {
                throw new IllegalArgumentException("Invalid equipment slot");
            }
            baseRolls = BetaDisplayValidation.map(baseRolls, 64, "base rolls");
            baseRolls.forEach((id, value) -> {
                BetaDisplayValidation.id(id, "rollId");
                BetaDisplayValidation.finite(value, "rollValue");
            });
            modEntries = BetaDisplayValidation.list(modEntries, 128, "MOD entries");
            modEntries.forEach(id -> BetaDisplayValidation.id(id, "modId"));
        }
    }
}
