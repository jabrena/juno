package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.fixture.TinyProgramClasspath;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code juno:flash} through a real, Maven-configured {@link FlashMojo}: a real Juno compile of
 * {@link io.github.jabrena.juno.maven.fixture.TinyProgram}, followed by a faked {@code arduino-cli compile},
 * port discovery, and upload. Never touches a real board. */
@DisplayName("juno:flash goal")
class FlashMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Compiles, verifies and uploads to the only matching board")
    void shouldCompileVerifyAndUploadToTheOnlyMatchingBoard() throws Exception {
        // Given
        FlashMojo mojo = lookupEmptyMojo("flash", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/flash")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());
        FakeExecutor executor = new FakeExecutor()
                .respond(0, "Sketch uses 1234 bytes")
                .respond(0, """
                        {
                          "detected_ports": [
                            {
                              "matching_boards": [{"name": "Board", "fqbn": "arduino:renesas_uno:unor4wifi"}],
                              "port": {"address": "/dev/cu.usbmodem1"}
                            }
                          ]
                        }""")
                .respond(0, "upload complete");
        mojo.useExecutor(executor);

        // When
        mojo.execute();

        // Then
        assertThat(executor.invocations()).hasSize(3);
        Command uploadCommand = executor.invocations().get(2);
        assertThat(uploadCommand.arguments()).contains("arduino-cli", "upload", "--port", "/dev/cu.usbmodem1",
                "--fqbn", "arduino:renesas_uno:unor4wifi");
    }
}
