package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Runs all three RCX outputs, A, B and C, forward at full power for five seconds with the brick's direct motor
 * commands, then stops them and does nothing more, printing each step and the brick's battery voltage over Serial.
 * The RCX needs no program of its own, only to be switched on, and works with LEGO's firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxDriveABC {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int ALL_OUTPUTS = RcxBrick.OUTPUT_A | RcxBrick.OUTPUT_B | RcxBrick.OUTPUT_C;
    private static final int RUN_SECONDS = 5;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        int battery = brick.batteryMillivolts();
        while (battery < 0) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(RETRY_MILLIS);
            battery = brick.batteryMillivolts();
        }
        printBattery(battery);

        Serial.println("A+B+C forward for 5 seconds");
        brick.drive(ALL_OUTPUTS, RcxBrick.MAX_POWER);
        Delay.seconds(RUN_SECONDS);
        brick.drive(ALL_OUTPUTS, 0);
        Serial.println("Stopped");
        printBattery(brick.batteryMillivolts());

        while (true) {
            Delay.seconds(RUN_SECONDS);
        }
    }

    /** Prints the battery voltage in pieces, so no string is allocated. */
    private static void printBattery(int millivolts) {
        Serial.print("RCX battery: ");
        Serial.print(millivolts);
        Serial.println(" mV");
    }
}
