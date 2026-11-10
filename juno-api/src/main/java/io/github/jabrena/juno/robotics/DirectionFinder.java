package io.github.jabrena.juno.robotics;

/**
 * A source of the robot's direction in Cartesian degrees, which grow counter-clockwise, as a drive's positive turn
 * does. A gyroscope, a compass or any other sensor can provide it, so a drive that holds a heading does not depend on
 * which.
 */
public interface DirectionFinder {
    /**
     * The direction in degrees, counter-clockwise positive, since {@link #resetCartesianZero()}. A finder that integrates
     * a rate, such as a gyroscope's, does not wrap it at a full turn.
     */
    float getDegreesCartesian();

    /** Makes the current direction zero. */
    void resetCartesianZero();

    /** Starts measuring the sensor's calibration; the robot must stand still until {@link #stopCalibration()}. */
    void startCalibration();

    /** Ends the calibration started by {@link #startCalibration()} and applies it to later directions. */
    void stopCalibration();
}
