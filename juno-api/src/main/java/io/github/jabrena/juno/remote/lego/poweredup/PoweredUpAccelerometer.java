package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * The Technic Hub's accelerometer: gravity-compensated acceleration along X, Y and Z. Get it from
 * {@link RemotePoweredUpHub#getAccelerometer}, which returns the same object on every call. It is also a
 * {@link SampleProvider} for the raw-scale values.
 *
 * <p>The scale (one raw count is one milli-g, about 0.0098 m/s^2) comes from the community protocol documentation and is
 * not verified: check it by printing the raw values from a program.
 */
public final class PoweredUpAccelerometer implements Accelerometer, SampleProvider {
    /** One raw count is one milli-g, unverified. */
    private static final float METRES_PER_SECOND_SQUARED_PER_COUNT = 9.80665f / 1000f;

    private final PoweredUpSensor sensor;
    private final float[] sample = new float[3];

    PoweredUpAccelerometer(PoweredUpTransport hubPort) {
        this.sensor = new PoweredUpSensor(hubPort, BuiltInSensor.TECHNIC_ACCELEROMETER, METRES_PER_SECOND_SQUARED_PER_COUNT);
    }

    void subscribe() {
        sensor.subscribe();
    }

    /** The acceleration along X, in whole metres per second squared. */
    @Override
    public int getXAccel() {
        return axis(0);
    }

    /** The acceleration along Y, in whole metres per second squared. */
    @Override
    public int getYAccel() {
        return axis(1);
    }

    /** The acceleration along Z, in whole metres per second squared. */
    @Override
    public int getZAccel() {
        return axis(2);
    }

    @Override
    public int sampleSize() {
        return sensor.sampleSize();
    }

    @Override
    public void fetchSample(float[] sample, int offset) {
        sensor.fetchSample(sample, offset);
    }

    private int axis(int index) {
        sensor.fetchSample(sample, 0);
        return (int) sample[index];
    }
}
