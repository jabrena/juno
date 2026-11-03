package io.github.jabrena.juno.games.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * A game's one UDP socket with a packet for each direction. Each packet wraps a fixed buffer that the game's protocol
 * reads and writes, so polling and sending allocate nothing: the packets, their buffers and the broadcast address are
 * made once, when the socket opens.
 */
public final class GameSocket {
    private final DatagramSocket socket;
    private final int localPort;
    private final InetAddress broadcast;
    private final byte[] incoming;
    private final byte[] outgoing;
    private final DatagramPacket inbox;
    private final DatagramPacket outbox;

    private GameSocket(DatagramSocket socket, int localPort, InetAddress broadcast, int packetSize) {
        this.socket = socket;
        this.localPort = localPort;
        this.broadcast = broadcast;
        this.incoming = new byte[packetSize];
        this.outgoing = new byte[packetSize];
        this.inbox = new DatagramPacket(incoming, packetSize);
        this.outbox = new DatagramPacket(outgoing, packetSize);
    }

    /** Binds {@code localPort}; throws when another socket still holds it, so the caller can retry or choose another. */
    public static GameSocket open(int localPort, int packetSize) throws IOException {
        InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        return new GameSocket(new DatagramSocket(localPort), localPort, broadcast, packetSize);
    }

    /** The port this socket is bound to. */
    public int localPort() {
        return localPort;
    }

    /** The buffer the next received datagram fills. */
    public byte[] incoming() {
        return incoming;
    }

    /** The buffer a send reads from. */
    public byte[] outgoing() {
        return outgoing;
    }

    /** Polls once: the length of the datagram received into {@link #incoming()}, 0 when none waits, -1 on failure. */
    public int receive() {
        try {
            socket.receive(inbox);
            return inbox.getLength();
        } catch (IOException e) {
            return -1;
        }
    }

    /** Where the datagram from the last {@link #receive} came from, to answer it. */
    public InetAddress sender() {
        return inbox.getAddress();
    }

    /** The port the datagram from the last {@link #receive} came from. */
    public int senderPort() {
        return inbox.getPort();
    }

    /** Sends the first {@code length} bytes of {@link #outgoing()} to {@code to}; false when it could not be sent. */
    public boolean send(InetAddress to, int port, int length) {
        if (to == null) {
            return false;
        }
        outbox.setAddress(to);
        outbox.setPort(port);
        outbox.setLength(length);
        return sendOutbox();
    }

    /** Sends the first {@code length} bytes of {@link #outgoing()} to the local network's broadcast address. */
    public boolean broadcast(int port, int length) {
        outbox.setAddress(broadcast);
        outbox.setPort(port);
        outbox.setLength(length);
        return sendOutbox();
    }

    /** Closes the socket, so its port can be bound again. */
    public void close() {
        socket.close();
    }

    private boolean sendOutbox() {
        try {
            socket.send(outbox);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
