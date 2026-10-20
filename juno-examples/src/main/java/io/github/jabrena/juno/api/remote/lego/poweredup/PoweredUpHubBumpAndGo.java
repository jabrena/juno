package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
import io.github.jabrena.juno.util.Delay;

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
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final int LEFT_BUMPER_PIN = 4;
    private static final int RIGHT_BUMPER_PIN = 5;
    private static final int LEFT = PoweredUpHubRemote.PORT_A;
    private static final int RIGHT = PoweredUpHubRemote.PORT_B;
    private static final int POWER = 40;
    private static final int POLL_MILLIS = 20;
    private static final int BACK_MILLIS = 1000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 500;

    private final Gpio gpio;
    private final Clock clock;

    PoweredUpHubBumpAndGo(Gpio gpio, Clock clock) {
        this.gpio = gpio;
        this.clock = clock;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpHubBumpAndGo app = new PoweredUpHubBumpAndGo(MCU.getGpio(), MCU.getClock());
        app.gpio.pinMode(LEFT_BUMPER_PIN, Gpio.getInputPullup());
        app.gpio.pinMode(RIGHT_BUMPER_PIN, Gpio.getInputPullup());
        Serial.println("Waiting for a Powered Up hub...");
        HUB.connect(0);

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
        if (!gpio.digitalRead(LEFT_BUMPER_PIN)) {
            pressed += 1;
        }
        if (!gpio.digitalRead(RIGHT_BUMPER_PIN)) {
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
        int started = clock.millis();
        while (clock.millis() - started < millis) {
            move(left, right);
        }
    }

    /** Drives each side {@code -1} (backwards), {@code 0} (stopped) or {@code 1} (forwards) at the cruising power. */
    private void move(int left, int right) {
        if (left == 0 && right == 0) {
            HUB.brakeMotor(LEFT);
            HUB.brakeMotor(RIGHT);
        } else {
            HUB.setMotorPower(LEFT, left * POWER);
            HUB.setMotorPower(RIGHT, right * POWER);
        }
        Delay.millis(POLL_MILLIS);
    }
}
