package io.github.jabrena.juno.api.hardware.device.ir;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Diagnoses two Grove infrared modules connected to the Nano Connector Carrier.
 *
 * <p>Point the two modules at each other a few centimetres apart. The clear Grove emitter is connected to the A0 socket and the
 * black Grove receiver is connected to the I2C socket's SCL/A5 signal. The probe reports intact bytes, timeouts, framing errors and
 * other mismatches so receiver-side failures can be distinguished from power, alignment or wiring problems.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierInfraredLoopback {
    private static final int BAUD = 2400;
    private static final int LATENCY_STEP_MICROS = 100;
    private static final int LATENCY_MAX_MICROS = 1000;
    private static final int PROBE_BYTES = 16;
    private static final int PAUSE_MILLIS = 1;
    private static final int ROUND_PAUSE_MILLIS = 3000;

    private NanoCarrierInfraredLoopback() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        while (true) {
            Serial.println("Grove IR loopback: point modules at each other");
            printIdleLevel(carrier);
            probe("TX A0 RX A5", carrier.getI2cScl(), carrier.getGroveA0());
            Delay.millis(ROUND_PAUSE_MILLIS);
        }
    }

    private static void printIdleLevel(NanoConnectorCarrier carrier) {
        Infrared.beginGrove(carrier.getI2cScl(), -1, BAUD);
        Delay.millis(20);
        Serial.print("A5 active while idle: ");
        Serial.println(Infrared.receiving() ? 1 : 0);
    }

    private static void probe(String label, int receivePin, int transmitPin) {
        Infrared.beginGrove(receivePin, transmitPin, BAUD);
        Serial.print(label);
        Serial.println(":");
        for (int latency = 0; latency <= LATENCY_MAX_MICROS; latency += LATENCY_STEP_MICROS) {
            printResults(latency);
        }
    }

    private static void printResults(int latencyMicros) {
        int intact = 0;
        int timeout = 0;
        int framing = 0;
        int mismatch = 0;
        for (int value = 0; value < PROBE_BYTES; value++) {
            int expected = value * 17;
            int received = Infrared.echoByte(expected, latencyMicros);
            if (received == expected) {
                intact++;
            } else if (received == Infrared.TIMEOUT) {
                timeout++;
            } else if (received == Infrared.FRAMING_ERROR) {
                framing++;
            } else {
                mismatch++;
            }
            Delay.millis(PAUSE_MILLIS);
        }
        Serial.print(latencyMicros);
        Serial.print(" us: intact=");
        Serial.print(intact);
        Serial.print(" timeout=");
        Serial.print(timeout);
        Serial.print(" framing=");
        Serial.print(framing);
        Serial.print(" mismatch=");
        Serial.println(mismatch);
    }
}
