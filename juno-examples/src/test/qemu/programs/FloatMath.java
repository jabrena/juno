package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/** float and double arithmetic and conversions on locals (hard-float shim, soft-float generated code). */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class FloatMath {
    static float scale(float value, float factor) {
        return value * factor + 0.5f;
    }

    public static void main(String[] args) {
        Serial.println((int) scale(10.0f, 2.5f));
        double a = 3.0;
        double b = 8.0;
        Serial.println((int) ((a + b) / 2.0 * 100));
        Serial.println((int) (1.0f / 3.0f * 1000000));
        Serial.println((long) (1e12 / 7.0));
        Serial.println((int) 3.99);
        Serial.println((int) -3.99);
        Serial.println((int) 1e20);
        Serial.println((int) Float.NaN);
        double total = 0;
        for (int i = 1; i <= 10; i++) {
            total += 1.0 / i;
        }
        Serial.println((int) (total * 1000));
        Serial.println(Math.sqrt(144.0) == 12.0 ? 1 : 0);
        Serial.println(0.1f + 0.2f == 0.3f ? 1 : 0);
        Serial.println(0.1 + 0.2 > 0.3 ? 1 : 0);
        Serial.println((int) Math.floor(-2.5));
        Serial.println((int) Math.ceil(-2.5));
    }
}
