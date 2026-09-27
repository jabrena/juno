package io.github.jabrena.juno.api;

/**
 * Test double shadowing the {@code juno} artifact's native {@code Random} with a seeded
 * {@link java.util.Random}. Programs seed it from the (simulated, hence repeatable) clock, so runs
 * are deterministic; tests may also call {@link #seed} directly.
 */
public final class Random {
    private static java.util.Random random = new java.util.Random(0);

    private Random() {
    }

    public static void seed(int seed) {
        random = new java.util.Random(seed);
    }

    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public static int nextInt(int origin, int bound) {
        return random.nextInt(origin, bound);
    }
}
