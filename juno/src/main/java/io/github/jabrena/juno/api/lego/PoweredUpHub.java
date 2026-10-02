package io.github.jabrena.juno.api.lego;

/**
 * A LEGO Powered Up hub (City Hub 88009, Technic Hub 88012, BOOST Move Hub 88006, DUPLO Train
 * Hub, ...) controlled over Bluetooth Low Energy, recognized as compiler intrinsics by Juno and
 * backed by the Arduino {@code ArduinoBLE} library ({@code arduino-cli lib install ArduinoBLE}).
 * The board acts as a BLE central speaking the LEGO Wireless Protocol 3.0 to one hub at a time.
 *
 * <p>Ports are numbered from {@link #PORT_A}; a City Hub has ports A and B, a Technic Hub and a
 * Move Hub A to D. {@link #setMotorPower} works for every Powered Up motor (train motors, simple
 * and tacho motors), with the power given as a percentage, {@code -100..100}.
 *
 * <p>Requires {@code @Board(ArduinoUnoR4WiFi.class)}: on the UNO R4 WiFi BLE is provided by the
 * ESP32-S3 radio module, which cannot run BLE and {@code Wifi} at the same time.
 */
public final class PoweredUpHub {
    private PoweredUpHub() {
    }

    public static final int PORT_A = 0;
    public static final int PORT_B = 1;
    public static final int PORT_C = 2;
    public static final int PORT_D = 3;

    /** {@link #hubType} values: the LEGO system type id advertised by each hub. */
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_DUPLO_TRAIN_HUB = 0x20;
    public static final int TYPE_MOVE_HUB = 0x40;
    public static final int TYPE_CITY_HUB = 0x41;
    public static final int TYPE_REMOTE_CONTROL = 0x42;
    public static final int TYPE_MARIO = 0x43;
    public static final int TYPE_TECHNIC_HUB = 0x80;

    /** {@link #setLedColor} values, the LEGO color indexes. */
    public static final int COLOR_OFF = 0;
    public static final int COLOR_PINK = 1;
    public static final int COLOR_PURPLE = 2;
    public static final int COLOR_BLUE = 3;
    public static final int COLOR_LIGHT_BLUE = 4;
    public static final int COLOR_CYAN = 5;
    public static final int COLOR_GREEN = 6;
    public static final int COLOR_YELLOW = 7;
    public static final int COLOR_ORANGE = 8;
    public static final int COLOR_RED = 9;
    public static final int COLOR_WHITE = 10;

    /**
     * Scans for the first advertising Powered Up hub and connects to it, returning whether a hub is
     * connected afterwards. Waits at most {@code timeoutMillis}, or indefinitely when it is zero or
     * negative. Returns {@code true} immediately if a hub is already connected.
     */
    public static native boolean connect(int timeoutMillis);

    /** Whether a hub is currently connected; turns {@code false} once the hub switches off or goes out of range. */
    public static native boolean isConnected();

    /** The connected hub's {@code TYPE_*} system type id, or {@link #TYPE_UNKNOWN} before {@link #connect}. */
    public static native int hubType();

    /**
     * Drives the motor on {@code port} at {@code powerPercent} of full power, {@code -100..100}
     * (negative runs backwards, {@code 0} lets it coast); out-of-range values are clamped.
     */
    public static native void setMotorPower(int port, int powerPercent);

    /** Actively brakes the motor on {@code port}, unlike {@code setMotorPower(port, 0)}, which lets it coast. */
    public static native void brakeMotor(int port);

    /** Sets the hub's status LED to one of the {@code COLOR_*} values. */
    public static native void setLedColor(int color);

    /** Closes the BLE connection, leaving the hub on and advertising again. */
    public static native void disconnect();

    /** Asks the hub to switch itself off, which also ends the connection. */
    public static native void switchOff();
}
