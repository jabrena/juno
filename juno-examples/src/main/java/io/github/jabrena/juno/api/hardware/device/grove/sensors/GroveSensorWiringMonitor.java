package io.github.jabrena.juno.api.hardware.device.grove.sensors;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveCompassProbe;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Diagnoses a Grove compass on I2C, an analog gas sensor on A0, and a PIR on D3. A compass on D8
 * or an analog gas sensor on D7 cannot be read correctly; move those plugs before running.
 */
@Board({ArduinoUnoR3WiFiRev2Board.class})
public final class GroveSensorWiringMonitor {
    private GroveSensorWiringMonitor() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        Serial.begin(BaudRate.BAUD_115200);
        GrovePirMotion pir = new GrovePirMotion();
        GroveAnalogGas gas = new GroveAnalogGas();
        GroveCompassProbe compassProbe = new GroveCompassProbe();
        pir.begin(shield.getD3());
        Delay.millis(1000);
        int compass = compassProbe.detect();
        Serial.print("Compass chip: ");
        switch (compass) {
            case GroveCompassProbe.BMM150 -> Serial.println("BMM150 (Grove V2.0)");
            default -> Serial.println("not found on I2C");
        }
        int[] compassAxes = new int[GroveCompassProbe.AXIS_LENGTH];
        while (true) {
            boolean compassReading = compass == GroveCompassProbe.BMM150
                    && compassProbe.readRaw(compassAxes, compassAxes.length);
            Serial.print("Compass X/Y/Z: ");
            if (compassReading) {
                Serial.print(compassAxes[GroveCompassProbe.X_AXIS]);
                Serial.print(" / ");
                Serial.print(compassAxes[GroveCompassProbe.Y_AXIS]);
                Serial.print(" / ");
                Serial.println(compassAxes[GroveCompassProbe.Z_AXIS]);
            } else {
                Serial.println("read failed");
            }
            Serial.print("Gas ADC A0: ");
            Serial.println(gas.rawValue(shield.getA0()));
            Serial.print("PIR D3: ");
            Serial.println(pir.motionDetected(shield.getD3()));
            Delay.millis(2000);
        }
    }
}
