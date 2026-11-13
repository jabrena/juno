package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * A sensor built into the Technic Hub (88012). It reports on its own port number once subscribed, like a sensor on an
 * external port, but it is not a {@link PoweredUpPort} and cannot take a motor. The port numbers follow the community
 * documentation of the LEGO Wireless Protocol and have not been verified on a hub.
 */
public enum BuiltInSensor {
    /** The Technic Hub's built-in accelerometer. */
    TECHNIC_ACCELEROMETER(0x61),
    /** The Technic Hub's built-in gyroscope. */
    TECHNIC_GYRO(0x62),
    /** The Technic Hub's built-in tilt sensor. */
    TECHNIC_TILT(0x63),
    /** The Technic Hub's built-in current sensor. */
    TECHNIC_CURRENT(0x3B),
    /** The Technic Hub's built-in voltage sensor. */
    TECHNIC_VOLTAGE(0x3C);

    private final int value;

    BuiltInSensor(int value) {
        this.value = value;
    }

    /** The port number the LEGO Wireless Protocol uses for this sensor. */
    int value() {
        return value;
    }
}
