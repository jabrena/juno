package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.RegulatedPair;

/**
 * Drives a two-wheel robot by distance and heading, with its wheels regulated by a {@link RegulatedPair} (such as an
 * RCX pair). Millimetre travel and in-place turns are converted to per-side encoder targets using {@link DriveGeometry}
 * and submitted as one coordinated movement, so the two wheels start together.
 *
 * <p>Conversion uses only the geometry's calibrated values: each side's encoder degrees per wheel revolution, the wheel
 * diameter and the effective track width. Positive travel is forward and a positive turn is counter-clockwise, so the
 * left wheel turns backwards and the right forwards in a positive turn. Straight travel uses the same millimetres for
 * both wheels, but each side's encoder ratio decides its target, so a robot whose sides are calibrated differently
 * still covers the same distance on each wheel. Create it once at startup.
 *
 * <p>Left and right are the robot's, seen from behind as it drives forward, and the geometry's encoder ratios are the
 * physical left and right wheels'. A build whose pair has its left side on the robot's right wheel is created with
 * {@code sidesSwapped}, so a counter-clockwise turn still turns counter-clockwise.
 */
public final class RegulatedDifferentialDrive {
    /** Scale of {@link #PI_MILLION}: π × 10⁶ rounded to an integer. */
    private static final long PI_MILLION = 3_141_593L;
    private static final long SCALE = 1_000_000L;
    /**
     * The largest magnitude any input may have, so that every product in the conversion fits a {@code long}: a distance
     * of a million millimetres, a turn of 1000 turns, and geometry values of a million.
     */
    private static final int MAX_MILLIMETRES = 1_000_000;
    private static final int MAX_ANGLE_DEGREES = 360_000;
    private static final int MAX_GEOMETRY = 1_000_000;

    private final RegulatedPair pair;
    private final DriveGeometry geometry;
    private final boolean sidesSwapped;

    /**
     * Creates the drive over {@code pair} with {@code geometry}.
     *
     * @throws IllegalArgumentException if a geometry value is too large for the conversion's arithmetic
     */
    public RegulatedDifferentialDrive(RegulatedPair pair, DriveGeometry geometry) {
        this(pair, geometry, false);
    }

    /**
     * Creates the drive over {@code pair} with {@code geometry}; with {@code sidesSwapped}, the pair's left side drives
     * the robot's right wheel and its right side the left wheel.
     *
     * @throws IllegalArgumentException if a geometry value is too large for the conversion's arithmetic
     */
    public RegulatedDifferentialDrive(RegulatedPair pair, DriveGeometry geometry, boolean sidesSwapped) {
        if (geometry.wheelDiameterMillimetres() > MAX_GEOMETRY
                || geometry.trackWidthMillimetres() > MAX_GEOMETRY
                || geometry.leftEncoderDegreesPerWheelRevolution() > MAX_GEOMETRY
                || geometry.rightEncoderDegreesPerWheelRevolution() > MAX_GEOMETRY) {
            throw new IllegalArgumentException("geometry values must be at most one million");
        }
        this.pair = pair;
        this.geometry = geometry;
        this.sidesSwapped = sidesSwapped;
    }

    /**
     * Sets the forward speed, in millimetres per second, by the left wheel's encoder ratio; the pair applies one speed
     * to both sides, so asymmetric gearing changes only the timing of the two sides, not where they stop.
     */
    public void setLinearSpeedMillimetresPerSecond(int millimetresPerSecond) {
        if (millimetresPerSecond <= 0 || millimetresPerSecond > MAX_MILLIMETRES) {
            throw new IllegalArgumentException("speed must be between 1 and one million mm/s");
        }
        int degrees = wheelDegrees(millimetresPerSecond, geometry.leftEncoderDegreesPerWheelRevolution());
        if (degrees == 0) {
            throw new IllegalArgumentException("speed is too low to represent in encoder degrees");
        }
        pair.setSpeed(degrees);
    }

    /** Drives straight forward (or backward, when negative) by {@code millimetres}, and waits until it completes. */
    public void travelMillimetres(int millimetres) {
        travelMillimetres(millimetres, false);
    }

    /** Drives straight by {@code millimetres}, returning at once when {@code immediateReturn} is set. */
    public void travelMillimetres(int millimetres, boolean immediateReturn) {
        checkMagnitude(millimetres, MAX_MILLIMETRES);
        int left = wheelDegrees(millimetres, geometry.leftEncoderDegreesPerWheelRevolution());
        int right = wheelDegrees(millimetres, geometry.rightEncoderDegreesPerWheelRevolution());
        requireRepresentable(millimetres != 0, left, right);
        rotate(left, right, immediateReturn);
    }

