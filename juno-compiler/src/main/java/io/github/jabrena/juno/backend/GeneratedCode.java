package io.github.jabrena.juno.backend;

/**
 * What a backend generates for one program.
 *
 * @param assembly the program's assembly source
 * @param runtimeShim a small {@code extern "C"} C++ source that must be compiled alongside {@code assembly} in the
 *     same sketch
 * @param entryPointSymbol the assembly symbol of the program's {@code main}, which the sketch wrapper calls
 */
public record GeneratedCode(String assembly, String runtimeShim, String entryPointSymbol) {
}
