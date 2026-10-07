package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.util.concurrent.StructuredTaskScope;

/**
 * JDK 25 {@link ScopedValue}s as a precision policy for parallel numeric work. The owner binds the maximum number
 * of decimals once; three subtasks approximate sqrt(2), pi and e, and each one stops iterating as
 * soon as its next correction is below 10^-decimals and prints with the same number of decimals, without
 * receiving the precision as a parameter. Subtasks only write numbers into inherited arrays: runtime Strings live
 * in a small rotating slot pool, so the owner formats and prints them after the join. A nested binding can lower the precision for its own extent, but
 * {@code Math.min} against the outer value means it can never raise it above what its owner allows.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public class ScopedValuesPrecision {
    private static final int MAX_DECIMALS = 6;
    private static final int CONSTANTS = 3;

    private static final ScopedValue<int[]> DECIMALS = ScopedValue.newInstance();
    private static final ScopedValue<double[]> VALUES = ScopedValue.newInstance();
    private static final ScopedValue<int[]> STEPS = ScopedValue.newInstance();
    private static final String[] NAMES = {"sqrt2", "pi", "e"};

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);

        final double[] values = new double[CONSTANTS];
        final int[] steps = new int[CONSTANTS];
        // The precision is a one-element array because Juno has no boxed Integer.
        ScopedValue.where(DECIMALS, new int[] {MAX_DECIMALS}).where(VALUES, values).where(STEPS, steps).run(() -> {
            report("owner binds " + decimals() + " decimals");
            ScopedValue.where(DECIMALS, new int[] {Math.min(2, decimals())}).run(() -> report("nested asks for 2"));
            ScopedValue.where(DECIMALS, new int[] {Math.min(9, decimals())}).run(() -> report("nested asks for 9, capped"));
            report("back in the owner");
        });
    }

    /** Forks one approximation per constant (a scope holds at most three subtasks), then prints and combines them. */
    private static void report(String title) {
        Serial.println("== " + title);
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> sqrtTwo());
            scope.fork(() -> pi());
            scope.fork(() -> euler());
            scope.join();
        } catch (InterruptedException interrupted) {
            throw new IllegalStateException("interrupted");
        }
        double[] values = VALUES.get();
        int[] steps = STEPS.get();
        for (int index = 0; index < CONSTANTS; index++) {
            Serial.println(NAMES[index] + " = " + format(values[index]) + " (" + steps[index] + " steps)");
        }
        Serial.println("sqrt2^2 = " + format(values[0] * values[0]));
        Serial.println("pi - e = " + format(values[1] - values[2]));
    }

    /** Newton's method on x^2 = 2. */
    private static void sqrtTwo() {
        double tolerance = tolerance();
        double x = 1.0;
        int steps = 0;
        double delta;
        do {
            double next = (x + 2.0 / x) / 2.0;
            delta = Math.abs(next - x);
            x = next;
            steps++;
        } while (delta >= tolerance);
        VALUES.get()[0] = x;
        STEPS.get()[0] = steps;
    }

    /** Nilakantha series: 3 + 4/(2*3*4) - 4/(4*5*6) + ... */
    private static void pi() {
        double tolerance = tolerance();
        double sum = 3.0;
        double sign = 1.0;
        int steps = 0;
        double term;
        do {
            double n = 2.0 + 2.0 * steps;
            term = 4.0 / (n * (n + 1.0) * (n + 2.0));
            sum += sign * term;
            sign = -sign;
            steps++;
        } while (term >= tolerance);
        VALUES.get()[1] = sum;
        STEPS.get()[1] = steps;
    }

    /** The series 1 + 1/1! + 1/2! + ... */
    private static void euler() {
        double tolerance = tolerance();
        double sum = 1.0;
        double term = 1.0;
        int steps = 0;
        do {
            steps++;
            term /= steps;
            sum += term;
        } while (term >= tolerance);
        VALUES.get()[2] = sum;
        STEPS.get()[2] = steps;
    }

    /** 10^-decimals for the inherited precision. */
    private static double tolerance() {
        return 1.0 / scale(decimals());
    }

    private static int decimals() {
        return DECIMALS.get()[0];
    }

    private static long scale(int decimals) {
        long scale = 1;
        for (int index = 0; index < decimals; index++) {
            scale *= 10;
        }
        return scale;
    }

    /** Rounds to the inherited number of decimals and prints it without relying on double-to-text conversion. */
    private static String format(double value) {
        int decimals = decimals();
        long scale = scale(decimals);
        long scaled = Math.round(Math.abs(value) * scale);
        String fraction = "" + (scaled % scale);
        while (fraction.length() < decimals) {
            fraction = "0" + fraction;
        }
        String sign = value < 0 && scaled != 0 ? "-" : "";
        return sign + (scaled / scale) + (decimals > 0 ? "." + fraction : "");
    }
}
