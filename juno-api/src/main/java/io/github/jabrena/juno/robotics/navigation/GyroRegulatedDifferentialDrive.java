package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;

/**
 * A {@link RegulatedDifferentialDrive} that holds its heading with a {@link DirectionFinder}, such as a
 * {@link GyroHeading}. The wheels are regulated where the direction finder cannot reach them, on an RCX for instance, so the heading is corrected between moves: a straight travel
 * is driven in segments, and after each segment the robot settles, the gyroscope measures how far its heading is from
 * the one it should have, and a pivot about one wheel takes the error out.
 *
 * <p>By default a turn is left to the wheel encoders, and the heading the robot then has is the one later moves hold. A
 * gyroscope read once a visit, between the infrared round trips that poll a moving pair, counts a fast rotation from a
 * few reads, and a turn that ends between two of them loses what it turned meanwhile: on the reference build an encoder
 * turn that looked exactly 90 degrees read 81, and correcting it made it worse. A straight segment rotates slowly, so its
 * drift is measured well. With a buffered gyroscope, which loses no sample, {@link #setCorrectTurns(boolean)} corrects
 * turns to the heading asked as well.
 *
 * <p>A pivot's finest turn is one sensor step of one wheel, so an error below {@code correctionDegrees} is left: it
 * must be at least half that step, or the pivot rounds to nothing (on 82 mm wheels and a 160 mm track with 16 steps a
 * turn, a step is 5.8 degrees, so 3 degrees). Turns, headings and errors are all Cartesian degrees, counter-clockwise
 * positive, as the drive's turns and the direction finder's directions are. Create it once at startup; its moves allocate
 * nothing.
 */
public final class GyroRegulatedDifferentialDrive {
    private static final int MILLIDEGREES_PER_DEGREE = 1000;

    private final RegulatedDifferentialDrive drive;
    private final DirectionFinder heading;
    private final int segmentMillimetres;
    private final int correctionDegrees;
    private final int settleMillis;
    /** The heading the robot should have, in thousandths of a Cartesian degree: the finder's at the first move. */
    private int targetMillidegrees;
    private boolean targetSet;
    private int corrections;
    private int lastErrorDegrees;
    private boolean correctTurns;

    /**
     * Creates the drive. {@code segmentMillimetres} is the longest straight move between two heading checks,
     * {@code correctionDegrees} the smallest error a pivot corrects, and {@code settleMillis} how long the robot is left
     * to stop before the gyroscope is read.
     */
    public GyroRegulatedDifferentialDrive(RegulatedDifferentialDrive drive, DirectionFinder heading, int segmentMillimetres,
            int correctionDegrees, int settleMillis) {
        if (segmentMillimetres <= 0 || correctionDegrees <= 0 || settleMillis < 0) {
            throw new IllegalArgumentException("segment and correction must be positive, settle not negative");
        }
        this.drive = drive;
        this.heading = heading;
        this.segmentMillimetres = segmentMillimetres;
        this.correctionDegrees = correctionDegrees;
        this.settleMillis = settleMillis;
    }

    /**
     * Whether a turn is corrected to the heading asked, as a straight segment is, rather than left to the encoders. Turn
     * it on only with a direction finder that counts fast rotations whole, such as a {@link GyroHeading} over a
     * {@code BufferedGyroscope}.
     */
    public void setCorrectTurns(boolean correctTurns) {
        this.correctTurns = correctTurns;
    }

    /** Makes the current heading the one later moves hold, as if the robot had just been placed. */
    public void holdCurrentHeading() {
        targetMillidegrees = currentMillidegrees();
        targetSet = true;
    }

    /**
     * Drives straight by {@code millimetres}, forward when positive, in segments of at most the configured length, and
     * after each one corrects the heading back to the one held.
     */
    public void travelMillimetres(int millimetres) {
        holdIfUnset();
        int remaining = millimetres;
        while (remaining != 0) {
            int segment = Math.min(Math.abs(remaining), segmentMillimetres);
            int signed = remaining > 0 ? segment : -segment;
            drive.travelMillimetres(signed);
            remaining -= signed;
            correct();
        }
    }

    /**
     * Turns in place by {@code degrees}, positive counter-clockwise, by the wheel encoders. With turns corrected, a pivot
     * then takes out the error from the heading asked, which later moves hold. Otherwise the heading the robot has after
     * the turn is held, and {@link #lastErrorDegrees()} reports how far the direction finder saw the turn from the one
     * asked, without acting on it.
     */
    public void turnDegrees(int degrees) {
        holdIfUnset();
        int asked = targetMillidegrees + degrees * MILLIDEGREES_PER_DEGREE;
        drive.turnDegrees(degrees);
        if (correctTurns) {
            targetMillidegrees = asked;
            correct();
            return;
        }
        settle();
        lastErrorDegrees = (currentMillidegrees() - asked) / MILLIDEGREES_PER_DEGREE;
        holdCurrentHeading();
    }

    /** How many correcting pivots the drive has made. */
    public int corrections() {
        return corrections;
    }

    /** The heading error measured after the last move, before its correction, in degrees, counter-clockwise positive. */
    public int lastErrorDegrees() {
        return lastErrorDegrees;
    }

    /** The heading the drive holds, in degrees, counter-clockwise positive. */
    public int targetDegrees() {
        return targetMillidegrees / MILLIDEGREES_PER_DEGREE;
    }

    private void holdIfUnset() {
        if (!targetSet) {
            holdCurrentHeading();
        }
    }

    /** Lets the robot settle, then pivots by the heading error when it is at least the correction threshold. */
    private void correct() {
        settle();
        int error = currentMillidegrees() - targetMillidegrees;
        lastErrorDegrees = error / MILLIDEGREES_PER_DEGREE;
        if (Math.abs(lastErrorDegrees) < correctionDegrees) {
            return;
        }
        // Too far counter-clockwise is corrected clockwise, and the other way round.
        drive.pivotDegrees(-lastErrorDegrees);
        corrections++;
        settle();
    }

    private int currentMillidegrees() {
        return (int) (heading.getDegreesCartesian() * MILLIDEGREES_PER_DEGREE);
    }

    private void settle() {
        if (settleMillis == 0) {
            return;
        }
        try {
            Thread.sleep(settleMillis);
        } catch (InterruptedException interrupted) {
            // An interrupted settle reads the heading early; the next move's correction catches what it missed.
            return;
        }
    }
}
