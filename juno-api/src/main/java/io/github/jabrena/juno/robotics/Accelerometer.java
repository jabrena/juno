package io.github.jabrena.juno.robotics;

/**
 * An acceleration sensor. Each axis is reported in whole metres per second
 * squared; for finer resolution, read the device as a {@link SampleProvider}.
 */
public interface Accelerometer {

    /** Returns the acceleration along the X axis, in metres per second squared. */
    int getXAccel();

    /** Returns the acceleration along the Y axis, in metres per second squared. */
    int getYAccel();

    /** Returns the acceleration along the Z axis, in metres per second squared. */
    int getZAccel();
}
