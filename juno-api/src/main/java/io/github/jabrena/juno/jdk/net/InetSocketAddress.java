package io.github.jabrena.juno.jdk.net;

/** Board-side port-only form of {@code java.net.InetSocketAddress} used when binding a server listener. */
public final class InetSocketAddress {
    private final int port;

    public InetSocketAddress(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
