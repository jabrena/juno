package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code juno:verify} through a real, Maven-configured {@link VerifyMojo}: a real Juno compile of
 * {@link io.github.jabrena.juno.maven.fixture.TinyProgram}, followed by a faked {@code arduino-cli compile}. */
@DisplayName("juno:verify goal")
class VerifyMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Compiles, then builds the sketch with arduino-cli")
    void shouldCompileThenBuildTheSketchWithArduinoCli() throws Exception {
        // Given
        VerifyMojo mojo = lookupEmptyMojo("verify", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/verify")
                .list("classpathElements", "classpathElement", "target/test-classes")
                .build());
        mojo.setLog(new RecordingLog());
        FakeExecutor executor = new FakeExecutor().respond(0, "Sketch uses 1234 bytes");
        mojo.useExecutor(executor);

        // When
        mojo.execute();

        // Then
        assertThat(executor.invocations()).hasSize(1);
        Command compileCommand = executor.invocations().getFirst();
        assertThat(compileCommand.arguments()).contains("arduino-cli", "compile", "--fqbn",
                "arduino:renesas_uno:unor4wifi");
        assertThat(compileCommand.arguments().getLast())
                .endsWith("juno-mojo-test/verify/TinyProgramAsm".replace('/', File.separatorChar));
    }

    @Test
    @DisplayName("Warns and builds for a configured FQBN override")
    void shouldWarnAndBuildForAConfiguredFqbnOverride() throws Exception {
        // Given
        VerifyMojo mojo = lookupEmptyMojo("verify", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("fqbn", "arduino:renesas_uno:unor4wifi:cache=download")
                .set("outputDirectory", "target/juno-mojo-test/verify-fqbn-override")
                .list("classpathElements", "classpathElement", "target/test-classes")
                .build());
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        FakeExecutor executor = new FakeExecutor().respond(0, "Sketch uses 1234 bytes");
        mojo.useExecutor(executor);

        // When
        mojo.execute();

        // Then
        assertThat(log.warnings()).anyMatch(line -> line.contains("overrides @Board target"));
        Command compileCommand = executor.invocations().getFirst();
        assertThat(compileCommand.arguments()).contains("--fqbn", "arduino:renesas_uno:unor4wifi:cache=download");
    }
}
