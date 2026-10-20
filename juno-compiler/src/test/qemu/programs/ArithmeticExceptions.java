package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Exceptions raised by arithmetic, both by generated code (integer division by zero) and from inside the java.math
 * runtime, caught by type and through a superclass, with the JDK's messages. Every message stays within the 31
 * characters of a runtime String once concatenated.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class ArithmeticExceptions {
    static int divide(int a, int b) {
        return a / b;
    }

    static long remainder(long a, long b) {
        return a % b;
    }

    public static void main(String[] args) {
        try {
            Serial.println(divide(1, 0));
        } catch (ArithmeticException e) {
            Serial.println("int: " + e.getMessage());
        }
        try {
            Serial.println(remainder(7L, 0L));
        } catch (ArithmeticException e) {
            Serial.println("long: " + e.getMessage());
        }
        try {
            Serial.println(BigInteger.TEN.divide(BigInteger.ZERO).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(BigInteger.TEN.mod(BigInteger.valueOf(-3)).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(new BigDecimal("12x").toString());
        } catch (NumberFormatException e) {
            Serial.println("bad decimal");
        }
        try {
            Serial.println(BigDecimal.ONE.divide(new BigDecimal("3")).toString());
        } catch (ArithmeticException e) {
            Serial.println("non-terminating");
        }
        try {
            Serial.println(new BigDecimal("1.25").setScale(1).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(new BigDecimal("-4").sqrt(MathContext.DECIMAL32).toString());
        } catch (RuntimeException e) {
            Serial.println("caught as RuntimeException");
        }
        try {
            Serial.println(new BigDecimal("7").divide(BigDecimal.ZERO, 2, RoundingMode.HALF_UP).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(BigDecimal.ZERO.divide(BigDecimal.ZERO, RoundingMode.DOWN).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            BigDecimal wide = new BigDecimal("98765432109876543210.5");
            Serial.println(wide.divide(BigDecimal.ZERO, 1, RoundingMode.DOWN).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(BigDecimal.ONE.divide(BigDecimal.ZERO, 19, RoundingMode.DOWN).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(BigDecimal.TEN.divide(new BigDecimal("0.0")).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(BigDecimal.ZERO.divide(BigDecimal.ZERO, MathContext.DECIMAL64).toString());
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        Serial.println("done");
    }
}
