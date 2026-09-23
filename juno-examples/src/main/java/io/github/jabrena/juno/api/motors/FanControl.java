package io.github.jabrena.juno.api.motors;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;

/**
 * Drives a relay or MOSFET connected to digital pin 7 to switch a 3-wire DC brushless fan on for
 * 10 seconds and off for 10 seconds, indefinitely. While the fan cycles, the UNO R4 WiFi's
 * built-in LED on pin 13 blinks every 500 milliseconds as a running-status indicator.
 *
 * <p><strong>Pin 7 never powers the fan directly.</strong> An Arduino digital pin can only source
 * a few tens of milliamps, far less than a fan motor draws (a typical 12V/2.6W fan draws about
 * 0.22A at full voltage, with a higher startup surge). Pin 7 only switches a relay module or
 * MOSFET, which carries the fan's actual supply current.
 *
 * <h2>Wiring (relay module)</h2>
 *
 * <pre>{@code
 * Arduino D7  -> Relay IN
 * Arduino 5V  -> Relay VCC
 * Arduino GND -> Relay GND, and also tied to the external supply's GND (common ground)
 *
 * Fan RED    -> Relay COM
 * Relay NO   -> External power supply (+)
 * Fan BLACK  -> External power supply (-)
 * Fan YELLOW -> leave unconnected (tachometer signal, not needed for on/off control)
 * }</pre>
 *
 * <p>Use an external power supply matching the voltage printed on the fan's label (commonly 12V
 * for this style of fan). Powering the fan from the Arduino's 5V pin or USB under-volts it and
 * makes it spin weakly or not at all.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class FanControl {
    private static final int FAN_SWITCH_PIN = 7;
    private static final int LED = 13;
    private static final int BLINK_HALF_PERIOD_MILLIS = 500;
    private static final int BLINKS_PER_PHASE = 10;

    public static void main(String[] args) {

        DigitalOutput fanSwitch = DigitalOutput.of(FAN_SWITCH_PIN);
        DigitalOutput led = DigitalOutput.of(LED);

        while (true) {
            fanSwitch.high();
            for (int i = 0; i < BLINKS_PER_PHASE; i++) {
                led.high();
                Delay.millis(BLINK_HALF_PERIOD_MILLIS);
                led.low();
                Delay.millis(BLINK_HALF_PERIOD_MILLIS);
            }

            fanSwitch.low();
            for (int i = 0; i < BLINKS_PER_PHASE; i++) {
                led.high();
                Delay.millis(BLINK_HALF_PERIOD_MILLIS);
                led.low();
                Delay.millis(BLINK_HALF_PERIOD_MILLIS);
            }
        }
    }
}
