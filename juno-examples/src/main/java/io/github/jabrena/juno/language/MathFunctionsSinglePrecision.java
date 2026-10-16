package io.github.jabrena.juno.language;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Demonstrates {@code java.lang.Math} on {@code int} and {@code float} values. Every call here builds on every board:
 * it avoids the double-precision libm functions that {@link MathFunctionsDoublePrecision} uses, which the UNO R3 and
 * UNO WiFi Rev2 toolchains do not provide.
 */
public final class MathFunctionsSinglePrecision {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        MathFunctionsSinglePrecision functions = new MathFunctionsSinglePrecision();

        Serial.println("Math functions, single precision demo");

        functions.integerHelpers();
        functions.floatHelpers();
        functions.rounding();
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

    private void floatHelpers() {
        Serial.println("Float helpers");
        float reading = -2.5f;
        Serial.println("abs(-2.5): " + Math.abs(reading));
        Serial.println("min(-2.5, 1.5): " + Math.min(reading, 1.5f));
        Serial.println("max(-2.5, 1.5): " + Math.max(reading, 1.5f));
        Serial.println("clamp(-2.5, 0, 1): " + Math.clamp(reading, 0.0f, 1.0f));
        Serial.println("signum(-2.5): " + Math.signum(reading));
    }

    private void rounding() {
        Serial.println("Rounding");
        float reading = -2.5f;
        // round(float) rounds half up and returns an int; floor and ceil widen to double.
        Serial.println("round(-2.5): " + Math.round(reading));
        Serial.println("floor(-2.5): " + Math.floor(reading));
        Serial.println("ceil(-2.5): " + Math.ceil(reading));
        float two = 2.0f;
        Serial.println("sqrt(2): " + (float) Math.sqrt(two));
    }
}
