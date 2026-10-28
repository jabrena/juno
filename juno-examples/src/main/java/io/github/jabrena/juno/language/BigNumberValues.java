package io.github.jabrena.juno.language;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Demonstrates a small arbitrary-precision calculation that fits the memory-constrained UNO R3. */
public final class BigNumberValues {
    private BigNumberValues() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        MathContext context = new MathContext(8, RoundingMode.HALF_UP);
        BigInteger integer = BigInteger.valueOf(123456789L);
        BigDecimal decimal = new BigDecimal("12.50").multiply(BigDecimal.valueOf(2), context);
        Serial.println("BigInteger: " + integer.toString());
        Serial.println("BigDecimal: " + decimal.toPlainString());
    }
}
