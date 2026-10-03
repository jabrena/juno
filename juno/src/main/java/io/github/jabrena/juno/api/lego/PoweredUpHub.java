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
 * <p>Motors and sensors report values once {@link #enableSensor} has subscribed to one of their
 * modes; {@link #readSensor} then returns the latest value the hub sent, e.g. a tacho motor's
 * position in degrees or the button pressed on a Powered Up remote.
 *
 * <p>Works on both boards. On the UNO R4 WiFi, BLE is provided by the ESP32-S3 radio module, which
 * cannot run BLE and {@code Wifi} at the same time. On the UNO Q, the radio belongs to the Linux
 * side: {@code ArduinoBLE} 2.1.0 or newer tunnels raw HCI to its {@code hci0} adapter through
 * {@code Arduino_RouterBridge}, which needs {@code arduino-router} 0.7.0 or newer on the board.
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

    /** {@link #enableSensor} modes of a tacho motor (BOOST, Technic and SPIKE motors with a rotation sensor). */
    public static final int MODE_MOTOR_SPEED = 1;
    /** Cumulative motor position in degrees, relative to where the motor was when the hub started. */
    public static final int MODE_MOTOR_POSITION = 2;

    /**
     * {@link #enableSensor} modes of the Color and Distance Sensor (88007): the detected color as the
     * sensor's own color number, which mostly but not fully matches the {@code COLOR_*} LED values,
     * or {@code -1} when it detects none.
     */
    public static final int MODE_COLOR = 0;
    /** Proximity of the nearest object, {@code 0} (touching) to {@code 10} (nothing in range). */
    public static final int MODE_PROXIMITY = 1;

    /**
     * {@link #enableSensor} mode of each button set ({@link #PORT_A} left, {@link #PORT_B} right) of
     * the Powered Up remote (88010), connected as a hub of {@link #TYPE_REMOTE_CONTROL}; reads one of
     * the {@code REMOTE_*} values.
     */
    public static final int MODE_REMOTE_BUTTONS = 0;
    public static final int REMOTE_RELEASED = 0;
    public static final int REMOTE_PLUS = 1;
    public static final int REMOTE_MINUS = -1;
    public static final int REMOTE_STOP = 127;

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

    /**
     * Asks the hub to report every change of {@code mode} of the motor or sensor on {@code port},
     * for {@link #readSensor} to return. Mode numbers are device specific; the {@code MODE_*}
     * constants cover common devices. Up to eight ports report at a time, and a new
     * {@link #connect} starts with none, so enable them again after reconnecting.
     */
    public static native void enableSensor(int port, int mode);

    /**
     * The latest value reported by the port {@link #enableSensor} subscribed to, or {@code 0}
     * before the first report. Decodes modes reporting a single 8-, 16- or 32-bit value; for a mode
     * reporting several values (e.g. a tilt sensor's axes) only its first byte is returned. Call it
     * regularly: values are received while it (or another hub call) runs, not during
     * {@code Delay.millis}.
     */
    public static native int readSensor(int port);

    /** Closes the BLE connection, leaving the hub on and advertising again. */
    public static native void disconnect();

    /** Asks the hub to switch itself off, which also ends the connection. */
    public static native void switchOff();
}
