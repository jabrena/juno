package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A StringBuilder result kept across many later concatenations, which once reused its slot (#366). */
@Board(ArduinoUnoR4WiFiBoard.class)
public class StringLifetime {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        StringBuilder builder = new StringBuilder(24);
        builder.append("my-network-ssid-abc");
        String ssid = builder.toString();

        for (int round = 0; round < 12; round++) {
            Serial.println("round " + round + " ssid length: " + ssid.length());
        }
        Serial.println("ssid=" + ssid);
    }
}
