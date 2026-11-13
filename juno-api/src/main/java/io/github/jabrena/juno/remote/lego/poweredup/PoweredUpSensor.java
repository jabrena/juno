package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * One of the Technic Hub's built-in motion sensors: its port reports three signed 16-bit values, which this class reads
 * and scales to standard units. The hub subscribes it on connect, and a read before the first report returns zero.
 */
final class PoweredUpSensor implements SampleProvider {
    private static final int VALUES = 3;
    private static final int VALUE_BYTES = 2;

    private final PoweredUpTransport hubPort;
    private final BuiltInSensor port;
    private final float scale;

    /** {@code scale} converts one raw value to the sensor's unit, such as metres per second squared. */
    PoweredUpSensor(PoweredUpTransport hubPort, BuiltInSensor port, float scale) {
        this.hubPort = hubPort;
        this.port = port;
        this.scale = scale;
    }

    @Override
    public int sampleSize() {
        return VALUES;
    }

    /** Processes the hub's pending reports, then writes the latest values; zero before the first report. */
    @Override
    public void fetchSample(float[] sample, int offset) {
        hubPort.readSensor(port.value());
        for (int i = 0; i < VALUES; i++) {
            sample[offset + i] = (float) hubPort.readSensorValue(port.value(), i, VALUE_BYTES) * scale;
        }
    }

    /** Asks the hub to report this sensor's values; called by the hub after each connect. */
    void subscribe() {
        hubPort.enableSensor(port.value(), RemotePoweredUpHub.MODE_IMU_VALUES);
    }
}
