package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
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
@SuppressWarnings("java:S2189")
public final class NanoPoweredUpHubInfo {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final int REPORT_INTERVAL_MS = 2000;

    private NanoPoweredUpHubInfo() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("Waiting for a Powered Up hub...");
        if (!HUB.connect(0)) {
            Serial.println("hub connection failed");
            return;
        }
        Serial.print("hub type=");
        Serial.println(HUB.hubType());
        while (true) {
            Serial.print("battery=");
            Serial.print(HUB.batteryPercent());
            Serial.print(" rssi=");
            Serial.println(HUB.rssi());
            Delay.millis(REPORT_INTERVAL_MS);
        }
    }
}
