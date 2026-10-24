package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads an {@link RcxTemperatureSensor} on the RCX's input 1 from the Arduino over infrared and prints the
 * temperature in degrees Celsius, with one decimal, once a second. Works with LEGO's firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class RcxTemperatureSensorExample {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int PERIOD_MILLIS = 1000;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxTemperatureSensor temperature = new RcxTemperatureSensor(remote.getSensor1());
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!temperature.begin()) {
            Serial.println("The RCX did not answer: is it on and in range, with the IR receiver wired?");
            Delay.millis(RETRY_MILLIS);
        }
        Serial.println("Temperature sensor on input 1");

        while (true) {
            printTemperature(temperature.temperatureTenths());
            Delay.millis(PERIOD_MILLIS);
        }
    }

    /** Prints tenths of a degree as degrees with one decimal, in pieces, so no string is allocated. */
    private static void printTemperature(int tenths) {
        Serial.print("temperature ");
        if (tenths < 0) {
            Serial.print("-");
            tenths = -tenths;
        }
        Serial.print(tenths / 10);
        Serial.print(".");
        Serial.print(tenths % 10);
        Serial.println(" C");
    }
}
