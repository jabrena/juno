package io.github.jabrena.juno.api;

/** Pseudorandom-number operations backed by Arduino's random generator. */
public final class Random {
    private Random() {
    }

    /** Initializes the generator's sequence. The same seed produces the same sequence. */
    public static native void seed(int seed);

    /**
     * Returns a pseudorandom value from zero (inclusive) to {@code bound} (exclusive).
     *
     * @param bound exclusive upper bound; must be positive
     * @return a value in {@code [0, bound)}
     */
    public static native int nextInt(int bound);

    /**
     * Returns a pseudorandom value from {@code origin} (inclusive) to {@code bound} (exclusive).
     *
     * @param origin inclusive lower bound
     * @param bound exclusive upper bound; must be greater than {@code origin}
     * @return a value in {@code [origin, bound)}
     */
    public static native int nextInt(int origin, int bound);
}
