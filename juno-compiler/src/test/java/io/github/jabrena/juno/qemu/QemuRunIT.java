package io.github.jabrena.juno.qemu;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.conformance.ConformanceWorkspace;
import io.github.jabrena.juno.conformance.ProgramDirectory;
import io.github.jabrena.juno.conformance.ProgramSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs Juno-generated Thumb-2 code instead of only compiling it: each program is compiled by Juno for a board,
 * assembled with the shim against a bare-metal harness, executed under QEMU (Cortex-M4, {@code mps2-an386}), and
 * its serial output compared with what the very same source prints on OpenJDK. This catches bugs that only exist
 * in the compiled code (calling convention, frame layout, garbage collection, exception unwinding), which neither
 * the IR tests nor an {@code arduino-cli} compile can see. QEMU runs with {@code -icount}, so the preemption tick of a
 * threaded program strikes at the same instructions every run and a failure under preemption reproduces exactly.
 *
 * <p>Programs whose device APIs no JVM can run live in {@code src/test/qemu/devices}, each with a hand-written
 * {@code .expected} output.
 *
 * <p>Programs live in {@code src/test/qemu/programs}; the OpenJDK side shadows {@code Serial} with
 * {@code src/test/qemu/openjdk}. The harness (stub {@code Arduino.h}, startup code, {@code run.sh}) is baked into
 * the image built from {@code src/test/docker/qemu/Dockerfile}. It models no hardware, so it covers language and
 * runtime behavior only. Opt-in: {@code ./mvnw -pl juno-compiler -am -Pqemu verify}. Skipped when Docker is not available.
 */
@Tag("qemu")
@Testcontainers(disabledWithoutDocker = true)
class QemuRunIT {
    private static final Logger LOG = LoggerFactory.getLogger(QemuRunIT.class);
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path QEMU = BASEDIR.resolve("src/test/qemu");
    private static final Path HARNESS = BASEDIR.resolve("src/test/docker/qemu");
    private static final ConformanceWorkspace WORKSPACE = new ConformanceWorkspace(BASEDIR, "qemu",
            CompilerTestSupport.JUNO_API_CLASSES);
    private static final ProgramDirectory PROGRAMS = new ProgramDirectory(QEMU.resolve("programs"));
    /**
     * Programs whose device APIs no JVM can run (the UNO Q's bridge to Linux, against the harness's stand-in
     * {@code Arduino_RouterBridge.h}), each compared with the {@code .expected} file next to it.
     */
    private static final ProgramDirectory DEVICES = new ProgramDirectory(QEMU.resolve("devices"));
    private static final Path DEVICE_CLASSES = BASEDIR.resolve("target/qemu/device-classes");
    /** Programs whose output differs from OpenJDK because of a known compiler bug, with the reason. */
    private static final Map<String, String> KNOWN_GAPS = Map.of();
    private static final String PUBLISHED_IMAGE = System.getProperty("juno.qemuImage", "").trim();

    @Container
    private static final GenericContainer<?> QEMU_RUNNER = createRunner();

    private static GenericContainer<?> createRunner() {
        if (!PUBLISHED_IMAGE.isEmpty()) {
            return new GenericContainer<>(DockerImageName.parse(PUBLISHED_IMAGE));
        }
        // Keep the named toolchain image across Maven JVMs. The heavyweight apt layer is stable and reusable;
        // a harness change only replaces the small COPY layer at the end of the Dockerfile.
        return new GenericContainer<>(new ImageFromDockerfile("juno-qemu:bookworm-v1", false)
                .withFileFromPath(".", HARNESS));
    }

    @BeforeAll
    static void compilePrograms() throws IOException {
        long start = System.nanoTime();
        List<Path> sources = new ArrayList<>(PROGRAMS.sources());
        sources.addAll(WORKSPACE.conformancePrograms().allSources());
        WORKSPACE.compilePrograms(sources);
        WORKSPACE.compileBaseline();
        WORKSPACE.compile(DEVICES.sources(), DEVICE_CLASSES);
        LOG.info("javac: {} programs and the OpenJDK-side sources in {} ms", sources.size(), millisSince(start));
    }

    /**
     * Every program under src/test/qemu/programs, once per Thumb-2 board it targets (SimavrRunIT runs the AVR ones).
     * The UNO Q thread port runs on Zephyr's kernel, which the harness replaces with a small stand-in
     * ({@code src/test/docker/qemu/zephyr/kernel.h}: baton semaphores, and a {@code k_timer} on SysTick for the preemption
     * tick, with threads on the process stack), so it covers the port's logic but not Zephyr's real scheduler.
     */
    static Stream<Arguments> programs() {
        return PROGRAMS.programs().stream().flatMap(program -> thumb2Boards()
                .filter(program::targets)
                .map(board -> Arguments.of(program.mainClass(), board)));
    }

