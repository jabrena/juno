package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Floating-point arithmetic, rounding, NaN, infinities, and signed zero. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class FloatingPointArithmetic {
    private FloatingPointArithmetic() {
    }

    public static void main(String[] args) {
        float floatThird = 1.0f / 3.0f;
        double doubleTenth = 0.1d;
        double sum = 0.0d;
        for (int i = 0; i < 10; i++) {
            sum += doubleTenth;
        }
        Serial.println((int) (floatThird * 1_000_000));
        Serial.println(sum == 1.0d);
        Serial.println(sum < 1.0d);
        Serial.println(Float.NaN != Float.NaN);
        Serial.println(1.0f / 0.0f > Float.MAX_VALUE);
        Serial.println(-0.0d == 0.0d);
        Serial.println(1.0d / -0.0d < 0.0d);
        Serial.println((long) (7.25d % 2.5d * 100.0d));
        Serial.println((int) (float) 16_777_217);
    }
}
