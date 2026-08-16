package io.github.gyai.projects.beta.activation.track1.bukkit;

/** Bounded logging is supplied by the Integration Gate. */
public interface Track1DiagnosticSink {
    void report(String operation, RuntimeException exception);
}
