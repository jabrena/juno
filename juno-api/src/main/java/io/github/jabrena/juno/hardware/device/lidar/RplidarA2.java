package io.github.jabrena.juno.hardware.device.lidar;

import io.github.jabrena.juno.hardware.device.usb.Cp210xSerial;
import io.github.jabrena.juno.util.Delay;

/**
 * A Slamtec RPLIDAR A2 (A2M8) 360-degree laser scanner reached through its USB adapter, whose CP2102 bridge sits on a
 * {@link io.github.jabrena.juno.hardware.device.usb.UsbHostShield}. The A2M8 talks at 115200 baud and sets its motor
 * speed from a {@code SET_MOTOR_PWM} command, which {@link #startMotor()} sends: without it the head stands still and
 * a scan is acknowledged but delivers no measurements.
 *
 * <pre>{@code
 * UsbHostShield usb = new UsbHostShield();
 * RplidarA2 lidar = new RplidarA2(new Cp210xSerial(usb));   // once, at startup
 * int[] scan = new int[RplidarA2.DEGREES];                  // distance in mm per whole degree, 0 = no return
 * if (usb.begin() && lidar.begin() && lidar.startScan()) {
 *     while (true) {
 *         if (lidar.poll(scan)) {
 *             // a full revolution is in scan
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Allocation: the constructor allocates the 360-entry working scan and two small buffers; {@link #poll(int[])} and
 * every other call are allocation-free. Call {@link #poll(int[])} continuously while scanning: the lidar sends about
 * 10 KB per second and the bridge buffers only half a second of it.
 *
 * @see <a href="https://bucket-download.slamtec.com/6494fd238cf5e0d881f56d914c6d1f355c0f582a/LR001_SLAMTEC_rplidar_protocol_v2.4_en.pdf">
 *     RPLIDAR interface protocol</a>
 */
public final class RplidarA2 {
    /** The A2M8's UART speed. */
    public static final int BAUD_RATE = 115_200;
    /** Entries of a scan array: one per whole degree, clockwise seen from above, 0 at the lidar's front. */
    public static final int DEGREES = 360;
    /** {@link #health()} result: the lidar works. */
    public static final int HEALTH_GOOD = 0;
    /** {@link #health()} result: the lidar works but reports a warning. */
    public static final int HEALTH_WARNING = 1;
    /** {@link #health()} result: the lidar is in protection stop; reset it (or power-cycle it) before scanning. */
    public static final int HEALTH_ERROR = 2;
    /** {@link #health()} result: the lidar did not answer. */
    public static final int HEALTH_NO_ANSWER = -1;

    /** The motor PWM {@link #startMotor()} sets: Slamtec's default, about 10 revolutions a second. */
    public static final int DEFAULT_MOTOR_PWM = 660;
    /** The largest motor PWM {@link #setMotorPwm(int)} accepts. */
    public static final int MAX_MOTOR_PWM = 1023;

    /** Entries {@link #info(int[])} fills: model, firmware major, firmware minor, hardware revision. */
    public static final int INFO_LENGTH = 4;

    private static final int SYNC = 0xA5;
    private static final int SYNC_RESPONSE = 0x5A;
    private static final int COMMAND_STOP = 0x25;
    private static final int COMMAND_RESET = 0x40;
    private static final int COMMAND_SCAN = 0x20;
    private static final int COMMAND_GET_INFO = 0x50;
    private static final int COMMAND_GET_HEALTH = 0x52;
    private static final int COMMAND_SET_MOTOR_PWM = 0xF0;
    private static final int TYPE_MEASUREMENT = 0x81;
    private static final int TYPE_INFO = 0x04;
    private static final int TYPE_HEALTH = 0x06;
    private static final int INFO_PAYLOAD = 20;
    private static final int HEALTH_PAYLOAD = 3;
    private static final int NODE_LENGTH = 5;
    private static final int RESPONSE_MILLIS = 500;
    private static final int STOP_SETTLE_MILLIS = 20;
    private static final int RESET_MILLIS = 1000;
    /** Bytes {@link #poll(int[])} handles before returning, so one call never runs for long: ~0.1 s of scanning. */
    private static final int POLL_BUDGET = 1024;

