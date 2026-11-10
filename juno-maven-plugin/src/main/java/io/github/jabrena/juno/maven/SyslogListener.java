package io.github.jabrena.juno.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Receives syslog datagrams on a bound UDP socket and writes one line per message: the arrival time, the severity, the
 * sender's host and app names, and the message. Boards send the nil timestamp, so the arrival time is the listener's.
 * A frame that is not RFC 5424 is written raw, with its sender's address, rather than dropped.
 */
final class SyslogListener {
    /** Larger than any frame a board sends; RFC 5426 receivers must accept at least 480 bytes, and should 2048. */
    private static final int MAX_DATAGRAM_BYTES = 2048;
    private static final DateTimeFormatter ARRIVAL = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String NIL = "-";

    private final DatagramSocket socket;
    private final Consumer<String> output;
    private final Clock clock;
    private final byte[] buffer = new byte[MAX_DATAGRAM_BYTES];

    SyslogListener(DatagramSocket socket, Consumer<String> output, Clock clock) {
        this.socket = socket;
        this.output = output;
        this.clock = clock;
    }

    /** Writes the messages of {@code count} datagrams, waiting as long as needed for each; a negative count never ends. */
    void listen(int count) {
        for (int received = 0; count < 0 || received < count; received++) {
            receive();
        }
    }

    /** Waits for one datagram and writes its message. */
    void receive() {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(packet);
        } catch (IOException failed) {
            throw new UncheckedIOException("Syslog receive failed on UDP port " + socket.getLocalPort(), failed);
        }
        String frame = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).stripTrailing();
        output.accept(line(frame, packet.getAddress()));
    }

    private String line(String frame, InetAddress sender) {
        String arrival = LocalTime.now(clock).format(ARRIVAL);
        return SyslogMessage.parse(frame)
                .map(message -> "%s %-9s %s %s: %s".formatted(arrival, message.severityName(),
                        NIL.equals(message.hostName()) ? sender.getHostAddress() : message.hostName(),
                        message.appName(), message.message()))
                .orElseGet(() -> "%s %-9s %s: %s".formatted(arrival, "RAW", sender.getHostAddress(), frame));
    }
}
