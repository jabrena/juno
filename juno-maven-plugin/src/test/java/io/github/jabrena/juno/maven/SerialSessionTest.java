package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
