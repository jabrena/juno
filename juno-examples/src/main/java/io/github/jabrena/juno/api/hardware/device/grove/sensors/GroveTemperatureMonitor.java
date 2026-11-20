package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints the temperature and raw ADC value of a Grove Temperature Sensor v1.2 on the Grove Base Shield's A0 socket. */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class})
public final class GroveTemperatureMonitor {

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        Serial.begin(BaudRate.BAUD_115200);
        GroveTemperatureV12 thermistor = new GroveTemperatureV12(MCU.getGpio());
        Delay.millis(1000);
        while (true) {
            int adc = thermistor.rawValue(shield.getA0());
            Serial.print("Temperature C: ");
            Serial.print(thermistor.celsiusFromRaw(adc));
            Serial.print("  ADC A0: ");
            Serial.println(adc);
            Delay.millis(2000);
        }
    }
}
