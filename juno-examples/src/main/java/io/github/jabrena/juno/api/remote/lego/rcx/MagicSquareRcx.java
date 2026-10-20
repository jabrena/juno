package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-motor RCX brick vehicle around a square over infrared, forever: straight for 3 seconds, stop
 * both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on.
 * The left motor is on output A and the right motor on output B (in remote-control mode). Wire an IR LED with a series
 * resistor to pin 3 and an IR receiver module to pin 2, as for the other RCX examples, and point them at the RCX
 * brick; the remote-control commands get no answer, so the receiver is not needed. The turn spins the vehicle on the spot for
 * {@code TURN_MILLIS}, which you tune for your vehicle. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquareRcx {
    private static final Clock CLOCK = MCU.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;
    private static final int REPEAT_MILLIS = 100;

    private final RemoteRcx remote;

    MagicSquareRcx(RemoteRcx remote) {
        this.remote = remote;
    }

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        MagicSquareRcx app = new MagicSquareRcx(remote);

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
            remote.sendButtons(buttons);
            Delay.millis(REPEAT_MILLIS);
        }
    }

    private void stop() {
        remote.sendButtons(0);
        Delay.millis(STOP_MILLIS);
    }
}
