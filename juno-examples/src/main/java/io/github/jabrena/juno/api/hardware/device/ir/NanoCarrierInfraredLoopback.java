package io.github.jabrena.juno.api.hardware.device.ir;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Diagnoses two Grove infrared modules connected to the Nano Connector Carrier.
 *
 * <p>Point the two modules at each other a few centimetres apart. The clear Grove emitter is connected to SCL/A5 and the
 * black Grove receiver is connected to the A0 socket. A non-zero result means the modules and
 * that carrier wiring are working; zero means power, alignment, module type or wiring still needs attention.
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
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getI2cScl());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA0());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        while (true) {
            Serial.println("Grove IR loopback: point modules at each other");
            probe("TX A5 RX A0", receiver, emitter);
            Delay.millis(ROUND_PAUSE_MILLIS);
        }
    }

    private static void probe(String label, InfraredReceiver receiver, InfraredEmitter emitter) {
        emitter.begin(receiver, BAUD);
        Serial.print(label);
        Serial.println(":");
        for (int latency = 0; latency <= LATENCY_MAX_MICROS; latency += LATENCY_STEP_MICROS) {
            int ok = countIntact(latency);
            Serial.print(latency);
            Serial.print(" us: ");
            Serial.print(ok);
            Serial.println("/16");
        }
    }

    private static int countIntact(int latencyMicros) {
        int ok = 0;
        for (int value = 0; value < PROBE_BYTES; value++) {
            if (Infrared.echoByte(value * 17, latencyMicros) == value * 17) {
                ok++;
            }
            Delay.millis(PAUSE_MILLIS);
        }
        return ok;
    }
}
