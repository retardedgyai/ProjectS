package io.github.gyai.projects.persistence.player;

import java.io.IOException;
import java.nio.file.Path;

/** Injectable filesystem boundary for atomic-replacement failure verification. */
public interface PlayerProgressFileOperations {
    void writeAndFlush(Path temporary, byte[] bytes) throws IOException;

    void copyPrevious(Path source, Path backup) throws IOException;

    void atomicReplace(Path temporary, Path target) throws IOException;
}
