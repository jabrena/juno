package io.github.jabrena.juno.api.hardware.device.carrier;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads a Grove PIR motion sensor plugged into a {@link NanoConnectorCarrier}'s A2/A3 socket and a Grove Light
 * Sensor v1.2 in its A0/A1 socket, printing both once a second. Keep the PIR out of the I2C socket: the bus
 * pull-ups hold its output between low and high.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.carrier.NanoCarrierGroveSensors -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierGroveSensors {
    private NanoCarrierGroveSensors() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GrovePirMotion pir = new GrovePirMotion();
        LightDetector light = new GroveLightV12(ArduinoNano.getGpio(), carrier.getGroveA0(),
                GroveLightV12.ADC_12_BIT);
        Serial.begin(BaudRate.BAUD_115200);
        pir.begin(carrier.getGroveA2());
        Delay.millis(2000);

        while (true) {
            Serial.print("PIR A2: ");
            Serial.print(pir.motionDetected(carrier.getGroveA2()) ? "motion" : "idle");
            Serial.print(" | Light A0 %: ");
            Serial.println((int) light.getLightValue());
            Delay.millis(1000);
        }
    }
}
