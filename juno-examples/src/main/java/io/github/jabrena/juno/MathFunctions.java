package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates {@code java.lang.Math}: integer helpers, rounding, powers and roots, and trigonometry. */
public final class MathFunctions {
    private MathFunctions() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Math functions demo");

        Serial.println("Integer helpers");
        int temperature = -17;
        Serial.print("abs(-17): ");
        Serial.println(Math.abs(temperature));
        Serial.print("min(-17, 4): ");
        Serial.println(Math.min(temperature, 4));
        Serial.print("max(-17, 4): ");
        Serial.println(Math.max(temperature, 4));
        Serial.print("clamp(300, 0, 255): ");
        Serial.println(Math.clamp(300, 0, 255));
        // Unlike / and %, floorDiv/floorMod round toward negative infinity.
        Serial.print("-17 / 5 and -17 % 5: ");
        Serial.print(temperature / 5);
        Serial.print(" ");
        Serial.println(temperature % 5);
        Serial.print("floorDiv(-17, 5) and floorMod(-17, 5): ");
        Serial.print(Math.floorDiv(temperature, 5));
        Serial.print(" ");
        Serial.println(Math.floorMod(temperature, 5));

        Serial.println("Rounding");
        double reading = -2.5;
        Serial.print("floor(-2.5): ");
        Serial.println(Math.floor(reading));
        Serial.print("ceil(-2.5): ");
        Serial.println(Math.ceil(reading));
        Serial.print("round(-2.5): ");
        Serial.println(Math.round(reading));
        Serial.print("signum(-2.5): ");
        Serial.println(Math.signum(reading));

        Serial.println("Powers and roots");
        Serial.print("pow(2, 10): ");
        Serial.println(Math.pow(2.0, 10.0));
        Serial.print("sqrt(2): ");
        Serial.println(Math.sqrt(2.0));
        Serial.print("cbrt(27): ");
        Serial.println(Math.cbrt(27.0));
        Serial.print("hypot(3, 4): ");
        Serial.println(Math.hypot(3.0, 4.0));
        Serial.print("exp(1): ");
        Serial.println(Math.exp(1.0));
        Serial.print("log(E): ");
        Serial.println(Math.log(Math.E));
        Serial.print("log10(1000): ");
        Serial.println(Math.log10(1000.0));

        Serial.println("Trigonometry every 45 degrees");
        for (int degrees = 0; degrees <= 180; degrees += 45) {
            double radians = Math.toRadians(degrees);
            Serial.print(degrees);
            Serial.print(" deg -> sin ");
            Serial.print(Math.sin(radians));
            Serial.print(", cos ");
            Serial.println(Math.cos(radians));
        }
        Serial.print("atan2(1, 1) in degrees: ");
        Serial.println(Math.toDegrees(Math.atan2(1.0, 1.0)));
        Serial.print("PI: ");
        Serial.println(Math.PI);
    }
}
