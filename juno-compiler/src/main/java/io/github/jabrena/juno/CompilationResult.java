package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.runtime.spi.CoprocessorImage;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Generated assembly, its C++ runtime shim, entry symbol, compilation report, and the FQBN the sketch builds with.
 * {@code coprocessor} is the image the board's other core runs, absent when the program is one image.
 */
public record CompilationResult(String assembly, String runtimeShim, String entryPointSymbol,
                                CompilationReport report, String fqbn, String mainLauncher,
                                Optional<CoprocessorImage> coprocessor) {

    public CompilationResult(String assembly, String runtimeShim, String entryPointSymbol, CompilationReport report,
                             String fqbn, String mainLauncher) {
        this(assembly, runtimeShim, entryPointSymbol, report, fqbn, mainLauncher, Optional.empty());
    }

    /** The FQBN the coprocessor image builds with: the program's board with the image's menu options. */
    public Optional<String> coprocessorFqbn() {
        return coprocessor.map(image -> fqbn + image.fqbnOptions());
    }

    /** Deterministic structural measurements of the generated sources. */
    public CompilationMetrics metrics() {
        return CompilationMetrics.from(this);
    }

    /** The {@code .ino} wrapper that calls this program's entry point. */
    public SketchWrapper sketchWrapper() {
        return new SketchWrapper(entryPointSymbol, mainLauncher);
    }

    /** Writes the assembly and runtime shim. */
    void writeTo(Path assemblyOutput, Path runtimeShimOutput) {
        GeneratedFiles files = new GeneratedFiles();
        files.write(assemblyOutput, assembly, "assembly");
        files.write(runtimeShimOutput, runtimeShim, "runtime shim");
    }
}
