package io.github.jabrena.juno;

import java.nio.file.Path;
import java.util.List;

/** What to compile: a closed-world classpath plus the entry point's fully qualified class name. */
public record CompilationRequest(List<Path> classPath, String mainClass) {
}
