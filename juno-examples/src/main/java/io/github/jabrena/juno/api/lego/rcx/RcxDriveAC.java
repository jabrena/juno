package io.github.jabrena.juno.api.lego.rcx;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Remote-controls a two-motor RCX vehicle from the Arduino with the brick's direct motor commands: the left
 * motor on output A and the right motor on output C drive forwards, backwards, spin left and spin right, each
 * for a moment with a stop in between, forever, printing each move and the brick's battery voltage over
 * Serial. The RCX needs no program of its own, only to be switched on.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. The brick acknowledges each command through the
 * receiver, so a move takes a few hundred milliseconds to set up. Pick the board with {@code -Djuno.board}.
 */
public class RcxDriveAC {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int LEFT = RcxBrick.OUTPUT_A;
    private static final int RIGHT = RcxBrick.OUTPUT_C;
    private static final int ALL_OUTPUTS = RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_B | RcxBrick.OUTPUT_C;
    private static final int SPEED = RcxBrick.MAX_POWER;
    private static final int MOVE_MILLIS = 1500;
    private static final int STOP_MILLIS = 700;
    private static final int PAUSE_SECONDS = 3;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        RcxDriveAC app = new RcxDriveAC();
        RcxRemote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        int battery = RcxBrick.batteryMillivolts();
        while (battery < 0) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(RETRY_MILLIS);
            battery = RcxBrick.batteryMillivolts();
        }
        app.printBattery(battery);
        RcxBrick.motorOff(ALL_OUTPUTS);

        while (true) {
            app.move("Forward", SPEED, SPEED);
            app.move("Backward", -SPEED, -SPEED);
            app.move("Spin left", -SPEED, SPEED);
            app.move("Spin right", SPEED, -SPEED);
            app.printBattery(RcxBrick.batteryMillivolts());
            Delay.seconds(PAUSE_SECONDS);
        }
    }

    /** Prints the battery voltage in pieces, so the main loop allocates no string. */
    private void printBattery(int millivolts) {
        Serial.print("RCX battery: ");
        Serial.print(millivolts);
        Serial.println(" mV");
    }

    /** Runs the left (A) and right (C) motors at the given speeds, {@code -7..7}, for a moment, then brakes both. */
    private void move(String name, int left, int right) {
        Serial.println(name);
        if (left == right) {
            RcxBrick.drive(LEFT | RIGHT, left);
        } else {
            RcxBrick.drive(LEFT, left);
            RcxBrick.drive(RIGHT, right);
        }
        Delay.millis(MOVE_MILLIS);
        RcxBrick.drive(LEFT | RIGHT, 0);
        Delay.millis(STOP_MILLIS);
    }
}
