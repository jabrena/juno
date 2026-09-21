package io.github.jabrena.juno;

import java.nio.file.Path;
import java.util.List;

/**
 * What to compile: a closed-world classpath plus the entry point's fully qualified class name.
 *
 * @param gcLoggingEnabled when {@code true}, the generated runtime shim's garbage collector prints
 *     one {@code Serial} line per collection (see {@link io.github.jabrena.juno.backend.CortexM4AsmBackend}).
 */
public record CompilationRequest(List<Path> classPath, String mainClass, boolean gcLoggingEnabled) {
    public CompilationRequest(List<Path> classPath, String mainClass) {
        this(classPath, mainClass, false);
    }
}
