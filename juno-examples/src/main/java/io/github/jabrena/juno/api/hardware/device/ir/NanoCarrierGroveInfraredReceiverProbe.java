package io.github.jabrena.juno.api.hardware.device.ir;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/** Counts raw active-low pulses on every Nano Connector Carrier socket pin to locate a Grove infrared receiver. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierGroveInfraredReceiverProbe {
    private static final int BAUD = 2400;
    private static final int START_MILLIS = 2000;
    private static final int WINDOW_MICROS = 250_000;

    private NanoCarrierGroveInfraredReceiverProbe() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        Clock clock = ArduinoNano.getClock();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("Grove IR receiver probe on every carrier signal pin");
        Serial.println("Hold a TV remote button toward the black receiver");

        while (true) {
            sampleWindow("A0", carrier.getA0(), clock);
            sampleWindow("A1", carrier.getA1(), clock);
            sampleWindow("A2", carrier.getA2(), clock);
            sampleWindow("A3", carrier.getA3(), clock);
            sampleWindow("A4", carrier.getI2cSda(), clock);
            sampleWindow("A5", carrier.getI2cScl(), clock);
            sampleWindow("D0", carrier.getUartRx(), clock);
            sampleWindow("D1", carrier.getUartTx(), clock);
            Serial.println("--");
        }
    }

    private static void sampleWindow(String label, int receivePin, Clock clock) {
        Infrared.beginGrove(receivePin, -1, BAUD);
        int samples = 0;
        int activeSamples = 0;
        int edges = 0;
        boolean previous = Infrared.receiving();
        int started = clock.micros();
        while (clock.micros() - started < WINDOW_MICROS) {
            boolean active = Infrared.receiving();
            samples++;
            if (active) {
                activeSamples++;
            }
            if (active != previous) {
                edges++;
                previous = active;
            }
        }
        Serial.print(label);
        Serial.print(" active=");
        Serial.print(activeSamples);
        Serial.print(" samples=");
        Serial.print(samples);
        Serial.print(" edges=");
        Serial.println(edges);
    }
}
