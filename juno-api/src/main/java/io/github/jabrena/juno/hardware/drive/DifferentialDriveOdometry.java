package io.github.jabrena.juno.hardware.drive;

import io.github.jabrena.juno.hardware.imu.Imu;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.hardware.motor.Encoder;

/**
 * Updates a caller-owned pose from two wheel encoders and an IMU. The IMU supplies the preferred
 * heading; wheel geometry supplies distance and provides a degraded heading estimate if a sample is
 * unavailable. All state is allocated when this object is constructed.
 */
public final class DifferentialDriveOdometry {
    private static final int FULL_TURN_MILLIDEGREES = 360_000;
    private static final int HALF_TURN_MILLIDEGREES = 180_000;
    private static final long PI_MILLION = 3_141_593L;

    private final Encoder leftEncoder;
    private final Encoder rightEncoder;
    private final Imu imu;
    private final DriveGeometry geometry;
    private final Pose pose;
    private final ImuSample sample = new ImuSample();
    private int previousLeftDegrees;
    private int previousRightDegrees;
    private int referenceHeadingMillidegrees;
    private int poseHeadingAtReference;
    private boolean initialized;

    /** Creates odometry that updates {@code pose} in place. */
    public DifferentialDriveOdometry(
            Encoder leftEncoder,
            Encoder rightEncoder,
            Imu imu,
            DriveGeometry geometry,
            Pose pose) {
        this.leftEncoder = leftEncoder;
        this.rightEncoder = rightEncoder;
        this.imu = imu;
        this.geometry = geometry;
        this.pose = pose;
    }

    /** Returns the pose object supplied to the constructor. */
    public Pose pose() {
        return pose;
    }

    /** Resets both encoders and the pose; the next update establishes a new IMU reference heading. */
    public void reset() {
        leftEncoder.reset();
        rightEncoder.reset();
        pose.reset();
        initialized = false;
    }

    /**
     * Samples the sensors and updates the pose. Returns {@code true} when the IMU sample was valid;
     * encoder-only odometry is still applied when it returns {@code false}.
     */
    public boolean update() {
        int leftDegrees = leftEncoder.positionDegrees();
        int rightDegrees = rightEncoder.positionDegrees();
        boolean imuValid = imu.read(sample);

        if (!initialized) {
            previousLeftDegrees = leftDegrees;
            previousRightDegrees = rightDegrees;
            if (!imuValid) {
                return false;
            }
            referenceHeadingMillidegrees = sample.headingMillidegrees();
            poseHeadingAtReference = pose.headingMillidegrees();
            initialized = true;
            return true;
        }

        int leftDistance = geometry.leftDistanceMillimetres(leftDegrees - previousLeftDegrees);
        int rightDistance = geometry.rightDistanceMillimetres(rightDegrees - previousRightDegrees);
        previousLeftDegrees = leftDegrees;
        previousRightDegrees = rightDegrees;

        int previousHeading = pose.headingMillidegrees();
        int wheelHeading = previousHeading + wheelHeadingDelta(leftDistance, rightDistance);
        int newHeading = imuValid
                ? poseHeadingAtReference + shortestDifference(sample.headingMillidegrees(), referenceHeadingMillidegrees)
                : wheelHeading;
        int midpointHeading = previousHeading + shortestDifference(newHeading, previousHeading) / 2;
        int distance = (int) (((long) leftDistance + rightDistance) / 2);
        pose.advance(distance, midpointHeading);
        pose.advance(0, newHeading);
        return imuValid;
    }

    private int wheelHeadingDelta(int leftDistance, int rightDistance) {
        long numerator = (long) (rightDistance - leftDistance) * 180_000_000L;
        return (int) (numerator / (PI_MILLION * geometry.trackWidthMillimetres()));
    }

    private static int shortestDifference(int current, int previous) {
        int difference = normalize(current - previous + HALF_TURN_MILLIDEGREES) - HALF_TURN_MILLIDEGREES;
        return difference == -HALF_TURN_MILLIDEGREES ? HALF_TURN_MILLIDEGREES : difference;
    }

    private static int normalize(int angle) {
        int normalized = angle % FULL_TURN_MILLIDEGREES;
        return normalized < 0 ? normalized + FULL_TURN_MILLIDEGREES : normalized;
    }
}
