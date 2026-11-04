package io.github.jabrena.juno.conformance;

import io.github.jabrena.juno.board.Board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What every emulator runner shares, laid out under {@code target/<backend>}: compiling the program and OpenJDK-side
 * sources, discovering the catalogued conformance programs, the OpenJDK baseline transcript, and comparing a backend's
 * transcript with it, keeping the expected and actual output next to the generated sources when they differ.
 */
public final class ConformanceWorkspace {
    /** The line every harness prints after the program returns. */
    public static final String EXIT_MARKER = "[juno-exit]\n";

    private final Path conformance;
    private final Path openJdkSources;
    private final Path programClasses;
    private final Path openJdkClasses;
    private final Path generated;
    private final Path junoApiClasses;
    private final ConformanceDigitalTwin digitalTwin;

    public ConformanceWorkspace(Path basedir, String backend, Path junoApiClasses) {
        Path target = basedir.resolve("target").resolve(backend);
        this.conformance = basedir.resolve("src/test/resources/conformance");
        this.openJdkSources = basedir.resolve("src/test/qemu/openjdk");
        this.programClasses = target.resolve("program-classes");
        this.openJdkClasses = target.resolve("openjdk-classes");
        this.generated = target.resolve("generated");
        this.junoApiClasses = junoApiClasses;
        this.digitalTwin = new ConformanceDigitalTwin(openJdkClasses, programClasses, junoApiClasses);
    }

    /** The catalogued conformance fixtures, validated against their sources. */
    public ConformanceCatalog catalog() {
        ConformanceCatalog catalog = ConformanceCatalog.load(conformance.resolve("catalog.xml"));
        catalog.validate(conformance);
        return catalog;
    }

    public ProgramSource source(ConformanceCatalog.Program program) {
        return new ProgramSource(conformance.resolve(program.source()));
    }

    public ProgramDirectory conformancePrograms() {
        return new ProgramDirectory(conformance.resolve("programs"));
    }

    /** Compiles the program sources into the classes both Juno and the OpenJDK baseline run. */
    public void compilePrograms(List<Path> sources) throws IOException {
        compile(sources, programClasses);
    }

    /** Compiles the OpenJDK-side doubles (a stdout-printing {@code Serial}) the baseline runs against. */
    public void compileBaseline() throws IOException {
        compile(new ProgramDirectory(openJdkSources).allSources(), openJdkClasses);
    }

    public void compile(List<Path> sources, Path destination) throws IOException {
        Files.createDirectories(destination);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new ArrayList<>(List.of("--enable-preview", "--release", "27",
                "-d", destination.toString(), "-cp", junoApiClasses.toString()));
        sources.forEach(source -> arguments.add(source.toString()));
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).as("javac %s", sources).isZero();
    }

    /** Juno's closed world for {@code classes}: those classes and the juno-api classes they call, nothing else. */
    public List<Path> junoClasspath(Path classes) {
        return List.of(classes, junoApiClasses);
    }

    public List<Path> junoClasspath() {
        return junoClasspath(programClasses);
    }

    /** What the program prints on OpenJDK, followed by the exit marker a harness prints after it returns. */
    public String baseline(String mainClass) throws IOException, InterruptedException {
        return digitalTwin.run(mainClass) + EXIT_MARKER;
    }

    /** Where the generated sources and diagnostics of {@code program} (a main class or simple name) go. */
    public Path generated(Board board, String program) {
        return generated.resolve(board.id()).resolve(program.substring(program.lastIndexOf('.') + 1));
    }

    /**
     * Asserts that a backend printed the expected transcript; when it did not, writes {@code expected.txt} and
     * {@code actual.txt} next to the program's generated sources first.
     */
    public void assertTranscript(Board board, String program, String expected, String actual, String description)
            throws IOException {
        if (!actual.equals(expected)) {
            Path directory = generated(board, program);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("expected.txt"), expected);
            Files.writeString(directory.resolve("actual.txt"), actual);
        }
        assertThat(actual).as(description).isEqualTo(expected);
    }
}
