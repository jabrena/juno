package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A StringBuilder result longer than one runtime-string slot, such as a URL with a runtime query. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public class LongStringBuilder {

    /** Appends a runtime value as characters: {@code append(String)} takes only a literal on the board. */
    static void appendDigits(StringBuilder builder, int value) {
        if (value >= 10) {
            appendDigits(builder, value / 10);
        }
        builder.append((char) ('0' + value % 10));
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        StringBuilder builder = new StringBuilder(128);
        builder.append("https://api.open-meteo.com/v1/forecast?latitude=");
        appendDigits(builder, 40);
        builder.append("&longitude=");
        builder.append('-');
        appendDigits(builder, 3);
        builder.append("&current=temperature_2m");
        String url = builder.toString();

        for (int round = 0; round < 12; round++) {
            Serial.println("round " + round + " length: " + url.length());
        }
        Serial.println(url);
        Serial.println("last: " + url.charAt(url.length() - 1));
    }
}
