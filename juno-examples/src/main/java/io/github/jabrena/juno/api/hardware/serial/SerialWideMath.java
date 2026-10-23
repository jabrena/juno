package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints {@code long} and {@code float} results over the Arduino Nano ESP32's native USB serial port at 115200 baud,
 * once every two seconds, so the board's output can be compared line by line with a desktop JVM run of the same
 * source: 64-bit products, division, shifts and comparisons, the float operators, and the saturating, NaN-aware
 * conversions between {@code int}, {@code long} and {@code float}.
 *
 * <p>The board's USB port re-enumerates when the program starts, so it waits two seconds before the first line,
 * giving a serial monitor time to reattach.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class SerialWideMath {
    /**
     * Opens the serial port and prints the same block of results indefinitely, with a round counter.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);

            long big = 3_000_000_000L + round;
            Serial.print("mul ");
            Serial.println(big * 7);
            Serial.print("div ");
            Serial.println(big / -9);
            Serial.print("rem ");
            Serial.println(big % 1000);
            Serial.print("shl ");
            Serial.println(big << 20);
            Serial.print("shr ");
            Serial.println(-big >> 3);
            Serial.print("ushr ");
            Serial.println(-big >>> 40);
            Serial.print("cmp ");
            Serial.println(big > 2_999_999_999L ? 1 : 0);

            float ratio = (round + 1) / 4f;
            Serial.print("fmul ");
            Serial.println(ratio * 1.5f + 0.25f);
            Serial.print("fdiv ");
            Serial.println(10f / ratio - 3f);
            Serial.print("frem ");
            Serial.println(7.5f % ratio);
            Serial.print("f2i ");
            Serial.println((int) (ratio * 1e10f));
            Serial.print("f2l ");
            Serial.println((long) (ratio * 1e10f));
            Serial.print("l2f ");
            Serial.println((float) big);
            float nan = 0f / 0f;
            Serial.print("nan ");
            Serial.println((nan < 1f ? 1 : 0) + (nan > 1f ? 2 : 0) + (int) nan);

            Delay.millis(2000);
            round = round + 1;
        }
    }
}
