package io.github.jabrena.juno.xtensa;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.conformance.ConformanceCatalog;
import io.github.jabrena.juno.conformance.ConformanceDigitalTwin;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The Xtensa counterpart of {@code QemuRunIT} for the primitive semantic-conformance slice: each catalogued program is
 * compiled by Juno for the Nano ESP32, built by the pinned {@code arduino:esp32} core into a bootable ESP32-S3 flash
 * image, booted under Espressif's QEMU, and its UART transcript compared with what the same source prints on OpenJDK.
 *
 * <p>The harness (Dockerfile, {@code run.sh}, {@code merge_flash.py} and the emulator {@code variant.cpp}) lives in
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
    private static final Path CONFORMANCE = BASEDIR.resolve("src/test/resources/conformance");
    private static final Path GENERATED = BASEDIR.resolve("target/xtensa/generated");
    private static final Path PROGRAM_CLASSES = BASEDIR.resolve("target/xtensa/program-classes");
    private static final Path OPENJDK_CLASSES = BASEDIR.resolve("target/xtensa/openjdk-classes");
    private static final ConformanceDigitalTwin DIGITAL_TWIN = new ConformanceDigitalTwin(OPENJDK_CLASSES,
            PROGRAM_CLASSES, CompilerTestSupport.JUNO_API_CLASSES);
    /** The one ESP32-S3 board/core path this pilot claims. */
    private static final Board BOARD = Board.NANO_ESP32;
    private static final String BEGIN_MARKER = "[juno-begin]\n";
    private static final String EXIT_MARKER = "[juno-exit]\n";
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
        compile(javaFiles(CONFORMANCE.resolve("programs")), PROGRAM_CLASSES);
        compile(javaFiles(BASEDIR.resolve("src/test/qemu/openjdk")), OPENJDK_CLASSES);
    }

    /** Primitive semantic-conformance fixtures from the XML catalog that declare the Nano ESP32. */
    static Stream<Arguments> conformancePrograms() {
        ConformanceCatalog catalog = ConformanceCatalog.load(CONFORMANCE.resolve("catalog.xml"));
        catalog.validate(CONFORMANCE);
        return catalog.programs().stream()
                .filter(program -> declares(CONFORMANCE.resolve(program.source()), BOARD))
                .map(program -> Arguments.of("demo." + simpleName(program.source()), BOARD, program.id()));
    }

    @ParameterizedTest(name = "conformance {0} ({1}) [{2}]")
    @MethodSource("conformancePrograms")
    void primitiveConformanceMatchesTheJvmUnderQemu(String mainClass, Board board, String programId) throws Exception {
        String expected = DIGITAL_TWIN.run(mainClass) + EXIT_MARKER;
        String actual = runUnderQemu(mainClass, board);
        if (!actual.equals(expected)) {
            Path directory = GENERATED.resolve(board.id()).resolve(simpleOf(mainClass));
            Files.writeString(directory.resolve("expected.txt"), expected);
            Files.writeString(directory.resolve("actual.txt"), actual);
        }
        assertThat(actual).as("conformance %s (%s) on %s under QEMU (ESP32-S3)", programId, mainClass,
                board.displayName()).isEqualTo(expected);
    }

    private static String runUnderQemu(String mainClass, Board board) throws Exception {
        String simpleName = simpleOf(mainClass);
        Path directory = GENERATED.resolve(board.id()).resolve(simpleName);
        // arduino-cli requires the sketch directory and its .ino to share the program's name.
        Path sketch = directory.resolve(simpleName);
        Files.createDirectories(sketch);
        long start = System.nanoTime();
        CompilationResult result = new JunoCompiler().compileTo(
                List.of(PROGRAM_CLASSES, CompilerTestSupport.JUNO_API_CLASSES), mainClass,
                sketch.resolve("program.S"), sketch.resolve("shim.cpp"), false, Optional.of(board.id()));
        Files.writeString(sketch.resolve(simpleName + ".ino"), harnessSketch(result.entryPointSymbol()));
        LOG.info("[{} / {}] Juno compile done in {} ms", mainClass, board.id(), millisSince(start));

        String work = "/work/" + board.id() + "/" + simpleName;
        XTENSA_RUNNER.execInContainer("mkdir", "-p", work);
        for (String file : List.of("program.S", "shim.cpp", simpleName + ".ino")) {
            XTENSA_RUNNER.copyFileToContainer(MountableFile.forHostPath(sketch.resolve(file)), work + "/" + file);
        }
        start = System.nanoTime();
        ExecResult run = XTENSA_RUNNER.execInContainer("/harness/run.sh", work);
        LOG.info("[{} / {}] run.sh exit {} in {} ms", mainClass, board.id(), run.getExitCode(), millisSince(start));
        Files.writeString(directory.resolve("uart.log"), run.getStdout());
        Files.writeString(directory.resolve("build.log"), run.getStderr());
        assertThat(run.getExitCode()).as("build and run %s on %s:%n%s%s", mainClass, board.displayName(),
                run.getStdout(), run.getStderr()).isZero();
        return transcript(run, "/harness/run.sh " + work);
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

    private static boolean declares(Path source, Board board) {
        try {
            return Files.readString(source).contains(board.annotationArgument());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void compile(List<Path> sources, Path destination) throws IOException {
        Files.createDirectories(destination);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new ArrayList<>(List.of("--enable-preview", "--release", "27",
                "-d", destination.toString(), "-cp", CompilerTestSupport.JUNO_API_CLASSES.toString()));
        sources.forEach(source -> arguments.add(source.toString()));
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).as("javac %s", sources).isZero();
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String simpleName(String source) {
        String file = source.substring(source.lastIndexOf('/') + 1);
        return file.substring(0, file.length() - ".java".length());
    }

    private static String simpleOf(String mainClass) {
        return mainClass.substring(mainClass.lastIndexOf('.') + 1);
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