    /** Turns in place by {@code degrees} (positive is counter-clockwise), and waits until it completes. */
    public void turnDegrees(int degrees) {
        turnDegrees(degrees, false);
    }

    /** Turns in place by {@code degrees}, returning at once when {@code immediateReturn} is set. */
    public void turnDegrees(int degrees, boolean immediateReturn) {
        checkMagnitude(degrees, MAX_ANGLE_DEGREES);
        int left = -turnWheelDegrees(degrees, geometry.leftEncoderDegreesPerWheelRevolution());
        int right = turnWheelDegrees(degrees, geometry.rightEncoderDegreesPerWheelRevolution());
        requireRepresentable(degrees != 0, left, right);
        rotate(left, right, immediateReturn);
    }

    /** Turns about one wheel by {@code degrees} (positive is counter-clockwise), and waits until it completes. */
    public void pivotDegrees(int degrees) {
        pivotDegrees(degrees, false);
    }

    /**
     * Turns about one wheel by {@code degrees}, positive counter-clockwise, returning at once when
     * {@code immediateReturn} is set: the outer wheel, the right one for a counter-clockwise pivot, moves forward along
     * an arc of the whole track width while the other stays. A pivot moves one wheel only, so its finest turn is one
     * sensor step of one wheel, half an in-place turn's: a small heading correction.
     *
     * @throws IllegalArgumentException if the pivot is outside the supported range or quantizes to zero degrees
     */
    public void pivotDegrees(int degrees, boolean immediateReturn) {
        checkMagnitude(degrees, MAX_ANGLE_DEGREES);
        if (degrees > 0) {
            int right = 2 * turnWheelDegrees(degrees, geometry.rightEncoderDegreesPerWheelRevolution());
            requireRepresentable(true, right, right);
            rotate(0, right, immediateReturn);
        } else if (degrees < 0) {
            int left = 2 * turnWheelDegrees(-degrees, geometry.leftEncoderDegreesPerWheelRevolution());
            requireRepresentable(true, left, left);
            rotate(left, 0, immediateReturn);
        }
    }

    /** Stops both wheels. */
    public void stop() {
        pair.stop();
    }

    /** Returns whether a movement is in progress. */
    public boolean isMoving() {
        return pair.isMoving();
    }

    /** Waits until the current movement completes or reports an error state. */
    public void waitComplete() {
        pair.waitComplete();
    }

    /** Moves the robot's left and right wheels by their degrees, on the pair's sides that drive them. */
    private void rotate(int leftWheel, int rightWheel, boolean immediateReturn) {
        if (sidesSwapped) {
            pair.rotate(rightWheel, leftWheel, immediateReturn);
        } else {
            pair.rotate(leftWheel, rightWheel, immediateReturn);
        }
    }

    /**
     * Encoder degrees for {@code millimetres} of travel on a wheel: {@code mm × degreesPerRevolution / (π × D)}, rounded
     * to the nearest degree, with the sign of the travel.
     */
    private int wheelDegrees(int millimetres, int degreesPerRevolution) {
        long numerator = (long) millimetres * degreesPerRevolution * SCALE;
        return (int) roundedQuotient(numerator, geometry.wheelDiameterMillimetres() * PI_MILLION);
    }

    /**
     * The signed encoder degrees one wheel travels in an in-place turn of {@code degrees}, counter-clockwise positive:
     * the arc each wheel covers is {@code W × angle / 360} of its half track, so
     * {@code degrees = W × angle × degreesPerRevolution / (360 × D)}, rounded to the nearest degree.
     */
    private int turnWheelDegrees(int degrees, int degreesPerRevolution) {
        long numerator = (long) geometry.trackWidthMillimetres() * degrees * degreesPerRevolution;
        return (int) roundedQuotient(numerator, 360L * geometry.wheelDiameterMillimetres());
    }

    private static void checkMagnitude(int value, int limit) {
        if (value > limit || value < -limit) {
            throw new IllegalArgumentException("a movement is outside the supported range");
        }
    }

    private static void requireRepresentable(boolean moving, int left, int right) {
        if (moving && (left == 0 || right == 0)) {
            throw new IllegalArgumentException("a movement quantizes to zero encoder degrees on a side");
        }
    }

    /** Rounds {@code numerator / denominator} to the nearest integer, halves away from zero. */
    static long roundedQuotient(long numerator, long denominator) {
        long half = denominator / 2;
        return numerator >= 0 ? (numerator + half) / denominator : -((-numerator + half) / denominator);
    }
}
