package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.net.BluetoothLe;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * A LEGO Powered Up hub (City Hub 88009, Technic Hub 88012, BOOST Move Hub 88006, DUPLO Train
 * Hub, ...) controlled over Bluetooth Low Energy, recognized as compiler intrinsics by Juno and
 * backed by the Arduino {@code ArduinoBLE} library ({@code arduino-cli lib install ArduinoBLE}).
 * The board acts as a BLE central speaking the LEGO Wireless Protocol 3.0 to one hub at a time.
 *
 * <p>Ports are numbered from {@link HubPort#A}; a City Hub has ports A and B, a Technic Hub and a
 * Move Hub A to D. Each motor is a {@link PoweredUpMotor} from {@link #getMotor}, which drives train, simple and
 * tacho motors alike with a power percentage, {@code -100..100}.
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
public final class RemotePoweredUpHub {
    /** The external ports A to D, indexed by their {@link HubPort#value()}: the motors {@link #getMotor} returns. */
    private static final int EXTERNAL_PORTS = 4;

    private final PoweredUpPort hubPort;
    private final PoweredUpMotor[] motors = new PoweredUpMotor[EXTERNAL_PORTS];
    private final PoweredUpTilt tilt;
    private final PoweredUpGyroscope gyroscope;
    private final PoweredUpAccelerometer accelerometer;
    private final PoweredUpBattery battery;

    /**
     * A hub controller that links to hubs over the board's Bluetooth LE radio {@code bluetooth}, such as
     * {@code ArduinoUno.getBluetoothLe()}, and times its waits on {@code clock}, such as {@code ArduinoUno.getClock()}.
     * Create it once, at startup. Only the boards with a BLE radio can be given one.
     */
    public RemotePoweredUpHub(BluetoothLe bluetooth, Clock clock) {
        this(new LegoWirelessPort(bluetooth, clock), clock);
    }

    /** A hub controller that drives {@code port} and gives its battery {@code clock} for its waits: fakes in a unit test. */
    RemotePoweredUpHub(PoweredUpPort port, Clock clock) {
        this.hubPort = port;
        motors[HubPort.A.value()] = new PoweredUpMotor(port, HubPort.A);
        motors[HubPort.B.value()] = new PoweredUpMotor(port, HubPort.B);
        motors[HubPort.C.value()] = new PoweredUpMotor(port, HubPort.C);
        motors[HubPort.D.value()] = new PoweredUpMotor(port, HubPort.D);
        tilt = new PoweredUpTilt(port);
        gyroscope = new PoweredUpGyroscope(port, tilt);
        accelerometer = new PoweredUpAccelerometer(port);
        battery = new PoweredUpBattery(port, clock);
    }

    /** The Technic Hub's accelerometer, which returns the same object on every call. The hub subscribes it on connect. */
    public Accelerometer getAccelerometer() {
        return accelerometer;
    }

    /** The Technic Hub's gyroscope, which returns the same object on every call. The hub subscribes it on connect. */
    public Gyroscope getGyroscope() {
        return gyroscope;
    }

    /** The Technic Hub's tilt sensor, which returns the same object on every call. The hub subscribes it on connect. */
    public SampleProvider getTilt() {
        return tilt;
    }

    /**
     * The motor on the external port {@code port} (A to D), as an {@link io.github.jabrena.juno.robotics.EncoderMotor}.
     * The same object is returned on every call. Its position is subscribed on the first read after each connect.
     *
     * @throws IllegalArgumentException if {@code port} is one of the Technic Hub's built-in sensor ports
     */
    public PoweredUpMotor getMotor(HubPort port) {
        if (port.value() >= EXTERNAL_PORTS) {
            throw new IllegalArgumentException("only the external ports A to D have motors");
        }
        return motors[port.value()];
    }

    /** {@link #hubType} values: the LEGO system type id advertised by each hub. */
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_DUPLO_TRAIN_HUB = 0x20;
    public static final int TYPE_MOVE_HUB = 0x40;
    public static final int TYPE_CITY_HUB = 0x41;
    public static final int TYPE_REMOTE_CONTROL = 0x42;
    public static final int TYPE_MARIO = 0x43;
    public static final int TYPE_TECHNIC_HUB = 0x80;

    /** {@link #enableSensor} modes of a tacho motor (BOOST, Technic and SPIKE motors with a rotation sensor). */
    public static final int MODE_MOTOR_SPEED = 1;
    /** Cumulative motor position in degrees, relative to where the motor was when the hub started. */
    public static final int MODE_MOTOR_POSITION = 2;

    /**
     * {@link #enableSensor} modes of the Color and Distance Sensor (88007): the detected color as the
     * sensor's own color number, which mostly but not fully matches the {@link HubColor} LED values,
     * or {@code -1} when it detects none.
     */
    public static final int MODE_COLOR = 0;
    /** Proximity of the nearest object, {@code 0} (touching) to {@code 10} (nothing in range). */
    public static final int MODE_PROXIMITY = 1;

    /**
     * {@link #enableSensor} mode of each button set ({@link HubPort#A} left, {@link HubPort#B} right) of
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
    public boolean connect(int timeoutMillis) {
        boolean connected = hubPort.connect(timeoutMillis);
        if (connected) {
            for (PoweredUpMotor motor : motors) {
                motor.onConnect();
            }
        }
        if (connected && hubType() == TYPE_TECHNIC_HUB) {
            accelerometer.subscribe();
            gyroscope.subscribe();
            tilt.subscribe();
        }
        return connected;
    }

    /** Whether a hub is currently connected; turns {@code false} once the hub switches off or goes out of range. */
    public boolean isConnected() {
        return hubPort.isConnected();
    }

    /** The connected hub's {@code TYPE_*} system type id, or {@link #TYPE_UNKNOWN} before {@link #connect}. */
    public int hubType() {
        return hubPort.hubType();
    }

    /** Sets the hub's status LED to one of the {@link HubColor} values. */
    public void setLedColor(HubColor color) {
        hubPort.setLedColor(color.value());
    }

    /**
     * Asks the hub to report every change of {@code mode} of the motor or sensor on {@code port},
     * for {@link #readSensor} to return. Mode numbers are device specific; the {@code MODE_*}
     * constants cover common devices. Up to eight ports report at a time, and a new
     * {@link #connect} starts with none, so enable them again after reconnecting.
     */
    public void enableSensor(HubPort port, int mode) {
        hubPort.enableSensor(port.value(), mode);
    }

    /**
     * The latest value reported by the port {@link #enableSensor} subscribed to, or {@code 0}
     * before the first report. Decodes modes reporting a single 8-, 16- or 32-bit value; for a mode
     * reporting several values (e.g. a tilt sensor's axes) only its first byte is returned. Call it
     * regularly: values are received while it (or another hub call) runs, not during
     * {@code Delay.millis}.
     */
    public int readSensor(HubPort port) {
        return hubPort.readSensor(port.value());
    }

    /**
     * The hub's battery level as a percentage, {@code 0..100}, or {@code -1} before the hub has
     * reported it. This is the hub's own battery property, not a voltage; the first call after
     * {@link #connect} subscribes to the hub's properties and waits up to 300 ms for the answers,
     * later calls return the latest update without blocking.
     */
    public int batteryPercent() {
        return hubPort.batteryPercent();
    }

    /** Whether the hub's green button is held down right now. Subscribed on the first property call. */
    public boolean buttonPressed() {
        return hubPort.buttonPressed();
    }

    /** The Bluetooth signal strength the hub reports for this link in dBm (a negative number), or {@code 0} if unknown. */
    public int rssi() {
        return hubPort.rssi();
    }

    /**
     * The hub's firmware version as the protocol's packed number, or {@code -1} if unknown; take it apart
     * with {@link #versionMajor}, {@link #versionMinor}, {@link #versionBugfix} and {@link #versionBuild}.
     */
    public int firmwareVersion() {
        return hubPort.firmwareVersion();
    }

    /** The hub's hardware version in the same packed form as {@link #firmwareVersion}, or {@code -1} if unknown. */
    public int hardwareVersion() {
        return hubPort.hardwareVersion();
    }

    /** I/O device types {@link #portDevice} can return, from the LEGO Wireless Protocol. */
    public static final int DEVICE_NONE = 0;
    public static final int DEVICE_MEDIUM_LINEAR_MOTOR = 1;
    public static final int DEVICE_MOVE_HUB_MEDIUM_LINEAR_MOTOR = 2;
    public static final int DEVICE_MOTION_SENSOR = 35;
    public static final int DEVICE_COLOR_DISTANCE_SENSOR = 37;
    public static final int DEVICE_MEDIUM_ANGULAR_MOTOR = 38;
    public static final int DEVICE_LARGE_ANGULAR_MOTOR = 39;
    public static final int DEVICE_TECHNIC_LARGE_MOTOR = 46;
    public static final int DEVICE_TECHNIC_XL_MOTOR = 47;
    public static final int DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR = 48;
    public static final int DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR = 49;

    /**
     * The I/O device type the hub reported (Hub Attached I/O) for the external port {@code port}
     * ({@link HubPort#A} to {@link HubPort#D}), one of the {@code DEVICE_*} constants or another protocol type id, or
     * {@code 0} if nothing is attached, the hub has not reported it yet, or {@code port} is out of range.
     * The hub announces its ports right after {@link #connect} and again when something is plugged or
     * unplugged; the announcements are received while a hub call runs, so call this regularly.
     */
    public int portDevice(HubPort port) {
        return hubPort.portDevice(port.value());
    }

    /**
     * Copies the hub's advertised name (up to 20 characters, ASCII bytes, no terminator) into
     * {@code buffer}, at most {@code capacity} bytes, and returns how many were copied, {@code 0} if
     * the hub has not reported its name.
     */
    public int hubName(byte[] buffer, int capacity) {
        return hubPort.hubName(buffer, capacity);
    }

    /**
     * The parts of a packed {@link #firmwareVersion} or {@link #hardwareVersion}: 4 bits of major
     * version, 4 of minor, two binary-coded-decimal digits of bug-fix number, and 16 bits of build.
     */
    public int versionMajor(int version) {
        return (version >> 28) & 0xF;
    }

    /** The minor version of a packed version number: 4 bits. */
    public int versionMinor(int version) {
        return (version >> 24) & 0xF;
    }

    /** The bug-fix number of a packed version number, decoded from two binary-coded-decimal digits. */
    public int versionBugfix(int version) {
        int bcd = (version >> 16) & 0xFF;
        return (bcd >> 4) * 10 + (bcd & 0xF);
    }

    /** The 16-bit build number of a packed version number. */
    public int versionBuild(int version) {
        return version & 0xFFFF;
    }

    /** Mode 0 of the Technic Hub's three motion-sensor ports, which report three values each: see {@link PoweredUpAccelerometer}, {@link PoweredUpGyroscope} and {@link PoweredUpTilt}. */
    public static final int MODE_IMU_VALUES = 0;

    /**
     * The {@code index}-th value of the latest report of the port {@link #enableSensor} subscribed to,
     * as a signed integer of {@code bytesPerValue} bytes (1, 2 or 4, little endian). For modes that
     * report several values, such as the three axes of a motion sensor, read them one by one with
     * {@code index} 0, 1 and 2. Returns {@code 0} before the first report or when the report is too short
     * for that index; see {@link #sensorReportSize}.
     */
    public int readSensorValue(HubPort port, int index, int bytesPerValue) {
        return hubPort.readSensorValue(port.value(), index, bytesPerValue);
    }

    /**
     * How many reports the hub has sent for {@code port} since {@link #enableSensor} subscribed to it,
     * counting repeats of an unchanged value, so a program can measure how often the hub reports.
     */
    public int sensorReportCount(HubPort port) {
        return hubPort.sensorReportCount(port.value());
    }

    /** How many value bytes the latest report of {@code port} carried (at most 16), {@code 0} before the first. */
    public int sensorReportSize(HubPort port) {
        return hubPort.sensorReportSize(port.value());
    }

    /** Sets the hub's status LED to any colour, each channel {@code 0..255}; unlike {@link #setLedColor}'s 11 indexes. */
    public void setLedRgb(int red, int green, int blue) {
        hubPort.setLedRgb(red, green, blue);
    }

    /**
     * Pairs the motors on ports {@code portA} and {@code portB} into one virtual port, so a single
     * command ({@link #setLinkedMotorPower}) drives both in sync, as for a two-motor vehicle. The hub
     * assigns the virtual port's number, which is returned, or {@code -1} if the hub did not create it
     * within 500 ms. Pairing the same two ports again returns the existing virtual port; a new
     * {@link #connect} starts with none. The message formats follow the protocol documentation and have
     * not been verified on a hub.
     */
    public int linkMotors(HubPort portA, HubPort portB) {
        return hubPort.linkMotors(portA.value(), portB.value());
    }

    /** Takes the virtual port {@code virtualPort} apart, so the two motors are driven separately again. */
    public void unlinkMotors(int virtualPort) {
        hubPort.unlinkMotors(virtualPort);
    }

    /**
     * Drives the two motors of a virtual port in one message: {@code first} for the motor on the first
     * port given to {@link #linkMotors} and {@code second} for the other, each {@code -100..100}
     * (clamped), negative for backwards, {@code 0} to coast. Mount mirrored motors with opposite signs.
     */
    public void setLinkedMotorPower(int virtualPort, int first, int second) {
        hubPort.setLinkedMotorPower(virtualPort, first, second);
    }

    /** Actively brakes both motors of a virtual port. */
    public void brakeLinkedMotors(int virtualPort) {
        hubPort.brakeLinkedMotors(virtualPort);
    }


    /**
     * The Technic Hub's battery: its charge level, supply voltage and supply current. The same object is returned on
     * every call.
     */
    public PoweredUpBattery getBattery() {
        return battery;
    }

    /** Closes the BLE connection, leaving the hub on and advertising again. */
    public void disconnect() {
        hubPort.disconnect();
    }

    /** Asks the hub to switch itself off, which also ends the connection. */
    public void switchOff() {
        hubPort.switchOff();
    }
}
