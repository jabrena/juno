package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompilationReport;
import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.analysis.ConfigSuggestionFormatter;
import io.github.jabrena.juno.analysis.RuntimeRiskReportFormatter;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A goal that first compiles the configured entry point into an Arduino sketch. */
abstract class AbstractJunoMojo extends AbstractArduinoMojo {
    private final RuntimeRiskReportFormatter riskFormatter = new RuntimeRiskReportFormatter();
    private final ConfigSuggestionFormatter suggestionFormatter = new ConfigSuggestionFormatter();

    /**
     * Fully qualified Java entry-point class: the one program to compile. There is no default, so a project with several
     * programs always says which one; when it is missing the goal fails with how to pass it.
     */
    @Parameter(property = "juno.main")
    private @Nullable String mainClass;

    /** Maven compile classpath used as Juno's closed-world classpath. */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private @Nullable List<String> classpathElements;

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
     * Target board (e.g. {@code arduino-uno-r4-wifi}, {@code arduino-uno-q}), required when the entry point has no
     * {@code @Board} annotation or declares more than one board; the compiler fails rather than picking one silently.
     * Ignored when the entry point declares a single board.
     */
    @Parameter(property = "juno.board")
    private @Nullable String board;

    /**
     * Size of the garbage-collected arena, in the JVM's {@code -Xmx} syntax ({@code 49152}, {@code 48k},
     * {@code 1m}); a multiple of 8. Defaults to the runtime's 8 KB. Every {@code new} array, string and object
     * lives in the arena, so a program that keeps large data sets in RAM (for example, tables loaded from a file at
     * startup) needs a bigger one; the compiler's startup estimate suggests a size when the default is too small.
     */
    @Parameter(property = "juno.Xmx")
    private @Nullable String arenaSize;

    /**
     * Stack of each extra task, in the JVM's {@code -Xss} syntax; a multiple of 8. Defaults to the board's own
     * size (2048 bytes on the UNO R4 WiFi, 4096 on the UNO Q).
     */
    @Parameter(property = "juno.Xss")
    private @Nullable String threadStackSize;

    /**
     * Number of scheduler slots, including the main task. Set this to one plus the maximum number of simultaneous
     * structured subtasks when RAM is constrained; it defaults to four.
     */
    @Parameter(property = "juno.maxThreads")
    private @Nullable String maxThreads;

    final CompiledSketch compileSketch() {
        String entryPoint = configured(mainClass).orElseThrow(() -> new ArduinoCliException(
                "Missing required Juno entry point: choose the program to compile with -Djuno.main=<class> "
                        + "(e.g. -Djuno.main=com.example.Main) or configure <mainClass>"));
        if (classpathElements == null || classpathElements.isEmpty()) {
            throw new ArduinoCliException("Maven compile classpath is empty");
        }
        List<Path> classpath = classpathElements.stream().map(Path::of).toList();
        SketchLayout layout = new SketchLayout(outputDirectory.toPath().toAbsolutePath().normalize(), entryPoint);

        RuntimeConfig runtimeConfig = RuntimeConfig.of(Optional.ofNullable(arenaSize),
                Optional.ofNullable(threadStackSize), Optional.ofNullable(maxThreads));
        CompilationResult result = new JunoCompiler().compileTo(
                new CompilationRequest(classpath, entryPoint, gcLog, configured(board), runtimeConfig),
                layout.assembly(), layout.runtimeShim());
        result.sketchWrapper().writeTo(layout.wrapper());

        CompilationReport report = result.report();
        String targetFqbn = targetFqbn(report.board().fqbn());
        if (!targetFqbn.equals(report.board().fqbn())) {
            getLog().warn("Configured FQBN " + targetFqbn + " overrides @Board target " + report.board().fqbn());
        }
        getLog().info("Generated " + layout.assembly() + ", " + layout.runtimeShim() + ", and " + layout.wrapper()
                + " for " + report.board().displayName() + " with the ASM backend (fqbn " + targetFqbn + ")");
        riskFormatter.format(report.runtimeRisks()).forEach(getLog()::info);
        suggestionFormatter.format(report.configSuggestions()).forEach(getLog()::info);
        return new CompiledSketch(layout.directory(), targetFqbn);
    }

    final void verifySketch(ArduinoCli cli, CompiledSketch sketch) {
        getLog().info("Compiling ASM Arduino sketch with arduino-cli");
        cli.compile(sketch.fqbn(), sketch.directory());
    }

    /** A generated sketch directory and the FQBN to build it for. */
    record CompiledSketch(Path directory, String fqbn) {
    }

    /**
     * Where one entry point's sketch goes: {@code <output>/<Main>Asm/}, holding {@code <Main>.S},
     * {@code <Main>Shim.cpp} and the {@code <Main>Asm.ino} wrapper Arduino CLI requires to match the directory.
     */
    private record SketchLayout(Path outputDirectory, String mainClass) {
        String simpleName() {
            return mainClass.substring(mainClass.lastIndexOf('.') + 1);
        }

        Path directory() {
            return outputDirectory.resolve(simpleName() + "Asm");
        }

        Path assembly() {
            return directory().resolve(simpleName() + ".S");
        }

        Path runtimeShim() {
            return directory().resolve(simpleName() + "Shim.cpp");
        }

        Path wrapper() {
            return directory().resolve(simpleName() + "Asm.ino");
        }
    }
}
