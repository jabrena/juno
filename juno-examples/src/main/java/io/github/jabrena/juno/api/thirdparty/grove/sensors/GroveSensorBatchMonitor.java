package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Monitors the new Grove sensor batch: DHT11 on D4, temperature v1.2 on A0, sound v1.6 on A1,
 * and light v1.2 on A2.
 */
@Board({ArduinoUnoR3WiFiRev2.class})
public final class GroveSensorBatchMonitor {
    private static final int DHT11_PIN = GroveBaseShield.D4;
    private static final int TEMPERATURE_PIN = GroveBaseShield.A0;
    private static final int SOUND_PIN = GroveBaseShield.A1;
    private static final int LIGHT_PIN = GroveBaseShield.A2;

    private GroveSensorBatchMonitor() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        int[] reading = new int[GroveDht11.READING_LENGTH];
        int[] frame = new int[GroveDht11.FRAME_LENGTH];
        Delay.millis(1000);
        while (true) {
            if (GroveDht11.read(DHT11_PIN, reading, reading.length, frame, frame.length)) {
                Serial.print("DHT11 temperature C: ");
                Serial.print(reading[GroveDht11.TEMPERATURE_CELSIUS]);
                Serial.print("  humidity %: ");
                Serial.println(reading[GroveDht11.HUMIDITY_PERCENT]);
            } else {
                Serial.println("DHT11 read failed");
            }
            int temperatureAdc = GroveTemperatureSensorV12.rawValue(TEMPERATURE_PIN);
            Serial.print("Temperature sensor C: ");
            Serial.println(GroveTemperatureSensorV12.celsiusFromRaw(temperatureAdc));
            Serial.print("Temperature sensor ADC A0: ");
            Serial.println(temperatureAdc);
            Serial.print("Sound sensor ADC A1: ");
            Serial.println(GroveSoundSensorV16.rawValue(SOUND_PIN));
            Serial.print("Light sensor ADC A2: ");
            Serial.println(GroveLightSensorV12.rawValue(LIGHT_PIN));
            Delay.millis(2000);
        }
    }
}
