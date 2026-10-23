package io.github.jabrena.juno.api.remote.lego.scout;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.scout.RemoteScout;
import io.github.jabrena.juno.remote.lego.scout.ScoutMotor;
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

    private final ScoutMotor leftMotor;
    private final ScoutMotor rightMotor;
    private int lastLeft = 2;
    private int lastRight = 2;

    MagicSquareScout(RemoteScout scout) {
        this.leftMotor = scout.getMotorA();
        this.rightMotor = scout.getMotorB();
    }

    public static void main(String[] args) {
        RemoteScout scout = new RemoteScout();
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquareScout app = new MagicSquareScout(scout);
        scout.begin(-1, TRANSMIT_PIN);
        scout.setMode(RemoteScout.MODE_POWER);

        while (true) {
            Serial.println("Straight");
            app.moveFor(1, 1, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.moveFor(-1, 1, TURN_MILLIS);
            app.stop();
        }
    }

    private void moveFor(int left, int right, int millis) {
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            move(left, right);
        }
    }

    private void stop() {
        move(0, 0);
        Delay.millis(STOP_MILLIS);
    }

    /** Drives each side {@code -1} (backwards), {@code 0} (stopped) or {@code 1} (forwards). */
    private void move(int left, int right) {
        if (left != lastLeft) {
            leftMotor.drive(left);
            lastLeft = left;
        }
        if (right != lastRight) {
            rightMotor.drive(right);
            lastRight = right;
        }
    }
}
