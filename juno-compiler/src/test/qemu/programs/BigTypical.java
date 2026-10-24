package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * java.math as a small board uses it: exact counters past long, and money-style decimals. Sized to run in the UNO WiFi
 * Rev2's default 1 KB arena, so every board must give the JVM's answers within it.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class BigTypical {
    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    public static void main(String[] args) {
        Serial.println(factorial(25).toString());
        BigInteger energy = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MAX_VALUE));
        Serial.println(energy.toString());
        Serial.println(energy.mod(BigInteger.valueOf(1_000_007)).toString());
        Serial.println(energy.compareTo(BigInteger.valueOf(Long.MAX_VALUE)));

        BigDecimal price = new BigDecimal("19.99");
        BigDecimal total = price.multiply(BigDecimal.valueOf(3)).add(new BigDecimal("0.05"));
        Serial.println(total.toString());
        BigDecimal withTax = total.multiply(new BigDecimal("1.21")).setScale(2, RoundingMode.HALF_UP);
        Serial.println(withTax.toString());
        Serial.println(withTax.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_EVEN).toString());
        Serial.println(withTax.compareTo(new BigDecimal("72.62")));
        Serial.println(new BigDecimal("0.1").add(new BigDecimal("0.2")).equals(new BigDecimal("0.3")) ? 1 : 0);
        Serial.println(BigDecimal.valueOf(2.5).add(BigDecimal.ONE).toString());
    }
}
