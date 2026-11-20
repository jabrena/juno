package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveSoundV16;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints the raw ADC level of a Grove Sound Sensor v1.6 on the Grove Base Shield's A1 socket. */
@Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class})
public final class GroveSoundMonitor {

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        Serial.begin(BaudRate.BAUD_115200);
        GroveSoundV16 sound = new GroveSoundV16(MCU.getGpio());
        Delay.millis(1000);
        while (true) {
            Serial.print("Sound ADC A1: ");
            Serial.println(sound.rawValue(shield.getA1()));
            Delay.millis(500);
        }
    }
}
