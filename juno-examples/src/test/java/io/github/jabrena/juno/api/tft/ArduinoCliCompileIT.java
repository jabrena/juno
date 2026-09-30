package io.github.jabrena.juno.api.tft;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.ClassFileReader;
import io.github.jabrena.juno.classfile.JavaClass;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
 * Compiles every TFT game the way {@code juno:verify} does — Juno generates the sketch, then
 * {@code arduino-cli compile} builds and links it with the real core for each board the game's
 * {@code @Board} annotation declares — inside a Docker container, so no local Arduino installation
 * is needed. A game declaring more than one board (see {@code io.github.jabrena.juno.annotations.Board})
 * is compiled once per declared board, exactly as a separate {@code juno:verify -Djuno.board=<id>}
 * run for each would.
 *
 * <p>Opt-in, as it needs Docker and downloads the cores on first use:
 * {@code ./mvnw install -DskipTests} once, then {@code ./mvnw -f juno-examples/pom.xml -Parduino-cli verify}.
 * The image is built from {@code src/test/docker/arduino-cli/Dockerfile}; to use a prebuilt image
 * instead, pass {@code -Djuno.arduinoCliImage=<image>}. Skipped when Docker is not available.
 */
@Tag("arduino-cli")
@Testcontainers(disabledWithoutDocker = true)
class ArduinoCliCompileIT {
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Pattern FLASH = Pattern.compile("Sketch uses (\\d+) bytes \\((\\d+)%\\)");
    private static final Pattern RAM = Pattern.compile("Global variables use (\\d+) bytes \\((\\d+)%\\)");

    @Container
    private static final GenericContainer<?> ARDUINO_CLI = container().withCommand("sleep", "infinity");

    /**
     * Every TFT program's fully qualified class name paired with each board its {@code @Board}
     * annotation declares (defaulting to {@link Board#DEFAULT} when absent): {@code TftTouchPaint}
     * in api.tft, and every game in games or one of its subpackages. A game is its {@code @Board}
     * entry point; the other classes of a multi-class game's subpackage are its parts.
     */
    static Stream<Arguments> games() throws IOException {
        Path sources = BASEDIR.resolve("src/main/java");
        List<String> games = new ArrayList<>();
        for (Path directory : List.of(sources.resolve("io/github/jabrena/juno/api/tft"),
                sources.resolve("io/github/jabrena/juno/games"))) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".java") && isEntryPoint(path))
                        .forEach(path -> {
                            String relative = sources.relativize(path).toString();
                            games.add(relative.substring(0, relative.length() - ".java".length())
                                    .replace(path.getFileSystem().getSeparator(), "."));
                        });
            }
        }
        return games.stream().sorted()
                .flatMap(game -> declaredBoards(game).stream().map(board -> Arguments.of(game, board)));
    }

    private static boolean isEntryPoint(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8).contains("@Board(");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Reads the compiled entry point's {@code @Board} targets directly from its class file. */
    private static List<Board> declaredBoards(String mainClass) {
        Path classFile = BASEDIR.resolve("target/classes").resolve(mainClass.replace('.', '/') + ".class");
        try {
            JavaClass javaClass = new ClassFileReader().read(Files.readAllBytes(classFile));
            List<String> apiClassNames = javaClass.boardApiClassNames();
            return apiClassNames.isEmpty()
                    ? List.of(Board.DEFAULT)
                    : apiClassNames.stream().map(Board::fromApiClassName).distinct().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("games")
    void compilesAndLinksWithArduinoCli(String game, Board board) throws Exception {
        Path sketch = generateSketch(game, board);
        String target = "/sketches/" + board.id() + "/" + sketch.getFileName();
        ARDUINO_CLI.copyFileToContainer(MountableFile.forHostPath(sketch), target);

        ExecResult result = ARDUINO_CLI.execInContainer("arduino-cli", "compile", "--fqbn", board.fqbn(), target);

        String output = result.getStdout() + result.getStderr();
        assertThat(result.getExitCode()).as("arduino-cli compile %s (%s):%n%s", game, board.displayName(), output)
                .isZero();
        Matcher flash = FLASH.matcher(output);
        Matcher ram = RAM.matcher(output);
        assertThat(flash.find()).as("flash usage reported:%n%s", output).isTrue();
        assertThat(ram.find()).as("RAM usage reported:%n%s", output).isTrue();
        assertThat(Integer.parseInt(flash.group(2))).as("flash used by %s (%s)", game, board.displayName())
                .isLessThan(100);
        assertThat(Integer.parseInt(ram.group(2))).as("RAM used by %s (%s)", game, board.displayName())
                .isLessThan(100);
        System.out.println(game + " (" + board.displayName() + "): " + flash.group() + ", " + ram.group());
    }

    /** Runs Juno on the game, writing the same sketch directory {@code juno:compile} does. */
    private static Path generateSketch(String mainClass, Board board) throws IOException {
        String game = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        String sketchName = game + "Asm";
        Path directory = BASEDIR.resolve("target/arduino-cli-sketches").resolve(board.id()).resolve(sketchName);
        Files.createDirectories(directory);
        CompilationResult result = new JunoCompiler().compileTo(junoClasspath(),
                mainClass, directory.resolve(game + ".S"),
                directory.resolve(game + "Shim.cpp"), false, Optional.of(board.id()));
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

    /**
     * Juno's closed-world classpath, as {@code juno:compile} builds it from the compile classpath:
     * the examples plus the {@code juno} artifact. Test classes (whose hardware doubles would replace
     * the real native API) and test-only libraries are left out.
     */
    private static List<Path> junoClasspath() {
        List<Path> entries = new ArrayList<>();
        entries.add(BASEDIR.resolve("target/classes"));
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        for (String entry : classpath.split(File.pathSeparator)) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            boolean reactorJuno = path.endsWith(Path.of("juno", "target", "classes"));
            boolean installedJuno = name.startsWith("juno-") && name.endsWith(".jar")
                    && !name.startsWith("juno-maven-plugin") && !name.startsWith("juno-examples");
            if (reactorJuno || installedJuno) {
                entries.add(path);
            }
        }
        return entries;
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
