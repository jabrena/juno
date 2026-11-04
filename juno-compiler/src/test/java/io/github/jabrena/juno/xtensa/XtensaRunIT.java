package io.github.jabrena.juno.xtensa;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.conformance.ConformanceWorkspace;
import io.github.jabrena.juno.conformance.ProgramSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The Xtensa counterpart of {@code QemuRunIT} for the primitive semantic-conformance slice: each catalogued program is
 * compiled by Juno for the Nano ESP32, built by the pinned {@code arduino:esp32} core into a bootable ESP32-S3 flash
 * image, booted under Espressif's QEMU, and its UART transcript compared with what the same source prints on OpenJDK.
 *
 * <p>The harness (Dockerfile, {@code run.sh}, {@code merge_flash.sh} and the emulator {@code variant.cpp}) lives in
 * {@code src/test/xtensa}. Unlike the bare-metal Thumb-2 and AVR harnesses it runs the real core and its boot chain;
 * the emulator variant only drops the board's double-tap recovery, which drives an LED peripheral QEMU does not model.
 * The sketch wrapper brackets the program with {@code [juno-begin]} and {@code [juno-exit]}, so the boot log before
 * it is not part of the transcript, and the core's {@code \r\n} line ends are compared as {@code \n}. The pilot covers
 * the Nano ESP32 only: Cardputer-specific behavior is out of scope. Opt-in:
 * {@code ./mvnw -pl juno-compiler -am -Pxtensa verify}. Skipped when Docker is not available.
 */
@Tag("xtensa")
@Testcontainers(disabledWithoutDocker = true)
class XtensaRunIT {
    private static final Logger LOG = LoggerFactory.getLogger(XtensaRunIT.class);
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path XTENSA = BASEDIR.resolve("src/test/xtensa");
    private static final ConformanceWorkspace WORKSPACE = new ConformanceWorkspace(BASEDIR, "xtensa",
            CompilerTestSupport.JUNO_API_CLASSES);
    /** The one ESP32-S3 board/core path this pilot claims. */
    private static final Board BOARD = Board.NANO_ESP32;
    private static final String BEGIN_MARKER = "[juno-begin]\n";
    private static final String EXIT_MARKER = ConformanceWorkspace.EXIT_MARKER;
    private static final String PUBLISHED_IMAGE = System.getProperty("juno.xtensaImage", "").trim();

    @Container
    private static final GenericContainer<?> XTENSA_RUNNER = createRunner();

    private static GenericContainer<?> createRunner() {
        if (!PUBLISHED_IMAGE.isEmpty()) {
            return new GenericContainer<>(DockerImageName.parse(PUBLISHED_IMAGE));
        }
        // Keep the named toolchain image across Maven JVMs: the core and QEMU layers are large and stable, and a
        // harness change only replaces the small layers at the end of the Dockerfile.
        return new GenericContainer<>(new ImageFromDockerfile("juno-xtensa:bookworm-v1", false)
                .withFileFromPath(".", XTENSA));
    }

    @BeforeAll
    static void compilePrograms() throws IOException {
        WORKSPACE.compilePrograms(WORKSPACE.conformancePrograms().allSources());
        WORKSPACE.compileBaseline();
    }

    /** Primitive semantic-conformance fixtures from the XML catalog that declare the Nano ESP32. */
    static Stream<Arguments> conformancePrograms() {
        return WORKSPACE.catalog().programs().stream().flatMap(program -> {
            ProgramSource source = WORKSPACE.source(program);
            return source.declares(BOARD) ? Stream.of(Arguments.of(source.mainClass(), BOARD, program.id()))
                    : Stream.empty();
        });
    }

