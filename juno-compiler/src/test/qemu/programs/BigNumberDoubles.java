package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * BigInteger.doubleValue and BigDecimal.doubleValue round correctly, as the JDK's do: halfway cases, subnormals,
 * overflow to infinity and underflow to signed zero. The UNO WiFi Rev2 does this on its software binary64. Magnitudes
 * near the subnormal range need a 10^324-sized intermediate, more than its default 1 KB arena holds, so they stay out.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class BigNumberDoubles {
    public static void main(String[] args) {
        Serial.println(new BigDecimal("0.1").doubleValue() == 0.1 ? 1 : 0);
        Serial.println(new BigDecimal("9007199254740993").doubleValue() == 9007199254740992.0 ? 1 : 0);
        Serial.println(new BigDecimal("1e400").doubleValue() == Double.POSITIVE_INFINITY ? 1 : 0);
        Serial.println(new BigDecimal("-1e-400").doubleValue() == 0.0 ? 1 : 0);
        Serial.println(new BigDecimal("123.456").doubleValue() == 123.456 ? 1 : 0);
        Serial.println(BigInteger.ONE.shiftLeft(1023).doubleValue() == 0x1p1023 ? 1 : 0);
        Serial.println(BigInteger.ONE.shiftLeft(1024).doubleValue() == Double.POSITIVE_INFINITY ? 1 : 0);
        BigInteger factorial = BigInteger.ONE;
        for (int i = 2; i <= 25; i++) {
            factorial = factorial.multiply(BigInteger.valueOf(i));
        }
        Serial.println(factorial.doubleValue() == 1.5511210043330986e25 ? 1 : 0);
        Serial.println(new BigDecimal("-0.000123456789012345678").doubleValue() == -0.000123456789012345678 ? 1 : 0);
    }
}
