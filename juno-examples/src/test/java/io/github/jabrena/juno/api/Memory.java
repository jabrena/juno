package io.github.jabrena.juno.api;

/**
 * Test double for Juno's arena introspection, shadowing the {@code juno} artifact's native {@code Memory} on the
 * test classpath (test classes come first). The JVM has no Juno arena: it reports an empty one of the default size,
 * or of {@code -Djuno.emulator.arenaBytes=<bytes>} to play a program built with a bigger {@code juno.Xmx}.
 */
public final class Memory {
    private Memory() {
    }

    public static int arenaUsedBytes() {
        return 0;
    }

    public static int arenaCapacityBytes() {
        return Integer.getInteger("juno.emulator.arenaBytes", 8192);
    }
}
