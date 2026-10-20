package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

/**
 * The classic bump and go robot: a two-motor RCX vehicle drives forwards until one of its two
 * touch sensors hits something, then stops, backs up, turns away from the obstacle and drives on.
 * The left motor is on output A and the right motor on output B of an RCX in remote-control mode; the
 * left touch sensor is on its input 1 and the right one on input 3, read back over infrared, so
 * the Arduino needs no sensor wiring. Wire an IR LED with a series resistor to pin 3 and an IR receiver
 * module (38 kHz demodulator) to pin 2, both pointed at the RCX. Each bump check is two request and
 * reply round trips with the brick, so a bump is noticed a fraction of a second late.
 * Pick the board with {@code -Djuno.board}.
 */
public class RcxBumpAndGo {
    private static final Clock CLOCK = MCU.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int LEFT_BUMPER = RcxBrick.INPUT_1;
    private static final int RIGHT_BUMPER = RcxBrick.INPUT_3;
    private static final int BACK_MILLIS = 1000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 500;

    private final RcxLink link;
    private final RcxRemote remote;
    private final RcxBrick brick;

    RcxBumpAndGo(RcxLink link, RcxRemote remote, RcxBrick brick) {
        this.link = link;
        this.remote = remote;
        this.brick = brick;
    }

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        RcxBumpAndGo app = new RcxBumpAndGo(link, remote, brick);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        if (!brick.setTouchSensor(LEFT_BUMPER) || !brick.setTouchSensor(RIGHT_BUMPER)) {
            Serial.println("The RCX did not answer: is it in range, with the IR receiver wired?");
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
        if (brick.isPressed(LEFT_BUMPER)) {
            pressed += 1;
        }
        if (brick.isPressed(RIGHT_BUMPER)) {
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
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            move(left, right);
        }
    }

    /** Drives each side {@code -1} (backwards), {@code 0} (stopped) or {@code 1} (forwards) with a remote command. */
    private void move(int left, int right) {
        int buttons = 0;
        if (left > 0) {
            buttons |= RcxRemote.A_FORWARD;
        } else if (left < 0) {
            buttons |= RcxRemote.A_BACKWARD;
        }
        if (right > 0) {
            buttons |= RcxRemote.B_FORWARD;
        } else if (right < 0) {
            buttons |= RcxRemote.B_BACKWARD;
        }
        remote.sendButtons(buttons);
    }
}
