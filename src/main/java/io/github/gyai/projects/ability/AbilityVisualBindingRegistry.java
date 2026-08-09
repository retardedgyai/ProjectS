package io.github.gyai.projects.ability;

import java.util.*;
/** Independent binding store so definitions and assignments have separate startup ownership. */
public final class AbilityVisualBindingRegistry {
    private final Map<String,String> values=new HashMap<>();
    public void register(AbilityVisualBinding binding) { if(values.putIfAbsent(binding.abilityId(),binding.visualId())!=null) throw new IllegalArgumentException("Duplicate ability visual binding"); }
    public Optional<String> findVisualId(String abilityId) { return Optional.ofNullable(values.get(abilityId)); }
}
