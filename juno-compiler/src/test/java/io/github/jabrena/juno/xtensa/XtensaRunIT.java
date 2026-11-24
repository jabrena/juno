package io.github.jabrena.juno.xtensa;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.conformance.ConformanceWorkspace;
import io.github.jabrena.juno.conformance.ProgramDirectory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The Xtensa counterpart of {@code QemuRunIT} for the primitive semantic-conformance slice: each catalogued program is
 * compiled by Juno for one ESP32 board (Nano ESP32, M5Stack Cardputer or AI Thinker ESP32-CAM), built by its pinned Arduino
 * core into a bootable flash image, booted under Espressif's QEMU, and its UART transcript compared with what the same source prints on OpenJDK.
 *
 * <p>The harness (Dockerfile, {@code run.sh}, {@code merge_flash.sh} and the emulator {@code variant.cpp}) lives in
 * {@code src/test/docker/xtensa}. Unlike the bare-metal Thumb-2 and AVR harnesses it runs the real core and its boot chain;
 * the emulator variant only drops the board's double-tap recovery, which drives an LED peripheral QEMU does not model.
 * The sketch wrapper brackets the program with {@code [juno-begin]} and {@code [juno-exit]}, so the boot log before
 * it is not part of the transcript, and the core's {@code \r\n} line ends are compared as {@code \n}. The pilot covers
 * the board's core behavior only: display, keyboard, camera and other board-specific peripherals stay out of scope. Opt-in:
 * {@code ./mvnw -pl juno-compiler -am -Pxtensa verify -Djuno.Xmx=24k} (the arena size CI uses). Skipped when Docker is not available.
 */
