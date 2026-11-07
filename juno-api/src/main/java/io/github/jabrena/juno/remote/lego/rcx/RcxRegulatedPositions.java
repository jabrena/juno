package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The two sides' positions as the brick reports them and as the host counts them. The brick's positions are 16-bit
 * and wrap; this unwraps each new reading into a signed count since the previous one, so a tachometer keeps counting
 * past a wrap. Each side also has a zero, which a reset moves without touching the brick's own reference.
 *
 * <p>Sides are indexed {@link RcxRegulatedPair#LEFT} and {@link RcxRegulatedPair#RIGHT}. The arrays are allocated once.
 */
final class RcxRegulatedPositions {
    private static final int MAX_WORD = 0xFFFF;
    private static final int SIGN_BIT = 0x8000;

    private final int[] raw = new int[2];
    private final int[] unwrapped = new int[2];
    private final int[] zero = new int[2];

    /** Records a fresh 16-bit reading of {@code side}, unwrapping it into the count since the previous reading. */
    void record(int side, int position) {
        int change = (((position - raw[side]) & MAX_WORD) ^ SIGN_BIT) - SIGN_BIT;
        unwrapped[side] += change;
        raw[side] = position;
    }

    /** The 16-bit position {@code steps} from {@code side}'s last reading, as a target for the brick. */
    int target(int side, int steps) {
        return (raw[side] + steps) & MAX_WORD;
    }

    /** The signed steps {@code side} has moved since its zero. */
    int stepsFromZero(int side) {
        return unwrapped[side] - zero[side];
    }

    /** Moves {@code side}'s zero to where it is now; the brick's reference is kept. */
    void zeroHere(int side) {
        zero[side] = unwrapped[side];
    }

    /** Starts the host's counts from zero, on both sides, from the readings already taken: used when the session is armed. */
    void restartFromReadings() {
        for (int side = RcxRegulatedPair.LEFT; side <= RcxRegulatedPair.RIGHT; side++) {
            unwrapped[side] = 0;
            zero[side] = 0;
        }
    }
}
