package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Concatenations longer than 31 bytes, built in a loop until the arena must reclaim them. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class})
public class LongStringConcat {

    static String host() {
        return "192.168.1.10";
    }

    static String nothing() {
        return null;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        String host = host();
        String list = "https://" + host + "/api/v1/mailbox/";
        String message = list + "0f8fad5b-d9cb-469f-a165-70867728950e";
        Serial.println(list);
        Serial.println(message);
        Serial.println("length " + message.length());

        int checksum = 0;
        for (int round = 0; round < 200; round++) {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + round + "&longitude=-" + (round % 7)
                    + "&current=temperature_2m";
            checksum += url.length() + url.charAt(url.length() - 1);
        }
        Serial.println("checksum " + checksum);

        Serial.println("short " + 12 + " fits " + true);
        Serial.println("longs " + Long.MIN_VALUE + " and " + Long.MAX_VALUE);
        Serial.println("ints " + Integer.MIN_VALUE + " " + Integer.MAX_VALUE + " " + 'x' + " " + false);
        Serial.println("missing " + nothing() + " stays printable as text");
        Serial.println("kept " + message);
    }
}
