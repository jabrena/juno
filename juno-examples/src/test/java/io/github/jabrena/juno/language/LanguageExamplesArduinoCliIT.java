package io.github.jabrena.juno.language;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.ClassFileReader;
import io.github.jabrena.juno.classfile.JavaClass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles the Java, Kotlin, Scala and Clojure language examples the way {@code juno:verify} does: Juno generates the sketch, then
 * {@code arduino-cli compile} builds and links it with the real core of every board the example's {@code @Board}
 * annotation declares, or on every board when it declares none, inside a Docker container. The examples are read from this
 * module's own compiled classes, so nothing is recompiled and no source tree is reached into. Nothing is executed:
 * the cores only prove that the generated shim links for that board. Run behavior is covered by {@code QemuRunIT}
 * and by flashing a board.
 *
 * <p>{@code UnsupportedFeature} is left out: it is rejected by design, and {@code LanguageExamplesCompilationTest}
 * asserts that rejection.
 *
 * <p>Opt-in, as it needs Docker and downloads the cores on first use:
 * {@code ./mvnw -pl juno-examples -am -Parduino-cli verify -Dit.test=LanguageExamplesArduinoCliIT}. The image is built
 * from the compiler's {@code juno-compiler/src/test/docker/arduino-cli/Dockerfile}; to use a prebuilt image instead,
 * pass {@code -Djuno.arduinoCliImage=<image>}. Skipped when Docker is not available.
 *
 * <p>The sketches compile concurrently in the one container; {@code arduino-cli} caches each board's precompiled core
 * and reuses it, so the first sketch of a board compiles alone and the others wait for that archive.
 */
@Tag("arduino-cli")
@Execution(ExecutionMode.CONCURRENT)
@Testcontainers(disabledWithoutDocker = true)
class LanguageExamplesArduinoCliIT {
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path DOCKERFILE_DIR = BASEDIR.resolve("../juno-compiler/src/test/docker/arduino-cli");
    private static final Path SKETCHES = BASEDIR.resolve("target/arduino-cli/language-sketches");
    private static final Path EXAMPLE_CLASSES = BASEDIR.resolve("target/classes");
    private static final Path API_CLASSES = codeSource(Watchdog.class);
    private static final String LANGUAGE_PACKAGE = "io/github/jabrena/juno/language";
    private static final String KOTLIN_PACKAGE = "io.github.jabrena.juno.language.kotlin.";
    private static final String SCALA_PACKAGE = "io.github.jabrena.juno.language.scala.";
    private static final String CLOJURE_PACKAGE = "io.github.jabrena.juno.language.clojure.";
    private static final String UNSUPPORTED_FEATURE = UnsupportedFeature.class.getName();
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";

    private static final Map<Board, Object> CORE_LOCKS = new ConcurrentHashMap<>();
    private static final Set<Board> CORE_CACHED = ConcurrentHashMap.newKeySet();

    @Container
    private static final GenericContainer<?> ARDUINO_CLI = container().withCommand("sleep", "infinity");

    /** One compilable Java language example, with the boards it targets. */
    private record Example(String name, String mainClass, List<Board> boards) {
        @Override
        public String toString() {
            return name;
        }
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("examples")
    void compilesAndLinksWithArduinoCli(Example example, Board board) throws Exception {
        Path sketch = generateSketch(example, board);
        String target = "/sketches/" + board.id() + "/" + sketch.getFileName();
        ARDUINO_CLI.copyFileToContainer(MountableFile.forHostPath(sketch), target);

        List<String> command = new ArrayList<>(List.of("arduino-cli", "compile", "--fqbn", board.fqbn()));
        if (board == Board.UNO_R3_WIFI_REV2) {
            command.add("--build-property");
            command.add("compiler.cpp.extra_flags=-DSPI_DRIVER_SELECT=3");
        }
        command.add(target);
        ExecResult result = compile(board, command.toArray(String[]::new));

        assertThat(result.getExitCode()).as("arduino-cli compile %s (%s):%n%s%s", example.name(),
                board.displayName(), result.getStdout(), result.getStderr()).isZero();
    }

