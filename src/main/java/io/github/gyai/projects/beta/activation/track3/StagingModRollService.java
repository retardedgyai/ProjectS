package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.equipment.EquipmentItemV1;
import io.github.gyai.projects.equipment.EquipmentModSlot;
import io.github.gyai.projects.equipment.EquipmentSlot;
import io.github.gyai.projects.equipment.EquipmentTier;
import io.github.gyai.projects.mod.ModDefinition;
import io.github.gyai.projects.mod.ModDisplayMetadata;
import io.github.gyai.projects.mod.ModEntry;
import io.github.gyai.projects.mod.ModRank;
import io.github.gyai.projects.mod.ModSource;
import io.github.gyai.projects.mod.ModSlotEntry;
import io.github.gyai.projects.mod.ModStackingLayer;
import io.github.gyai.projects.mod.ModTagMatchPolicy;
import io.github.gyai.projects.schema.SchemaVersions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Small non-production MOD roller for the staging blade only. Candidates and
 * weights are explicit policy, so a resolved proposal can be journaled and
 * replayed without asking the RNG again.
 */
public final class StagingModRollService {
    // ModDefinition intentionally accepts the repository canonical ID grammar
    // (no slash in the path), unlike the staging item fixture IDs.
    public static final String KEEN_EDGE = "projects:staging-keen-edge";
    /** The only staging T1 craft fixture is intentionally fixed at ILv 1. */
    private static final int STAGING_T1_ITEM_LEVEL = 1;
    private static final ModSource SOURCE = new ModSource(
            "projects:staging-fixtures", "projects:staging-craft");
    private final List<Candidate> candidates;
    private final DoubleSupplier random;

    public StagingModRollService() {
        this(defaultCandidates(), new Random()::nextDouble);
    }

    public StagingModRollService(List<Candidate> candidates, DoubleSupplier random) {
        this.candidates = List.copyOf(candidates == null ? List.of() : candidates);
        this.random = Objects.requireNonNull(random, "random");
        if (this.candidates.isEmpty() || this.candidates.size() > 2
                || this.candidates.stream().anyMatch(value -> value.weight() <= 0.0
                || !Double.isFinite(value.weight()))) {
            throw new IllegalArgumentException("invalid staging MOD policy");
        }
    }

    public EquipmentItemV1 resolve(EquipmentItemV1 preview) {
        Objects.requireNonNull(preview, "preview");
        if (!preview.itemId().equals(StagingEconomyCatalog.TEST_BLADE_T1)
                || preview.slot() != EquipmentSlot.WEAPON || preview.modSlots().isEmpty()) {
            throw new IllegalArgumentException("staging MOD is only available to the T1 weapon fixture");
        }
        // This is fixture eligibility, not a production item-level policy. Check it before
        // any candidate selection or RNG so malformed/reused previews stay inert.
        if (preview.tier() != EquipmentTier.T1
                || preview.itemLevel() != STAGING_T1_ITEM_LEVEL) return preview;
        Optional<EquipmentModSlot> vacant = preview.modSlots().stream()
                .filter(slot -> slot.entry().isEmpty()).findFirst();
        if (vacant.isEmpty()) return preview;
        Set<String> existingIds = preview.modSlots().stream().flatMap(slot -> slot.entry().stream())
                .map(StagingModRollService::modId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Candidate> available = candidates.stream()
                .filter(candidate -> !existingIds.contains(candidate.definition().modId()))
                .filter(candidate -> eligible(candidate.definition(), preview)).toList();
        if (available.isEmpty()) return preview;
        Candidate candidate = choose(available);
        ModDefinition definition = candidate.definition();
        if (existingIds.contains(definition.modId())) return preview;
        double value = definition.minimumValue()
                + (definition.maximumValue() - definition.minimumValue()) * boundedRandom();
        if (!Double.isFinite(value)) throw new IllegalStateException("staging MOD value is not finite");
        ModEntry entry = new ModEntry(SchemaVersions.MOD_DEFINITION, definition.modId(),
                definition.rank(), value, definition.definitionRevision(), definition.source(),
                vacant.orElseThrow().index());
        ArrayList<EquipmentModSlot> slots = new ArrayList<>(preview.modSlots());
        int index = slots.indexOf(vacant.orElseThrow());
        slots.set(index, new EquipmentModSlot(vacant.orElseThrow().index(), Optional.of(entry)));
        return new EquipmentItemV1(preview.schemaVersion(), preview.itemId(), preview.category(),
                preview.slot(), preview.tier(), preview.itemLevel(), preview.rarity(), preview.quality(),
                preview.baseStatRolls(), List.copyOf(slots), preview.crafter(),
                preview.enhancementLevel(), preview.broken(), preview.binding(),
                preview.tradePolicy(), preview.instanceId());
    }

    public List<Candidate> candidates() { return candidates; }

    private Candidate choose(List<Candidate> available) {
        double total = available.stream().mapToDouble(Candidate::weight).sum();
        double cursor = boundedRandom() * total;
        for (Candidate candidate : available) {
            cursor -= candidate.weight();
            if (cursor < 0.0) return candidate;
        }
        return available.getLast();
    }

    private static String modId(ModSlotEntry entry) {
        return switch (entry) {
            case ModEntry known -> known.modId();
            case io.github.gyai.projects.mod.UnknownModEntry unknown -> unknown.modId();
        };
    }

    private static boolean eligible(ModDefinition definition, EquipmentItemV1 preview) {
        return definition.rank() == ModRank.RANK_1
                && definition.allowedSlots().contains(preview.slot())
                && preview.tier() == definition.rank().tier()
                && definition.acceptsAttackTags(java.util.Set.<AttackTag>of());
    }

    private double boundedRandom() {
        double value = random.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalStateException("staging MOD RNG is not finite");
        return Math.max(0.0, Math.min(Math.nextDown(1.0), value));
    }

    /** Explicit test fixture; it is not a production MOD definition. */
    public static List<Candidate> defaultCandidates() {
        ModDefinition keenEdge = new ModDefinition(SchemaVersions.MOD_DEFINITION, KEEN_EDGE,
                ModRank.RANK_1, java.util.Set.of(EquipmentSlot.WEAPON), java.util.Set.of(),
                java.util.Set.of(), ModTagMatchPolicy.ALL_REQUIRED,
                "projects:physical-attack", 1.0, 2.0, ModStackingLayer.BASE_FLAT, SOURCE,
                new ModDisplayMetadata("Keen Edge (Staging)", "Non-production test MOD"), 1);
        return List.of(new Candidate(keenEdge, 1.0));
    }

    public record Candidate(ModDefinition definition, double weight) {
        public Candidate {
            definition = Objects.requireNonNull(definition, "definition");
            if (!definition.modId().startsWith("projects:staging-")) {
                throw new IllegalArgumentException("staging MOD ID is required");
            }
        }
    }
}
