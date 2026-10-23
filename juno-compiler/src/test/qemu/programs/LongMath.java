package demo;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** long arithmetic held in register pairs, on locals only (see WideCalls for long parameters and results). */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class LongMath {
    public static void main(String[] args) {
        long factorial = 1;
        for (int i = 2; i <= 20; i++) {
            factorial *= i;
        }
        Serial.println(factorial);
        Serial.println(Long.MAX_VALUE + 1);
        Serial.println(123456789012L / 1000L);
        Serial.println(-123456789012L % 1000L);
        Serial.println(1L << 40);
        Serial.println(-1L >>> 60);
        Serial.println(-1024L >> 3);
        long a = 987654321987L;
        long b = 12345L;
        Serial.println((a * 31 + b) ^ (a >>> 7));
        long accumulator = 1;
        for (int i = 0; i < 40; i++) {
            accumulator = (accumulator * 31 + i) ^ (accumulator >>> 7);
        }
        Serial.println(accumulator);
        Serial.println(accumulator > 0 ? 1 : 0);
        Serial.println((int) (accumulator >> 32));
        Serial.println((long) Integer.MIN_VALUE);
    }
}
