package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

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
    private static final RemotePoweredUpHub HUB = new RemotePoweredUpHub();
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final HubPort LEFT = HubPort.A;
    private static final HubPort RIGHT = HubPort.B;
    private static final HubPort TILT = HubPort.TECHNIC_TILT;
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
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                HUB.connect(0);
                HUB.enableSensor(TILT, RemotePoweredUpHub.MODE_IMU_VALUES);
                app.waitForTilt();
            }
            Serial.println("Straight");
            HUB.setLedColor(HubColor.GREEN);
            app.drive(POWER, POWER, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            HUB.setLedColor(HubColor.BLUE);
            app.turnLeft();
            app.stop();
        }
    }

    /** Gives the hub a moment to send its first tilt report, so the yaw is not read as 0 before it exists. */
    private void waitForTilt() {
        int started = CLOCK.millis();
        while (HUB.sensorReportSize(TILT) < TILT_REPORT_BYTES
                && CLOCK.millis() - started < SENSOR_WAIT_MILLIS) {
            HUB.readSensor(TILT);
        }
    }

    /** The tilt sensor's yaw angle in degrees, {@code -180..180} (the first of its three values). */
    private int yaw() {
        return HUB.readSensorValue(TILT, 0, VALUE_BYTES);
    }

    /** How many degrees the yaw has moved since {@code start}, as a signed angle {@code -180..180}. */
    private int turnedSince(int start) {
        return (yaw() - start + HALF_TURN + FULL_TURN) % FULL_TURN - HALF_TURN;
    }

    /** Spins on the spot until the yaw has moved TURN_DEGREES either way, or the safety timeout. */
    private void turnLeft() {
        int startYaw = yaw();
        HUB.getMotor(LEFT).drive(-TURN_POWER);
        HUB.getMotor(RIGHT).drive(TURN_POWER);
        int started = CLOCK.millis();
        while (HUB.isConnected() && Math.abs(turnedSince(startYaw)) < TURN_DEGREES
                && CLOCK.millis() - started < TURN_TIMEOUT_MILLIS) {
        }
    }

    private void drive(int left, int right, int millis) {
        HUB.getMotor(LEFT).drive(left);
        HUB.getMotor(RIGHT).drive(right);
        Delay.millis(millis);
    }

    private void stop() {
        HUB.getMotor(LEFT).stop();
        HUB.getMotor(RIGHT).stop();
        Delay.millis(STOP_MILLIS);
    }
}
