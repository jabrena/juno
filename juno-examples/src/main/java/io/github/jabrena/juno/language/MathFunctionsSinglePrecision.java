package io.github.jabrena.juno.language;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Demonstrates {@code java.lang.Math} on {@code float} values. Every call here builds on every board:
 * it avoids the double-precision libm functions that {@link MathFunctionsDoublePrecision} uses, which the UNO R3 and
 * UNO WiFi Rev2 toolchains do not provide.
 */
public final class MathFunctionsSinglePrecision {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        MathFunctionsSinglePrecision functions = new MathFunctionsSinglePrecision();

        Serial.println("Math functions, single precision demo");

        functions.floatHelpers();
        functions.rounding();
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