    private final Cp210xSerial serial;
    private final byte[] command = new byte[6];
    private final int[] window = new int[INFO_PAYLOAD];
    /** Distances of the revolution being collected, per whole degree. */
    private final int[] working = new int[DEGREES];
    private int filled;
    private boolean revolutionStarted;
    private int revolutions;
    private int nodes;
    /** Milliseconds the response being read has waited for bytes so far. */
    private int waitedMillis;

    /** A lidar behind {@code serial}, the adapter's bridge; nothing is sent until {@link #begin()}. */
    public RplidarA2(Cp210xSerial serial) {
        this.serial = serial;
    }

    /**
     * Opens the adapter's bridge at {@link #BAUD_RATE} and stops any scan still running. The motor stays as it was:
     * call {@link #startMotor()} before scanning.
     * Returns {@code false} when the adapter is not attached or is not a CP210x.
     */
    public boolean begin() {
        if (!serial.begin(BAUD_RATE)) {
            return false;
        }
        stop();
        return true;
    }

    /** Spins the motor up at {@link #DEFAULT_MOTOR_PWM}. Allow a second or two before scanning. */
    public boolean startMotor() {
        return setMotorPwm(DEFAULT_MOTOR_PWM);
    }

    /** Stops the motor; a running scan stops delivering measurements. */
    public boolean stopMotor() {
        return setMotorPwm(0);
    }

    /**
     * Sets the motor speed, {@code 0} (stopped) to {@link #MAX_MOTOR_PWM}; a value outside is clamped. Faster is a
     * shorter revolution with fewer points per degree. Returns {@code false} when the command could not be sent.
     */
    public boolean setMotorPwm(int pwm) {
        int clamped = pwm < 0 ? 0 : pwm > MAX_MOTOR_PWM ? MAX_MOTOR_PWM : pwm;
        command[0] = (byte) SYNC;
        command[1] = (byte) COMMAND_SET_MOTOR_PWM;
        command[2] = 2;
        command[3] = (byte) clamped;
        command[4] = (byte) (clamped >> 8);
        command[5] = (byte) (SYNC ^ COMMAND_SET_MOTOR_PWM ^ 2 ^ (clamped & 0xFF) ^ (clamped >> 8));
        return serial.write(command, 0, 6);
    }

    /** Stops scanning and drops what is still in flight. The lidar stays powered and its motor keeps spinning. */
    public void stop() {
        send(COMMAND_STOP);
        Delay.millis(STOP_SETTLE_MILLIS);
        serial.flush();
        filled = 0;
    }

    /** Reboots the lidar's core, which clears a protection stop; takes about a second. */
    public void reset() {
        send(COMMAND_RESET);
        Delay.millis(RESET_MILLIS);
        serial.flush();
        filled = 0;
    }

    /** The lidar's health: {@link #HEALTH_GOOD}, {@link #HEALTH_WARNING}, {@link #HEALTH_ERROR} or {@link #HEALTH_NO_ANSWER}. */
    public int health() {
        send(COMMAND_GET_HEALTH);
        if (!readResponse(TYPE_HEALTH, HEALTH_PAYLOAD)) {
            return HEALTH_NO_ANSWER;
        }
        return window[0];
    }

    /**
     * Fills {@code info} with the model, firmware major and minor version, and hardware revision. Returns
     * {@code false} when the lidar did not answer.
     */
    public boolean info(int[] info) {
        send(COMMAND_GET_INFO);
        if (!readResponse(TYPE_INFO, INFO_PAYLOAD)) {
            return false;
        }
        info[0] = window[0];
        info[1] = window[2];
        info[2] = window[1];
        info[3] = window[3];
        return true;
    }

    /** Starts the measurement stream. Returns {@code false} when the lidar does not acknowledge it. */
    public boolean startScan() {
        stop();
        send(COMMAND_SCAN);
        filled = 0;
        revolutionStarted = false;
        revolutions = 0;
        nodes = 0;
        clearWorking();
        return readResponse(TYPE_MEASUREMENT, 0);
    }

