package io.github.gyai.projects.beta.activation.track3;

import java.nio.file.Path;

/** Resolves only the approved beta-staging transaction subtree. */
public record StagingEconomyPaths(Path pluginDataDirectory, Path transactionsDirectory) {
    public StagingEconomyPaths {
        if (pluginDataDirectory == null || transactionsDirectory == null) {
            throw new IllegalArgumentException("staging paths are required");
        }
        pluginDataDirectory = pluginDataDirectory.toAbsolutePath().normalize();
        transactionsDirectory = transactionsDirectory.toAbsolutePath().normalize();
        Path staging = pluginDataDirectory.resolve("beta-staging").normalize();
        if (!transactionsDirectory.equals(staging.resolve("transactions").normalize())
                || !transactionsDirectory.startsWith(pluginDataDirectory)) {
            throw new IllegalArgumentException("transactions path must be beta-staging/transactions");
        }
    }

    public static StagingEconomyPaths under(Path pluginDataDirectory) {
        Path root = pluginDataDirectory.toAbsolutePath().normalize();
        return new StagingEconomyPaths(
                root, root.resolve("beta-staging").resolve("transactions"));
    }
}
