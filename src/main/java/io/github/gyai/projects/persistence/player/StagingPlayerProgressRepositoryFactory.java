package io.github.gyai.projects.persistence.player;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/** Package bridge that preserves the canonical codec whitelist for staging writes. */
public final class StagingPlayerProgressRepositoryFactory {
    private StagingPlayerProgressRepositoryFactory() { }

    public static PlayerProgressRepository create(Path playersDirectory,
                                                  Set<String> settingWhitelist) {
        return new FilePlayerProgressRepository(playersDirectory,
                settingWhitelist == null ? Set.of() : settingWhitelist,
                FilePlayerProgressRepository.DEFAULT_WRITE_QUEUE_CAPACITY,
                new NioPlayerProgressFileOperations(), Duration.ofSeconds(30));
    }
}
