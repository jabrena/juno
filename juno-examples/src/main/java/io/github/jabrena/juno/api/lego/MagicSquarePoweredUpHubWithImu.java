package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives a two-motor vehicle around a square, forever: straight for 3 seconds, stop both motors, turn
 * left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on. The left motor is on
 * port A and the right motor on port B of a Technic Hub (88012), which carries the vehicle with its top
 * face up. The turn is closed-loop with the hub's own built-in tilt sensor: the vehicle spins on the
 * spot until the sensor's yaw angle has changed by 90 degrees, so it needs no timing calibration and no
 * extra sensor ({@code TURN_TIMEOUT_MILLIS} is only a safety stop). The turn stops when the yaw has
 * moved 90 degrees in either direction, so it does not depend on the sign convention of the sensor, but
 * if the vehicle spins the wrong way, swap the motors' power signs in {@code turnLeft}. The sensor's
 * port and values follow the community protocol documentation; run {@code PoweredUpHubImuMonitor} to see
 * them on your hub. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquarePoweredUpHubWithImu {
    private static final int LEFT = PoweredUpHubRemote.PORT_A;
    private static final int RIGHT = PoweredUpHubRemote.PORT_B;
    private static final int TILT = PoweredUpHubRemote.PORT_TECHNIC_TILT;
    private static final int POWER = 40;
    private static final int TURN_POWER = 30;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_DEGREES = 90;
    private static final int TURN_TIMEOUT_MILLIS = 4000;
    private static final int STOP_MILLIS = 2000;
    private static final int TILT_REPORT_BYTES = 6;
    private static final int SENSOR_WAIT_MILLIS = 1000;
    private static final int VALUE_BYTES = 2;
    private static final int HALF_TURN = 180;
    private static final int FULL_TURN = 360;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquarePoweredUpHubWithImu app = new MagicSquarePoweredUpHubWithImu();

        while (true) {
            if (!PoweredUpHubRemote.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHubRemote.connect(0);
                PoweredUpHubRemote.enableSensor(TILT, PoweredUpHubRemote.MODE_IMU_VALUES);
                app.waitForTilt();
            }
            Serial.println("Straight");
            PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_GREEN);
            app.drive(POWER, POWER, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_BLUE);
            app.turnLeft();
            app.stop();
        }
    }

    /** Gives the hub a moment to send its first tilt report, so the yaw is not read as 0 before it exists. */
    private void waitForTilt() {
        int started = Clock.millis();
        while (PoweredUpHubRemote.sensorReportSize(TILT) < TILT_REPORT_BYTES
                && Clock.millis() - started < SENSOR_WAIT_MILLIS) {
            PoweredUpHubRemote.readSensor(TILT);
        }
    }

    /** The tilt sensor's yaw angle in degrees, {@code -180..180} (the first of its three values). */
    private int yaw() {
        return PoweredUpHubRemote.readSensorValue(TILT, 0, VALUE_BYTES);
    }

    /** How many degrees the yaw has moved since {@code start}, as a signed angle {@code -180..180}. */
    private int turnedSince(int start) {
        return (yaw() - start + HALF_TURN + FULL_TURN) % FULL_TURN - HALF_TURN;
    }

    /** Spins on the spot until the yaw has moved TURN_DEGREES either way, or the safety timeout. */
    private void turnLeft() {
        int startYaw = yaw();
        PoweredUpHubRemote.setMotorPower(LEFT, -TURN_POWER);
        PoweredUpHubRemote.setMotorPower(RIGHT, TURN_POWER);
        int started = Clock.millis();
        while (PoweredUpHubRemote.isConnected() && Math.abs(turnedSince(startYaw)) < TURN_DEGREES
                && Clock.millis() - started < TURN_TIMEOUT_MILLIS) {
        }
    }

    private void drive(int left, int right, int millis) {
        PoweredUpHubRemote.setMotorPower(LEFT, left);
        PoweredUpHubRemote.setMotorPower(RIGHT, right);
        Delay.millis(millis);
    }

    private void stop() {
        PoweredUpHubRemote.brakeMotor(LEFT);
        PoweredUpHubRemote.brakeMotor(RIGHT);
        Delay.millis(STOP_MILLIS);
    }
}
