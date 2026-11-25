package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.fixture.TinyProgramClasspath;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
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
        // Given: one output directory, compiled first for the UNO R4 WiFi and then for the Portenta H7
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
                .set("board", "arduino-portenta-h7")
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
        assertThat(log.infos()).anyMatch(line -> line.contains("Generated") && line.contains("Portenta H7"));
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
    @Test
    @DisplayName("Links the juno.dm.model file into a program that calls DecisionModel.begin()")
    void shouldLinkTheDecisionModelFile() throws Exception {
        // Given
        Path model = Files.createDirectories(Path.of("target/juno-mojo-test/dm-model")).resolve("TINY.JEV");
        Files.write(model, tinyJevModel());
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.DecisionModelProgram")
                .set("board", "arduino-nano-esp32")
                .set("decisionModel", model.toString())
                .set("outputDirectory", "target/juno-mojo-test/dm-linked")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());

        // When
        mojo.execute();

        // Then
        assertThat(Path.of("target/juno-mojo-test/dm-linked/DecisionModelProgramAsm/DecisionModelProgramShim.cpp"))
                .content().contains("juno_dm_linked_model[31]", "juno_dm_begin_linked");
    }

    @Test
    @DisplayName("Fails a program that calls DecisionModel.begin() without juno.dm.model")
    void shouldRequireTheDecisionModelFile() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.DecisionModelProgram")
                .set("board", "arduino-nano-esp32")
                .set("outputDirectory", "target/juno-mojo-test/dm-missing")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Djuno.dm.model=<file>");
    }

    @Test
    @DisplayName("Links the juno.lm.model file into a program that calls LanguageModel.begin()")
    void shouldLinkTheLanguageModelFile() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.LanguageModelProgram")
                .set("board", "arduino-nano-esp32")
                .set("languageModel", "../juno-ai/juno-models/src/main/resources/lm/JUNO.BIN")
                .set("outputDirectory", "target/juno-mojo-test/lm-linked")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());

        // When
        mojo.execute();

        // Then
        assertThat(Path.of("target/juno-mojo-test/lm-linked/LanguageModelProgramAsm/LanguageModelProgramShim.cpp"))
                .content().contains("juno_lm_linked_model[381907]", "juno_lm_bundled_model");
    }

    @Test
    @DisplayName("Fails a program that calls LanguageModel.begin() without juno.lm.model")
    void shouldRequireTheLanguageModelFile() throws Exception {
        // Given
        CompileMojo mojo = lookupEmptyMojo("compile", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.LanguageModelProgram")
                .set("board", "arduino-nano-esp32")
                .set("outputDirectory", "target/juno-mojo-test/lm-missing")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Djuno.lm.model=<file>");
    }

    /** A 2-action, 3-feature JEV1 model: 9 header bytes, then per action a scale, a bias and 3 weights. */
    private static byte[] tinyJevModel() {
        ByteBuffer model = ByteBuffer.allocate(31);
        model.put(new byte[] {'J', 'E', 'V', '1', 1}).putShort((short) 2).putShort((short) 3);
        model.putFloat(0.5f).putFloat(0f).put(new byte[] {1, 2, 3});
        model.putFloat(0.25f).putFloat(1f).put(new byte[] {3, 2, 1});
        return model.array();
    }
}
