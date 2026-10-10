package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompilationReport;
import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.analysis.ConfigSuggestionFormatter;
import io.github.jabrena.juno.analysis.RuntimeRiskReportFormatter;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

abstract class AbstractJunoMojo extends AbstractArduinoMojo {
    /** Fully qualified Java entry-point class. */
    @Parameter(property = "juno.main", required = true)
    private String mainClass;

    /** Maven compile classpath used as Juno's closed-world classpath. */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    /** Directory under which one Arduino sketch directory is created per entry point. */
    @Parameter(property = "juno.outputDirectory", defaultValue = "${project.build.directory}/juno", required = true)
    private File outputDirectory;

    /**
     * Have the generated garbage collector print one Serial line per collection (arena bytes used
     * before/after), visible via {@code juno:monitor}. Off by default: costs no extra
     * flash/RAM/time when disabled, since the print statements aren't emitted at all.
     */
    @Parameter(property = "juno.gcLog", defaultValue = "false")
    private boolean gcLog;

    /**
     * Target board (e.g. {@code arduino-uno-r4-wifi}, {@code arduino-uno-q}), required only when the entry point's
     * {@code @Board} annotation declares more than one board; the compiler fails rather than picking
     * one silently. Ignored when the entry point declares a single board.
     */
    @Parameter(property = "juno.board")
    private String board;

    /**
     * Size of the garbage-collected arena, in the JVM's {@code -Xmx} syntax ({@code 49152}, {@code 48k},
     * {@code 1m}); a multiple of 8. Defaults to the runtime's 8 KB. Every {@code new} array, string and object
     * lives in the arena, so a program that keeps large data sets in RAM (for example, tables loaded from a file at
     * startup) needs a bigger one; the compiler's startup estimate suggests a size when the default is too small.
     */
    @Parameter(property = "juno.Xmx")
    private String arenaSize;

    /**
     * Stack of each extra task, in the JVM's {@code -Xss} syntax; a multiple of 8. Defaults to the board's own
     * size (2048 bytes on the UNO R4 WiFi, 4096 on the UNO Q).
     */
    @Parameter(property = "juno.Xss")
    private String threadStackSize;

    final CompiledSketch compileSketch() {
        if (mainClass == null || mainClass.isBlank()) {
            throw new ArduinoCliException("Missing required Juno entry point; configure <mainClass> or -Djuno.main=<class>");
        }
        if (classpathElements == null || classpathElements.isEmpty()) {
            throw new ArduinoCliException("Maven compile classpath is empty");
        }

        String trimmedMain = mainClass.trim();
        String simpleName = trimmedMain.substring(trimmedMain.lastIndexOf('.') + 1);
        List<Path> classpath = classpathElements.stream().map(Path::of).toList();

        return compileAsmSketch(classpath, trimmedMain, simpleName);
    }

    private CompiledSketch compileAsmSketch(List<Path> classpath, String mainClass, String simpleName) {
        String sketchName = simpleName + "Asm";
        Path sketchDirectory = outputDirectory().resolve(sketchName);
        Path assembly = sketchDirectory.resolve(simpleName + ".S");
        Path shim = sketchDirectory.resolve(simpleName + "Shim.cpp");
        Path wrapper = sketchDirectory.resolve(sketchName + ".ino");

        Optional<String> requestedBoard = board == null || board.isBlank() ? Optional.empty() : Optional.of(board.trim());
        RuntimeConfig runtimeConfig = RuntimeConfig.of(Optional.ofNullable(arenaSize),
                Optional.ofNullable(threadStackSize));
        CompilationResult result = new JunoCompiler().compileTo(
                new CompilationRequest(classpath, mainClass, gcLog, requestedBoard, runtimeConfig), assembly, shim);
        writeAsmWrapper(wrapper, result.entryPointSymbol());
        String targetFqbn = targetFqbn(result.report().board().fqbn());
        warnForFqbnOverride(targetFqbn, result.report());
        getLog().info("Generated " + assembly + ", " + shim + ", and " + wrapper + " for "
                + result.report().board().displayName() + " with the ASM backend (fqbn "
                + targetFqbn + ")");
        RuntimeRiskReportFormatter.format(result.report().runtimeRisks()).forEach(getLog()::info);
        ConfigSuggestionFormatter.format(result.report().configSuggestions()).forEach(getLog()::info);
        return new CompiledSketch(sketchDirectory, targetFqbn);
    }

    private Path outputDirectory() {
        return outputDirectory.toPath().toAbsolutePath().normalize();
    }

    private void warnForFqbnOverride(String targetFqbn, CompilationReport report) {
        if (!targetFqbn.equals(report.board().fqbn())) {
            getLog().warn("Configured FQBN " + targetFqbn + " overrides @Board target "
                    + report.board().fqbn());
        }
    }

    static void writeAsmWrapper(Path output, String entryPointSymbol) {
        String wrapper = """
                // Generated by Juno. Do not edit.
                extern "C" void %s();

                void setup() {
                  %s();
                }

                void loop() {
                }
                """.formatted(entryPointSymbol, entryPointSymbol);
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, wrapper, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CompileException("Cannot write generated ASM sketch wrapper to " + output, exception);
        }
    }

    final void verifySketch(ArduinoCli cli, CompiledSketch sketch) {
        getLog().info("Compiling ASM Arduino sketch with arduino-cli");
        cli.compile(sketch.fqbn(), sketch.directory());
    }

    record CompiledSketch(Path directory, String fqbn) {
    }
}
