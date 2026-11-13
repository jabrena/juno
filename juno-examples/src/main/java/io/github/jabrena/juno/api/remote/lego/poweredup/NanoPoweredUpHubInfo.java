package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

/**
 * Connects the Nano ESP32 to a LEGO Powered UP hub over Bluetooth LE and prints the hub's type, battery level and
 * signal strength every two seconds at 115200 baud. It only reads from the hub: no motor or light is driven.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.remote.lego.poweredup.NanoPoweredUpHubInfo -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoPoweredUpHubInfo {
    private static final int REPORT_INTERVAL_MS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("Waiting for a Powered Up hub...");
        RemotePoweredUpHub hub = new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock());
        if (!hub.connect(0)) {
            Serial.println("hub connection failed");
            return;
        }
        Serial.print("hub type=");
        Serial.println(hub.hubType());
        while (true) {
            Serial.print("battery=");
            Serial.print(hub.batteryPercent());
            Serial.print(" rssi=");
            Serial.println(hub.rssi());
            Delay.millis(REPORT_INTERVAL_MS);
        }
    }
}
