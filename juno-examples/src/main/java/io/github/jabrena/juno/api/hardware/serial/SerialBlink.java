package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Blinks the Arduino Nano ESP32's built-in LED once per second and reports every change over the native USB serial
 * port at 115200 baud: {@code on 0}, {@code off 0}, {@code on 1}, and so on. It exercises the first APIs the Nano
 * ESP32 supports: {@code Gpio}, {@code DigitalOutput}, {@code Delay} and {@code Serial}.
 *
 * <p>The board's USB port re-enumerates when the program starts, so it waits two seconds before the first line,
 * giving a serial monitor time to reattach.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class SerialBlink {
    /**
     * Opens the serial port and blinks the built-in LED indefinitely, printing each state with a cycle counter.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        DigitalOutput led = DigitalOutput.of(Arduino.getGpio().getBuiltinLed());
        Delay.millis(2000);

        int cycle = 0;
        while (true) {
            led.high();
            Serial.print("on ");
            Serial.println(cycle);
            Delay.millis(500);

            led.low();
            Serial.print("off ");
            Serial.println(cycle);
            Delay.millis(500);

            cycle = cycle + 1;
        }
    }
}
