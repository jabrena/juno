package io.github.jabrena.juno.api.hardware.device.grove;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.device.grove.GrovePicoShield;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Blinks a Grove LED on the Grove Shield for Pi Pico's D16 socket and prints the raw reading of an analog Grove module
 * (a light sensor or rotary angle sensor) on its A0 socket once per blink.
 */
@Board(RaspberryPico2WifiBoard.class)
public final class RaspberryPico2GroveShield {
    private static final int BLINK_DELAY_MILLIS = 500;

    /** Toggles the D16 LED every 500 milliseconds and prints the A0 reading after each toggle. */
    public static void main(String[] args) {
        Gpio gpio = RaspberryPico2.getGpio();
        GrovePicoShield shield = new GrovePicoShield(gpio);
        DigitalOutput led = DigitalOutput.of(shield.getD16());
        Serial.begin(BaudRate.BAUD_9600);
        Delay.millis(1000);
        while (true) {
            led.high();
            printA0(gpio, shield);
            Delay.millis(BLINK_DELAY_MILLIS);
            led.low();
            printA0(gpio, shield);
            Delay.millis(BLINK_DELAY_MILLIS);
        }
    }

    private static void printA0(Gpio gpio, GrovePicoShield shield) {
        Serial.print("A0: ");
        Serial.println(gpio.analogRead(shield.getA0()));
    }
}
