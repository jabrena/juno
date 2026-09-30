package io.github.jabrena.juno.api.io.net;

/**
 * Allocation-free UDP transport recognized as compiler intrinsics by Juno.
 *
 * <p>One socket is available to a Juno program. Call {@link #listen(int)} once after Wi-Fi is
 * connected, then use {@link #broadcast} as the discovery primitive, {@link #send} to produce a
 * datagram for a known peer, and {@link #receive} to consume the next datagram without blocking.
 * The same API is lowered to {@code WiFiUDP} on UNO R4 WiFi and {@code BridgeUDP} on UNO Q.
 *
 * <p>An endpoint is a caller-owned {@code int[5]} containing the four IPv4 octets followed by the
 * UDP port. Payloads are caller-owned byte arrays; the runtime never allocates or retains them.
 * UDP is unreliable and unordered, so applications that require delivery must add sequence
 * numbers, acknowledgements, or retries to their protocol.
 */
public final class Udp {
    /** Number of integer entries in an endpoint array. */
    public static final int ENDPOINT_SIZE = 5;
    /** Index of the remote port in an endpoint array. */
    public static final int PORT = 4;

    private Udp() {
    }

    /**
     * Binds the program's UDP socket to {@code localPort}. Calling this again closes the previous
     * socket first. Returns {@code true} when the socket was opened.
     */
    public static native boolean listen(int localPort);

    /**
     * Sends {@code length} bytes from {@code payload} to the IPv4 address and {@code remotePort}.
     * {@code address} must contain at least four octets. Returns the payload length on success or
     * {@code -1} when the socket is not listening or the packet could not be sent.
     */
    public static native int send(int[] address, int remotePort, byte[] payload, int length);

    /**
     * Sends {@code length} bytes to the link-local IPv4 all-hosts group at {@code remotePort}.
     * This portable group broadcast is the minimum building block for LAN discovery and remains
     * within the local network. Returns the payload length on success or {@code -1} on failure.
     */
    public static native int broadcast(int remotePort, byte[] payload, int length);

    /**
     * Non-blockingly consumes the next datagram. At most {@code capacity} bytes are copied into
     * {@code payload}; excess bytes in that datagram are discarded. {@code capacity} must be
     * positive. On success, {@code source} receives the sender's four IPv4 octets and port.
     * Returns the copied byte count, {@code 0} when no packet is waiting, or {@code -1} for
     * invalid state/input (including non-positive {@code capacity}) or a read failure.
     */
    public static native int receive(byte[] payload, int capacity, int[] source);

    /** Closes the socket. */
    public static native void stop();
}
