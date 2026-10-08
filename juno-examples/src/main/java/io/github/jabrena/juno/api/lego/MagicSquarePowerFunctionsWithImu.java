package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.imu.Bno055;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives a two-motor Power Functions vehicle around a square over infrared, forever: straight for
 * 3 seconds, stop both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees,
 * and so on. The left motor is on the red output and the right motor on the blue output of an IR
 * Receiver 8884 set to channel 1; wire an IR LED with a series resistor to pin 3 and point it at the
 * receiver. The turn is closed-loop: the vehicle carries the Arduino with an Arduino 9 Axis Motion
 * Shield, and spins on the spot until its BNO055 reports 90 degrees anticlockwise, so it needs no
 * timing calibration ({@code TURN_TIMEOUT_MILLIS} is only a safety stop). Keep the vehicle still while
 * the sensor starts. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquarePowerFunctionsWithImu {
    private static final int TRANSMIT_PIN = 3;
    private static final int CHANNEL = PowerFunctionsRemote.CHANNEL_1;
    private static final int SPEED = 5;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_DEGREES = 90;
    private static final int TURN_TIMEOUT_MILLIS = 4000;
    private static final int STOP_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquarePowerFunctionsWithImu app = new MagicSquarePowerFunctionsWithImu();
        if (!Bno055.begin()) {
            Serial.println("No BNO055 found: is the 9 Axis Motion Shield attached?");
            return;
        }
        PowerFunctionsRemote.begin(TRANSMIT_PIN);

        while (true) {
            Serial.println("Straight");
            app.drive(STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.turnLeft();
            app.stop();
        }
    }

    /** Repeats the command, as a receiver stops a motor about a second after the last one it heard. */
    private void drive(int millis) {
        int started = Clock.millis();
        while (Clock.millis() - started < millis) {
            PowerFunctionsRemote.setSpeeds(CHANNEL, SPEED, SPEED);
        }
    }

    /** Spins left, sending commands until the sensor reports TURN_DEGREES anticlockwise, or the safety timeout. */
    private void turnLeft() {
        int startHeading = Bno055.headingDegrees();
        int started = Clock.millis();
        while (Bno055.turnedSince(startHeading) > -TURN_DEGREES && Clock.millis() - started < TURN_TIMEOUT_MILLIS) {
            PowerFunctionsRemote.setSpeeds(CHANNEL, -SPEED, SPEED);
        }
    }

    private void stop() {
        PowerFunctionsRemote.stop(CHANNEL);
        Delay.millis(STOP_MILLIS);
    }
}
