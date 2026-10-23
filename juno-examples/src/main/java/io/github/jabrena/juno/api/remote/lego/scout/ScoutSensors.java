package io.github.jabrena.juno.api.remote.lego.scout;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.scout.RemoteScout;
import io.github.jabrena.juno.util.Delay;

/** Reads the touch sensor on the Scout's left/input 1 connector and the temperature sensor on right/input 2 over IR. */
public class ScoutSensors {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;

    private final RcxTouchSensor touch;
    private final RcxTemperatureSensor temperature;

    ScoutSensors(RemoteScout remote) {
        // The user's setup has the touch sensor on the left connector and the temperature sensor on the right.
        this.touch = new RcxTouchSensor(remote.getSensor1());
        this.temperature = new RcxTemperatureSensor(remote.getSensor2());
    }

    public static void main(String[] args) {
        RemoteScout remote = new RemoteScout();
        ScoutSensors sensors = new ScoutSensors(remote);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        if (!sensors.touch.begin() || !sensors.temperature.begin()) {
            Serial.println("The Scout did not answer sensor configuration: check power, range and IR wiring");
            return;
        }

        while (true) {
            Serial.println(sensors.touch.isPressed() ? "Touch: pressed" : "Touch: released");
            Serial.print("Temperature: ");
            Serial.print(sensors.temperature.temperatureTenths());
            Serial.println(" tenths C");
            Delay.millis(1000);
        }
    }
}
