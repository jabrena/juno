package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;

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
public final class PoweredUpHubRemote {
    private PoweredUpHubRemote() {
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

    /**
     * The hub's battery level as a percentage, {@code 0..100}, or {@code -1} before the hub has
     * reported it. This is the hub's own battery property, not a voltage; the first call after
     * {@link #connect} subscribes to the hub's properties and waits up to 300 ms for the answers,
     * later calls return the latest update without blocking.
     */
    public static native int batteryPercent();

    /** Whether the hub's green button is held down right now. Subscribed on the first property call. */
    public static native boolean buttonPressed();

    /** The Bluetooth signal strength the hub reports for this link in dBm (a negative number), or {@code 0} if unknown. */
    public static native int rssi();

    /**
     * The hub's firmware version as the protocol's packed number, or {@code -1} if unknown; take it apart
     * with {@link #versionMajor}, {@link #versionMinor}, {@link #versionBugfix} and {@link #versionBuild}.
     */
    public static native int firmwareVersion();

    /** The hub's hardware version in the same packed form as {@link #firmwareVersion}, or {@code -1} if unknown. */
    public static native int hardwareVersion();

    /**
     * Copies the hub's advertised name (up to 20 characters, ASCII bytes, no terminator) into
     * {@code buffer}, at most {@code capacity} bytes, and returns how many were copied, {@code 0} if
     * the hub has not reported its name.
     */
    public static native int hubName(byte[] buffer, int capacity);

    /**
     * The parts of a packed {@link #firmwareVersion} or {@link #hardwareVersion}: 4 bits of major
     * version, 4 of minor, two binary-coded-decimal digits of bug-fix number, and 16 bits of build.
     */
    public static int versionMajor(int version) {
        return (version >> 28) & 0xF;
    }

    public static int versionMinor(int version) {
        return (version >> 24) & 0xF;
    }

    public static int versionBugfix(int version) {
        int bcd = (version >> 16) & 0xFF;
        return (bcd >> 4) * 10 + (bcd & 0xF);
    }

    public static int versionBuild(int version) {
        return version & 0xFFFF;
    }

    /**
     * The Technic Hub's (88012) built-in motion sensors, which report like a sensor on a port once
     * {@link #enableSensor} subscribes to them: the accelerometer, the gyroscope and the tilt sensor.
     * Each mode reports three signed 16-bit values; read them with {@code readSensorValue(port, index, 2)}.
     * The port numbers and the meaning of the values follow the community documentation of the LEGO
     * Wireless Protocol and have not been verified on a hub.
     */
    public static final int PORT_TECHNIC_ACCELEROMETER = 0x61;
    public static final int PORT_TECHNIC_GYRO = 0x62;
    public static final int PORT_TECHNIC_TILT = 0x63;

    /** Mode 0 of the three built-in sensors: gravity per axis, rotation rate per axis, and tilt angles in degrees. */
    public static final int MODE_IMU_VALUES = 0;

    /**
     * The {@code index}-th value of the latest report of the port {@link #enableSensor} subscribed to,
     * as a signed integer of {@code bytesPerValue} bytes (1, 2 or 4, little endian). For modes that
     * report several values, such as the three axes of a motion sensor, read them one by one with
     * {@code index} 0, 1 and 2. Returns {@code 0} before the first report or when the report is too short
     * for that index; see {@link #sensorReportSize}.
     */
    public static native int readSensorValue(int port, int index, int bytesPerValue);

    /**
     * How many reports the hub has sent for {@code port} since {@link #enableSensor} subscribed to it,
     * counting repeats of an unchanged value, so a program can measure how often the hub reports.
     */
    public static native int sensorReportCount(int port);

    /** How many value bytes the latest report of {@code port} carried (at most 16), {@code 0} before the first. */
    public static native int sensorReportSize(int port);

    /** Sets the hub's status LED to any colour, each channel {@code 0..255}; unlike {@link #setLedColor}'s 11 indexes. */
    public static native void setLedRgb(int red, int green, int blue);

    /**
     * Pairs the motors on ports {@code portA} and {@code portB} into one virtual port, so a single
     * command ({@link #setLinkedMotorPower}) drives both in sync, as for a two-motor vehicle. The hub
     * assigns the virtual port's number, which is returned, or {@code -1} if the hub did not create it
     * within 500 ms. Pairing the same two ports again returns the existing virtual port; a new
     * {@link #connect} starts with none. The message formats follow the protocol documentation and have
     * not been verified on a hub.
     */
    public static native int linkMotors(int portA, int portB);

    /** Takes the virtual port {@code virtualPort} apart, so the two motors are driven separately again. */
    public static native void unlinkMotors(int virtualPort);

    /**
     * Drives the two motors of a virtual port in one message: {@code first} for the motor on the first
     * port given to {@link #linkMotors} and {@code second} for the other, each {@code -100..100}
     * (clamped), negative for backwards, {@code 0} to coast. Mount mirrored motors with opposite signs.
     */
    public static native void setLinkedMotorPower(int virtualPort, int first, int second);

    /** Actively brakes both motors of a virtual port. */
    public static native void brakeLinkedMotors(int virtualPort);

    /** The Technic Hub's (88012) built-in current and voltage sensors, on the same footing as the motion sensors above. */
    public static final int PORT_TECHNIC_CURRENT = 0x3B;
    public static final int PORT_TECHNIC_VOLTAGE = 0x3C;

    private static final int RAW_FULL_SCALE = 4095;
    private static final int VOLTAGE_FULL_SCALE_MILLIVOLTS = 9615;
    private static final int CURRENT_FULL_SCALE_MILLIAMPS = 4175;
    private static final int INTERNAL_SENSOR_WAIT_MILLIS = 300;

    /**
     * The Technic Hub's supply voltage in millivolts (about 9000 with fresh batteries), or {@code -1} if
     * the hub has not answered. The first call subscribes to the hub's voltage sensor and waits up to
     * 300 ms for its first report, so later calls return the latest value without blocking. The scale
     * (a raw 0 to 4095 reading spanning 0 to 9.615 V) comes from the community protocol documentation
     * and is not verified.
     */
    public static int batteryMillivolts() {
        int raw = internalReading(PORT_TECHNIC_VOLTAGE);
        return raw < 0 ? -1 : raw * VOLTAGE_FULL_SCALE_MILLIVOLTS / RAW_FULL_SCALE;
    }

    /**
     * The current the Technic Hub draws, in milliamps (all motors and the hub together), or {@code -1} if
     * the hub has not answered; subscribed on the first call like {@link #batteryMillivolts}. Its scale (a
     * raw 0 to 4095 reading spanning 0 to 4175 mA) is likewise unverified.
     */
    public static int currentMilliamps() {
        int raw = internalReading(PORT_TECHNIC_CURRENT);
        return raw < 0 ? -1 : raw * CURRENT_FULL_SCALE_MILLIAMPS / RAW_FULL_SCALE;
    }

    /** Subscribes to an internal sensor if it has not reported yet, waits briefly for the report, and returns its raw value or -1. */
    private static int internalReading(int port) {
        if (sensorReportSize(port) == 0) {
            enableSensor(port, MODE_IMU_VALUES);
            int started = Clock.millis();
            while (sensorReportSize(port) == 0 && Clock.millis() - started < INTERNAL_SENSOR_WAIT_MILLIS) {
                readSensor(port);
            }
        }
        return sensorReportSize(port) == 0 ? -1 : readSensorValue(port, 0, 2);
    }

    /** Closes the BLE connection, leaving the hub on and advertising again. */
    public static native void disconnect();

    /** Asks the hub to switch itself off, which also ends the connection. */
    public static native void switchOff();
}
