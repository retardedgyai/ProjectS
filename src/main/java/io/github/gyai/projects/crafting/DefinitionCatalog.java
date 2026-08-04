package io.github.gyai.projects.crafting;

import io.github.gyai.projects.gathering.ResourceDefinitionV1;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DefinitionCatalog {
    private final Map<String, ResourceDefinitionV1> resources;
    private final Map<String, RecipeDefinitionV1> recipes;

    public DefinitionCatalog(
            List<ResourceDefinitionV1> resources,
            List<RecipeDefinitionV1> recipes
    ) {
        this.resources = indexResources(resources);
        this.recipes = indexRecipes(recipes);
        for (RecipeDefinitionV1 recipe : this.recipes.values()) {
            for (RecipeDefinitionV1.Input input : recipe.inputs()) {
                if (!this.resources.containsKey(input.resourceId())) {
                    throw new IllegalArgumentException(
                            "Unknown input resource: " + input.resourceId());
                }
            }
            recipe.catalyst().ifPresent(catalyst -> {
                if (!this.resources.containsKey(catalyst.resourceId())) {
                    throw new IllegalArgumentException(
                            "Unknown catalyst: " + catalyst.resourceId());
                }
            });
        }
    }

    public Map<String, ResourceDefinitionV1> resources() {
        return resources;
    }

    public Map<String, RecipeDefinitionV1> recipes() {
        return recipes;
    }

    private static Map<String, ResourceDefinitionV1> indexResources(
            List<ResourceDefinitionV1> definitions
    ) {
        LinkedHashMap<String, ResourceDefinitionV1> result = new LinkedHashMap<>();
        for (ResourceDefinitionV1 definition : safe(definitions)) {
            Objects.requireNonNull(definition, "resource definition");
            if (result.put(definition.resourceId(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate resource ID: " + definition.resourceId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, RecipeDefinitionV1> indexRecipes(
            List<RecipeDefinitionV1> definitions
    ) {
        LinkedHashMap<String, RecipeDefinitionV1> result = new LinkedHashMap<>();
        for (RecipeDefinitionV1 definition : safe(definitions)) {
            Objects.requireNonNull(definition, "recipe definition");
            if (result.put(definition.recipeId(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate recipe ID: " + definition.recipeId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static <T> List<T> safe(List<T> definitions) {
        return definitions == null ? List.of() : List.copyOf(definitions);
    }
}