    /**
     * Reads what the lidar sent since the last call. When a revolution completes, copies it into {@code scan} (360
     * distances in millimetres, 0 where no return came back) and returns {@code true}; otherwise leaves {@code scan}
     * alone and returns {@code false}.
     */
    public boolean poll(int[] scan) {
        for (int budget = 0; budget < POLL_BUDGET; budget++) {
            int value = serial.read();
            if (value < 0) {
                return false;
            }
            window[filled] = value;
            filled++;
            if (filled == NODE_LENGTH) {
                if (isNode()) {
                    filled = 0;
                    if (takeNode(scan)) {
                        return true;
                    }
                } else {
                    shiftWindow();
                }
            }
        }
        return false;
    }

    /**
     * Whether the adapter is still open: {@code false} once it was unplugged or stopped answering, after which
     * {@link #begin()} enumerates it again.
     */
    public boolean isConnected() {
        return serial.isOpen();
    }

    /** Complete revolutions {@link #poll(int[])} has returned since the last {@link #startScan()}. */
    public int revolutions() {
        return revolutions;
    }

    /** Measurement nodes received since the last {@link #startScan()}, valid or not. */
    public int nodes() {
        return nodes;
    }

    /** Stores one node; returns {@code true} when it starts a new revolution and the previous one went to {@code scan}. */
    private boolean takeNode(int[] scan) {
        nodes++;
        boolean start = (window[0] & 1) != 0;
        boolean completed = false;
        if (start) {
            if (revolutionStarted) {
                for (int degree = 0; degree < DEGREES; degree++) {
                    scan[degree] = working[degree];
                }
                revolutions++;
                completed = true;
            }
            revolutionStarted = true;
            clearWorking();
        }
        int angle = (((window[1] >> 1) | (window[2] << 7)) >> 6) % DEGREES;
        int distance = (window[3] | (window[4] << 8)) >> 2;
        working[angle] = distance;
        return completed;
    }

    /** A node's start and inverse-start bits differ and its check bit is set. */
    private boolean isNode() {
        boolean start = (window[0] & 1) != 0;
        boolean inverseStart = (window[0] & 2) != 0;
        return start != inverseStart && (window[1] & 1) != 0;
    }

    /** Drops the oldest byte, so a stream joined mid-node still finds the node boundaries. */
    private void shiftWindow() {
        for (int i = 1; i < NODE_LENGTH; i++) {
            window[i - 1] = window[i];
        }
        filled = NODE_LENGTH - 1;
    }

    private void clearWorking() {
        for (int degree = 0; degree < DEGREES; degree++) {
            working[degree] = 0;
        }
    }

    private void send(int request) {
        command[0] = (byte) SYNC;
        command[1] = (byte) request;
        serial.write(command, 0, 2);
    }

    /**
     * Waits for the response descriptor {@code A5 5A len len len len type} of the given type, then reads
     * {@code payload} bytes into {@link #window}. Returns {@code false} on a time-out or another type.
     */
    private boolean readResponse(int type, int payload) {
        waitedMillis = 0;
        int previous = -1;
        int value = readWithinTimeout();
        while (value >= 0 && !(previous == SYNC && value == SYNC_RESPONSE)) {
            previous = value;
            value = readWithinTimeout();
        }
        if (value < 0) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            value = readWithinTimeout();
            if (value < 0) {
                return false;
            }
        }
        if (value != type) {
            return false;
        }
        for (int i = 0; i < payload; i++) {
            value = readWithinTimeout();
            if (value < 0) {
                return false;
            }
            window[i] = value;
        }
        return true;
    }

    /** The next byte, or -1 once the response has taken {@link #RESPONSE_MILLIS} in all. */
    private int readWithinTimeout() {
        while (true) {
            int value = serial.read();
            if (value >= 0) {
                return value;
            }
            if (waitedMillis >= RESPONSE_MILLIS) {
                return -1;
            }
            Delay.millis(1);
            waitedMillis++;
        }
    }
}
