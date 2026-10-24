package io.github.jabrena.juno.hardware.drive;

/**
 * Applies a bounded proportional correction to keep a differential drive on a target heading. The
 * controller reads the caller-owned {@link Pose} and delegates motor commands to
 * {@link DifferentialDrive}; it does not depend on an IMU or motor protocol.
 */
public final class HeadingHoldController {
    private static final int QUARTER_TURN_MILLIDEGREES = 90_000;
    private static final int FULL_TURN_MILLIDEGREES = 360_000;
    private static final int HALF_TURN_MILLIDEGREES = 180_000;

    private final DifferentialDrive drive;
    private final Pose pose;
    private final int correctionAtQuarterTurn;
    private final int maximumCorrection;
    private int targetHeadingMillidegrees;

    /**
     * Creates a controller. {@code correctionAtQuarterTurn} is the turn-power correction at a
     * ninety-degree error; {@code maximumCorrection} limits the correction for larger errors.
     */
    public HeadingHoldController(
            DifferentialDrive drive,
            Pose pose,
            int correctionAtQuarterTurn,
            int maximumCorrection) {
        if (correctionAtQuarterTurn < 0 || maximumCorrection < 0 || maximumCorrection > DifferentialDrive.MAX_POWER) {
            throw new IllegalArgumentException("heading corrections must be in the range 0..100");
        }
        this.drive = drive;
        this.pose = pose;
        this.correctionAtQuarterTurn = correctionAtQuarterTurn;
        this.maximumCorrection = maximumCorrection;
        targetHeadingMillidegrees = pose.headingMillidegrees();
    }

    /** Sets the target heading, normalized to {@code 0..359999} milli-degrees. */
    public void setTargetHeadingMillidegrees(int targetHeadingMillidegrees) {
        this.targetHeadingMillidegrees = normalize(targetHeadingMillidegrees);
    }

    /** Uses the current pose heading as the new target. */
    public void captureCurrentHeading() {
        targetHeadingMillidegrees = pose.headingMillidegrees();
    }

    /** Returns the current target heading in milli-degrees. */
    public int targetHeadingMillidegrees() {
        return targetHeadingMillidegrees;
    }

    /** Returns the signed shortest error from the current pose to the target heading. */
    public int headingErrorMillidegrees() {
        int error = normalize(targetHeadingMillidegrees - pose.headingMillidegrees() + HALF_TURN_MILLIDEGREES)
                - HALF_TURN_MILLIDEGREES;
        return error == -HALF_TURN_MILLIDEGREES ? HALF_TURN_MILLIDEGREES : error;
    }

    /** Drives forward with a proportional correction toward the target heading. */
    public void holdHeading(int forwardPower) {
        drive.drive(forwardPower, correction());
    }

    /** Returns the current bounded turn-power correction. */
    public int correction() {
        int correction = (int) ((long) headingErrorMillidegrees() * correctionAtQuarterTurn
                / QUARTER_TURN_MILLIDEGREES);
        if (correction > maximumCorrection) {
            return maximumCorrection;
        }
        if (correction < -maximumCorrection) {
            return -maximumCorrection;
        }
        return correction;
    }

    private static int normalize(int angle) {
        int normalized = angle % FULL_TURN_MILLIDEGREES;
        return normalized < 0 ? normalized + FULL_TURN_MILLIDEGREES : normalized;
    }
}
