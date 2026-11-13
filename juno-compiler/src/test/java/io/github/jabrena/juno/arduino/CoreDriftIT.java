package io.github.jabrena.juno.arduino;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detects an Arduino core upgrade that takes RAM from every program. Each board links
 * {@code CoreDriftBaseline} with the core the container has installed, and its static RAM is compared with the figure
 * recorded in {@code core-drift-baselines.txt}; the check fails when the growth exceeds the board's margin.
 *
 * <p>Opt-in with the other arduino-cli tests: {@code ./mvnw -pl juno-compiler -am -Parduino-cli verify}. The container
 * runs {@code arduino-cli} with {@code LC_ALL=C}, so its English output is parsed whatever the host's locale. When the
 * core is upgraded on purpose, re-measure the figure and update the baselines file in the same change.
 */
@Tag("arduino-cli")
@Testcontainers(disabledWithoutDocker = true)
class CoreDriftIT {
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path PROGRAM = BASEDIR.resolve("src/test/arduino/programs/CoreDriftBaseline.java");
    private static final Path CLASSES = BASEDIR.resolve("target/arduino-cli/drift-classes");
    private static final String SKETCH_NAME = "CoreDriftBaselineAsm";

    @Container
    private static final GenericContainer<?> ARDUINO_CLI = ArduinoCliCompileIT.container()
            .withCommand("sleep", "infinity");

    @BeforeAll
    static void compileBaselineProgram() throws IOException {
        Files.createDirectories(CLASSES);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler.run(null, null, null, "--enable-preview", "--release", "27", "-d", CLASSES.toString(),
                "-cp", CompilerTestSupport.JUNO_API_CLASSES.toString(), PROGRAM.toString()))
                .as("javac of CoreDriftBaseline").isZero();
    }

    static Stream<CoreBaseline> baselines() throws IOException {
        return CoreBaseline.load().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("baselines")
    void shouldNotGrowTheStaticRamBeyondTheMargin(CoreBaseline baseline) throws Exception {
        // Given: the baseline program, generated the way juno:compile writes it, copied into the container
        GeneratedSketch sketch = generateSketch(baseline.board());
        String target = "/sketches/drift/" + baseline.board().id() + "/" + SKETCH_NAME;
        ARDUINO_CLI.copyFileToContainer(MountableFile.forHostPath(sketch.directory()), target);
        String fqbn = sketch.fqbn();

        // When: compile it with the core the container has installed
        List<String> command = new ArrayList<>(List.of("env", "LC_ALL=C", "LANG=C", "arduino-cli",
                "compile", "--fqbn", fqbn));
        if (baseline.board() == Board.UNO_R3_WIFI_REV2) {
            command.add("--build-property");
            command.add("compiler.cpp.extra_flags=-DSPI_DRIVER_SELECT=3");
        }
        command.add(target);
        ExecResult compile = ARDUINO_CLI.execInContainer(command.toArray(String[]::new));
        String output = compile.getStdout() + compile.getStderr();
        assertThat(compile.getExitCode()).as("arduino-cli compile %s:%n%s", baseline.board().id(), output).isZero();

        ExecResult cores = ARDUINO_CLI.execInContainer("env", "LC_ALL=C", "LANG=C", "arduino-cli", "core", "list");
        CoreDriftCheck.Measurement measured = new CoreDriftCheck.Measurement(
                ArduinoCliReport.installedVersion(cores.getStdout(), ArduinoCliReport.platformOf(fqbn)),
                ArduinoCliReport.staticRamBytes(output));

        // Then
        CoreDriftCheck.Verdict verdict = CoreDriftCheck.evaluate(baseline, measured);
        assertThat(verdict.passes()).as(verdict.message()).isTrue();
    }

    /** A generated sketch directory and the FQBN the compiler says it builds with. */
    private record GeneratedSketch(Path directory, String fqbn) {
    }

    /** Writes the sketch juno:compile would write for {@code board}. */
    private static GeneratedSketch generateSketch(Board board) throws IOException {
        Path directory = BASEDIR.resolve("target/arduino-cli/drift").resolve(board.id()).resolve(SKETCH_NAME);
        Files.createDirectories(directory);
        CompilationResult result = new JunoCompiler().compileTo(
                List.of(CLASSES, CompilerTestSupport.JUNO_API_CLASSES), "demo.CoreDriftBaseline",
                directory.resolve("CoreDriftBaseline.S"), directory.resolve("CoreDriftBaselineShim.cpp"), false,
                Optional.of(board.id()));
        result.sketchWrapper().writeTo(directory.resolve(SKETCH_NAME + ".ino"));
        return new GeneratedSketch(directory, result.fqbn());
    }
}
