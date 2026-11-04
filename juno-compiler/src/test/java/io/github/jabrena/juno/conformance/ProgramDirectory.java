package io.github.jabrena.juno.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** A directory of Java program sources, listed in file-name order so runs and reports are deterministic. */
public record ProgramDirectory(Path directory) {
    public ProgramDirectory {
        Objects.requireNonNull(directory);
    }

    /** The programs directly in the directory. */
    public List<ProgramSource> programs() {
        return sources().stream().map(ProgramSource::new).toList();
    }

    /** The {@code .java} files directly in the directory. */
    public List<Path> sources() {
        try (Stream<Path> files = Files.list(directory)) {
            return javaFiles(files);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The {@code .java} files anywhere under the directory. */
    public List<Path> allSources() {
        try (Stream<Path> files = Files.walk(directory)) {
            return javaFiles(files);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> javaFiles(Stream<Path> files) {
        return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
    }
}
