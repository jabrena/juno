package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Exact integers and a 20-digit square root from {@code java.math}, printed once at 115200 baud: 2 to the 64th power,
 * 20 factorial, and the square root of 2. Each line stays under the 31-byte limit of a runtime {@code String}, so the
 * values can be checked against a desktop JVM run of the same source.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialBigNumbers -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class SerialBigNumbers {
    private static final MathContext TWENTY_DIGITS = new MathContext(20, RoundingMode.HALF_EVEN);

    private SerialBigNumbers() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        BigInteger twoToTheSixtyFourth = BigInteger.TWO.pow(64);
        Serial.print("2^64=");
        Serial.println(twoToTheSixtyFourth.toString());

        BigInteger factorial = BigInteger.ONE;
        for (int i = 2; i <= 20; i++) {
            factorial = factorial.multiply(BigInteger.valueOf(i));
        }
        Serial.print("20!=");
        Serial.println(factorial.toString());

        BigDecimal root = new BigDecimal(2).sqrt(TWENTY_DIGITS);
        Serial.print("sqrt2=");
        Serial.println(root.toString());
    }
}
