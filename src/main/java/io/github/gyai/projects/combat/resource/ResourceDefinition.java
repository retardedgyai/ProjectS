package io.github.gyai.projects.combat.resource;

public record ResourceDefinition(ResourceType type, int maximum, double regenerationPerSecond) {
    public static final ResourceDefinition FIGHTING_SPIRIT =
            new ResourceDefinition(ResourceType.FIGHTING_SPIRIT, 100, 0);
    public static final ResourceDefinition NONE = new ResourceDefinition(ResourceType.NONE, 0, 0);
}
