package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;
import io.github.jabrena.juno.api.util.Delay;

/** Prints DHT22-format temperature and humidity from the Grove module on D4. */
@Board({ArduinoUnoR3WiFiRev2.class})
public final class GroveDht22Monitor {
    private GroveDht22Monitor() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        Serial.begin(BaudRate.BAUD_115200);
        GroveDht22 dht22 = new GroveDht22();
        int[] reading = new int[GroveDht22.READING_LENGTH];
        int[] frame = new int[GroveDht22.FRAME_LENGTH];
        Delay.millis(1000);
        while (true) {
            if (dht22.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
                Serial.print("Temperature C: ");
                printTenths(reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS]);
                Serial.print("  Humidity %: ");
                printTenths(reading[GroveDht22.HUMIDITY_TENTHS_PERCENT]);
                Serial.println("");
            } else {
                Serial.println("DHT22-format read failed");
            }
            Delay.millis(2000);
        }
    }

    private static void printTenths(int value) {
        if (value < 0) {
            Serial.print("-");
        }
        int magnitude = value < 0 ? -value : value;
        Serial.print(magnitude / 10);
        Serial.print(".");
        Serial.print(magnitude % 10);
    }
}
