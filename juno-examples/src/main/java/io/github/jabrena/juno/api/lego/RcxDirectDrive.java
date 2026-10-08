package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Drives an RCX brick's motors on outputs A and B with the brick's direct motor commands, which, unlike the
 * remote's buttons, have seven power levels: it ramps both motors up through the forward levels 1 to 7 and
 * back down, then does the same backwards, forever, printing the light sensor on input 2 (as a percentage)
 * and the battery voltage as it goes. Wire an IR LED with a series resistor to pin 3 and an IR receiver
 * module to pin 2 (the brick acknowledges each command), both pointed at the RCX. Each change of level
 * is several commands, so a step takes a few hundred milliseconds by itself. Pick the board with
 * {@code -Djuno.board}.
 */
public class RcxDirectDrive {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int MOTORS = RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_B;
    private static final int LIGHT_INPUT = RcxBrick.INPUT_2;
    private static final int STEP_MILLIS = 800;
    private static final int PAUSE_SECONDS = 2;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxDirectDrive app = new RcxDirectDrive();
        RcxRemote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        Serial.println("RCX battery: " + RcxBrick.batteryMillivolts() + " mV");
        if (!RcxBrick.setLightSensor(LIGHT_INPUT)) {
            Serial.println("The RCX did not answer: is it in range, with the IR receiver wired?");
            return;
        }

        while (true) {
            app.ramp(1);
            app.ramp(-1);
            Delay.seconds(PAUSE_SECONDS);
        }
    }

    /** Ramps the speed up to full in the given direction (1 forwards, -1 backwards), then back to a stop. */
    private void ramp(int direction) {
        for (int level = 1; level <= RcxBrick.MAX_POWER; level++) {
            step(direction * level);
        }
        for (int level = RcxBrick.MAX_POWER - 1; level >= 1; level--) {
            step(direction * level);
        }
        RcxBrick.drive(MOTORS, 0);
    }

    private void step(int speed) {
        RcxBrick.drive(MOTORS, speed);
        Serial.println("Speed " + speed + ", light " + RcxBrick.readSensor(LIGHT_INPUT) + " %");
        Delay.millis(STEP_MILLIS);
    }
}
