package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;

import java.util.List;

/** Track-local provider contract. The later Integration Gate owns registration. */
public interface BetaRuntimeModuleProvider {
    List<BetaRuntimeModule> modules();
}
