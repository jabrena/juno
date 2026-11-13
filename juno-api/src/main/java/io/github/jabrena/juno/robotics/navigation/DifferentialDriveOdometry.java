package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.Encoder;
import io.github.jabrena.juno.robotics.Gyroscope;

/**
 * Updates a caller-owned pose from two wheel encoders and a heading sensor. The sensor supplies the preferred heading;
 * wheel geometry supplies distance and provides a degraded heading estimate if a sample is unavailable. All state is
 * allocated when this object is constructed.
 *
 * <p>The pose is Cartesian, as a {@link DirectionFinder}'s direction and leJOS's pose are: its heading grows
 * counter-clockwise and its y axis points to the left of heading zero. A {@link Gyroscope}'s angle, which grows
 * clockwise, is turned around to match.
 *
 * <p>Distances come from each encoder's count since {@link #reset()}, not from the change since the previous update, so
 * the rounding to whole millimetres does not add up over many short updates.
 */
public final class DifferentialDriveOdometry {
    private static final int MILLIDEGREES_PER_DEGREE = 1000;
    private static final int FULL_TURN_MILLIDEGREES = 360_000;
    private static final int HALF_TURN_MILLIDEGREES = 180_000;
    private static final long PI_MILLION = 3_141_593L;
    /** A sensor heading that is no valid sample: valid ones are normalized to {@code 0..359_999}. */
    private static final int NO_SAMPLE = -1;

    private final Encoder leftEncoder;
    private final Encoder rightEncoder;
    private final HeadingSensor heading;
    private final DriveGeometry geometry;
    private final Pose pose;
    private int previousLeftMillimetres;
    private int previousRightMillimetres;
    private int previousCentreMillimetres;
    private int referenceHeadingMillidegrees;
    private int poseHeadingAtReference;
    private boolean initialized;
    /** Whether a valid sensor sample has set the reference heading yet; encoder-only odometry runs until one does. */
    private boolean referenceEstablished;

    /**
     * Creates odometry that updates {@code pose} in place, with the heading from {@code gyro}'s angle: whole degrees,
     * read once each {@link #update()}, and no sample while the angle reads negative.
     */
    public DifferentialDriveOdometry(
            Encoder leftEncoder,
            Encoder rightEncoder,
            Gyroscope gyro,
            DriveGeometry geometry,
            Pose pose) {
        this(leftEncoder, rightEncoder, new GyroscopeHeading(gyro), geometry, pose);
    }

    /**
     * Creates odometry that updates {@code pose} in place, with the heading from {@code direction}, such as a
     * {@link GyroHeading}: thousandths of a degree, followed between updates, and a valid sample on every update.
     */
    public DifferentialDriveOdometry(
            Encoder leftEncoder,
            Encoder rightEncoder,
            DirectionFinder direction,
            DriveGeometry geometry,
            Pose pose) {
        this(leftEncoder, rightEncoder, new DirectionFinderHeading(direction), geometry, pose);
    }

    private DifferentialDriveOdometry(
            Encoder leftEncoder,
            Encoder rightEncoder,
            HeadingSensor heading,
            DriveGeometry geometry,
            Pose pose) {
        this.leftEncoder = leftEncoder;
        this.rightEncoder = rightEncoder;
        this.heading = heading;
        this.geometry = geometry;
        this.pose = pose;
    }

    /** Returns the pose object supplied to the constructor. */
    public Pose pose() {
        return pose;
    }

    /** Resets both encoders and the pose; the next update establishes a new reference heading. */
    public void reset() {
        leftEncoder.resetTachoCount();
        rightEncoder.resetTachoCount();
        pose.reset();
        initialized = false;
        referenceEstablished = false;
    }

