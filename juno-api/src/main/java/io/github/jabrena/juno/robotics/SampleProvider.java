package io.github.jabrena.juno.robotics;

/**
 * Source of sensor samples. A sample is a fixed number of {@code float}
 * elements written into a caller-owned array, so a program allocates its buffer once at startup and polls without
 * allocating.
 *
 * <p>Samples use standard units: length in metres, angles in degrees, temperature in degrees Celsius, pressure in
 * pascals, speed in metres per second and acceleration in metres per second squared. A quantity without a clear unit
 * is normalised to the range 0 to 1. Spatial samples use a right-handed frame with X forwards, Y to the left and Z
 * up, list their elements in X, Y, Z order, and count a counter-clockwise rotation as positive.
 */
public interface SampleProvider {

    /** Returns the number of elements in a sample; it does not change while the program runs. */
    int sampleSize();

    /**
     * Fetches one sample into {@code sample}, writing {@link #sampleSize()} elements starting at {@code offset}.
     *
     * @param sample the caller-owned array that receives the sample
     * @param offset the index of the first element to write
     */
    void fetchSample(float[] sample, int offset);
}
