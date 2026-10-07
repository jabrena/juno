package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Long big-number loops on the 8 KB arena: every iteration leaves garbage, so the collector runs mid-computation. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class BigNumbersGc {
    private static final MathContext PRECISION = new MathContext(50);

    private BigNumbersGc() {
    }

    static BigInteger fibonacci(int n) {
        BigInteger previous = BigInteger.ZERO;
        BigInteger current = BigInteger.ONE;
        for (int i = 1; i < n; i++) {
            BigInteger next = previous.add(current);
            previous = current;
            current = next;
        }
        return current;
    }

    static BigDecimal euler() {
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int k = 1; k < 45; k++) {
            term = term.divide(BigDecimal.valueOf(k), PRECISION);
            sum = sum.add(term, PRECISION);
        }
        return sum;
    }

    /** arctan(1/x) by its Taylor series. */
    static BigDecimal arctanInverse(int x) {
        BigDecimal square = BigDecimal.valueOf((long) x * x);
        BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(x), PRECISION);
        BigDecimal sum = power;
        for (int k = 1; k < 40; k++) {
            power = power.divide(square, PRECISION);
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * k + 1), PRECISION);
            sum = k % 2 == 1 ? sum.subtract(term, PRECISION) : sum.add(term, PRECISION);
        }
        return sum;
    }

    static int digitSum(BigInteger value) {
        String text = value.toString();
        int sum = 0;
        for (int i = 0; i < text.length(); i++) {
            sum += text.charAt(i) - '0';
        }
        return sum;
    }

    public static void main() {
        Serial.println(fibonacci(400).toString());
        BigInteger factorial = BigInteger.ONE;
        for (int i = 2; i <= 150; i++) {
            factorial = factorial.multiply(BigInteger.valueOf(i));
        }
        Serial.println(digitSum(factorial));
        Serial.println(euler().toString());
        BigDecimal pi = arctanInverse(5).multiply(BigDecimal.valueOf(16))
                .subtract(arctanInverse(239).multiply(BigDecimal.valueOf(4)));
        Serial.println(pi.setScale(40, RoundingMode.HALF_EVEN).toString());
        Serial.println(new BigDecimal(2).sqrt(PRECISION).toString());
    }
}
