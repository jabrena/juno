package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.util.Delay;

/** Prints the light level, in percent, of a Grove Light Sensor v1.2 on the Grove Base Shield's A2 socket. */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class})
public final class GroveLightMonitor {

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        Serial.begin(BaudRate.BAUD_115200);
        LightDetector light = new GroveLightV12(MCU.getGpio(), shield.getA2(), GroveLightV12.ADC_10_BIT);
        Delay.millis(1000);
        while (true) {
            Serial.print("Light A2 %: ");
            Serial.println((int) light.getLightValue());
            Delay.millis(1000);
        }
    }
}
