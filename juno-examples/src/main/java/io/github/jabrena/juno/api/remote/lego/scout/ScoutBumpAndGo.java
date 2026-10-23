package io.github.jabrena.juno.api.remote.lego.scout;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.scout.RemoteScout;
import io.github.jabrena.juno.remote.lego.scout.ScoutMotor;
import io.github.jabrena.juno.util.Delay;

/**
 * The classic bump and go robot: a two-motor Scout vehicle drives forwards until one of its two
 * touch sensors hits something, then stops, backs up, turns away from the obstacle and drives on.
 * The left motor is on output A and the right motor on output B of a Scout in power mode; the left
 * touch sensor is on its input 1 and the right one on input 2, read back over infrared, so the Arduino
 * needs no sensor wiring. Wire an IR LED with a series resistor to pin 3 and an IR receiver module
 * (38 kHz demodulator) to pin 2, both pointed at the Scout, which beeps on every bump. Each bump check
 * is two request and reply round trips with the brick, so a bump is noticed a fraction of a second late.
 * Pick the board with {@code -Djuno.board}.
 */
public class ScoutBumpAndGo {
    private static final Clock CLOCK = Arduino.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int BUMP_SOUND = RemoteScout.SOUND_TOUCH_1_PRESSED;
    private static final int BACK_MILLIS = 1000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 500;

    private final RemoteScout scout;
    private final ScoutMotor leftMotor;
    private final ScoutMotor rightMotor;
    private final RcxTouchSensor leftBumper;
    private final RcxTouchSensor rightBumper;
    private int lastLeft = 2;
    private int lastRight = 2;

    ScoutBumpAndGo(RemoteScout scout) {
        this.scout = scout;
        this.leftMotor = scout.getMotorA();
        this.rightMotor = scout.getMotorB();
        this.leftBumper = new RcxTouchSensor(scout.getSensor1());
        this.rightBumper = new RcxTouchSensor(scout.getSensor2());
    }

    public static void main(String[] args) {
        RemoteScout scout = new RemoteScout();
        Serial.begin(BaudRate.BAUD_115200);
        ScoutBumpAndGo app = new ScoutBumpAndGo(scout);
        scout.begin(RECEIVE_PIN, TRANSMIT_PIN);
        scout.setMode(RemoteScout.MODE_POWER);
        if (!app.leftBumper.begin() || !app.rightBumper.begin()) {
            Serial.println("The Scout did not answer: is it in range, with the IR receiver wired?");
            return;
        }

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
        if (leftBumper.isPressed()) {
            pressed += 1;
        }
        if (rightBumper.isPressed()) {
            pressed += 2;
        }
        return pressed;
    }

    /** Stops, backs up, and spins away from the side that was hit (left when both were). */
    private void avoid(int bumped) {
        Serial.println(bumped == 1 ? "Bumped left" : bumped == 2 ? "Bumped right" : "Bumped both");
        move(0, 0);
        scout.playSound(BUMP_SOUND);
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
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            move(left, right);
        }
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
