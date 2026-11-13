package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.power.Battery;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The Technic Hub's battery as the board's shared {@link Battery} interface: the hub's own charge percentage, and its
 * supply voltage read from the hub's internal voltage sensor. Get it from {@link RemotePoweredUpHub#getBattery}, which
 * returns the same object on every call.
 *
 * <p>The voltage scale (a raw 0 to 4095 reading spanning 0 to 9.615 V) comes from the community protocol documentation
 * and is not verified.
 */
public final class PoweredUpBattery implements Battery {
    private static final int RAW_FULL_SCALE = 4095;
    private static final int VOLTAGE_FULL_SCALE_MILLIVOLTS = 9615;
    private static final int INTERNAL_SENSOR_WAIT_MILLIS = 300;

    private final PoweredUpTransport hubPort;
    private final Clock clock;

    PoweredUpBattery(PoweredUpTransport hubPort, Clock clock) {
        this.hubPort = hubPort;
        this.clock = clock;
    }

    /** The hub's own charge estimate, {@code 0..100}; {@code 0} until the hub has reported it. */
    @Override
    public int levelPercent() {
        return Math.max(0, hubPort.batteryPercent());
    }

    /**
     * The Technic Hub's supply voltage in millivolts (about 9000 with fresh batteries), or {@code -1} if the hub has
     * not answered. The first call subscribes to the voltage sensor and waits up to 300 ms for its first report, so
     * later calls return the latest value without blocking.
     */
    @Override
    public int voltageMillivolts() {
        int raw = reading(BuiltInSensor.TECHNIC_VOLTAGE);
        return raw < 0 ? -1 : raw * VOLTAGE_FULL_SCALE_MILLIVOLTS / RAW_FULL_SCALE;
    }

    /**
     * Subscribes to an internal sensor on {@code port} if it has not reported yet, waits briefly for the report, and
     * returns its raw value, or {@code -1} if it has not answered. Shared by the voltage and current readings.
     */
    int reading(BuiltInSensor port) {
        if (hubPort.sensorReportSize(port.value()) == 0) {
            hubPort.enableSensor(port.value(), RemotePoweredUpHub.MODE_IMU_VALUES);
            int started = clock.millis();
            while (hubPort.sensorReportSize(port.value()) == 0 && clock.millis() - started < INTERNAL_SENSOR_WAIT_MILLIS) {
                hubPort.readSensor(port.value());
            }
        }
        return hubPort.sensorReportSize(port.value()) == 0 ? -1 : hubPort.readSensorValue(port.value(), 0, 2);
    }
}
