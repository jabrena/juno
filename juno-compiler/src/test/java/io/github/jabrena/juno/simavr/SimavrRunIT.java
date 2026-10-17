package io.github.jabrena.juno.simavr;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
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
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AVR counterpart of {@code QemuRunIT}: runs Juno-generated AVR code for every AVR board instead of only
 * compiling it. Each program is compiled by Juno, built with avr-gcc against the bare-metal harness in
 * {@code src/test/simavr} (a stand-in megaavr core), executed under simavr (an ATmega2560: the ATmega4809's
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
    private static final Path QEMU_PROGRAMS = BASEDIR.resolve("src/test/qemu/programs");
    private static final Path PROGRAM_CLASSES = BASEDIR.resolve("target/simavr/program-classes");
    private static final Path OPENJDK_CLASSES = BASEDIR.resolve("target/simavr/openjdk-classes");
    private static final Path LOCAL_HARNESS = BASEDIR.resolve("target/simavr/harness");
    private static final String EXIT_MARKER = "[juno-exit]\n";
    private static final List<String> HARNESS_FILES = List.of("Arduino.h", "WiFiNINA.h", "WiFiUdp.h", "SPI.h",
            "SdFat.h", "startup.cpp", "run.sh");

    private static GenericContainer<?> container;

    @BeforeAll
    static void prepare() throws Exception {
        if (!buildLocalHarness()) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Neither a local avr-gcc and libsimavr toolchain nor Docker is available");
            container = new GenericContainer<>(new ImageFromDockerfile("juno-simavr:bookworm-v1", false)
                    .withFileFromPath(".", SIMAVR));
            container.start();
        }
        List<Path> sources = new ArrayList<>(javaFiles(QEMU_PROGRAMS));
        sources.addAll(javaFiles(SIMAVR.resolve("programs")));
        compile(sources, PROGRAM_CLASSES);
        List<Path> openJdkSources;
        try (Stream<Path> files = Files.walk(BASEDIR.resolve("src/test/qemu/openjdk"))) {
            openJdkSources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        compile(openJdkSources, OPENJDK_CLASSES);
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
            Files.copy(SIMAVR.resolve(file), LOCAL_HARNESS.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        Process build = new ProcessBuilder("gcc", "-O2", SIMAVR.resolve("runner.c").toString(), "-o",
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
    static Stream<Arguments> programs() throws IOException {
        List<Arguments> programs = new ArrayList<>();
        for (Board board : Board.values()) {
            if (board.core().architecture() != Architecture.AVR) {
                continue;
            }
            for (Path source : javaFiles(QEMU_PROGRAMS)) {
                if (Files.readString(source).contains(board.annotationArgument())) {
                    programs.add(Arguments.of(simpleName(source), board, false));
                }
            }
            for (Path source : javaFiles(SIMAVR.resolve("programs"))) {
                if (Files.readString(source).contains(board.annotationArgument())) {
                    programs.add(Arguments.of(simpleName(source), board, true));
                }
            }
        }
        return programs.stream();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void matchesTheJvmUnderSimavr(String simpleName, Board board, boolean peripheral) throws Exception {
        String expected = peripheral
                ? Files.readString(SIMAVR.resolve("programs").resolve(simpleName + ".expected"))
                : runOnJvm("demo." + simpleName) + EXIT_MARKER;
        String actual = runUnderSimavr("demo." + simpleName, simpleName, board);
        assertThat(actual).as("%s on %s under simavr", simpleName, board.displayName()).isEqualTo(expected);
    }

    private static String runUnderSimavr(String mainClass, String simpleName, Board board) throws Exception {
        Path directory = BASEDIR.resolve("target/simavr/generated").resolve(board.id()).resolve(simpleName);
        Files.createDirectories(directory);
        long start = System.nanoTime();
        List<Path> classpath = List.of(PROGRAM_CLASSES, CompilerTestSupport.JUNO_API_CLASSES);
        CompilationResult result = new JunoCompiler().compileTo(classpath, mainClass, directory.resolve("program.S"),
                directory.resolve("shim.cpp"), false, Optional.of(board.id()));
        LOG.info("[{} / {}] Juno compile done in {} ms", simpleName, board.id(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        if (container == null) {
            Process run = new ProcessBuilder("sh", LOCAL_HARNESS.resolve("run.sh").toString(), directory.toString(),
                    result.entryPointSymbol()).redirectErrorStream(false).start();
            String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errors = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(run.waitFor(2, TimeUnit.MINUTES)).isTrue();
            assertThat(run.exitValue()).as("build and run %s:%n%s%s", simpleName, output, errors).isZero();
            return output;
        }
        String work = "/work/" + board.id() + "/" + simpleName;
        container.execInContainer("mkdir", "-p", work);
        container.copyFileToContainer(MountableFile.forHostPath(directory.resolve("program.S")), work + "/program.S");
        container.copyFileToContainer(MountableFile.forHostPath(directory.resolve("shim.cpp")), work + "/shim.cpp");
        var run = container.execInContainer("/harness/run.sh", work, result.entryPointSymbol());
        assertThat(run.getExitCode()).as("build and run %s:%n%s%s", simpleName, run.getStdout(), run.getStderr())
                .isZero();
        return run.getStdout();
    }

    /** The same program on OpenJDK, with {@code Serial} shadowed by a stdout-printing double. */
    private static String runOnJvm(String mainClass) throws Exception {
        String classpath = String.join(File.pathSeparator, OPENJDK_CLASSES.toString(), PROGRAM_CLASSES.toString(),
                CompilerTestSupport.JUNO_API_CLASSES.toString());
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-preview", "-cp", classpath, mainClass).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("OpenJDK run of %s:%n%s", mainClass, output).isZero();
        return output;
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String simpleName(Path source) {
        String name = source.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private static void compile(List<Path> sources, Path destination) throws IOException {
        Files.createDirectories(destination);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new ArrayList<>(List.of("--enable-preview", "--release", "27",
                "-d", destination.toString(), "-cp", CompilerTestSupport.JUNO_API_CLASSES.toString()));
        sources.forEach(source -> arguments.add(source.toString()));
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).as("javac %s", sources).isZero();
    }
}