    @ParameterizedTest(name = "conformance {0} ({1}) [{2}]")
    @MethodSource("conformancePrograms")
    void primitiveConformanceMatchesTheJvmUnderQemu(String mainClass, Board board, String programId) throws Exception {
        String expected = WORKSPACE.baseline(mainClass);
        String actual = runUnderQemu(mainClass, board);
        WORKSPACE.assertTranscript(board, mainClass, expected, actual, "conformance %s (%s) on %s under QEMU (ESP32-S3)"
                .formatted(programId, mainClass, board.displayName()));
    }

    private static String runUnderQemu(String mainClass, Board board) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        Path directory = WORKSPACE.generated(board, mainClass);
        // arduino-cli requires the sketch directory and its .ino to share the program's name.
        Path sketch = directory.resolve(simpleName);
        Files.createDirectories(sketch);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(WORKSPACE.junoClasspath(), mainClass,
                sketch.resolve("program.S"), sketch.resolve("shim.cpp"), false, Optional.of(board.id()));
        Files.writeString(sketch.resolve(simpleName + ".ino"), harnessSketch(result.entryPointSymbol()));
        LOG.info("[{} / {}] Juno compile done in {} ms", mainClass, board.id(), millisSince(start));

        String work = "/work/" + board.id() + "/" + simpleName;
        XTENSA_RUNNER.execInContainer("mkdir", "-p", work);
        for (String file : List.of("program.S", "shim.cpp", simpleName + ".ino")) {
            XTENSA_RUNNER.copyFileToContainer(MountableFile.forHostPath(sketch.resolve(file)), work + "/" + file);
        }
        start = System.nanoTime();
        ExecResult run = runOnce(work);
        // QEMU occasionally dies on the host before the guest prints anything (the timeout wrapper reports a core
        // dump). That is an emulator crash, not a program fault, so the run is repeated once.
        if (!run.getStdout().contains(BEGIN_MARKER.strip()) && (run.getStderr() + run.getStdout()).contains("dumped core")) {
            LOG.warn("[{} / {}] QEMU died before the program started; retrying once", mainClass, board.id());
            run = runOnce(work);
        }
        LOG.info("[{} / {}] run.sh exit {} in {} ms", mainClass, board.id(), run.getExitCode(), millisSince(start));
        WORKSPACE.writeEmulatorLog(board, mainClass, "/harness/run.sh " + work, run.getStdout(), run.getStderr());
        assertThat(run.getExitCode()).as("build and run %s on %s:%n%s%s", mainClass, board.displayName(),
                run.getStdout(), run.getStderr()).isZero();
        return transcript(run, "/harness/run.sh " + work);
    }

    private static ExecResult runOnce(String work) throws IOException, InterruptedException {
        return XTENSA_RUNNER.execInContainer("/harness/run.sh", work);
    }

    /** The program's output between the markers, inclusive of the exit marker, with {@code \r\n} as {@code \n}. */
    private static String transcript(ExecResult run, String command) {
        String uart = run.getStdout().replace("\r\n", "\n");
        int begin = uart.indexOf(BEGIN_MARKER);
        if (begin < 0) {
            fail("%s: the program never started (no %s); emulator output:%n%s", command, BEGIN_MARKER.strip(), uart);
        }
        int end = uart.indexOf(EXIT_MARKER, begin);
        if (end < 0) {
            fail("%s: no %s before the timeout; emulator output:%n%s", command, EXIT_MARKER.strip(), uart);
        }
        return uart.substring(begin + BEGIN_MARKER.length(), end + EXIT_MARKER.length());
    }

    /**
     * The sketch around the generated entry point. {@code Serial} is UART0 on the emulator build, which needs
     * {@code begin} before the first byte; the markers frame the transcript.
     */
    private static String harnessSketch(String entryPointSymbol) {
        return """
                // Generated by XtensaRunIT. Do not edit.
                extern "C" void %1$s();

                void setup() {
                  Serial.begin(115200);
                  Serial.print("[juno-begin]\\n");
                  %1$s();
                  Serial.print("[juno-exit]\\n");
                  Serial.flush();
                }

                void loop() {
                }
                """.formatted(entryPointSymbol);
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