@Tag("xtensa")
@Testcontainers(disabledWithoutDocker = true)
class XtensaRunIT {
    private static final Logger LOG = LoggerFactory.getLogger(XtensaRunIT.class);
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path XTENSA = BASEDIR.resolve("src/test/docker/xtensa");
    private static final ConformanceWorkspace WORKSPACE = new ConformanceWorkspace(BASEDIR, "xtensa",
            CompilerTestSupport.JUNO_API_CLASSES);
    /**
     * The ESP32 board this run claims: the Nano ESP32 (ESP32-S3), the M5Stack Cardputer (ESP32-S3) or the AI Thinker
     * ESP32-CAM (ESP32, LX6). CI runs one board per job with {@code -Djuno.xtensaBoard=<id>}.
     */
    private static final Board BOARD = Board.fromId(System.getProperty("juno.xtensaBoard", "arduino-nano-esp32"));
    /** The runtime programs QemuRunIT runs on Thumb-2; the ones that declare the Nano ESP32 run here too. */
    private static final ProgramDirectory QEMU_PROGRAMS =
            new ProgramDirectory(BASEDIR.resolve("src/test/qemu/programs"));
    /**
     * Programs that fail intermittently under QEMU and get a second attempt before the test fails.
     *
     * <p>TODO: revisit soon and remove this retry. {@code ManyVirtualThreads} on the two-carrier ESP32-S3 (seen on the
     * M5Stack Cardputer) sometimes hangs after {@code [juno-begin]} (CI run 45374749330) and once crashed with
     * {@code LoadProhibited} at address 0x4 on core 1 (CI run 45270180550). Both point at a race in the cross-core
     * virtual-thread scheduler, not at the test; the retry only keeps unrelated changes from failing on it.
     */
    private static final Set<String> KNOWN_FLAKY = Set.of("demo.ManyVirtualThreads");
    private static final int FLAKY_ATTEMPTS = 2;
    private static final String BEGIN_MARKER = "[juno-begin]\n";
    private static final String EXIT_MARKER = ConformanceWorkspace.EXIT_MARKER;
    private static final String PUBLISHED_IMAGE = System.getProperty("juno.xtensaImage", "").trim();
    /**
     * The runtime configuration, read exactly as the Maven plugin reads it. CI enlarges the arena and runs each board
     * with {@code -Djuno.carriers=1} and {@code 2} in parallel jobs; the {@code DualCarrier*} programs only exercise
     * the cross-core runtime on the two-carrier run.
     */
    private static final RuntimeConfig RUNTIME_CONFIG = RuntimeConfig.of(
            Optional.ofNullable(System.getProperty("juno.Xmx")), Optional.ofNullable(System.getProperty("juno.Xss")),
            Optional.ofNullable(System.getProperty("juno.carriers")));
    /**
     * Bounds one build-and-run, so a stalled arduino-cli or QEMU fails its own program's test with the output so far,
     * instead of silently holding the whole CI step. A CI run takes about 70 s per program.
     */
    private static final int RUN_TIMEOUT_SECONDS = Integer.getInteger("juno.xtensaRunTimeoutSeconds", 600);
    /** {@code timeout}'s exit status when the limit expired, and when the command then needed SIGKILL. */
    private static final int TIMED_OUT = 124;
    private static final int KILLED = 137;

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
        List<Path> sources = new ArrayList<>(QEMU_PROGRAMS.sources());
        sources.addAll(WORKSPACE.conformancePrograms().allSources());
        WORKSPACE.compilePrograms(sources);
        WORKSPACE.compileBaseline();
    }

    /** Runtime programs from src/test/qemu/programs that declare the board (threads, continuations, two carriers). */
    static Stream<Arguments> programs() {
        return QEMU_PROGRAMS.programs().stream().filter(program -> program.declares(BOARD))
                .map(program -> Arguments.of(program.mainClass(), BOARD));
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void matchesTheJvmUnderQemu(String mainClass, Board board) throws Exception {
        String expected = WORKSPACE.baseline(mainClass);
        int attempts = KNOWN_FLAKY.contains(mainClass) ? FLAKY_ATTEMPTS : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                String actual = runUnderQemu(mainClass, board);
                WORKSPACE.assertTranscript(board, mainClass, expected, actual,
                        "%s on %s under QEMU".formatted(mainClass, board.displayName()));
                return;
            } catch (AssertionError failure) {
                if (attempt >= attempts) {
                    throw failure;
                }
                LOG.warn("[{} / {}] known-flaky program failed attempt {} of {}, retrying: {}", mainClass, board.id(),
                        attempt, attempts, failure.getMessage().lines().findFirst().orElse(""));
            }
        }
    }

    /**
     * Primitive semantic-conformance fixtures from the XML catalog that declare the board. They test Java semantics in
     * the shared Xtensa backend, so they declare the Nano ESP32 only, and the other boards run none of them: those runs
     * keep to the runtime programs above, which exercise each board's own core.
     */
    static Stream<Arguments> conformancePrograms() {
        return WORKSPACE.catalog().programs().stream().flatMap(program -> {
            ProgramSource source = WORKSPACE.source(program);
            return source.declares(BOARD) ? Stream.of(Arguments.of(source.mainClass(), BOARD, program.id()))
                    : Stream.empty();
        });
    }

    @ParameterizedTest(name = "conformance {0} ({1}) [{2}]", allowZeroInvocations = true)
    @MethodSource("conformancePrograms")
    void primitiveConformanceMatchesTheJvmUnderQemu(String mainClass, Board board, String programId) throws Exception {
        String expected = WORKSPACE.baseline(mainClass);
        String actual = runUnderQemu(mainClass, board);
        WORKSPACE.assertTranscript(board, mainClass, expected, actual, "conformance %s (%s) on %s under QEMU"
                .formatted(programId, mainClass, board.displayName()));
    }

    private static String runUnderQemu(String mainClass, Board board) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        Path directory = WORKSPACE.generated(board, mainClass);
        // arduino-cli requires the sketch directory and its .ino to share the program's name.
        Path sketch = directory.resolve(simpleName);
        Files.createDirectories(sketch);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(new CompilationRequest(WORKSPACE.junoClasspath(),
                mainClass, false, Optional.of(board.id()), RUNTIME_CONFIG), sketch.resolve("program.S"),
                sketch.resolve("shim.cpp"));
        Files.writeString(sketch.resolve(simpleName + ".ino"), harnessSketch(result.entryPointSymbol()));
        LOG.info("[{} / {}] Juno compile done in {} ms", mainClass, board.id(), millisSince(start));

        String work = "/work/" + board.id() + "/" + simpleName;
        XTENSA_RUNNER.execInContainer("mkdir", "-p", work);
        for (String file : List.of("program.S", "shim.cpp", simpleName + ".ino")) {
            XTENSA_RUNNER.copyFileToContainer(MountableFile.forHostPath(sketch.resolve(file)), work + "/" + file);
        }
        LOG.info("[{} / {}] running run.sh (arduino-cli build + QEMU, limit {} s)", mainClass, board.id(),
                RUN_TIMEOUT_SECONDS);
        start = System.nanoTime();
        ExecResult run = runOnce(work);
        LOG.info("[{} / {}] run.sh exit {} in {} ms", mainClass, board.id(), run.getExitCode(), millisSince(start));
        // run.sh reports where its time went (build, boot to [juno-begin], program) and decodes a panic's backtrace
        // (addr2line's "0x...: function at file:line" and "(inlined by)" lines) on stderr.
        run.getStderr().lines().filter(line -> line.startsWith("run.sh timing:")
                        || line.startsWith("run.sh: decoded backtrace") || line.startsWith("0x")
                        || line.contains("(inlined by)"))
                .forEach(line -> LOG.info("[{} / {}] {}", mainClass, board.id(), line));
        WORKSPACE.writeEmulatorLog(board, mainClass, "/harness/run.sh " + work, run.getStdout(), run.getStderr());
        if (run.getExitCode() == TIMED_OUT || run.getExitCode() == KILLED) {
            fail("%s on %s: /harness/run.sh did not finish within %d s (raise -Djuno.xtensaRunTimeoutSeconds);"
                    + " output so far:%n%s%s", mainClass, board.displayName(), RUN_TIMEOUT_SECONDS, run.getStdout(),
                    run.getStderr());
        }
        assertThat(run.getExitCode()).as("build and run %s on %s:%n%s%s", mainClass, board.displayName(),
                run.getStdout(), run.getStderr()).isZero();
        return transcript(run, "/harness/run.sh " + work);
    }

    private static ExecResult runOnce(String work) throws IOException, InterruptedException {
        // GNU timeout signals run.sh's whole process group, so a stalled arduino-cli or QEMU child stops too.
        return XTENSA_RUNNER.execInContainer("timeout", "-k", "10", String.valueOf(RUN_TIMEOUT_SECONDS),
                "/harness/run.sh", work, BOARD.id());
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
