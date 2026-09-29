package io.github.jabrena.juno;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What to compile: a closed-world classpath plus the entry point's fully qualified class name.
 *
 * @param gcLoggingEnabled when {@code true}, the generated runtime shim's garbage collector prints
 *     one {@code Serial} line per collection (see {@link io.github.jabrena.juno.backend.Thumb2AsmBackend}).
 * @param requestedBoard the target board (see {@code io.github.jabrena.juno.board.Board#fromId}), required
 *     only when the entry point's {@code @Board} annotation declares more than one board.
 */
public record CompilationRequest(List<Path> classPath, String mainClass, boolean gcLoggingEnabled,
                                  Optional<String> requestedBoard) {
    public CompilationRequest(List<Path> classPath, String mainClass) {
        this(classPath, mainClass, false, Optional.empty());
    }

    public CompilationRequest(List<Path> classPath, String mainClass, boolean gcLoggingEnabled) {
        this(classPath, mainClass, gcLoggingEnabled, Optional.empty());
    }
}
