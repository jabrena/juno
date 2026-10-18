package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code juno:monitor} through a real, Maven-configured {@link MonitorMojo}; never touches a real board. */
class MonitorMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    void requiresABoardParameter() throws Exception {
        MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
        mojo = configureMojo(mojo, configuration().set("arduinoCli", "arduino-cli").build());
        mojo.setLog(new RecordingLog());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Missing required target board");
    }

    @Test
    void opensTheMonitorOnTheResolvedPortAtTheConfiguredBaudRate() throws Exception {
        MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("board", "arduino-uno-q")
                .set("baudRate", "115200")
                .build());
        mojo.setLog(new RecordingLog());
        FakeExecutor executor = new FakeExecutor()
                .respond(0, boardListJson("/dev/cu.usbmodem1", "arduino:zephyr:unoq"))
                .respond(0, "");
        mojo.useExecutor(executor);

        mojo.execute();

        assertThat(executor.invocations()).hasSize(2);
        Command monitorCommand = executor.invocations().get(1);
        assertThat(monitorCommand.isInteractive()).isTrue();
        assertThat(monitorCommand.arguments()).containsExactly("arduino-cli", "monitor", "--port",
                "/dev/cu.usbmodem1", "--config", "baudrate=115200");
    }

    private static String boardListJson(String address, String fqbn) {
        return """
                {
                  "detected_ports": [
                    {
                      "matching_boards": [{"name": "Board", "fqbn": "%s"}],
                      "port": {"address": "%s"}
                    }
                  ]
                }""".formatted(fqbn, address);
    }
}
