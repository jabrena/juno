package io.github.jabrena.juno.hardware.net.udp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link Syslog} on the JVM, sending real datagrams to a socket on the loopback interface. */
@DisplayName("Syslog")
class SyslogTest {
    private static final int RECEIVE_TIMEOUT_MILLIS = 2000;

    private DatagramSocket receiver;
    private DatagramSocket sender;

    @BeforeEach
    void openSockets() throws IOException {
        receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        receiver.setSoTimeout(RECEIVE_TIMEOUT_MILLIS);
        sender = new DatagramSocket();
    }

    @AfterEach
    void closeSockets() {
        receiver.close();
        sender.close();
    }

    @Test
    @DisplayName("Sends a whole line as one RFC 5424 frame with facility local0")
    void shouldSendAWholeLineAsOneRfc5424Frame() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[128]);

        // When
        boolean sent = syslog.info("travel started");

        // Then
        assertThat(sent).isTrue();
        assertThat(receive()).isEqualTo("<134>1 - nano-rcx drive - - - travel started");
    }

    @Test
    @DisplayName("Encodes each severity in the priority")
    void shouldEncodeEachSeverityInThePriority() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[128]);

        // When
        syslog.error("e");
        syslog.warning("w");
        syslog.notice("n");
        syslog.debug("d");
        syslog.log(Syslog.EMERGENCY, "x");

        // Then
        assertThat(receive()).startsWith("<131>1 ");
        assertThat(receive()).startsWith("<132>1 ");
        assertThat(receive()).startsWith("<133>1 ");
        assertThat(receive()).startsWith("<135>1 ");
        assertThat(receive()).startsWith("<128>1 ");
    }

    @Test
    @DisplayName("Builds a trace line from text and numbers, including zero, negatives and the int extremes")
    void shouldBuildATraceLineFromTextAndNumbers() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[128]);

        // When
        syslog.begin(Syslog.INFO).text("t=").number(0).text(" left=").number(-31).text(" right=").number(699)
                .text(" min=").number(Integer.MIN_VALUE).text(" max=").number(Integer.MAX_VALUE).send();

        // Then
        assertThat(receive()).isEqualTo(
                "<134>1 - nano-rcx drive - - - t=0 left=-31 right=699 min=-2147483648 max=2147483647");
    }

    @Test
    @DisplayName("Starts every frame afresh, so a message never carries the previous one's tail")
    void shouldStartEveryFrameAfresh() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[128]);
        syslog.info("a much longer first message");

        // When
        syslog.info("short");

        // Then
        receive();
        assertThat(receive()).isEqualTo("<134>1 - nano-rcx drive - - - short");
    }

    @Test
    @DisplayName("Truncates a message at the end of the buffer")
    void shouldTruncateAMessageAtTheEndOfTheBuffer() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[Syslog.MINIMUM_BUFFER_LENGTH]);

        // When
        syslog.info("this message is far longer than the buffer");

        // Then
        String frame = receive();
        assertThat(frame).hasSize(Syslog.MINIMUM_BUFFER_LENGTH).isEqualTo("<134>1 - nano-rcx drive - - - th");
    }

    @Test
    @DisplayName("Sends characters outside printable ASCII as question marks")
    void shouldSendCharactersOutsidePrintableAsciiAsQuestionMarks() throws IOException {
        // Given
        Syslog syslog = syslog(new byte[128]);

        // When
        syslog.info("90°\tturn");

        // Then
        assertThat(receive()).endsWith(" - - - 90??turn");
    }

    @Test
    @DisplayName("Counts a failed send instead of throwing")
    void shouldCountAFailedSendInsteadOfThrowing() {
        // Given
        Syslog syslog = syslog(new byte[128]);
        sender.close();

        // When
        boolean sent = syslog.info("lost");

        // Then
        assertThat(sent).isFalse();
        assertThat(syslog.failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("Rejects a port, a buffer, a name or a severity outside its range")
    void shouldRejectArgumentsOutsideTheirRange() {
        InetAddress loopback = InetAddress.getLoopbackAddress();

        assertThatThrownBy(() -> new Syslog(sender, loopback, 0, "h", "a", new byte[64]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("port");
        assertThatThrownBy(() -> new Syslog(sender, loopback, 5514, "h", "a", new byte[31]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("buffer");
        assertThatThrownBy(() -> new Syslog(sender, loopback, 5514, "my host", "a", new byte[64]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("printable");
        assertThatThrownBy(() -> new Syslog(sender, loopback, 5514, "h", "", new byte[64]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("characters");
        assertThatThrownBy(() -> new Syslog(sender, loopback, 5514, "h", "a".repeat(49), new byte[64]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("characters");
        assertThatThrownBy(() -> syslog(new byte[64]).begin(8))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("severity");
    }

    private Syslog syslog(byte[] buffer) {
        return new Syslog(sender, InetAddress.getLoopbackAddress(), receiver.getLocalPort(), "nano-rcx", "drive",
                buffer);
    }

    private String receive() throws IOException {
        byte[] data = new byte[512];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        receiver.receive(packet);
        return new String(data, 0, packet.getLength(), StandardCharsets.US_ASCII);
    }
}
