package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * The Technic Hub's gyroscope. Its sample is the rotation rate about X, Y and Z, in degrees per second, and its
 * {@link Gyroscope} view is the heading and the yaw rate: the heading comes from the hub's tilt yaw, read through the same
 * {@link PoweredUpTilt} that {@link RemotePoweredUpHub#getTilt} returns. Get it from
 * {@link RemotePoweredUpHub#getGyroscope}, which returns the same object on every call.
 *
 * <p>The heading is zero at the yaw of the last {@link #reset}, so call it once the hub has reported, or the heading starts
 * at the hub's absolute yaw. The scale (one raw count is one degree per second) is unverified.
 */
public final class PoweredUpGyroscope implements Gyroscope, SampleProvider {
    private static final int YAW = 0;
    private static final int YAW_RATE = 2;
    private static final int FULL_TURN = 360;
    /** One raw count is one degree per second, unverified. */
    private static final float DEGREES_PER_SECOND_PER_COUNT = 1f;

    private final PoweredUpSensor rate;
    private final PoweredUpTilt tilt;
    private final float[] sample = new float[3];
    private int origin;

    PoweredUpGyroscope(PoweredUpPort hubPort, PoweredUpTilt tilt) {
        this.rate = new PoweredUpSensor(hubPort, HubPort.TECHNIC_GYRO, DEGREES_PER_SECOND_PER_COUNT);
        this.tilt = tilt;
    }

    void subscribe() {
        rate.subscribe();
    }

    /** The heading in whole degrees, {@code 0..359}, clockwise since the last {@link #reset}, from the tilt yaw. */
    @Override
    public int getAngle() {
        tilt.fetchSample(sample, 0);
        return (((int) sample[YAW] - origin) % FULL_TURN + FULL_TURN) % FULL_TURN;
    }

    /** The yaw rate in degrees per second, clockwise positive. */
    @Override
    public float getAngularVelocity() {
        rate.fetchSample(sample, 0);
        return sample[YAW_RATE];
    }

    /**
     * Does nothing: the hub calibrates its own sensors, and this library measures no offset to subtract. Present because
     * {@link Gyroscope} declares it.
     */
    @Override
    public void recalibrateOffset() {
    }

    /** Sets the heading to zero at the current tilt yaw. */
    @Override
    public void reset() {
        tilt.fetchSample(sample, 0);
        origin = (int) sample[YAW];
    }

    /** The rotation rate about X, Y and Z, in degrees per second. */
    @Override
    public int sampleSize() {
        return rate.sampleSize();
    }

    @Override
    public void fetchSample(float[] sample, int offset) {
        rate.fetchSample(sample, offset);
    }
}
