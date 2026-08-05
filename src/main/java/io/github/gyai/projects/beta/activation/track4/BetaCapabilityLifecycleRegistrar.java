package io.github.gyai.projects.beta.activation.track4;

/** Registration boundary for the capability-channel player lifecycle listener. */
public interface BetaCapabilityLifecycleRegistrar {
    void register(BetaCapabilityLifecycleListener listener);

    void unregister();
}
