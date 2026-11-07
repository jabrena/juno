package io.github.jabrena.juno.remote.lego.rcx;

/**
 * Converts between encoder-shaft degrees and the firmware's raw sensor steps for one sensor gearing: a step is one
 * count of the 16-step rotation sensor, scaled by {@code stepsPerMotorRevolution} for the motor's shaft. Every
 * conversion rounds to the nearest step, halves away from zero, and checks the range the 16-bit firmware can express.
 */
final class RcxRegulatedScale {
    private static final int DEGREES_PER_TURN = 360;
    private static final int MAX_WORD = 0xFFFF;
    /** A signed displacement of this many steps or more is ambiguous on the 16-bit ring and is rejected. */
    private static final int AMBIGUOUS_STEPS = 0x8000;

    private final int stepsPerMotorRevolution;

    RcxRegulatedScale(int stepsPerMotorRevolution) {
        if (stepsPerMotorRevolution < 1 || stepsPerMotorRevolution > MAX_WORD) {
            throw new IllegalArgumentException("sensor steps per motor revolution must be between 1 and 65535");
        }
        this.stepsPerMotorRevolution = stepsPerMotorRevolution;
    }

    /**
     * Converts a relative movement in encoder-shaft degrees to a signed step displacement. Rejects a non-zero movement
     * that rounds to zero, and one of 32768 steps or more, which the 16-bit ring cannot express.
     */
    int steps(int degrees) {
        long steps = roundedQuotient((long) degrees * stepsPerMotorRevolution, DEGREES_PER_TURN);
        if (degrees != 0 && steps == 0) {
            throw new IllegalArgumentException(degrees + " degrees is less than one sensor step");
        }
        if (steps >= AMBIGUOUS_STEPS || steps <= -AMBIGUOUS_STEPS) {
            throw new IllegalArgumentException(degrees + " degrees is 32768 sensor steps or more");
        }
        return (int) steps;
    }

    /** Converts a rate in encoder-shaft degrees per second to the raw sensor counts per second, which must fit one word. */
    int rateCounts(int degreesPerSecond) {
        if (degreesPerSecond < 1) {
            throw new IllegalArgumentException("rates must be positive");
        }
        long counts = roundedQuotient((long) degreesPerSecond * stepsPerMotorRevolution, DEGREES_PER_TURN);
        if (counts == 0 || counts > MAX_WORD) {
            throw new IllegalArgumentException(degreesPerSecond + " degrees per second does not fit the brick's profile");
        }
        return (int) counts;
    }

    /** Converts a signed step count to encoder-shaft degrees, rounded to the nearest degree. */
    int degrees(long steps) {
        return (int) roundedQuotient(steps * DEGREES_PER_TURN, stepsPerMotorRevolution);
    }

    /** Rounds {@code numerator / denominator} to the nearest integer, halves away from zero. */
    static long roundedQuotient(long numerator, long denominator) {
        long half = denominator / 2;
        return numerator >= 0 ? (numerator + half) / denominator : -((-numerator + half) / denominator);
    }
}
