package io.github.jabrena.juno.simavr;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.conformance.ConformanceWorkspace;
import io.github.jabrena.juno.conformance.ProgramDirectory;
import io.github.jabrena.juno.conformance.ProgramSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AVR counterpart of {@code QemuRunIT}: runs Juno-generated AVR code for every AVR board instead of only
 * compiling it. Each program is compiled by Juno, built with avr-gcc against the bare-metal harness in
 * {@code src/test/docker/simavr} (a stand-in megaavr core), executed under simavr (an ATmega2560: the ATmega4809's
 * instruction set, more RAM), and its serial output compared with what the same source prints on OpenJDK. This is
 * what shows an 8-bit target keeps Java's semantics: a 32-bit {@code int} on 8-bit registers, the frame layout and
 * calling convention, the shim thunks, the software {@code double}, the collector scanning 16-bit references.
 *
 * <p>The programs are those under {@code src/test/qemu/programs} whose {@code @Board} names an AVR board, compared
 * with OpenJDK, plus {@code src/test/simavr/programs}, whose peripheral APIs (HTTP, UDP, SD, LCD, TFT) no JVM can run,
 * compared with the {@code .expected} file next to each. Opt-in: {@code ./mvnw -pl juno-compiler -am -Psimavr verify}.
 * It builds on a local avr-gcc and libsimavr toolchain when one is installed (Debian/Ubuntu: gcc-avr avr-libc
 * libsimavr-dev libelf-dev), otherwise in Docker, and is skipped when neither is available.
 */
@Tag("simavr")
class SimavrRunIT {
    private static final Logger LOG = LoggerFactory.getLogger(SimavrRunIT.class);
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path SIMAVR = BASEDIR.resolve("src/test/simavr");
    private static final Path HARNESS = BASEDIR.resolve("src/test/docker/simavr");
    private static final ConformanceWorkspace WORKSPACE = new ConformanceWorkspace(BASEDIR, "simavr",
            CompilerTestSupport.JUNO_API_CLASSES);
    private static final ProgramDirectory QEMU_PROGRAMS =
            new ProgramDirectory(BASEDIR.resolve("src/test/qemu/programs"));
    private static final ProgramDirectory PERIPHERAL_PROGRAMS = new ProgramDirectory(SIMAVR.resolve("programs"));
    private static final Path LOCAL_HARNESS = BASEDIR.resolve("target/simavr/harness");
    private static final String PUBLISHED_IMAGE = System.getProperty("juno.simavrImage", "").trim();
    private static final List<String> HARNESS_FILES = List.of("Arduino.h", "WiFiNINA.h", "WiFiUdp.h", "SPI.h",
            "SdFat.h", "startup.cpp", "run.sh");

    private static GenericContainer<?> container;

