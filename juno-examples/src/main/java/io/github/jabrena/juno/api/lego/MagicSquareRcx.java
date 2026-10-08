package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives a two-motor RCX brick vehicle around a square over infrared, forever: straight for 3 seconds, stop
 * both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on.
 * The left motor is on output A and the right motor on output B (in remote-control mode). Wire an IR LED with a series
 * resistor to pin 3 and point it at the RCX brick. The turn spins the vehicle on the spot for
 * {@code TURN_MILLIS}, which you tune for your vehicle. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquareRcx {
    private static final int TRANSMIT_PIN = 3;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;
    private static final int REPEAT_MILLIS = 100;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquareRcx app = new MagicSquareRcx();
        RcxRemote.begin(-1, TRANSMIT_PIN);

        while (true) {
            Serial.println("Straight");
            app.drive(RcxRemote.A_FORWARD | RcxRemote.B_FORWARD, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.drive(RcxRemote.A_BACKWARD | RcxRemote.B_FORWARD, TURN_MILLIS);
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

    private void stop() {
        RcxRemote.sendButtons(0);
        Delay.millis(STOP_MILLIS);
    }
}
