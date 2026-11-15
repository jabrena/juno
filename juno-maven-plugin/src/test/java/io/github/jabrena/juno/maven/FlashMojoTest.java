package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.fixture.TinyProgramClasspath;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    @DisplayName("Builds a Pico 2 W secret-store program with the secure firmware in its boot slot, then uploads that")
    void shouldInstallThePicoSecureFirmwareBeforeUploading() throws Exception {
        // Given: arduino-cli is faked, so the UF2 it would build is put where it would leave it
        Path sketch = Path.of("target/juno-mojo-test/flash-pico/PicoSecretProgramAsm").toAbsolutePath().normalize();
        Path uf2 = sketch.resolve("build/PicoSecretProgramAsm.ino.uf2");
        Files.createDirectories(uf2.getParent());
        Files.write(uf2, PicoSecureFirmwareInstallerTest.PicoUf2.of(49));
        FlashMojo mojo = lookupEmptyMojo("flash", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.PicoSecretProgram")
                .set("board", "raspberry-pi-pico-2-w")
                .set("outputDirectory", "target/juno-mojo-test/flash-pico")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .build());
        mojo.setLog(new RecordingLog());
        FakeExecutor executor = new FakeExecutor()
                .respond(0, "Sketch uses 1234 bytes")
                .respond(0, """
                        {
                          "detected_ports": [
                            {
                              "matching_boards": [{"name": "Pico 2W", "fqbn": "rp2040:rp2040:rpipico2w"}],
                              "port": {"address": "/dev/cu.usbmodem1201"}
                            }
                          ]
                        }""")
                .respond(0, "upload complete");
        mojo.useExecutor(executor);

        // When
        mojo.execute();

        // Then
        String build = sketch.resolve("build").toString();
        assertThat(executor.invocations().get(0).arguments()).contains("compile", "--build-property",
                "build.ram_length=504k", "--build-path", build);
        assertThat(executor.invocations().get(2).arguments()).contains("upload", "--port", "/dev/cu.usbmodem1201",
                "--input-dir", build);
        assertThat(PicoSecureFirmwareInstallerTest.PicoUf2.payload(Files.readAllBytes(uf2), 0))
                .as("the boot slot now starts with the secure firmware")
                .startsWith((byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x20);
    }
}
