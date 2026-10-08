package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.imu.Bno055;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives a two-motor RCX vehicle around a square over infrared, forever: straight for 3 seconds, stop both
 * motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on. The left motor is on output A and the right motor on output B of an RCX in remote-control mode; wire an IR LED with a series resistor to pin 3 and point it at the RCX.
 * The turn is closed-loop: the vehicle carries the Arduino with an Arduino 9 Axis Motion Shield, and
 * spins on the spot until its BNO055 reports 90 degrees anticlockwise, so it needs no timing calibration
 * ({@code TURN_TIMEOUT_MILLIS} is only a safety stop). Keep the vehicle still while the sensor starts.
 * Pick the board with {@code -Djuno.board}.
 */
public class MagicSquareRcxWithImu {
    private static final int TRANSMIT_PIN = 3;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_DEGREES = 90;
    private static final int TURN_TIMEOUT_MILLIS = 4000;
    private static final int STOP_MILLIS = 2000;
    private static final int REPEAT_MILLIS = 100;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquareRcxWithImu app = new MagicSquareRcxWithImu();
        if (!Bno055.begin()) {
            Serial.println("No BNO055 found: is the 9 Axis Motion Shield attached?");
            return;
        }
        RcxRemote.begin(-1, TRANSMIT_PIN);

        while (true) {
            Serial.println("Straight");
            app.drive(RcxRemote.A_FORWARD | RcxRemote.B_FORWARD, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.turnLeft();
            app.stop();
        }
    }

    /** Holds buttons on the virtual remote the way a real one does: a command every 100 ms. */
    private void drive(int buttons, int millis) {
        int started = Clock.millis();
        while (Clock.millis() - started < millis) {
            RcxRemote.sendButtons(buttons);
            Delay.millis(REPEAT_MILLIS);
        }
    }

    /** Spins left, sending commands until the sensor reports TURN_DEGREES anticlockwise, or the safety timeout. */
    private void turnLeft() {
        int startHeading = Bno055.headingDegrees();
        int started = Clock.millis();
        while (Bno055.turnedSince(startHeading) > -TURN_DEGREES && Clock.millis() - started < TURN_TIMEOUT_MILLIS) {
            RcxRemote.sendButtons(RcxRemote.A_BACKWARD | RcxRemote.B_FORWARD);
        }
    }

    private void stop() {
        RcxRemote.sendButtons(0);
        Delay.millis(STOP_MILLIS);
    }
}
