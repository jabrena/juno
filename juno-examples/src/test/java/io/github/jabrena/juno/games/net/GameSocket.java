package io.github.jabrena.juno.games.net;

import java.net.InetAddress;

/**
 * Test double shadowing the game's {@code GameSocket}: a network with no other boards. It binds no port, so every
 * emulated run can open its own socket without colliding with another one still open in the same JVM. Sends succeed
 * and go nowhere, and nothing is ever received.
 */
public final class GameSocket {
    private final int localPort;
    private final byte[] incoming;
    private final byte[] outgoing;

    private GameSocket(int localPort, int packetSize) {
        this.localPort = localPort;
        this.incoming = new byte[packetSize];
        this.outgoing = new byte[packetSize];
    }

    public static GameSocket open(int localPort, int packetSize) {
        return new GameSocket(localPort, packetSize);
    }

    public int localPort() {
        return localPort;
    }

    public byte[] incoming() {
        return incoming;
    }

    public byte[] outgoing() {
        return outgoing;
    }

    public int receive() {
        return 0;
    }

    public InetAddress sender() {
        return null;
    }

    public int senderPort() {
        return 0;
    }

    public boolean send(InetAddress to, int port, int length) {
        return to != null;
    }

    public boolean broadcast(int port, int length) {
        return true;
    }

    public void close() {
    }
}
