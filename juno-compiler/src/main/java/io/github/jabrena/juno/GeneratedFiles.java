package io.github.jabrena.juno;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes generated sources, creating parent directories and reporting failures as {@link CompileException}s. */
final class GeneratedFiles {
    void write(Path output, String source, String description) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CompileException("Cannot write generated " + description + " to " + output, exception);
        }
    }
}
