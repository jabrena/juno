package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates {@code java.lang.Math}: integer helpers, rounding, powers and roots, and trigonometry. */
public final class MathFunctions {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        MathFunctions functions = new MathFunctions();

        Serial.println("Math functions demo");

        functions.integerHelpers();
        functions.rounding();
        functions.powersAndRoots();
        functions.trigonometry();
    }

    private void integerHelpers() {
        Serial.println("Integer helpers");
        int temperature = -17;
        Serial.println("abs(-17): " + Math.abs(temperature));
        Serial.println("min(-17, 4): " + Math.min(temperature, 4));
        Serial.println("max(-17, 4): " + Math.max(temperature, 4));
        Serial.println("clamp(300, 0, 255): " + Math.clamp(300, 0, 255));
        // Unlike / and %, floorDiv/floorMod round toward negative infinity.
        Serial.println("-17 / 5: " + temperature / 5);
        Serial.println("-17 % 5: " + temperature % 5);
        Serial.println("floorDiv(-17, 5): " + Math.floorDiv(temperature, 5));
        Serial.println("floorMod(-17, 5): " + Math.floorMod(temperature, 5));
    }

    private void rounding() {
        Serial.println("Rounding");
        double reading = -2.5;
        Serial.println("floor(-2.5): " + Math.floor(reading));
        Serial.println("ceil(-2.5): " + Math.ceil(reading));
        Serial.println("round(-2.5): " + Math.round(reading));
        Serial.println("signum(-2.5): " + Math.signum(reading));
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
