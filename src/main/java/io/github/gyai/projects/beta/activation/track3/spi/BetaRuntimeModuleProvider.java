package io.github.gyai.projects.beta.activation.track3.spi;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;

import java.util.List;

/** Track-local SPI; the later Integration Gate owns adaptation and registration. */
public interface BetaRuntimeModuleProvider {
    List<? extends BetaRuntimeModule> modules();
}
