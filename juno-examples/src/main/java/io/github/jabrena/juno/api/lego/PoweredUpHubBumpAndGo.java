package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * The classic bump and go robot: a two-motor LEGO vehicle drives forwards until one of its two
 * touch sensors hits something, then stops, backs up, turns away from the obstacle and drives on.
 * The left motor is on port A and the right motor on port B of a Technic Hub, Move Hub or City Hub. The touch sensors are plain
 * switches wired to the Arduino, which carries the vehicle: left bumper between pin 4 and GND, right
 * bumper between pin 5 and GND (the pins use the internal pull-up, so a pressed switch reads low).
 * The hub connects over Bluetooth LE, so no IR LED is needed.
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubBumpAndGo {
    private static final int LEFT_BUMPER_PIN = 4;
    private static final int RIGHT_BUMPER_PIN = 5;
    private static final int LEFT = PoweredUpHubRemote.PORT_A;
    private static final int RIGHT = PoweredUpHubRemote.PORT_B;
    private static final int POWER = 40;
    private static final int POLL_MILLIS = 20;
    private static final int BACK_MILLIS = 1000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 500;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpHubBumpAndGo app = new PoweredUpHubBumpAndGo();
        Gpio.pinMode(LEFT_BUMPER_PIN, Gpio.INPUT_PULLUP);
        Gpio.pinMode(RIGHT_BUMPER_PIN, Gpio.INPUT_PULLUP);
        Serial.println("Waiting for a Powered Up hub...");
        PoweredUpHubRemote.connect(0);

        while (true) {
            int bumped = app.bumped();
            if (bumped == 0) {
                app.move(1, 1);
            } else {
                app.avoid(bumped);
            }
        }
    }

    /** Which bumper is pressed: 0 none, 1 left, 2 right, 3 both. */
    private int bumped() {
        int pressed = 0;
        if (!Gpio.digitalRead(LEFT_BUMPER_PIN)) {
            pressed += 1;
        }
        if (!Gpio.digitalRead(RIGHT_BUMPER_PIN)) {
            pressed += 2;
        }
        return pressed;
    }

    /** Stops, backs up, and spins away from the side that was hit (left when both were). */
    private void avoid(int bumped) {
        Serial.println(bumped == 1 ? "Bumped left" : bumped == 2 ? "Bumped right" : "Bumped both");
        move(0, 0);
        Delay.millis(STOP_MILLIS);
        moveFor(-1, -1, BACK_MILLIS);
        if (bumped == 1) {
            moveFor(1, -1, TURN_MILLIS);
        } else {
            moveFor(-1, 1, TURN_MILLIS);
        }
        move(0, 0);
    }

    private void moveFor(int left, int right, int millis) {
        int started = Clock.millis();
        while (Clock.millis() - started < millis) {
            move(left, right);
        }
    }

    /** Drives each side {@code -1} (backwards), {@code 0} (stopped) or {@code 1} (forwards) at the cruising power. */
    private void move(int left, int right) {
        if (left == 0 && right == 0) {
            PoweredUpHubRemote.brakeMotor(LEFT);
            PoweredUpHubRemote.brakeMotor(RIGHT);
        } else {
            PoweredUpHubRemote.setMotorPower(LEFT, left * POWER);
            PoweredUpHubRemote.setMotorPower(RIGHT, right * POWER);
        }
        Delay.millis(POLL_MILLIS);
    }
}
