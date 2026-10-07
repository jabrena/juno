package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** BigInteger, BigDecimal and MathContext: arithmetic, rounding, text, comparisons and exceptions. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class BigNumbers {
    private static final BigDecimal TWO_AND_A_HALF = new BigDecimal("2.5");

    private BigNumbers() {
    }

    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    static void print(BigInteger value) {
        Serial.println(value.toString());
    }

    static void print(BigDecimal value) {
        Serial.println(value.toString());
    }

    public static void main() {
        // BigInteger
        print(factorial(30));
        BigInteger big = new BigInteger("123456789012345678901234567890");
        BigInteger other = new BigInteger("-987654321098765432109876543210");
        print(big.add(other));
        print(big.subtract(other));
        print(big.multiply(other));
        print(other.divide(big));
        print(other.remainder(big));
        print(other.mod(big));
        print(big.pow(5));
        print(big.sqrt());
        print(big.gcd(BigInteger.valueOf(30)));
        print(big.negate());
        print(other.abs());
        print(big.shiftLeft(70));
        print(other.shiftRight(65));
        print(big.max(other));
        print(big.min(other));
        print(BigInteger.TEN.pow(40).add(BigInteger.TWO));
        Serial.println(big.signum());
        Serial.println(other.signum());
        Serial.println(big.bitLength());
        Serial.println(big.compareTo(other));
        Serial.println(big.equals(new BigInteger("123456789012345678901234567890")));
        Serial.println(big.intValue());
        Serial.println(other.longValue());
        Serial.println(BigInteger.valueOf(1L << 40).doubleValue() == 1099511627776.0);

        // BigDecimal arithmetic and text
        BigDecimal x = new BigDecimal("12345.6789");
        BigDecimal y = new BigDecimal("-0.00045");
        print(x.add(y));
        print(x.subtract(y));
        print(x.multiply(y));
        print(x.negate());
        print(y.abs());
        print(new BigDecimal("1E+3"));
        print(new BigDecimal("0.0000001"));
        print(new BigDecimal("100").stripTrailingZeros());
        Serial.println(new BigDecimal("1E+3").toPlainString());
        Serial.println(new BigDecimal("0.000123").toPlainString());
        Serial.println(x.scale());
        Serial.println(x.precision());
        Serial.println(y.signum());
        print(x.movePointLeft(3));
        print(x.movePointRight(6));
        print(BigDecimal.valueOf(25, 1));
        print(new BigDecimal(7));
        print(BigDecimal.ONE.add(BigDecimal.TEN).add(BigDecimal.TWO));
        print(TWO_AND_A_HALF.pow(7));

        // division, rounding and MathContext
        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal(3);
        print(one.divide(three, 20, RoundingMode.HALF_UP));
        print(one.divide(three, 20, RoundingMode.DOWN));
        print(one.divide(three, 20, RoundingMode.CEILING));
        print(one.divide(three, RoundingMode.HALF_EVEN));
        print(one.divide(three, new MathContext(30)));
        print(new BigDecimal("7").divide(new BigDecimal("8")));
        print(new BigDecimal("2").divide(three, MathContext.DECIMAL64));
        print(new BigDecimal("2").sqrt(new MathContext(40)));
        print(new BigDecimal("144").sqrt(MathContext.DECIMAL32));
        print(new BigDecimal("3.14159265358979323846").setScale(5, RoundingMode.HALF_UP));
        print(new BigDecimal("3.14159265358979323846").round(new MathContext(8, RoundingMode.FLOOR)));
        print(new BigDecimal("1.5").setScale(0, RoundingMode.HALF_EVEN));
        print(new BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN));
        print(new BigDecimal("2.5").setScale(0, RoundingMode.HALF_DOWN));
        print(new BigDecimal("-2.5").setScale(0, RoundingMode.UP));
        print(x.add(y, new MathContext(6)));
        print(x.multiply(y, new MathContext(3)));
        Serial.println(x.compareTo(y));
        Serial.println(new BigDecimal("2.0").equals(new BigDecimal("2.00")));
        Serial.println(new BigDecimal("2.0").compareTo(new BigDecimal("2.00")));
        print(x.max(y));
        print(x.min(y));
        Serial.println(x.intValue());
        Serial.println(x.longValue());
        Serial.println(new BigDecimal("0.5").doubleValue() == 0.5);
        print(new BigDecimal(x.toBigInteger()));
        Serial.println(x.unscaledValue().toString());
        Serial.println(new MathContext(12).getPrecision());

        // exceptions
        try {
            print(one.divide(three));
        } catch (ArithmeticException e) {
            Serial.println("non-terminating");
        }
        try {
            print(big.divide(BigInteger.ZERO));
        } catch (ArithmeticException e) {
            Serial.println("by zero");
        }
        try {
            print(new BigDecimal("1.25").setScale(1));
        } catch (ArithmeticException e) {
            Serial.println("rounding necessary");
        }
        try {
            print(new BigInteger("12x4"));
        } catch (NumberFormatException e) {
            Serial.println("bad integer");
        }
        try {
            print(new BigDecimal("1.2.3"));
        } catch (NumberFormatException e) {
            Serial.println("bad decimal");
        }
        try {
            print(new BigDecimal("-4").sqrt(MathContext.DECIMAL64));
        } catch (ArithmeticException e) {
            Serial.println("negative root");
        }
    }
}
