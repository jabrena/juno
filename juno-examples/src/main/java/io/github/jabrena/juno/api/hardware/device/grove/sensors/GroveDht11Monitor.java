package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints temperature and humidity from a Grove DHT11 on the Grove Base Shield's D4 socket. */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class})
public final class GroveDht11Monitor {

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        Serial.begin(BaudRate.BAUD_115200);
        GroveDht11 dht11 = new GroveDht11();
        int[] reading = new int[GroveDht11.READING_LENGTH];
        int[] frame = new int[GroveDht11.FRAME_LENGTH];
        Delay.millis(1000);
        while (true) {
            if (dht11.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
                Serial.print("DHT11 temperature C: ");
                Serial.print(reading[GroveDht11.TEMPERATURE_CELSIUS]);
                Serial.print("  humidity %: ");
                Serial.println(reading[GroveDht11.HUMIDITY_PERCENT]);
            } else {
                Serial.println("DHT11 read failed");
            }
            Delay.millis(2000);
        }
    }
}
