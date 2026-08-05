package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.beta.activation.track1.module.Track1RuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track2.CombatElementsRuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track3.Track3RuntimeModuleProvider;
import io.github.gyai.projects.beta.activation.track4.Track4RuntimeModuleProvider;

import java.util.ArrayList;
import java.util.List;

/** Identity-preserving registry of the four production provider instances. */
public final class BetaActivationWave1ProviderRegistry {
    private final Track1RuntimeModuleProvider track1;
    private final CombatElementsRuntimeModuleProvider track2;
    private final Track3RuntimeModuleProvider track3;
    private final Track4RuntimeModuleProvider track4;
    private final List<BetaRuntimeModule> modules;

    public BetaActivationWave1ProviderRegistry(
            Track1RuntimeModuleProvider track1,
            CombatElementsRuntimeModuleProvider track2,
            Track3RuntimeModuleProvider track3,
            Track4RuntimeModuleProvider track4
    ) {
        this.track1 = java.util.Objects.requireNonNull(track1);
        this.track2 = java.util.Objects.requireNonNull(track2);
        this.track3 = java.util.Objects.requireNonNull(track3);
        this.track4 = java.util.Objects.requireNonNull(track4);
        ArrayList<BetaRuntimeModule> actual = new ArrayList<>();
        actual.addAll(track1.modules());
        actual.add(track2.module());
        actual.addAll(track3.modules());
        actual.addAll(track4.modules());
        modules = new BetaActivationWave1ModuleRegistry(actual).modules();
    }

    public List<BetaRuntimeModule> modules() { return modules; }
    public Track1RuntimeModuleProvider track1() { return track1; }
    public CombatElementsRuntimeModuleProvider track2() { return track2; }
    public Track3RuntimeModuleProvider track3() { return track3; }
    public Track4RuntimeModuleProvider track4() { return track4; }

    public boolean preservesModuleIdentity() {
        ArrayList<BetaRuntimeModule> actual = new ArrayList<>();
        actual.addAll(track1.modules()); actual.add(track2.module());
        actual.addAll(track3.modules()); actual.addAll(track4.modules());
        return actual.size() == modules.size()
                && actual.stream().allMatch(candidate -> modules.stream()
                .anyMatch(registered -> registered == candidate));
    }
}
