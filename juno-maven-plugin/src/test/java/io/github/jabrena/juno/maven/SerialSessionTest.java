package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerialSessionTest {

    @Test
    @DisplayName("board lines come back in order, with CR and empty lines dropped")
    void readsLinesInOrder() {
        byte[] reply = "READY\r\n\nOK wifi.ssid\nEND\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        try (SerialSession session = new SerialSession(new ByteArrayInputStream(reply), sent)) {
            assertThat(session.nextLine(Duration.ofSeconds(2))).isEqualTo("READY");
            assertThat(session.nextLine(Duration.ofSeconds(2))).isEqualTo("OK wifi.ssid");
            assertThat(session.nextLine(Duration.ofSeconds(2))).isEqualTo("END");
        }
    }

    @Test
    @DisplayName("a request is written as one line with a line feed")
    void writesOneLine() {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        try (SerialSession session = new SerialSession(new ByteArrayInputStream(new byte[0]), sent)) {
            session.send(SecretProtocol.listRequest());
        }

        assertThat(sent.toString(StandardCharsets.US_ASCII)).isEqualTo("LIST\n");
    }

    @Test
    @DisplayName("a port that cannot be configured yet is retried, as after a Pico 2 W reboots from BOOTSEL")
    void retriesConfigureUntilThePortIsBack() throws Exception {
        // Given: stty fails once, as it does before the port re-enumerates, then succeeds
        Path port = Files.createTempFile("juno-serial", ".port");
        FakeExecutor executor = new FakeExecutor().respond(1, "No such file or directory").respond(0, "");

        // When
        try (SerialSession session = SerialSession.open(port, executor, Duration.ofSeconds(5))) {
            // Then
            assertThat(session).isNotNull();
            assertThat(executor.invocations()).hasSize(2);
        } finally {
            Files.deleteIfExists(port);
        }
    }

    @Test
    @DisplayName("a port that never comes back fails once the timeout passes, saying why")
    void failsWhenThePortNeverComesBack() {
        // Given
        Path port = Path.of("target/no-such-serial-port");
        FakeExecutor executor = new FakeExecutor();
        for (int i = 0; i < 20; i++) {
            executor.respond(1, "No such file or directory");
        }

        // When & Then
        assertThatThrownBy(() -> SerialSession.open(port, executor, Duration.ofMillis(300)))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("Could not open " + port + " for the secret session")
                .hasMessageContaining("stty");
    }
}
