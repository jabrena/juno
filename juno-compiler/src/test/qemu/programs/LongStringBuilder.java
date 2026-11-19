package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A StringBuilder result longer than 31 bytes, such as a URL with a runtime query. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
public class LongStringBuilder {

    static String host() {
        return "api.open-meteo.com";
    }

    static String nothing() {
        return null;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        StringBuilder builder = new StringBuilder(128);
        builder.append("https://");
        builder.append(host());
        builder.append("/v1/forecast?latitude=");
        builder.append(String.valueOf(40));
        builder.append("&longitude=");
        builder.append('-');
        builder.append(String.valueOf(3));
        builder.append("&current=temperature_2m");
        String url = builder.toString();

        for (int round = 0; round < 12; round++) {
            Serial.println("round " + round + " length: " + url.length());
        }
        Serial.println(url);
        Serial.println("last: " + url.charAt(url.length() - 1));

        StringBuilder copy = new StringBuilder(128);
        copy.append(url);
        copy.append(nothing());
        Serial.println(copy.toString());
    }
}
