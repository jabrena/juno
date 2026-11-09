package io.github.jabrena.juno.hardware.net.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Sends log lines as RFC 5424 syslog datagrams over UDP (RFC 5426), so a board can be followed over Wi-Fi without a USB
 * cable; {@code juno:monitor -Djuno.syslog=<port>} receives them. Each message is one datagram:
 * {@code <PRI>1 - HOSTNAME APP-NAME - - - MSG}, with facility {@code local0} and the nil timestamp, since a board need
 * not know the time; the receiver stamps the arrival.
 *
 * <p>Create it once, at startup, and reuse it: it formats every message into the caller-owned buffer and sends it with
 * one {@link DatagramPacket}, so logging in a loop allocates nothing. A whole line is {@link #info(String)} and its
 * siblings; a line built from fields uses {@link #begin(int)}, {@link #text(String)}, {@link #number(int)} and
 * {@link #send()}, without {@code String} concatenation:
 *
 * <pre>{@code
 * syslog.begin(Syslog.INFO).text("left=").number(left).text(" right=").number(right).send();
 * }</pre>
 *
 * <p>It sends on a {@link DatagramSocket} the program owns, because a program has a single UDP socket on the board: a
 * second socket would replace the first. The destination may be a computer's address or the broadcast address
 * {@code 255.255.255.255}, which reaches every monitor on the local network without configuring one. A message longer
 * than the buffer is truncated, and a failed send is counted in {@link #failures()} rather than thrown, so logging
 * never breaks the program's control loop. Characters outside printable ASCII are sent as {@code ?}.
 */
public final class Syslog {
    /** The UDP port {@code juno:monitor} listens on by default; the standard 514 needs root on most computers. */
    public static final int DEFAULT_PORT = 5514;

    /** System is unusable. */
    public static final int EMERGENCY = 0;
    /** Action must be taken immediately. */
    public static final int ALERT = 1;
    /** Critical condition. */
    public static final int CRITICAL = 2;
    /** Error condition. */
    public static final int ERROR = 3;
    /** Warning condition. */
    public static final int WARNING = 4;
    /** Normal but significant condition. */
    public static final int NOTICE = 5;
    /** Informational message. */
    public static final int INFO = 6;
    /** Debug-level message. */
    public static final int DEBUG = 7;

    /** The smallest buffer that holds a frame's header with one-character names and a few characters of message. */
    public static final int MINIMUM_BUFFER_LENGTH = 32;

    private static final int FACILITY_LOCAL0 = 16;
    private static final int MAX_PORT = 65535;
    private static final int MAX_HOST_NAME_LENGTH = 255;
    private static final int MAX_APP_NAME_LENGTH = 48;
    private static final int FIRST_PRINTABLE = 33;
    private static final int LAST_PRINTABLE = 126;

    private final DatagramSocket socket;
    private final DatagramPacket packet;
    private final byte[] buffer;
    private final String hostName;
    private final String appName;
    private int length;
    private int failures;

    /**
     * A logger sending to {@code destination:port} on {@code socket}. {@code hostName} and {@code appName} identify
     * the sender in every frame: printable ASCII without spaces, at most 255 and 48 characters. {@code buffer} holds one
     * frame and sets the longest message; 128 bytes suits a short trace line.
     */
    public Syslog(DatagramSocket socket, InetAddress destination, int port, String hostName, String appName,
            byte[] buffer) {
        if (port < 1 || port > MAX_PORT) {
            throw new IllegalArgumentException("syslog port must be 1 to 65535");
        }
        if (buffer.length < MINIMUM_BUFFER_LENGTH) {
            throw new IllegalArgumentException("syslog buffer must hold at least 32 bytes");
        }
        requireName(hostName, MAX_HOST_NAME_LENGTH);
        requireName(appName, MAX_APP_NAME_LENGTH);
        this.socket = socket;
        this.buffer = buffer;
        this.hostName = hostName;
        this.appName = appName;
        this.packet = new DatagramPacket(buffer, buffer.length, destination, port);
    }

    /** Sends {@code message} at {@link #ERROR}; returns whether the datagram left the board. */
    public boolean error(String message) {
        return log(ERROR, message);
    }

    /** Sends {@code message} at {@link #WARNING}; returns whether the datagram left the board. */
    public boolean warning(String message) {
        return log(WARNING, message);
    }

    /** Sends {@code message} at {@link #NOTICE}; returns whether the datagram left the board. */
    public boolean notice(String message) {
        return log(NOTICE, message);
    }

    /** Sends {@code message} at {@link #INFO}; returns whether the datagram left the board. */
    public boolean info(String message) {
        return log(INFO, message);
    }

    /** Sends {@code message} at {@link #DEBUG}; returns whether the datagram left the board. */
    public boolean debug(String message) {
        return log(DEBUG, message);
    }

    /** Sends {@code message} at {@code severity}, {@link #EMERGENCY} to {@link #DEBUG}. */
    public boolean log(int severity, String message) {
        return begin(severity).text(message).send();
    }

    /** Starts a new frame at {@code severity}, discarding any unsent one; continue with {@link #text} and {@link #number}. */
    public Syslog begin(int severity) {
        if (severity < EMERGENCY || severity > DEBUG) {
            throw new IllegalArgumentException("syslog severity must be 0 to 7");
        }
        length = 0;
        put('<');
        number(FACILITY_LOCAL0 * 8 + severity);
        text(">1 - ");
        text(hostName);
        put(' ');
        text(appName);
        text(" - - - ");
        return this;
    }

    /** Appends {@code value} to the frame begun by {@link #begin(int)}, truncating at the end of the buffer. */
    public Syslog text(String value) {
        int count = value.length();
        for (int i = 0; i < count; i++) {
            char character = value.charAt(i);
            put(character >= ' ' && character <= LAST_PRINTABLE ? character : '?');
        }
        return this;
    }

    /** Appends {@code value} in decimal, with a leading {@code -} when negative. */
    public Syslog number(int value) {
        if (value < 0) {
            put('-');
        }
        int divisor = 1;
        // Counts down from the leading digit; working on the negative value keeps Integer.MIN_VALUE exact.
        int negative = value < 0 ? value : -value;
        while (negative / divisor <= -10) {
            divisor = divisor * 10;
        }
        while (divisor > 0) {
            put((char) ('0' - (negative / divisor) % 10));
            divisor = divisor / 10;
        }
        return this;
    }

    /** Sends the frame begun by {@link #begin(int)}; a failure is counted in {@link #failures()} instead of thrown. */
    public boolean send() {
        packet.setLength(length);
        try {
            socket.send(packet);
            return true;
        } catch (IOException failed) {
            failures++;
            return false;
        }
    }

    /** How many sends have failed since this logger was created. */
    public int failures() {
        return failures;
    }

    private void put(char character) {
        if (length < buffer.length) {
            buffer[length] = (byte) character;
            length++;
        }
    }

    private static void requireName(String name, int maxLength) {
        int count = name.length();
        if (count == 0 || count > maxLength) {
            throw new IllegalArgumentException("syslog host and app names must be 1 to 255 and 1 to 48 characters");
        }
        for (int i = 0; i < count; i++) {
            char character = name.charAt(i);
            if (character < FIRST_PRINTABLE || character > LAST_PRINTABLE) {
                throw new IllegalArgumentException("syslog host and app names must be printable ASCII without spaces");
            }
        }
    }
}
