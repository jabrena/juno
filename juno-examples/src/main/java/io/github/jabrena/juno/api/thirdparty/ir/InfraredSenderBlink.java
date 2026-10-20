package io.github.jabrena.juno.api.thirdparty.ir;

import io.github.jabrena.juno.api.hardware.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Checks an IR transmitter module on its own: it sends the 38 kHz carrier for one second, in short bursts
 * as a remote control does, then stays dark for one second, forever, printing {@code on} and {@code off} as
 * it goes. Wire the transmitter's DAT to pin 3, with VCC and GND. Infrared light is invisible to the eye
 * but most phone cameras show it: look at the LED through the camera (the front one if the rear filters
 * infrared) and it glows faintly purple during {@code on}. With the receiver wired to pin 2 and
 * {@link InfraredReceiverProbe} on a second board, the bursts are counted there too. Pick the board with
 * {@code -Djuno.board}.
 */
public class InfraredSenderBlink {
    private static final int NO_PIN = -1;
    private static final int TRANSMIT_PIN = 3;
    private static final int BAUD = 2400;
    private static final int BURST_MICROS = 600;
    private static final int GAP_MICROS = 600;
    private static final int BURSTS_PER_SECOND = 833;
    private static final int OFF_MILLIS = 1000;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Infrared.begin(NO_PIN, TRANSMIT_PIN, BAUD);
        Delay.millis(START_MILLIS);
        Serial.println("IR sender blink: watch the LED with a phone camera");

        while (true) {
            Serial.println("on");
            for (int i = 0; i < BURSTS_PER_SECOND; i++) {
                Infrared.mark(BURST_MICROS);
                Infrared.space(GAP_MICROS);
            }
            Serial.println("off");
            Delay.millis(OFF_MILLIS);
        }
    }
}