    @BeforeAll
    static void prepare() throws Exception {
        if (!PUBLISHED_IMAGE.isEmpty()) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "The published simavr image requires Docker");
            container = new GenericContainer<>(DockerImageName.parse(PUBLISHED_IMAGE));
            container.start();
        } else if (!buildLocalHarness()) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Neither a local avr-gcc and libsimavr toolchain nor Docker is available");
            container = new GenericContainer<>(new ImageFromDockerfile("juno-simavr:bookworm-v1", false)
                    .withFileFromPath(".", HARNESS));
            container.start();
        }
        List<Path> sources = new ArrayList<>(QEMU_PROGRAMS.sources());
        sources.addAll(WORKSPACE.conformancePrograms().allSources());
        sources.addAll(PERIPHERAL_PROGRAMS.sources());
        WORKSPACE.compilePrograms(sources);
        WORKSPACE.compileBaseline();
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    /** Compiles the harness's runner into target/ when avr-gcc, avr-g++, gcc and the simavr headers are installed. */
    private static boolean buildLocalHarness() throws Exception {
        if (!onPath("avr-gcc") || !onPath("avr-g++") || !onPath("gcc")
                || !Files.exists(Path.of("/usr/include/simavr/sim_avr.h"))) {
            return false;
        }
        Files.createDirectories(LOCAL_HARNESS);
        for (String file : HARNESS_FILES) {
            Files.copy(HARNESS.resolve(file), LOCAL_HARNESS.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        Process build = new ProcessBuilder("gcc", "-O2", HARNESS.resolve("runner.c").toString(), "-o",
                LOCAL_HARNESS.resolve("simavr-runner").toString(), "-I/usr/include/simavr", "-lsimavr")
                .redirectErrorStream(true).start();
        String output = new String(build.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!build.waitFor(2, TimeUnit.MINUTES) || build.exitValue() != 0) {
            LOG.warn("Cannot build the local simavr runner, falling back to Docker:\n{}", output);
            return false;
        }
        return true;
    }

    private static boolean onPath(String executable) {
        return Stream.of(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
                .anyMatch(directory -> Files.isExecutable(Path.of(directory, executable)));
    }

    /** Every AVR-capable conformance program, and every AVR peripheral program, once per AVR board. */
    static Stream<Arguments> programs() {
        return avrBoards().flatMap(board -> Stream.concat(
                QEMU_PROGRAMS.programs().stream().filter(program -> program.declares(board))
                        .map(program -> Arguments.of(program.simpleName(), board, false)),
                PERIPHERAL_PROGRAMS.programs().stream().filter(program -> program.declares(board))
                        .map(program -> Arguments.of(program.simpleName(), board, true))));
    }

    /** Primitive semantic-conformance fixtures from the XML catalog, once per supported AVR board. */
    static Stream<Arguments> conformancePrograms() {
        return WORKSPACE.catalog().programs().stream().flatMap(program -> {
            ProgramSource source = WORKSPACE.source(program);
            return avrBoards().filter(source::targets)
                    .map(board -> Arguments.of(source.simpleName(), board, false, program.id()));
        });
    }

    private static Stream<Board> avrBoards() {
        return Stream.of(Board.values()).filter(board -> board.core().architecture() == Architecture.AVR);
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void matchesTheJvmUnderSimavr(String simpleName, Board board, boolean peripheral) throws Exception {
        String expected = peripheral
                ? Files.readString(PERIPHERAL_PROGRAMS.directory().resolve(simpleName + ".expected"))
                : WORKSPACE.baseline("demo." + simpleName);
        String actual = runUnderSimavr("demo." + simpleName, simpleName, board);
        WORKSPACE.assertTranscript(board, simpleName, expected, actual,
                "%s on %s under simavr".formatted(simpleName, board.displayName()));
    }

    @ParameterizedTest(name = "conformance {0} ({1}) [{3}]")
    @MethodSource("conformancePrograms")
    void primitiveConformanceMatchesTheJvmUnderSimavr(String simpleName, Board board, boolean peripheral,
            String programId) throws Exception {
        LOG.info("[conformance {} / {} / {}] start", programId, simpleName, board.id());
        String expected = WORKSPACE.baseline("demo." + simpleName);
        String actual = runUnderSimavr("demo." + simpleName, simpleName, board);
        WORKSPACE.assertTranscript(board, simpleName, expected, actual,
                "conformance %s (%s) on %s under simavr".formatted(programId, simpleName, board.displayName()));
    }

    private static String runUnderSimavr(String mainClass, String simpleName, Board board) throws Exception {
        Path directory = WORKSPACE.generated(board, simpleName);
        Files.createDirectories(directory);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(WORKSPACE.junoClasspath(), mainClass, directory.resolve("program.S"),
                directory.resolve("shim.cpp"), false, Optional.of(board.id()));
        LOG.info("[{} / {}] Juno compile done in {} ms", simpleName, board.id(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        if (container == null) {
            Process run = new ProcessBuilder("sh", LOCAL_HARNESS.resolve("run.sh").toString(), directory.toString(),
                    result.entryPointSymbol()).redirectErrorStream(false).start();
            String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errors = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            WORKSPACE.writeEmulatorLog(board, simpleName,
                    "sh " + LOCAL_HARNESS.resolve("run.sh") + " " + directory + " " + result.entryPointSymbol(), output,
                    errors);
            assertThat(run.waitFor(2, TimeUnit.MINUTES)).isTrue();
            assertThat(run.exitValue()).as("build and run %s:%n%s%s", simpleName, output, errors).isZero();
            return output;
        }
        String work = "/work/" + board.id() + "/" + simpleName;
        container.execInContainer("mkdir", "-p", work);
        container.copyFileToContainer(MountableFile.forHostPath(directory.resolve("program.S")), work + "/program.S");
        container.copyFileToContainer(MountableFile.forHostPath(directory.resolve("shim.cpp")), work + "/shim.cpp");
        var run = container.execInContainer("/harness/run.sh", work, result.entryPointSymbol());
        WORKSPACE.writeEmulatorLog(board, simpleName, "/harness/run.sh " + work + " " + result.entryPointSymbol(),
                run.getStdout(), run.getStderr());
        assertThat(run.getExitCode()).as("build and run %s:%n%s%s", simpleName, run.getStdout(), run.getStderr())
                .isZero();
        return run.getStdout();
    }
}
