package io.github.gyai.projects.crafting;

import io.github.gyai.projects.schema.SchemaVersions;
import io.github.gyai.projects.transaction.DomainId;
import io.github.gyai.projects.transaction.QuantityMath;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RecipeDefinitionV1(
        int schemaVersion,
        long revision,
        String recipeId,
        RecipeType type,
        List<Input> inputs,
        Optional<Input> catalyst,
        OutputProposal output,
        FeePlaceholder fee,
        Optional<String> facilityRequirement,
        Optional<ProfessionRequirement> professionRequirement,
        Optional<String> regionalSpecialization
) {
    public RecipeDefinitionV1 {
        if (schemaVersion != SchemaVersions.RECIPE_DEFINITION) {
            throw new IllegalArgumentException("Unsupported recipe schema");
        }
        if (revision < 0) throw new IllegalArgumentException("Negative revision");
        recipeId = DomainId.requireNamespaced(recipeId, "recipe ID");
        Objects.requireNonNull(type, "type");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        if (inputs.isEmpty()) throw new IllegalArgumentException("Recipe has no inputs");
        HashSet<String> inputIds = new HashSet<>();
        for (Input input : inputs) {
            Objects.requireNonNull(input, "input");
            if (!inputIds.add(input.resourceId())) {
                throw new IllegalArgumentException("Duplicate recipe input: "
                        + input.resourceId());
            }
        }
        catalyst = catalyst == null ? Optional.empty() : catalyst;
        catalyst.ifPresent(value -> {
            if (inputIds.contains(value.resourceId())) {
                throw new IllegalArgumentException("Catalyst duplicates an input");
            }
        });
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(fee, "fee");
        facilityRequirement = validateOptionalKey(
                facilityRequirement, "facility requirement");
        professionRequirement = professionRequirement == null
                ? Optional.empty() : professionRequirement;
        regionalSpecialization = validateOptionalKey(
                regionalSpecialization, "regional specialization");
        if (type == RecipeType.CRAFT_EQUIPMENT_BASE && !output.equipmentBase()) {
            throw new IllegalArgumentException(
                    "Equipment recipe must produce an equipment base");
        }
        if (type != RecipeType.CRAFT_EQUIPMENT_BASE && output.equipmentBase()) {
            throw new IllegalArgumentException(
                    "Refining recipe cannot produce an equipment base");
        }
    }

    public enum RecipeType {
        REFINE_DIRECT,
        REFINE_STAGED,
        CRAFT_EQUIPMENT_BASE
    }

    public enum InputDisposition {
        REFUNDABLE,
        NON_REFUNDABLE
    }

    public record Input(
            String resourceId,
            long quantity,
            InputDisposition disposition
    ) {
        public Input {
            resourceId = DomainId.requireNamespaced(resourceId, "resource ID");
            quantity = QuantityMath.requirePositive(quantity, "input quantity");
            Objects.requireNonNull(disposition, "disposition");
        }
    }

    public record FeePlaceholder(boolean specified, double amount) {
        public FeePlaceholder {
            if (!Double.isFinite(amount) || amount < 0
                    || (!specified && amount != 0.0)) {
                throw new IllegalArgumentException("Invalid fee placeholder");
            }
        }

        public static FeePlaceholder unspecified() {
            return new FeePlaceholder(false, 0.0);
        }
    }

    public record ProfessionRequirement(String professionId, long minimumProgress) {
        public ProfessionRequirement {
            professionId = DomainId.requireNamespaced(
                    professionId, "profession ID");
            if (minimumProgress < 0) {
                throw new IllegalArgumentException("Negative mastery progress");
            }
        }
    }

    private static Optional<String> validateOptionalKey(
            Optional<String> value,
            String label
    ) {
        Optional<String> result = value == null ? Optional.empty() : value;
        return result.map(entry -> DomainId.requireNamespaced(entry, label));
    }
}
