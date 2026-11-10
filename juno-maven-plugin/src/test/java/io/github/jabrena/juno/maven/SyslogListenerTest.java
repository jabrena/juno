package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SyslogListener} on a real loopback socket, with a fixed clock for the arrival time. */
@DisplayName("Syslog listener")
class SyslogListenerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:04:07.218Z"), ZoneOffset.UTC);

    private DatagramSocket socket;
    private DatagramSocket sender;
    private final List<String> lines = new ArrayList<>();

    @BeforeEach
    void openSockets() throws IOException {
        socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(2000);
        sender = new DatagramSocket();
    }

    @AfterEach
    void closeSockets() {
        socket.close();
        sender.close();
    }

    @Test
    @DisplayName("Writes one line per message with the arrival time, severity, host and app")
    void shouldWriteOneLinePerMessage() throws IOException {
        // Given
        send("<134>1 - nano-rcx drive - - - t=200 left=3 right=-3\n");
        send("<132>1 - nano-rcx drive - - - travel ended");

        // When
        new SyslogListener(socket, lines::add, CLOCK).listen(2);

        // Then
        assertThat(lines).containsExactly(
                "12:04:07.218 INFO      nano-rcx drive: t=200 left=3 right=-3",
                "12:04:07.218 WARNING   nano-rcx drive: travel ended");
    }

    @Test
    @DisplayName("Names the sender by address when the frame's host name is nil")
    void shouldNameTheSenderByAddressWhenTheHostNameIsNil() throws IOException {
        // Given
        send("<131>1 - - drive - - - stalled");

        // When
        new SyslogListener(socket, lines::add, CLOCK).receive();

        // Then
        assertThat(lines).containsExactly("12:04:07.218 ERROR     127.0.0.1 drive: stalled");
    }

    @Test
    @DisplayName("Writes a frame that is not RFC 5424 raw instead of dropping it")
    void shouldWriteAFrameThatIsNotRfc5424Raw() throws IOException {
        // Given
        send("hello from a board");

        // When
        new SyslogListener(socket, lines::add, CLOCK).receive();

        // Then
        assertThat(lines).containsExactly("12:04:07.218 RAW       127.0.0.1: hello from a board");
    }

    @Test
    @DisplayName("Reports a receive failure with the port")
    void shouldReportAReceiveFailureWithThePort() {
        // Given
        SyslogListener listener = new SyslogListener(socket, lines::add, CLOCK);

        // When & Then: nothing is sent, so the receive times out
        assertThatThrownBy(listener::receive)
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("UDP port " + socket.getLocalPort());
    }

    private void send(String frame) throws IOException {
        byte[] data = frame.getBytes(StandardCharsets.UTF_8);
        sender.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), socket.getLocalPort()));
    }
}