    /** Every language example with a {@code public static void main}, once per board it targets. */
    static Stream<Arguments> examples() throws IOException {
        List<Arguments> arguments = new ArrayList<>();
        for (Example example : examples(EXAMPLE_CLASSES.resolve(LANGUAGE_PACKAGE))) {
            for (Board board : example.boards()) {
                arguments.add(Arguments.of(example, board));
            }
        }
        return arguments.stream();
    }

    private static List<Example> examples(Path languageClasses) throws IOException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(languageClasses)) {
            classFiles = files.filter(path -> path.toString().endsWith(".class") && !path.toString().contains("$")
                            && !path.getFileName().toString().equals("package-info.class"))
                    .sorted().toList();
        }
        List<Example> examples = new ArrayList<>();
        for (Path classFile : classFiles) {
            JavaClass javaClass = new ClassFileReader().read(Files.readAllBytes(classFile));
            String mainClass = className(classFile);
            if (javaClass.findMethod("main", MAIN_DESCRIPTOR) == null || mainClass.equals(UNSUPPORTED_FEATURE)) {
                continue;
            }
            examples.add(new Example(exampleName(mainClass), mainClass, boardsFor(javaClass)));
        }
        return examples;
    }

    /**
     * The sketch name. Java, Kotlin, Scala and Clojure examples share simple names (Arrays, Strings, ...), so Kotlin,
     * Scala and Clojure get a prefix.
     */
    private static String exampleName(String mainClass) {
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        if (mainClass.startsWith(KOTLIN_PACKAGE)) {
            return "Kotlin" + simpleName;
        }
        if (mainClass.startsWith(SCALA_PACKAGE)) {
            return "Scala" + simpleName;
        }
        return mainClass.startsWith(CLOJURE_PACKAGE) ? "Clojure" + simpleName : simpleName;
    }

    private static String className(Path classFile) {
        String relative = EXAMPLE_CLASSES.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
    }

    /** The {@code @Board} targets of the entry point; every board when it declares none. */
    private static List<Board> boardsFor(JavaClass javaClass) {
        List<String> apiClassNames = javaClass.boardApiClassNames();
        return apiClassNames.isEmpty()
                ? List.of(Board.values())
                : apiClassNames.stream().map(Board::fromApiClassName).distinct().toList();
    }

    /** Compiles alone while {@code board}'s cached core is first built, then concurrently with the others. */
    private static ExecResult compile(Board board, String[] command) throws IOException, InterruptedException {
        synchronized (CORE_LOCKS.computeIfAbsent(board, key -> new Object())) {
            if (!CORE_CACHED.contains(board)) {
                ExecResult result = ARDUINO_CLI.execInContainer(command);
                if (result.getExitCode() == 0) {
                    CORE_CACHED.add(board);
                }
                return result;
            }
        }
        return ARDUINO_CLI.execInContainer(command);
    }

    /** Runs Juno on the example, writing the same sketch directory {@code juno:compile} does. */
    private static Path generateSketch(Example example, Board board) throws IOException {
        String sketchName = example.name() + "Asm";
        Path directory = SKETCHES.resolve(board.id()).resolve(sketchName);
        Files.createDirectories(directory);
        CompilationResult result = new JunoCompiler().compileTo(
                List.of(EXAMPLE_CLASSES, API_CLASSES), example.mainClass(),
                directory.resolve(example.name() + ".S"), directory.resolve(example.name() + "Shim.cpp"), false,
                Optional.of(board.id()));
        // The same .ino wrapper juno-maven-plugin writes.
        result.sketchWrapper().writeTo(directory.resolve(sketchName + ".ino"));
        return directory;
    }

    private static Path codeSource(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static GenericContainer<?> container() {
        String prebuilt = System.getProperty("juno.arduinoCliImage", "");
        if (!prebuilt.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuilt));
        }
        return new GenericContainer<>(new ImageFromDockerfile("juno-arduino-cli", false)
                .withFileFromPath(".", DOCKERFILE_DIR));
    }
}
