package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** String literals, concat operands, null conversion, and supported String.valueOf overloads. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class StringSemantics {
    private StringSemantics() {
    }

    public static void main(String[] args) {
        boolean active = true;
        int count = -12;
        long total = 123456789L;
        float ratio = 1.5f;
        double precise = 2.5;
        char marker = '!';
        String absent = null;

        Serial.println("active=" + active + ", count=" + count);
        Serial.println("total=" + total + ", ratio=" + ratio);
        Serial.println("precise=" + precise + ", marker=" + marker);
        Serial.println("absent=" + absent);
        Serial.println("int=" + String.valueOf(count));
        Serial.println("double=" + String.valueOf(precise));
    }
}
