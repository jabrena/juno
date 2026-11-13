package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * The Technic Hub's tilt sensor: three angles in degrees, yaw first, then pitch and roll. Get it from
 * {@link RemotePoweredUpHub#getTilt}, which returns the same object on every call.
 */
public final class PoweredUpTilt implements SampleProvider {
    private static final float DEGREES_PER_COUNT = 1f;

    private final PoweredUpSensor sensor;

    PoweredUpTilt(PoweredUpTransport hubPort) {
        this.sensor = new PoweredUpSensor(hubPort, BuiltInSensor.TECHNIC_TILT, DEGREES_PER_COUNT);
    }

    void subscribe() {
        sensor.subscribe();
    }

    @Override
    public int sampleSize() {
        return sensor.sampleSize();
    }

    @Override
    public void fetchSample(float[] sample, int offset) {
        sensor.fetchSample(sample, offset);
    }
}
