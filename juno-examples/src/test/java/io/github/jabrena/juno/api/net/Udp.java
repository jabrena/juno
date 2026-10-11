package io.github.jabrena.juno.api.net;

/**
 * Test double shadowing the {@code juno} artifact's native {@code Udp}: a network with no other boards.
 * Sends succeed and go nowhere, and nothing is ever received.
 */
public final class Udp {
    public static final int ENDPOINT_SIZE = 5;
    public static final int PORT = 4;

    private Udp() {
    }

    public static boolean listen(int localPort) {
        return true;
    }

    public static int send(int[] address, int remotePort, byte[] payload, int length) {
        return length;
    }

    public static int broadcast(int remotePort, byte[] payload, int length) {
        return length;
    }

    public static int receive(byte[] payload, int capacity, int[] source) {
        return 0;
    }

    public static void stop() {
    }
}
