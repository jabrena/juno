package io.github.jabrena.juno.api.thirdparty.grove;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Prints readings from a Grove DHT11 plugged into the Grove Base Shield's D4 socket. */
@Board({ArduinoUnoR3.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class GroveDht11Monitor {
    private static final int SIGNAL_PIN = GroveBaseShield.D4;
    private static final int READ_INTERVAL_MILLIS = 2000;

    private GroveDht11Monitor() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        int[] reading = new int[GroveDht11.READING_LENGTH];
        Delay.millis(1000);
        while (true) {
            if (GroveDht11.read(SIGNAL_PIN, reading, reading.length)) {
                Serial.print("Temperature C: ");
                Serial.print(reading[GroveDht11.TEMPERATURE_CELSIUS]);
                Serial.print("  Humidity %: ");
                Serial.println(reading[GroveDht11.HUMIDITY_PERCENT]);
            } else {
                Serial.println("DHT11 read failed");
            }
            Delay.millis(READ_INTERVAL_MILLIS);
        }
    }
}
