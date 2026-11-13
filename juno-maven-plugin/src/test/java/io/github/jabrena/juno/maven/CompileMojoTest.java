package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.fixture.TinyProgramClasspath;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code juno:compile} through a real, Maven-configured {@link CompileMojo}; the happy-path case runs the real
 * Juno compiler against {@link io.github.jabrena.juno.maven.fixture.TinyProgram} on this module's own test
 * classpath, never touching {@code arduino-cli}. Every Mojo parameter is set in pure Java through
 * {@link MojoConfig}, not an XML fixture file. */
@DisplayName("juno:compile goal")
class CompileMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Requires a main class")
    void shouldRequireAMainClass() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("outputDirectory", "target/juno-mojo-test/missing-main-class")
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Missing required Juno entry point");
    }

    @Test
    @DisplayName("Rejects an empty classpath")
    void shouldRejectAnEmptyClasspath() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("outputDirectory", "target/juno-mojo-test/empty-classpath")
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Maven compile classpath is empty");
    }

    @Test
    @DisplayName("Generates the assembly, shim and wrapper for a real entry point")
    void shouldGenerateTheAssemblyShimAndWrapperForARealEntryPoint() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/compile")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);

        // When
        mojo.execute();

        // Then
        Path sketchDirectory = Path.of("target/juno-mojo-test/compile/TinyProgramAsm");
        assertThat(sketchDirectory.resolve("TinyProgram.S")).isRegularFile();
        assertThat(sketchDirectory.resolve("TinyProgramShim.cpp")).isRegularFile();
        assertThat(sketchDirectory.resolve("TinyProgramAsm.ino")).isRegularFile();
        assertThat(log.infos()).anyMatch(line -> line.contains("Generated") && line.contains("UNO R4 WiFi"));
    }

    @Test
    @DisplayName("Fails with actionable guidance when neither @Board nor juno.board names a target")
    void shouldRequireABoardWhenNeitherAnnotationNorOptionNamesOne() throws Exception {
        // Given: TinyProgram has no @Board annotation, and no juno.board is configured
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("outputDirectory", "target/juno-mojo-test/no-board")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Target board is required")
                .hasMessageContaining("-Djuno.board=<id>");
    }

    @Test
    @DisplayName("Regenerates for the board selected by the latest build, not the previous output")
    void shouldRegenerateForTheBoardSelectedNow() throws Exception {
        // Given: one output directory, compiled first for the UNO R4 WiFi and then for the UNO Q
        RecordingLog log = new RecordingLog();
        CompileMojo first = lookupEmptyMojo("compile", new File("."));
        first = configureMojo(first, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/regenerate")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        first.setLog(log);
        first.execute();

        CompileMojo second = lookupEmptyMojo("compile", new File("."));
        second = configureMojo(second, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-q")
                .set("outputDirectory", "target/juno-mojo-test/regenerate")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        second.setLog(log);

        // When
        second.execute();

        // Then
        Path sketchDirectory = Path.of("target/juno-mojo-test/regenerate/TinyProgramAsm");
        assertThat(sketchDirectory.resolve("TinyProgram.S")).isRegularFile();
        assertThat(log.infos()).anyMatch(line -> line.contains("Generated") && line.contains("UNO R4 WiFi"));
        assertThat(log.infos()).anyMatch(line -> line.contains("Generated") && line.contains("UNO Q"));
    }

    @Test
    @DisplayName("Rejects an invalid carrier override")
    void shouldRejectAnInvalidCarrierOverride() throws Exception {
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("carrierCount", "3")
                .set("outputDirectory", "target/juno-mojo-test/invalid-carriers")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class).hasMessageContaining("juno.carriers");
    }
}