    /** Primitive semantic-conformance fixtures from the XML catalog, once per supported Thumb-2 board. */
    static Stream<Arguments> conformancePrograms() {
        return WORKSPACE.catalog().programs().stream().flatMap(program -> {
            ProgramSource source = WORKSPACE.source(program);
            return thumb2Boards().filter(source::targets)
                    .map(board -> Arguments.of(source.mainClass(), board, program.id()));
        });
    }

    /** Every program under src/test/qemu/devices, once per Thumb-2 board its {@code @Board} names. */
    static Stream<Arguments> devices() {
        return DEVICES.programs().stream().flatMap(program -> thumb2Boards()
                .filter(program::declares)
                .map(board -> Arguments.of(program.mainClass(), board)));
    }

    private static Stream<Board> thumb2Boards() {
        return Stream.of(Board.values()).filter(board -> board.core().architecture() == Architecture.THUMB2);
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void matchesTheJvmUnderQemu(String mainClass, Board board) throws Exception {
        Assumptions.assumeFalse(KNOWN_GAPS.containsKey(mainClass), () -> "known gap: " + KNOWN_GAPS.get(mainClass));
        LOG.info("[{} / {}] start", mainClass, board.id());
        long start = System.nanoTime();
        String expected = WORKSPACE.baseline(mainClass);
        LOG.info("[{} / {}] OpenJDK run done in {} ms ({} chars)", mainClass, board.id(), millisSince(start),
                expected.length());

        String actual = runUnderQemu(mainClass, board, WORKSPACE.junoClasspath());
        LOG.info("[{} / {}] total {} ms", mainClass, board.id(), millisSince(start));

        WORKSPACE.assertTranscript(board, mainClass, expected, actual,
                "%s on %s under QEMU".formatted(mainClass, board.displayName()));
    }

    @ParameterizedTest(name = "conformance {0} ({1}) [{2}]")
    @MethodSource("conformancePrograms")
    void primitiveConformanceMatchesTheJvmUnderQemu(String mainClass, Board board, String programId) throws Exception {
        String expected = WORKSPACE.baseline(mainClass);
        String actual = runUnderQemu(mainClass, board, WORKSPACE.junoClasspath());
        WORKSPACE.assertTranscript(board, mainClass, expected, actual,
                "conformance %s (%s) on %s under QEMU".formatted(programId, mainClass, board.displayName()));
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("devices")
    void matchesItsExpectedOutputUnderQemu(String mainClass, Board board) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        String expected = Files.readString(DEVICES.directory().resolve(simpleName + ".expected"));

        String actual = runUnderQemu(mainClass, board, WORKSPACE.junoClasspath(DEVICE_CLASSES));

        WORKSPACE.assertTranscript(board, mainClass, expected, actual,
                "%s on %s under QEMU".formatted(mainClass, board.displayName()));
    }

    private static String runUnderQemu(String mainClass, Board board, List<Path> classpath) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        Path directory = WORKSPACE.generated(board, mainClass);
        Files.createDirectories(directory);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(classpath, mainClass,
                directory.resolve("program.S"), directory.resolve("shim.cpp"), false, Optional.of(board.id()));
        LOG.info("[{} / {}] Juno compile done in {} ms", mainClass, board.id(), millisSince(start));
        start = System.nanoTime();
        String work = "/work/" + board.id() + "/" + simpleName;
        QEMU_RUNNER.execInContainer("mkdir", "-p", work);
        QEMU_RUNNER.copyFileToContainer(MountableFile.forHostPath(directory.resolve("program.S")),
                work + "/program.S");
        QEMU_RUNNER.copyFileToContainer(MountableFile.forHostPath(directory.resolve("shim.cpp")),
                work + "/shim.cpp");
        LOG.info("[{} / {}] copied to container in {} ms; running run.sh (gcc build + QEMU)", mainClass,
                board.id(), millisSince(start));
        start = System.nanoTime();
        var run = QEMU_RUNNER.execInContainer("/harness/run.sh", work, result.entryPointSymbol());
        LOG.info("[{} / {}] run.sh exit {} in {} ms", mainClass, board.id(), run.getExitCode(), millisSince(start));
        WORKSPACE.writeEmulatorLog(board, mainClass, "/harness/run.sh " + work + " " + result.entryPointSymbol(),
                run.getStdout(), run.getStderr());
        assertThat(run.getExitCode()).as("build and run %s on %s:%n%s%s", mainClass, board.displayName(),
                run.getStdout(), run.getStderr()).isZero();
        return run.getStdout();
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
