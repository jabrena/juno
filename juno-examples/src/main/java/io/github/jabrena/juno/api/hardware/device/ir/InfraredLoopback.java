package io.github.jabrena.juno.api.hardware.device.ir;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Checks an IR transmitter module (e.g. HX-53) and receiver module (e.g. HX-M121 or VS1838B) wired to
 * the same board: every byte {@code 0..255} is sent through the transmitter and read back through the
 * receiver, which hears the board's own IR LED. Wire the transmitter's DAT to pin 3 and the receiver's
 * DAT to pin 2, with VCC and GND, and put the two modules a few centimetres apart facing each other.
 *
 * <p>The receiver delays what it hears by its response time, so the test repeats for several sampling
 * latencies and prints how many bytes came back intact, as {@code latency: ok/256}. Zero everywhere
 * means no light reaches the receiver (wiring or aim); a clear peak at some latency means the wiring
 * works and the software 38 kHz carrier is accepted by the demodulator. Both boards send 2400 baud
 * RCX-style frames. Pick the board with {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class InfraredLoopback {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int BAUD = 2400;
    private static final int BYTES = 256;
    private static final int LATENCY_STEP_MICROS = 50;
    private static final int LATENCY_MAX_MICROS = 1000;
    private static final int PAUSE_MILLIS = 1;
    private static final int ROUND_PAUSE_SECONDS = 5;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        InfraredLoopback app = new InfraredLoopback();
        Infrared.begin(RECEIVE_PIN, TRANSMIT_PIN, BAUD);

        while (true) {
            Serial.println("Infrared loopback: sending 256 bytes per latency");
            int bestLatency = 0;
            int bestOk = 0;
            for (int latency = 0; latency <= LATENCY_MAX_MICROS; latency += LATENCY_STEP_MICROS) {
                int probe = Infrared.echoByte(0xFF, latency);
                int ok = app.countIntact(latency);
                Serial.print(latency);
                Serial.print(" us, probe ");
                Serial.print(probe);
                Serial.print(": ");
                Serial.print(ok);
                Serial.print("/");
                Serial.println(BYTES);
                if (ok > bestOk) {
                    bestOk = ok;
                    bestLatency = latency;
                }
            }
            if (bestOk == 0) {
                Serial.println("Nothing received: check the wiring and that the modules face each other");
            } else {
                Serial.print("Best: ");
                Serial.print(bestOk);
                Serial.print("/");
                Serial.print(BYTES);
                Serial.print(" at ");
                Serial.print(bestLatency);
                Serial.println(" us");
            }
            Delay.seconds(ROUND_PAUSE_SECONDS);
        }
    }

    /** How many of the 256 byte values come back unchanged when sampled with the given latency. */
    private int countIntact(int latencyMicros) {
        int ok = 0;
        for (int value = 0; value < BYTES; value++) {
            if (Infrared.echoByte(value, latencyMicros) == value) {
                ok++;
            }
            Delay.millis(PAUSE_MILLIS);
        }
        return ok;
    }
}
