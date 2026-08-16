package io.github.gyai.projects.beta.activation.track2;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;
import io.github.gyai.projects.beta.activation.BetaRuntimeModuleId;

/** Track-local SPI adapted by the Wave 1 Integration Gate. */
public interface BetaRuntimeModuleProvider {
    BetaRuntimeModuleId moduleId();

    BetaRuntimeModule module();
}
