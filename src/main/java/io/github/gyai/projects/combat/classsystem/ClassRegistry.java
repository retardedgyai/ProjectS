package io.github.gyai.projects.combat.classsystem;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClassRegistry {
    private final Map<String, RegisteredClass> classes = new LinkedHashMap<>();

    public void register(ClassDefinition definition, ClassController controller) {
        classes.put(definition.id(), new RegisteredClass(definition, controller));
    }

    public RegisteredClass getByWeapon(String weaponId) {
        if (weaponId == null) return null;
        return classes.values().stream()
                .filter(entry -> entry.definition().requiredWeaponId().equals(weaponId)).findFirst().orElse(null);
    }

    public Collection<RegisteredClass> getAll() { return java.util.List.copyOf(classes.values()); }

    public record RegisteredClass(ClassDefinition definition, ClassController controller) { }
}
