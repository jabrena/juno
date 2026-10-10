package demo;

import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.ir.Infrared;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Infrared byte transmit and receive. */
public final class InfraredApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        // Infrared.begin(receivePin, transmitPin, baudRate);
        Infrared.begin(Gpio.D2, Gpio.D3, 2400);

        // Two ways to transmit: a framed serial byte (start, 8 data bits, parity, stop), or raw pulses where
        // mark() lights the IR LED with the 38 kHz carrier and space() keeps it dark, 500 us each.
        Infrared.writeByte(0x55);
        Infrared.mark(500);
        Infrared.space(500);
        if (Infrared.receiving()) {
            Serial.println(Infrared.readByte(1000));
        }
    }
}
