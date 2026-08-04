package io.github.gyai.projects.monster.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

final class DefinitionReloadGuard {
    private DefinitionReloadGuard() {
    }

    static List<Path> yamlFiles(
            Path directory,
            int maximumDefinitions,
            long maximumFileBytes
    ) throws IOException {
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.filter(value -> value.getFileName().toString()
                            .endsWith(".yml"))
                    .sorted()
                    .toList();
        }
        validateCount(files.size(), maximumDefinitions);
        for (Path path : files) {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(path.getFileName()
                        + "は通常ファイルではありません");
            }
            validateFileSize(Files.size(path), maximumFileBytes);
        }
        return files;
    }

    static void validateCount(int count, int maximumDefinitions)
            throws IOException {
        if (count < 0 || count > maximumDefinitions) {
            throw new IOException("定義ファイルは最大"
                    + maximumDefinitions + "件です: " + count);
        }
    }

    static void validateFileSize(long bytes, long maximumFileBytes)
            throws IOException {
        if (bytes < 0L || bytes > maximumFileBytes) {
            throw new IOException("定義ファイルは最大"
                    + maximumFileBytes + " bytesです: " + bytes);
        }
    }
}
