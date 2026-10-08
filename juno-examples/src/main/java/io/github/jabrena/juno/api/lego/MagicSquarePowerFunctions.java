package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives a two-motor Power Functions vehicle around a square over infrared, forever: straight for
 * 3 seconds, stop both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees,
 * and so on. The left motor is on the red output and the right motor on the blue output of an IR
 * Receiver 8884 set to channel 1; wire an IR LED with a series resistor to pin 3 and point it at the
 * receiver. The turn spins the vehicle on the spot for {@code TURN_MILLIS}, which you tune for your
 * vehicle. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquarePowerFunctions {
    private static final int TRANSMIT_PIN = 3;
    private static final int CHANNEL = PowerFunctionsRemote.CHANNEL_1;
    private static final int SPEED = 5;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquarePowerFunctions app = new MagicSquarePowerFunctions();
        PowerFunctionsRemote.begin(TRANSMIT_PIN);

        while (true) {
            Serial.println("Straight");
            app.drive(SPEED, SPEED, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.drive(-SPEED, SPEED, TURN_MILLIS);
            app.stop();
        }
    }

    /** Repeats the command, as a receiver stops a motor about a second after the last one it heard. */
    private void drive(int left, int right, int millis) {
        int started = Clock.millis();
        while (Clock.millis() - started < millis) {
            PowerFunctionsRemote.setSpeeds(CHANNEL, left, right);
        }
    }

    private void stop() {
        PowerFunctionsRemote.stop(CHANNEL);
        Delay.millis(STOP_MILLIS);
    }
}
