package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveGpsAir530;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;

/** Reads signed latitude, longitude and altitude from a Grove GPS Air530 on the Grove Base Shield's D2 socket. */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR3WiFiRev2Board.class, ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class GroveGpsAir530Reader {
    private GroveGpsAir530Reader() {
    }

    public static void main(String[] args) {
        Gpio gpio = MCU.getGpio();
        Clock clock = MCU.getClock();
        GroveBaseShield shield = new GroveBaseShield(gpio);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, shield.getD2());
        byte[] sentence = new byte[GroveGpsAir530.SENTENCE_CAPACITY];
        int[] position = new int[GroveGpsAir530.POSITION_LENGTH];

        Serial.begin(BaudRate.BAUD_115200);
        gps.begin();
        Serial.println("Grove GPS Air530 ready");
        while (true) {
            if (gps.readPosition(sentence, sentence.length, position, position.length, 5_000)) {
                Serial.print("latitude microdegrees=");
                Serial.println(position[GroveGpsAir530.LATITUDE_MICRODEGREES]);
                Serial.print("longitude microdegrees=");
                Serial.println(position[GroveGpsAir530.LONGITUDE_MICRODEGREES]);
                Serial.print("altitude centimetres=");
                Serial.println(position[GroveGpsAir530.ALTITUDE_CENTIMETRES]);
            } else {
                Serial.println("GPS fix unavailable");
            }
        }
    }
}
