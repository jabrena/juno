package io.github.jabrena.juno.api.remote.lego.scout;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.remote.lego.scout.RemoteScout;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-motor Scout brick vehicle around a square over infrared, forever: straight for 3 seconds, stop
 * both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on.
 * The left motor is on output A and the right motor on output B, with the Scout in power mode. Wire an IR LED with a series
 * resistor to pin 3 and point it at the Scout brick. The turn spins the vehicle on the spot for
 * {@code TURN_MILLIS}, which you tune for your vehicle. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquareScout {
    private static final Clock CLOCK = Arduino.getClock();
    private static final int TRANSMIT_PIN = 3;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;
    private static final int REPEAT_MILLIS = 100;

    private final RemoteScout scout;

    MagicSquareScout(RemoteScout scout) {
        this.scout = scout;
    }

    public static void main(String[] args) {
        RemoteScout scout = new RemoteScout();
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquareScout app = new MagicSquareScout(scout);
        scout.begin(-1, TRANSMIT_PIN);
        scout.setMode(RemoteScout.MODE_POWER);

        while (true) {
            Serial.println("Straight");
            app.drive(RemoteRcx.A_FORWARD | RemoteRcx.B_FORWARD, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.drive(RemoteRcx.A_BACKWARD | RemoteRcx.B_FORWARD, TURN_MILLIS);
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

    private void stop() {
        scout.sendButtons(0);
        scout.stopAll();
        Delay.millis(STOP_MILLIS);
    }
}
