package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Map;

/**
 * The bounded {@code java.net} datagram subset: {@code InetAddress.getByAddress(byte[])} for IPv4, {@code DatagramPacket}
 * over a caller-owned buffer, and {@code DatagramSocket} on the program's single UDP socket. Programs use the JDK
 * classes unchanged; the compiler lowers them onto the same shims as the UDP transport, so a board runs the same code.
 *
 * <p>Deliberate differences from the JDK: the packet's capacity is the length its constructor is given, the address
 * object a receive fills is the packet's own, a second socket replaces the first, and failures raise
 * {@code IOException} (a timeout too) rather than the JDK's more specific subclasses.
 */
public final class DatagramMethods {
    public static final String INET_ADDRESS = "java/net/InetAddress";
    public static final String DATAGRAM_PACKET = "java/net/DatagramPacket";
    public static final String DATAGRAM_SOCKET = "java/net/DatagramSocket";

    /** Every exception a datagram call can raise, so a program that uses one has their class ids. */
    public static final List<String> EXCEPTIONS = List.of("java/io/IOException", "java/lang/IllegalArgumentException");

    static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef(INET_ADDRESS, "getByAddress", "([B)Ljava/net/InetAddress;"),
                    Intrinsic.INET_ADDRESS_GET_BY_ADDRESS),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "<init>", "([BI)V"), Intrinsic.DATAGRAM_PACKET_NEW_RECEIVE),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "<init>", "([BILjava/net/InetAddress;I)V"),
                    Intrinsic.DATAGRAM_PACKET_NEW_SEND),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "getData", "()[B"), Intrinsic.DATAGRAM_PACKET_GET_DATA),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "getLength", "()I"), Intrinsic.DATAGRAM_PACKET_GET_LENGTH),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "setLength", "(I)V"), Intrinsic.DATAGRAM_PACKET_SET_LENGTH),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "getAddress", "()Ljava/net/InetAddress;"),
                    Intrinsic.DATAGRAM_PACKET_GET_ADDRESS),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "setAddress", "(Ljava/net/InetAddress;)V"),
                    Intrinsic.DATAGRAM_PACKET_SET_ADDRESS),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "getPort", "()I"), Intrinsic.DATAGRAM_PACKET_GET_PORT),
            Map.entry(new MethodRef(DATAGRAM_PACKET, "setPort", "(I)V"), Intrinsic.DATAGRAM_PACKET_SET_PORT),
            Map.entry(new MethodRef(DATAGRAM_SOCKET, "<init>", "(I)V"), Intrinsic.DATAGRAM_SOCKET_NEW),
            Map.entry(new MethodRef(DATAGRAM_SOCKET, "send", "(Ljava/net/DatagramPacket;)V"),
                    Intrinsic.DATAGRAM_SOCKET_SEND),
            Map.entry(new MethodRef(DATAGRAM_SOCKET, "receive", "(Ljava/net/DatagramPacket;)V"),
                    Intrinsic.DATAGRAM_SOCKET_RECEIVE),
            Map.entry(new MethodRef(DATAGRAM_SOCKET, "setSoTimeout", "(I)V"),
                    Intrinsic.DATAGRAM_SOCKET_SET_SO_TIMEOUT),
            Map.entry(new MethodRef(DATAGRAM_SOCKET, "close", "()V"), Intrinsic.DATAGRAM_SOCKET_CLOSE));

    private DatagramMethods() {
    }

    /** Whether {@code intrinsic} can leave an exception pending: a failed send, receive, timeout or bad argument. */
    public static boolean mayThrow(Intrinsic intrinsic) {
        return intrinsic.name().startsWith("DATAGRAM_") || intrinsic.name().startsWith("INET_ADDRESS_");
    }
}
