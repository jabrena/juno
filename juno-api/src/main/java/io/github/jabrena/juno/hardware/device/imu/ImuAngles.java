package io.github.jabrena.juno.hardware.device.imu;

/**
 * Integer tilt maths shared by the inertial drivers. The boards' single-precision libm has no {@code atan2}, so the
 * angle comes from a table of {@code atan(k / 16)} in milli-degrees with linear interpolation between entries, which
 * stays within about 0.1 degree and needs no floating-point state.
 */
final class ImuAngles {
    private static final int STEPS = 16;
    private static final int QUARTER_TURN = 90_000;
    private static final int HALF_TURN = 180_000;
    private static final int[] ATAN_TABLE = {
        0, 3576, 7125, 10620, 14036, 17354, 20556, 23629, 26565, 29358, 32005, 34509, 36870, 39094, 41186, 43152, 45000
    };

    private ImuAngles() {
    }

    /**
     * The angle of the vector {@code (x, y)} from the positive x axis in milli-degrees, {@code -180000..180000}, as
     * {@code atan2(y, x)} would return it. {@code 0} for the zero vector.
     */
    static int atan2Millidegrees(int y, int x) {
        int absY = Math.abs(y);
        int absX = Math.abs(x);
        int base = absY <= absX
                ? ratioAngle(absY, absX)
                : QUARTER_TURN - ratioAngle(absX, absY);
        if (x >= 0) {
            return y < 0 ? -base : base;
        }
        return y < 0 ? base - HALF_TURN : HALF_TURN - base;
    }

    /** The angle of {@code num / den} for {@code 0 <= num <= den}, in milli-degrees from {@code 0} to {@code 45000}. */
    private static int ratioAngle(int num, int den) {
        if (den == 0) {
            return 0;
        }
        int index = num * STEPS / den;
        if (index >= STEPS) {
            return ATAN_TABLE[STEPS];
        }
        int fraction = num * STEPS - index * den;
        return ATAN_TABLE[index] + (ATAN_TABLE[index + 1] - ATAN_TABLE[index]) * fraction / den;
    }

    /** {@code sqrt(a * a + b * b)} rounded down, by Newton's method on integers. */
    static int magnitude(int a, int b) {
        long sum = (long) a * a + (long) b * b;
        if (sum == 0) {
            return 0;
        }
        long root = sum;
        long next = (root + 1) / 2;
        // Halving from the sum reaches the root's range in about 16 steps; the loop ends once it stops shrinking.
        while (next < root) {
            root = next;
            next = (root + sum / root) / 2;
        }
        return (int) root;
    }
}
