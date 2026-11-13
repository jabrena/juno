package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Diagnoses an analog gas sensor on A0 and a PIR on D3. A gas sensor on D7 cannot be read correctly; move that plug
 * before running.
 */
@Board({ArduinoUnoR3WiFiRev2Board.class})
public final class GroveSensorWiringMonitor {
    private GroveSensorWiringMonitor() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        Serial.begin(BaudRate.BAUD_115200);
        GrovePirMotion pir = new GrovePirMotion(MCU.getGpio());
        GroveAnalogGas gas = new GroveAnalogGas(MCU.getGpio());
        pir.begin(shield.getD3());
        Delay.millis(1000);
        while (true) {
            Serial.print("Gas ADC A0: ");
            Serial.println(gas.rawValue(shield.getA0()));
            Serial.print("PIR D3: ");
            Serial.println(pir.motionDetected(shield.getD3()));
            Delay.millis(2000);
        }
    }
}
