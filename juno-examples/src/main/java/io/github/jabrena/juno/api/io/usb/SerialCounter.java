package io.github.jabrena.juno.api.io.usb;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

/**
 * Demonstrates integer output over the Arduino UNO R4 WiFi's USB serial connection. The program
 * opens the connection at 9600 baud and prints the values {@code 0}, {@code 1}, {@code 2}, and so
 * on, placing each value on its own line and waiting one second between values.
 *
 * <p>After uploading the sketch, find the board's port and open a serial monitor at the same baud
 * rate:
 * <pre>{@code
 * arduino-cli board list
 * arduino-cli monitor -p <PORT> -c baudrate=9600
 * }</pre>
 */
@Board(ArduinoUnoR4WiFi.class)
public final class SerialCounter {
    /**
     * Initializes USB serial output and prints an incrementing counter indefinitely.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);

        int counter = 0;
        while (true) {
            Serial.println(counter);
            counter = counter + 1;
            Delay.millis(1000);
        }
    }
}
