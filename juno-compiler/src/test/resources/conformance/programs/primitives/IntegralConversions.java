package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Integral widening, narrowing, promotion, and cast behavior. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class IntegralConversions {
    private IntegralConversions() {
    }

    public static void main(String[] args) {
        byte byteValue = (byte) 200;
        short shortValue = (short) 40000;
        char charValue = (char) 65535;
        Serial.println(byteValue);
        Serial.println(shortValue);
        Serial.println((int) charValue);
        Serial.println((byte) (byteValue + 100));
        Serial.println((short) (shortValue * 3));
        Serial.println((char) (charValue + 2));
        long longValue = 0x7fff_ffffL;
        Serial.println((int) longValue);
        Serial.println((long) Integer.MIN_VALUE);
        Serial.println((int) (longValue + 0x1_0000_0000L));
    }
}