    /**
     * Samples the sensors and updates the pose. Returns {@code true} when the heading sample was valid;
     * encoder-only odometry is still applied when it returns {@code false}.
     */
    public boolean update() {
        int leftMillimetres = geometry.leftDistanceMillimetres(leftEncoder.getTachoCount());
        int rightMillimetres = geometry.rightDistanceMillimetres(rightEncoder.getTachoCount());
        int sensorHeading = heading.cartesianMillidegrees();
        boolean headingValid = sensorHeading != NO_SAMPLE;

        if (!initialized) {
            previousLeftMillimetres = leftMillimetres;
            previousRightMillimetres = rightMillimetres;
            previousCentreMillimetres = (int) (((long) leftMillimetres + rightMillimetres) / 2);
            initialized = true;
            if (!headingValid) {
                return false;
            }
            establishReference(sensorHeading);
            return true;
        }

        int leftDistance = leftMillimetres - previousLeftMillimetres;
        int rightDistance = rightMillimetres - previousRightMillimetres;
        previousLeftMillimetres = leftMillimetres;
        previousRightMillimetres = rightMillimetres;
        if (headingValid && !referenceEstablished) {
            establishReference(sensorHeading);
        }

        int previousHeading = pose.headingMillidegrees();
        int wheelHeading = previousHeading + wheelHeadingDelta(leftDistance, rightDistance);
        int newHeading = headingValid
                ? poseHeadingAtReference + shortestDifference(sensorHeading, referenceHeadingMillidegrees)
                : wheelHeading;
        int midpointHeading = previousHeading + shortestDifference(newHeading, previousHeading) / 2;
        // The centre's distance from the totals, as the wheels' are: halving each update's sum lost half a millimetre
        // whenever it was odd.
        int centreMillimetres = (int) (((long) leftMillimetres + rightMillimetres) / 2);
        int distance = centreMillimetres - previousCentreMillimetres;
        previousCentreMillimetres = centreMillimetres;
        pose.advance(distance, midpointHeading);
        pose.advance(0, newHeading);
        return headingValid;
    }

    /** Takes the current pose heading as the one the sensor's {@code sensorHeading} is measured from. */
    private void establishReference(int sensorHeading) {
        referenceHeadingMillidegrees = sensorHeading;
        poseHeadingAtReference = pose.headingMillidegrees();
        referenceEstablished = true;
    }

    /**
     * The counter-clockwise turn, in thousandths of a degree, the two wheels' distances make: the right wheel further
     * ahead turns the robot counter-clockwise by {@code (right - left) × 180 000 / (π × track)}, with π scaled by a
     * million.
     */
    private int wheelHeadingDelta(int leftDistance, int rightDistance) {
        long numerator = (long) (rightDistance - leftDistance) * 180_000_000_000L;
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

    /**
     * Where the heading comes from. Each constructor creates only its own kind, so a board program that uses one never
     * links the other's sensor interface.
     */
    private interface HeadingSensor {
        /** The heading, counter-clockwise, in thousandths of a degree within a turn, or {@link #NO_SAMPLE}; one read. */
        int cartesianMillidegrees();
    }

    /** A gyroscope's angle: whole degrees, turned around from clockwise, and no sample while it reads negative. */
    private static final class GyroscopeHeading implements HeadingSensor {
        private final Gyroscope gyro;

        GyroscopeHeading(Gyroscope gyro) {
            this.gyro = gyro;
        }

        @Override
        public int cartesianMillidegrees() {
            int degrees = gyro.getAngle();
            return degrees >= 0 ? normalize(-degrees * MILLIDEGREES_PER_DEGREE) : NO_SAMPLE;
        }
    }

    /** A direction finder's Cartesian degrees, as they are; always a sample. */
    private static final class DirectionFinderHeading implements HeadingSensor {
        private final DirectionFinder direction;

        DirectionFinderHeading(DirectionFinder direction) {
            this.direction = direction;
        }

        @Override
        public int cartesianMillidegrees() {
            // Normalized, so a finder that does not wrap at a full turn stays within an int.
            return normalize((int) (direction.getDegreesCartesian() * MILLIDEGREES_PER_DEGREE));
        }
    }
}
