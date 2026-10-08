package io.github.jabrena.juno.arduino;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.ClassFileReader;
import io.github.jabrena.juno.classfile.JavaClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Compiles small, focused programs the way {@code juno:verify} does: Juno generates the sketch, then
 * {@code arduino-cli compile} builds and links it with the real core of every board the program's {@code @Board}
 * annotation declares, inside a Docker container, so no local Arduino installation is needed. Each program in
 * {@code src/test/arduino/programs} exercises one API (GPIO, Serial, Servo, I2C, ...) or one shield (TFT touch,
 * LCD keypad, ...), so a failure names the feature that broke. Nothing is executed: the cores only prove that the
 * generated shim includes the right headers, calls the real library signatures and links for that board. Run
 * behavior is covered by {@code QemuRunIT} (language) and by flashing a board (peripherals).
 *
 * <p>Opt-in, as it needs Docker and downloads the cores on first use:
 * {@code ./mvnw -f juno/pom.xml -Parduino-cli verify}. The image is built from
 * {@code src/test/docker/arduino-cli/Dockerfile}; to use a prebuilt image instead, pass
 * {@code -Djuno.arduinoCliImage=<image>}. Skipped when Docker is not available.
 */
@Tag("arduino-cli")
@Testcontainers(disabledWithoutDocker = true)
class ArduinoCliCompileIT {
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path PROGRAMS = BASEDIR.resolve("src/test/arduino/programs");
    private static final Path PROGRAM_CLASSES = BASEDIR.resolve("target/arduino-cli/program-classes");

    @Container
    private static final GenericContainer<?> ARDUINO_CLI = container().withCommand("sleep", "infinity");

    @BeforeAll
    static void compilePrograms() throws IOException {
        Files.createDirectories(PROGRAM_CLASSES);
        List<String> arguments = new ArrayList<>(List.of("--enable-preview", "--release", "27",
                "-d", PROGRAM_CLASSES.toString(), "-cp", BASEDIR.resolve("target/classes").toString()));
        sources().forEach(source -> arguments.add(source.toString()));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).as("javac of the programs")
                .isZero();
    }

    /** Every program under src/test/arduino/programs, once per board its {@code @Board} declares, or on every board when it declares none (a generic feature). */
    static Stream<Arguments> programs() throws IOException {
        return sources().stream()
                .map(source -> source.getFileName().toString().replace(".java", ""))
                .flatMap(name -> declaredBoards("demo." + name).stream().map(board -> Arguments.of(name, board)));
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(PROGRAMS)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** Reads the compiled entry point's {@code @Board} targets directly from its class file; no annotation means every board. */
    private static List<Board> declaredBoards(String mainClass) {
        Path classFile = PROGRAM_CLASSES.resolve(mainClass.replace('.', '/') + ".class");
        try {
            JavaClass javaClass = new ClassFileReader().read(Files.readAllBytes(classFile));
            List<String> apiClassNames = javaClass.boardApiClassNames();
            return apiClassNames.isEmpty()
                    ? List.of(Board.values())
                    : apiClassNames.stream().map(Board::fromApiClassName).distinct().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("programs")
    void compilesAndLinksWithArduinoCli(String program, Board board) throws Exception {
        Path sketch = generateSketch(program, board);
        String target = "/sketches/" + board.id() + "/" + sketch.getFileName();
        ARDUINO_CLI.copyFileToContainer(MountableFile.forHostPath(sketch), target);

        ExecResult result = ARDUINO_CLI.execInContainer("arduino-cli", "compile", "--fqbn", board.fqbn(), target);

        assertThat(result.getExitCode()).as("arduino-cli compile %s (%s):%n%s%s", program, board.displayName(),
                result.getStdout(), result.getStderr()).isZero();
    }

    /** Runs Juno on the program, writing the same sketch directory {@code juno:compile} does. */
    private static Path generateSketch(String program, Board board) throws IOException {
        String sketchName = program + "Asm";
        Path directory = BASEDIR.resolve("target/arduino-cli/sketches").resolve(board.id()).resolve(sketchName);
        Files.createDirectories(directory);
        CompilationResult result = new JunoCompiler().compileTo(
                List.of(PROGRAM_CLASSES, BASEDIR.resolve("target/classes")), "demo." + program,
                directory.resolve(program + ".S"), directory.resolve(program + "Shim.cpp"), false,
                Optional.of(board.id()));
        // The .ino wrapper juno-maven-plugin writes (AbstractJunoMojo#writeAsmWrapper).
        String wrapper = """
                // Generated by Juno. Do not edit.
                extern "C" void %s();

                void setup() {
                  %s();
                }

                void loop() {
                }
                """.formatted(result.entryPointSymbol(), result.entryPointSymbol());
        Files.writeString(directory.resolve(sketchName + ".ino"), wrapper, StandardCharsets.UTF_8);
        return directory;
    }

    private static GenericContainer<?> container() {
        String prebuilt = System.getProperty("juno.arduinoCliImage", "");
        if (!prebuilt.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuilt));
        }
        return new GenericContainer<>(new ImageFromDockerfile("juno-arduino-cli", false)
                .withFileFromPath(".", BASEDIR.resolve("src/test/docker/arduino-cli")));
    }
}
