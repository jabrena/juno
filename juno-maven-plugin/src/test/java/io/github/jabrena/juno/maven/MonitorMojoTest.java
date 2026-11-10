package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code juno:monitor} through a real, Maven-configured {@link MonitorMojo}; never touches a real board. */
@DisplayName("juno:monitor goal")
class MonitorMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Requires a board parameter")
    void shouldRequireABoardParameter() throws Exception {
        // Given
        MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
        mojo = configureMojo(mojo, configuration().set("arduinoCli", "arduino-cli").build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Missing required target board");
    }

    @Test
    @DisplayName("Opens the monitor on the resolved port at the configured baud rate")
    void shouldOpenTheMonitorOnTheResolvedPortAtTheConfiguredBaudRate() throws Exception {
        // Given
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

        // When
        mojo.execute();

        // Then
        assertThat(executor.invocations()).hasSize(2);
        Command monitorCommand = executor.invocations().get(1);
        assertThat(monitorCommand.isInteractive()).isTrue();
        assertThat(monitorCommand.arguments()).containsExactly("arduino-cli", "monitor", "--port",
                "/dev/cu.usbmodem1", "--config", "baudrate=115200");
    }

    @Test
    @DisplayName("With a syslog port, prints the board's messages and opens no serial port")
    void shouldPrintSyslogMessagesWithoutOpeningASerialPort() throws Exception {
        // Given
        int port = freeUdpPort();
        MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("syslog", String.valueOf(port))
                .build());
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        FakeExecutor executor = new FakeExecutor();
        mojo.useExecutor(executor);
        mojo.stopSyslogAfter(1);
        MonitorMojo listening = mojo;
        Thread goal = Thread.ofVirtual().start(() -> {
            try {
                listening.execute();
            } catch (MojoFailureException failed) {
                throw new IllegalStateException(failed);
            }
        });

        // When: resend until the goal has bound the port and received one datagram
        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] frame = "<134>1 - nano-rcx drive - - - travel started".getBytes(StandardCharsets.US_ASCII);
            while (goal.isAlive()) {
                sender.send(new DatagramPacket(frame, frame.length, InetAddress.getLoopbackAddress(), port));
                goal.join(Duration.ofMillis(50));
            }
        }

        // Then
        assertThat(log.infos()).contains("Listening for syslog on UDP port " + port + "; press Ctrl+C to stop");
        assertThat(log.infos()).anyMatch(line -> line.endsWith(" INFO      nano-rcx drive: travel started"));
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Rejects a syslog port outside 1 to 65535")
    void shouldRejectASyslogPortOutsideItsRange() throws Exception {
        // Given
        MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("syslog", "70000")
                .build());
        mojo.setLog(new RecordingLog());

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Invalid syslog port 70000");
    }

    @Test
    @DisplayName("Fails clearly when the syslog port is already taken")
    void shouldFailClearlyWhenTheSyslogPortIsTaken() throws Exception {
        try (DatagramSocket taken = new DatagramSocket(0)) {
            // Given
            MonitorMojo mojo = lookupEmptyMojo("monitor", new File("."));
            mojo = configureMojo(mojo, configuration()
                    .set("arduinoCli", "arduino-cli")
                    .set("syslog", String.valueOf(taken.getLocalPort()))
                    .build());
            mojo.setLog(new RecordingLog());

            // When & Then
            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoFailureException.class)
                    .hasMessageContaining("Cannot listen for syslog on UDP port " + taken.getLocalPort());
        }
    }

    private static int freeUdpPort() throws IOException {
        try (DatagramSocket probe = new DatagramSocket(0)) {
            return probe.getLocalPort();
        }
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
