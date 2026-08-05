package io.github.gyai.projects.beta.activation.track4;

/** Track 3 capability boundary used for dependency health and command admission. */
public interface StagingEconomyOperationPort {
    boolean available();

    String healthDetail();
}
