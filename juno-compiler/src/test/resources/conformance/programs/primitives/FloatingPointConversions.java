package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Floating-point narrowing, widening, rounding, and saturating primitive casts. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class FloatingPointConversions {
    private FloatingPointConversions() {
    }

    public static void main(String[] args) {
        Serial.println((int) 3.99d);
        Serial.println((int) -3.99d);
        Serial.println((int) 1e20d);
        Serial.println((int) Float.NaN);
        Serial.println((long) Float.POSITIVE_INFINITY);
        Serial.println((long) Float.NEGATIVE_INFINITY);
        Serial.println((long) 1e30f);
        Serial.println((long) (float) 0x7fff_ffff_ffff_ffffL);
        Serial.println((long) (float) 16_777_217);
        Serial.println((int) (double) Float.MAX_VALUE == Integer.MAX_VALUE);
    }
}
