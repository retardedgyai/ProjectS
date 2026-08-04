package io.github.gyai.projects.persistence.player;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class NioPlayerProgressFileOperations
        implements PlayerProgressFileOperations {
    @Override
    public void writeAndFlush(Path temporary, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    @Override
    public void copyPrevious(Path source, Path backup) throws IOException {
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    @Override
    public void atomicReplace(Path temporary, Path target) throws IOException {
        Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
