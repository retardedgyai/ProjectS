package io.github.gyai.projects.gathering;

import java.time.Instant;

@FunctionalInterface
public interface RespawnPolicy {
    boolean canRespawn(Instant depletedAt, Instant now);
}
