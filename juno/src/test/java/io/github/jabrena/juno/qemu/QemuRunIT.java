package io.github.jabrena.juno.qemu;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
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
import org.testcontainers.utility.MountableFile;

/**
 * Runs Juno-generated Thumb-2 code instead of only compiling it: each program is compiled by Juno for a board,
 * assembled with the shim against a bare-metal harness, executed under QEMU (Cortex-M4, {@code mps2-an386}), and
 * its serial output compared with what the very same source prints on OpenJDK. This catches bugs that only exist
 * in the compiled code (calling convention, frame layout, garbage collection, exception unwinding), which neither
 * the IR tests nor an {@code arduino-cli} compile can see.
 *
 * <p>Programs live in {@code src/test/qemu/programs}; the OpenJDK side shadows {@code Serial} with
 * {@code src/test/qemu/openjdk}. The harness (stub {@code Arduino.h}, startup code, {@code run.sh}) is baked into
 * the image built from {@code src/test/qemu/Dockerfile}. It models no hardware, so it covers language and
 * runtime behavior only. Opt-in: {@code ./mvnw -f juno/pom.xml -Pqemu verify}. Skipped when Docker is not available.
 */
@Tag("qemu")
@Testcontainers(disabledWithoutDocker = true)
class QemuRunIT {
    private static final Logger LOG = LoggerFactory.getLogger(QemuRunIT.class);
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path QEMU = BASEDIR.resolve("src/test/qemu");
    private static final Path PROGRAM_CLASSES = BASEDIR.resolve("target/qemu/program-classes");
    private static final Path OPENJDK_CLASSES = BASEDIR.resolve("target/qemu/openjdk-classes");
    /** Programs whose output differs from OpenJDK because of a known compiler bug, with the reason. */
    private static final Map<String, String> KNOWN_GAPS = Map.of();
    private static final String EXIT_MARKER = "[juno-exit]\n";

    @Container
    private static final GenericContainer<?> QEMU_RUNNER = new GenericContainer<>(
            // Keep the named toolchain image across Maven JVMs. The heavyweight apt layer is stable and reusable;
            // a harness change only replaces the small COPY layer at the end of the Dockerfile.
            new ImageFromDockerfile("juno-qemu:bookworm-v1", false).withFileFromPath(".", QEMU));

    @BeforeAll
    static void compilePrograms() throws IOException {
        long start = System.nanoTime();
        List<Path> sources;
        try (Stream<Path> files = Files.list(QEMU.resolve("programs"))) {
            sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        compile(sources, PROGRAM_CLASSES, junoClasspath());
        List<Path> openJdkSources;
        try (Stream<Path> files = Files.walk(QEMU.resolve("openjdk"))) {
            openJdkSources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        compile(openJdkSources, OPENJDK_CLASSES, junoClasspath());
        LOG.info("javac: {} programs + {} OpenJDK-side sources in {} ms", sources.size(), openJdkSources.size(),
                millisSince(start));
    }

    /** Every program under src/test/qemu/programs, once per board. */
    static Stream<Arguments> programs() throws IOException {
        List<String> mainClasses = new ArrayList<>();
        try (Stream<Path> files = Files.list(QEMU.resolve("programs"))) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".java")).sorted()
                    .forEach(name -> mainClasses.add("demo." + name.substring(0, name.length() - ".java".length())));
        }
        return mainClasses.stream().flatMap(mainClass -> Stream.of(Board.values())
                .filter(board -> targets(mainClass, board))
                .map(board -> Arguments.of(mainClass, board)));
    }

    /**
     * Whether a program runs on {@code board}: every board unless it declares some in its {@code @Board}, so a
     * program can opt out of a board the harness cannot model. The UNO Q thread port runs on Zephyr's kernel, which the harness replaces with a small
     * cooperative stand-in ({@code src/test/qemu/zephyr/kernel.h}), so it covers the port's logic but not Zephyr's
     * real scheduler.
     */
    private static boolean targets(String mainClass, Board board) {
        Path source = QEMU.resolve("programs").resolve(mainClass.substring(mainClass.lastIndexOf('.') + 1) + ".java");
        try {
            String text = Files.readString(source);
            return !text.contains("@Board(") || text.contains(board.annotationArgument());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void matchesTheJvmUnderQemu(String mainClass, Board board) throws Exception {
        Assumptions.assumeFalse(KNOWN_GAPS.containsKey(mainClass), () -> "known gap: " + KNOWN_GAPS.get(mainClass));
        LOG.info("[{} / {}] start", mainClass, board.id());
        long start = System.nanoTime();
        String expected = runOnJvm(mainClass);
        LOG.info("[{} / {}] OpenJDK run done in {} ms ({} chars)", mainClass, board.id(), millisSince(start),
                expected.length());

        String actual = runUnderQemu(mainClass, board);
        LOG.info("[{} / {}] total {} ms", mainClass, board.id(), millisSince(start));

        assertThat(actual).as("%s on %s under QEMU", mainClass, board.displayName())
                .isEqualTo(expected + EXIT_MARKER);
    }

    private static String runUnderQemu(String mainClass, Board board) throws Exception {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        Path directory = BASEDIR.resolve("target/qemu/generated").resolve(board.id()).resolve(simpleName);
        Files.createDirectories(directory);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(junoClasspathWith(PROGRAM_CLASSES), mainClass,
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
        assertThat(run.getExitCode()).as("build and run %s on %s:%n%s%s", mainClass, board.displayName(),
                run.getStdout(), run.getStderr()).isZero();
        return run.getStdout();
    }

    /** The same program on OpenJDK, with {@code Serial} shadowed by a stdout-printing double. */
    private static String runOnJvm(String mainClass) throws Exception {
        List<String> classpath = new ArrayList<>();
        classpath.add(OPENJDK_CLASSES.toString());
        classpath.add(PROGRAM_CLASSES.toString());
        junoClasspathWith(PROGRAM_CLASSES).forEach(path -> classpath.add(path.toString()));
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-preview",
                "-cp", String.join(File.pathSeparator, classpath), mainClass)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("OpenJDK run of %s:%n%s", mainClass, output).isZero();
        return output;
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void compile(List<Path> sources, Path destination, List<Path> classpath)
            throws IOException {
        Files.createDirectories(destination);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new ArrayList<>(List.of(
                "--enable-preview", "--release", "27",
                "-d", destination.toString(), "-cp",
                String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList())));
        sources.forEach(source -> arguments.add(source.toString()));
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new)))
                .as("javac %s", sources).isZero();
    }

    private static List<Path> junoClasspathWith(Path programClasses) {
        List<Path> entries = new ArrayList<>(List.of(programClasses));
        entries.addAll(junoClasspath());
        return entries;
    }

    /** Juno's closed world: its own classes (the API the programs call) and nothing from the test classpath. */
    private static List<Path> junoClasspath() {
        return List.of(BASEDIR.resolve("target/classes"));
    }
}
