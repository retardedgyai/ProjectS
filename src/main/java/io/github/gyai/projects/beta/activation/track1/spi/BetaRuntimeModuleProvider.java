package io.github.gyai.projects.beta.activation.track1.spi;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;

import java.util.List;

/** Track-local discovery contract; the Integration Gate owns central registration. */
public interface BetaRuntimeModuleProvider {
    List<BetaRuntimeModule> modules();
}
