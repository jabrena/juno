package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Map;

/**
 * The bounded {@code java.net.Socket} subset: one IPv4 TCP connection, its {@code OutputStream} and {@code InputStream}.
 *
 * <p>Deliberate differences from the JDK: the streams are the socket itself, a read waits for the whole requested length
 * (or until the socket timeout) rather than returning what is available, the default timeout is five seconds rather
 * than unlimited, and a failure or timeout raises {@code IOException}. Only one socket can be open at a time. The
 * input stream's {@code read} is the SD card's shared {@code InputStream.read}, which dispatches to the socket by handle.
 */
public final class SocketMethods {
    public static final String SOCKET = "java/net/Socket";
    public static final String OUTPUT_STREAM = "java/io/OutputStream";
    public static final String INPUT_STREAM = "java/io/InputStream";

    /** Every exception a socket call can raise, so a program that uses one has their class ids. */
    public static final List<String> EXCEPTIONS = List.of("java/io/IOException", "java/lang/IllegalArgumentException");

    static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef(SOCKET, "<init>", "(Ljava/net/InetAddress;I)V"), Intrinsic.SOCKET_NEW),
            Map.entry(new MethodRef(SOCKET, "getOutputStream", "()Ljava/io/OutputStream;"),
                    Intrinsic.SOCKET_GET_OUTPUT_STREAM),
            Map.entry(new MethodRef(SOCKET, "getInputStream", "()Ljava/io/InputStream;"),
                    Intrinsic.SOCKET_GET_INPUT_STREAM),
            Map.entry(new MethodRef(SOCKET, "setSoTimeout", "(I)V"), Intrinsic.SOCKET_SET_SO_TIMEOUT),
            Map.entry(new MethodRef(SOCKET, "close", "()V"), Intrinsic.SOCKET_CLOSE),
            Map.entry(new MethodRef(OUTPUT_STREAM, "write", "([BII)V"), Intrinsic.SOCKET_OUTPUT_STREAM_WRITE));

    private SocketMethods() {
    }

    /** Whether {@code intrinsic} can leave an exception pending: a failed connect, write or read. */
    public static boolean mayThrow(Intrinsic intrinsic) {
        return intrinsic.name().startsWith("SOCKET_");
    }
}
