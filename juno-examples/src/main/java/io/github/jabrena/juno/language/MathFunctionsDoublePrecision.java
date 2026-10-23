package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Demonstrates the double-precision {@code java.lang.Math} functions: powers and roots, exponentials and logarithms,
 * and trigonometry. Double-precision libm is only on the UNO R4 WiFi and UNO Q toolchains, so the other boards cannot
 * build it; {@link MathFunctionsSinglePrecision} covers the rest.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class MathFunctionsDoublePrecision {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        MathFunctionsDoublePrecision functions = new MathFunctionsDoublePrecision();

        Serial.println("Math functions, double precision demo");

        functions.powersAndRoots();
        functions.trigonometry();
    }

    private void powersAndRoots() {
        Serial.println("Powers and roots");
        Serial.println("pow(2, 10): " + Math.pow(2.0, 10.0));
        Serial.println("sqrt(2): " + Math.sqrt(2.0));
        Serial.println("cbrt(27): " + Math.cbrt(27.0));
        Serial.println("hypot(3, 4): " + Math.hypot(3.0, 4.0));
        Serial.println("exp(1): " + Math.exp(1.0));
        Serial.println("log(E): " + Math.log(Math.E));
        Serial.println("log10(1000): " + Math.log10(1000.0));
    }

    private void trigonometry() {
        Serial.println("Trigonometry every 45 degrees");
        for (int degrees = 0; degrees <= 180; degrees += 45) {
            double radians = Math.toRadians(degrees);
            Serial.println("sin(" + degrees + "): " + Math.sin(radians));
            Serial.println("cos(" + degrees + "): " + Math.cos(radians));
        }
        Serial.println("atan2(1, 1) deg: " + Math.toDegrees(Math.atan2(1.0, 1.0)));
        Serial.println("PI: " + Math.PI);
    }
}
