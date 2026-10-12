package io.github.jabrena.juno;

import java.nio.file.Path;

/** Generated assembly, its C++ runtime shim, entry symbol, and compilation report. */
public record CompilationResult(String assembly, String runtimeShim, String entryPointSymbol,
                                CompilationReport report) {
    /** Deterministic structural measurements of the generated sources. */
    public CompilationMetrics metrics() {
        return CompilationMetrics.from(this);
    }

    /** The {@code .ino} wrapper that calls this program's entry point. */
    public SketchWrapper sketchWrapper() {
        return new SketchWrapper(entryPointSymbol);
    }

    /** Writes the assembly and runtime shim. */
    void writeTo(Path assemblyOutput, Path runtimeShimOutput) {
        GeneratedFiles files = new GeneratedFiles();
        files.write(assemblyOutput, assembly, "assembly");
        files.write(runtimeShimOutput, runtimeShim, "runtime shim");
    }
}
