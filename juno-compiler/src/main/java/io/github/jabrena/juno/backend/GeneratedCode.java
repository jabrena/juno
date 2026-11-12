package io.github.jabrena.juno.backend;

/**
 * What a backend generates for one program.
 *
 * @param assembly the program's assembly source
 * @param runtimeShim a small {@code extern "C"} C++ source that must be compiled alongside {@code assembly} in the
 *     same sketch
 * @param entryPointSymbol the assembly symbol of the program's {@code main}, which the sketch wrapper calls
 */
public record GeneratedCode(String assembly, String runtimeShim, String entryPointSymbol, String fqbnOptions,
                            String mainLauncher) {
    /** Code that builds with the board's own FQBN and whose sketch calls the entry point directly. */
    public GeneratedCode(String assembly, String runtimeShim, String entryPointSymbol) {
        this(assembly, runtimeShim, entryPointSymbol, "", "");
    }
}
