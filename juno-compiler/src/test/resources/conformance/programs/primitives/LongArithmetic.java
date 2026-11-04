package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** 64-bit integral arithmetic and shifts. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class LongArithmetic {
    private LongArithmetic() {
    }

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
        Serial.println(Long.MIN_VALUE / -1);
    }
}
