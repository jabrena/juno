package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints {@code double} results over the Arduino Nano ESP32's native USB serial port at 115200 baud, once every two
 * seconds, so the board's output can be compared line by line with a desktop JVM run of the same source: the double
 * operators, the saturating, NaN-aware conversions to and from {@code int}, {@code long} and {@code float}, and the
 * double-precision {@code java.lang.Math} functions. Results the serial port prints with only two decimals are
 * scaled first, so every digit that matters reaches the line.
 *
 * <p>The board's USB port re-enumerates when the program starts, so it waits two seconds before the first line,
 * giving a serial monitor time to reattach.
 */
@Board(ArduinoNanoEsp32Board.class)
@SuppressWarnings("java:S2189")
public final class SerialDoubleMath {
    /**
     * Opens the serial port and prints the same block of results indefinitely, with a round counter.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    @SuppressWarnings("java:S3518")
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);

            double x = (round + 1) / 8.0;
            Serial.print("dmul ");
            Serial.println((long) ((x * 3.25 + 0.5) * 1e9));
            Serial.print("ddiv ");
            Serial.println((long) ((1.0 / x - 2.0) * 1e9));
            Serial.print("drem ");
            Serial.println((long) ((7.75 % x) * 1e9));
            Serial.print("dcmp ");
            Serial.println((x > 0.5 ? 1 : 0) + (x == 0.25 ? 2 : 0));
            Serial.print("d2i ");
            Serial.println((int) (x * 1e10));
            Serial.print("d2l ");
            Serial.println((long) (-x * 1e19));
            Serial.print("l2d ");
            Serial.println((long) ((double) (9_007_199_254_740_993L + round) / 2));
            Serial.print("f2d ");
            Serial.println((long) ((double) (float) x * 1e9));
            @SuppressWarnings("java:S1764")
            double nan = 0.0 / 0.0;
            Serial.print("nan ");
            Serial.println((nan < 1.0 ? 1 : 0) + (nan > 1.0 ? 2 : 0) + (int) nan);

            Serial.print("sin ");
            Serial.println((long) (Math.sin(x) * 1e9));
            Serial.print("sqrt ");
            Serial.println((long) (Math.sqrt(x) * 1e9));
            Serial.print("pow ");
            Serial.println((long) (Math.pow(x, 2.5) * 1e9));
            Serial.print("atan2 ");
            Serial.println((long) (Math.atan2(x, 2.0) * 1e9));
            Serial.print("exp ");
            Serial.println((long) (Math.exp(x) * 1e9));

            Delay.millis(2000);
            round = round + 1;
        }
    }
}
