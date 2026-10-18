package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code juno:boards} through a real, Maven-configured {@link BoardsMojo}; never touches a real process. */
class BoardsMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    void reportsNoPortsDetected() throws Exception {
        BoardsMojo mojo = lookupConfiguredMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        mojo.useExecutor(new FakeExecutor().respond(0, "{\"detected_ports\": []}"));

        mojo.execute();

        assertThat(log.infos()).contains("No serial ports detected. Connect a board and try again.");
    }

    @Test
    void listsEveryDetectedPort() throws Exception {
        BoardsMojo mojo = lookupConfiguredMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        mojo.useExecutor(new FakeExecutor().respond(0, """
                {
                  "detected_ports": [
                    {
                      "matching_boards": [
                        {"name": "Arduino UNO R4 WiFi", "fqbn": "arduino:renesas_uno:unor4wifi"}
                      ],
                      "port": {"address": "/dev/cu.usbmodem1", "protocol": "serial"}
                    }
                  ]
                }"""));

        mojo.execute();

        assertThat(log.infos()).contains("Detected ports (use -Djuno.port=<port> to select one):",
                "  /dev/cu.usbmodem1 [serial] - Arduino UNO R4 WiFi (arduino:renesas_uno:unor4wifi)");
    }

    @Test
    void wrapsAnArduinoCliFailureAsAMojoFailureException() throws Exception {
        BoardsMojo mojo = lookupConfiguredMojo();
        mojo.setLog(new RecordingLog());
        mojo.useExecutor(new FakeExecutor().respond(1, "arduino-cli: command not found"));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Command failed with exit code 1");
    }

    private BoardsMojo lookupConfiguredMojo() throws Exception {
        BoardsMojo mojo = lookupEmptyMojo("boards", new File("."));
        return configureMojo(mojo, configuration().set("arduinoCli", "arduino-cli").build());
    }
}
