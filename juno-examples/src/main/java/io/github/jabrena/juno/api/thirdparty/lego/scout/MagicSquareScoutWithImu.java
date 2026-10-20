package io.github.jabrena.juno.api.thirdparty.lego.scout;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.thirdparty.imu.NineAxisMotionShield;
import io.github.jabrena.juno.thirdparty.lego.rcx.RcxRemote;
import io.github.jabrena.juno.thirdparty.lego.scout.ScoutRemote;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-motor Scout vehicle around a square over infrared, forever: straight for 3 seconds, stop both
 * motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on. The left motor is on output A and the right motor on output B of a Scout in power mode; wire an IR LED with a series resistor to pin 3 and point it at the Scout.
 * The turn is closed-loop: the vehicle carries the Arduino with an Arduino 9 Axis Motion Shield, and
 * spins on the spot until its BNO055 reports 90 degrees anticlockwise, so it needs no timing calibration
 * ({@code TURN_TIMEOUT_MILLIS} is only a safety stop). Keep the vehicle still while the sensor starts.
 * Pick the board with {@code -Djuno.board}.
 */
public class MagicSquareScoutWithImu {
    private static final NineAxisMotionShield IMU = new NineAxisMotionShield();
    private static final Clock CLOCK = MCU.getClock();
    private static final int TRANSMIT_PIN = 3;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_DEGREES = 90;
    private static final int TURN_TIMEOUT_MILLIS = 4000;
    private static final int STOP_MILLIS = 2000;
    private static final int REPEAT_MILLIS = 100;

    private final ScoutRemote scout;

    MagicSquareScoutWithImu(ScoutRemote scout) {
        this.scout = scout;
    }

    public static void main(String[] args) {
        ScoutRemote scout = new ScoutRemote();
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquareScoutWithImu app = new MagicSquareScoutWithImu(scout);
        if (!IMU.begin()) {
            Serial.println("No BNO055 found: is the 9 Axis Motion Shield attached?");
            return;
        }
        scout.begin(-1, TRANSMIT_PIN);
        scout.setMode(ScoutRemote.MODE_POWER);

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
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            scout.sendButtons(buttons);
            Delay.millis(REPEAT_MILLIS);
        }
    }

    /** Spins left, sending commands until the sensor reports TURN_DEGREES anticlockwise, or the safety timeout. */
    private void turnLeft() {
        int startHeading = IMU.headingDegrees();
        int started = CLOCK.millis();
        while (IMU.turnedSince(startHeading) > -TURN_DEGREES && CLOCK.millis() - started < TURN_TIMEOUT_MILLIS) {
            scout.sendButtons(RcxRemote.A_BACKWARD | RcxRemote.B_FORWARD);
        }
    }

    private void stop() {
        scout.sendButtons(0);
        scout.stopAll();
        Delay.millis(STOP_MILLIS);
    }
}
