package io.github.gyai.projects.combat.classsystem;

import io.github.gyai.projects.combat.resource.ResourceDefinition;
import org.bukkit.Material;

public record ClassDefinition(String id, String displayName, String requiredWeaponId,
                              ResourceDefinition resource, Material devIcon, String description) {
}
