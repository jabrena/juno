package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.StructuredTaskScope;

/**
 * JDK 25 {@link ScopedValue}s as a precision policy for parallel numeric work. The owner binds a
 * {@link MathContext} once; three subtasks approximate sqrt(2), pi and e with {@link BigDecimal}, and each one
 * stops iterating as soon as its next correction is below 10^-precision and rounds with the same context, without
 * receiving the precision as a parameter. A nested binding can lower the precision for its own extent, but
 * {@code Math.min} against the outer value means it can never raise it above what its owner allows.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ScopedValuesPrecision {
    private static final int MAX_DIGITS = 40;
    private static final int CONSTANTS = 3;

    private static final ScopedValue<MathContext> PRECISION = ScopedValue.newInstance();
    private static final ScopedValue<int[]> STEPS = ScopedValue.newInstance();
    private static final String[] NAMES = {"sqrt2", "pi", "e"};

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);

        ScopedValuesPrecision example = new ScopedValuesPrecision();
        final int[] steps = new int[CONSTANTS];
        ScopedValue
            .where(PRECISION, new MathContext(MAX_DIGITS))
            .where(STEPS, steps)
            .run(() -> example.scenarios());
    }

    private void scenarios() {
        ownerBinding();
        nestedLowerPrecision();
        nestedCappedPrecision();
        backInOwner();
    }

    private void ownerBinding() {
        report("owner binds " + digits() + " digits");
    }

    /** A nested binding may lower the precision for its own extent. */
    private void nestedLowerPrecision() {
        ScopedValue.where(PRECISION, new MathContext(Math.min(5, digits()))).run(() -> report("nested asks for 5"));
    }

    /** Asking for more than the owner allows is capped by {@code Math.min}. */
    private void nestedCappedPrecision() {
        ScopedValue.where(PRECISION, new MathContext(Math.min(60, digits()))).run(() -> report("nested asks for 60, capped"));
    }

    /** The outer binding is back as soon as the nested ones return. */
    private void backInOwner() {
        report("back in the owner");
    }

    /** Forks one approximation per constant (a scope holds at most three subtasks), then prints and combines them. */
    private void report(String title) {
        Serial.println("== " + title);
        BigDecimal sqrt2;
        BigDecimal pi;
        BigDecimal e;
        try (var scope = StructuredTaskScope.open()) {
            var first = scope.fork(() -> sqrtTwo());
            var second = scope.fork(() -> pi());
            var third = scope.fork(() -> euler());
            scope.join();
            sqrt2 = first.get();
            pi = second.get();
            e = third.get();
        } catch (InterruptedException interrupted) {
            throw new IllegalStateException("interrupted");
        }
        MathContext context = PRECISION.get();
        BigDecimal[] values = {sqrt2, pi, e};
        int[] steps = STEPS.get();
        for (int index = 0; index < CONSTANTS; index++) {
            Serial.print(NAMES[index]);
            Serial.print(" = ");
            Serial.print(values[index].round(context).toString());
            Serial.print(" (");
            Serial.print(steps[index]);
            Serial.println(" steps)");
        }
        Serial.print("sqrt2^2 = ");
        Serial.println(sqrt2.multiply(sqrt2, context).toString());
        Serial.print("pi - e = ");
        Serial.println(pi.subtract(e, context).toString());
    }

    /** Newton's method on x^2 = 2. */
    private BigDecimal sqrtTwo() {
        MathContext context = PRECISION.get();
        BigDecimal two = new BigDecimal(2);
        BigDecimal tolerance = tolerance();
        BigDecimal x = BigDecimal.ONE;
        int steps = 0;
        BigDecimal delta;
        do {
            BigDecimal next = x.add(two.divide(x, context), context).divide(two, context);
            delta = next.subtract(x).abs();
            x = next;
            steps++;
        } while (delta.compareTo(tolerance) >= 0);
        STEPS.get()[0] = steps;
        return x;
    }

    /** Machin's formula: pi = 16 arctan(1/5) - 4 arctan(1/239). */
    private BigDecimal pi() {
        MathContext context = PRECISION.get();
        BigDecimal first = arctanInverse(5).multiply(BigDecimal.valueOf(16), context);
        BigDecimal second = arctanInverse(239).multiply(BigDecimal.valueOf(4), context);
        return first.subtract(second, context);
    }

    /** arctan(1/n) = 1/n - 1/(3 n^3) + 1/(5 n^5) - ..., counting its terms in the shared step slot. */
    private BigDecimal arctanInverse(int n) {
        MathContext context = PRECISION.get();
        BigDecimal tolerance = tolerance();
        BigDecimal square = BigDecimal.valueOf((long) n * n);
        BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(n), context);
        BigDecimal sum = power;
        int k = 1;
        while (power.compareTo(tolerance) >= 0) {
            power = power.divide(square, context);
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * k + 1), context);
            sum = k % 2 == 1 ? sum.subtract(term, context) : sum.add(term, context);
            k++;
            STEPS.get()[1] = STEPS.get()[1] + 1;
        }
        return sum;
    }

    /** The series 1 + 1/1! + 1/2! + ... */
    private BigDecimal euler() {
        MathContext context = PRECISION.get();
        BigDecimal tolerance = tolerance();
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        int steps = 0;
        do {
            steps++;
            term = term.divide(BigDecimal.valueOf(steps), context);
            sum = sum.add(term, context);
        } while (term.compareTo(tolerance) >= 0);
        STEPS.get()[2] = steps;
        return sum;
    }

    private int digits() {
        return PRECISION.get().getPrecision();
    }

    /** 10^-(digits + 2): two guard digits beyond the inherited precision. */
    private BigDecimal tolerance() {
        return BigDecimal.ONE.movePointLeft(digits() + 2);
    }
}
