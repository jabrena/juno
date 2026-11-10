package io.github.jabrena.juno.robotics;

/**
 * A {@link Gyroscope} that keeps sampling its angular velocity at a fixed period in a buffer of its own, such as a FIFO
 * on the sensor, so a reader that comes late still gets every sample since its previous read. A heading integrated from
 * it loses no rotation while the program is busy, as one from single reads of {@link #getAngularVelocity()} does when a
 * fast turn ends between two of them.
 */
public interface BufferedGyroscope extends Gyroscope {
    /** Empties the buffer and starts filling it, one sample every {@link #samplePeriodMillis()}. */
    void startBuffering();

    /** The time between two buffered samples, in milliseconds. */
    int samplePeriodMillis();

    /**
     * Reads every sample buffered since the previous call and returns the rotation they add up to, in thousandths of a
     * degree, clockwise positive like {@link #getAngle()}, with the offset of {@link #recalibrateOffset()} removed. How
     * many samples it read is then in {@link #bufferedSamples()}.
     */
    int readBufferedRotation();

    /** How many samples the last {@link #readBufferedRotation()} read; each covers {@link #samplePeriodMillis()}. */
    int bufferedSamples();
}
