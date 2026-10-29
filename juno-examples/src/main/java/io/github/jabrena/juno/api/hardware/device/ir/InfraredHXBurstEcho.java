package io.github.jabrena.juno.api.hardware.device.ir;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks that an IR transmitter and receiver on the same board hear each other, at the simplest level before
 * {@link InfraredHXLoopback} tries whole bytes: it sends 100 single bursts of the 38 kHz carrier for each of a few
 * lengths and counts how many the receiver reports within 600 microseconds after each ends (its output lags the light), as {@code 600 us: hits/100}, plus a
 * {@code noise} line counting the same samples with the LED dark. Wire the transmitter's DAT to pin 3 and the
 * receiver's OUT to pin 2, with VCC and GND, and point the modules at each other a few centimetres apart (or both
 * at a sheet of white paper).
 *
 * <p>Hits near 100 with noise near 0 means the pair works; hits near 0 everywhere means no light reaches the
 * receiver (aim, distance or the transmitter's wiring); hits as high as the noise means the receiver is
 * reporting interference, not the transmitter. Pick the board with {@code -Djuno.board}.
 */
public class InfraredHXBurstEcho {
    private final Clock clock = ArduinoUno.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int BAUD = 2400;
    private static final int TRIES = 100;
    private static final int LENGTHS = 4;
    private static final int GAP_MICROS = 3000;
    private static final int LISTEN_MICROS = 600;
    private static final int ROUND_PAUSE_SECONDS = 3;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        new InfraredHXBurstEcho().run();
    }

    private void run() {
        Serial.begin(BaudRate.BAUD_115200);
        int[] lengths = {200, 400, 600, 1000};
        Infrared.beginHX(RECEIVE_PIN, TRANSMIT_PIN, BAUD);
        Delay.millis(START_MILLIS);

        while (true) {
            Serial.println("Infrared burst echo");
            Infrared.space(GAP_MICROS);
            Infrared.mark(417);
            int started = clock.micros();
            int firstLow = -1;
            int lastLow = -1;
            while (clock.micros() - started < 2000) {
                int elapsed = clock.micros() - started;
                if (Infrared.receiving()) {
                    if (firstLow < 0) {
                        firstLow = elapsed;
                    }
                    lastLow = elapsed;
                }
            }
            Serial.print("417 us response: ");
            Serial.print(firstLow);
            Serial.print("..");
            Serial.print(lastLow);
            Serial.println(" us after mark");
            Serial.print("noise: ");
            Serial.print(countHits(0));
            Serial.println("/100");
            for (int i = 0; i < LENGTHS; i++) {
                Serial.print(lengths[i]);
                Serial.print(" us: ");
                Serial.print(countHits(lengths[i]));
                Serial.println("/100");
            }
            Delay.seconds(ROUND_PAUSE_SECONDS);
        }
    }

    /** How many of 100 bursts of {@code micros} (0 sends none) the receiver reports within the listening window. */
    private int countHits(int micros) {
        int hits = 0;
        for (int i = 0; i < TRIES; i++) {
            Infrared.space(GAP_MICROS);
            if (micros > 0) {
                Infrared.mark(micros);
            }
            if (heard()) {
                hits++;
            }
        }
        return hits;
    }

    /** Whether the receiver reports a burst at any point in the next {@value #LISTEN_MICROS} microseconds. */
    private boolean heard() {
        int started = clock.micros();
        while (clock.micros() - started < LISTEN_MICROS) {
            if (Infrared.receiving()) {
                return true;
            }
        }
        return false;
    }
}
