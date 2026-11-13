package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.robotics.Gyroscope;
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
 * port and values follow the community protocol documentation and are not verified on hardware.
 * Pick the board with {@code -Djuno.board}.
 */
public class MagicSquarePoweredUpHubWithGyro {
    private static final PoweredUpPort LEFT = PoweredUpPort.A;
    private static final PoweredUpPort RIGHT = PoweredUpPort.B;
    private static final int POWER = 40;
    private static final int TURN_POWER = 30;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_DEGREES = 90;
    private static final int TURN_TIMEOUT_MILLIS = 4000;
    private static final int STOP_MILLIS = 2000;
    private static final int HALF_TURN = 180;
    private static final int FULL_TURN = 360;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Clock clock = ArduinoUno.getClock();
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), clock);
        MagicSquarePoweredUpHubWithGyro app = new MagicSquarePoweredUpHubWithGyro();

        while (true) {
            if (!hub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                hub.connect(0);
            }
            Serial.println("Straight");
            hub.setLedColor(HubColor.GREEN);
            app.drive(hub, POWER, POWER, STRAIGHT_MILLIS);
            app.stop(hub);
            Serial.println("Turn left");
            hub.setLedColor(HubColor.BLUE);
            app.turnLeft(hub, clock);
            app.stop(hub);
        }
    }

    /** The heading change since the last {@code reset}, as a signed angle {@code -180..180}. */
    private int turnedDegrees(Gyroscope gyroscope) {
        return (gyroscope.getAngle() + HALF_TURN) % FULL_TURN - HALF_TURN;
    }

    /** Spins on the spot until the gyroscope's heading has moved TURN_DEGREES either way, or the safety timeout. */
    private void turnLeft(RemotePoweredUpHub hub, Clock clock) {
        Gyroscope gyroscope = hub.getGyroscope();
        gyroscope.reset();
        hub.getMotor(LEFT).drive(-TURN_POWER);
        hub.getMotor(RIGHT).drive(TURN_POWER);
        int started = clock.millis();
        while (hub.isConnected() && Math.abs(turnedDegrees(gyroscope)) < TURN_DEGREES
                && clock.millis() - started < TURN_TIMEOUT_MILLIS) {
        }
    }

    private void drive(RemotePoweredUpHub hub, int left, int right, int millis) {
        hub.getMotor(LEFT).drive(left);
        hub.getMotor(RIGHT).drive(right);
        Delay.millis(millis);
    }

    private void stop(RemotePoweredUpHub hub) {
        hub.getMotor(LEFT).stop();
        hub.getMotor(RIGHT).stop();
        Delay.millis(STOP_MILLIS);
    }
}
