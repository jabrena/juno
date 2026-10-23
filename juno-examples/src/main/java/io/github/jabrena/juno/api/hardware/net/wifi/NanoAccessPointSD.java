package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Starts a Wi-Fi access point named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * so a phone or laptop can join the Nano ESP32 directly. Without a card or configuration file, it starts an open
 * network named {@link Wifi#DEFAULT_ACCESS_POINT_SSID}. Prints the board's address (192.168.4.1 unless the core changes
 * it) and the status every five seconds at 115200 baud; {@link Wifi#STATUS_AP_LISTENING} ({@code 7}) means the access
 * point is up.
 *
 * <p>The card needs {@code ap.ssid} and {@code ap.password}. The passphrase must be at least eight characters (WPA2);
 * leave {@code ap.password} empty to open the network.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.wifi.NanoAccessPointSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoAccessPointSD {
    private static final Wifi WIFI = Arduino.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final AccessPoint DEFAULT_ACCESS_POINT = new AccessPoint(Wifi.DEFAULT_ACCESS_POINT_SSID,
            Wifi.DEFAULT_ACCESS_POINT_PASSWORD);
    private static final int STATUS_INTERVAL_MS = 5000;

    private NanoAccessPointSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        AccessPoint accessPoint = loadAccessPoint();
        WIFI.beginAP(accessPoint.ssid(), accessPoint.password());
        printAddress();
        while (true) {
            Serial.print("WiFi status: ");
            Serial.println(WIFI.status());
            Delay.millis(STATUS_INTERVAL_MS);
        }
    }

    private static void printAddress() {
        int[] octets = new int[4];
        WIFI.localIP(octets);
        Serial.print("access point address=");
        Serial.print(octets[0]);
        Serial.print(".");
        Serial.print(octets[1]);
        Serial.print(".");
        Serial.print(octets[2]);
        Serial.print(".");
        Serial.println(octets[3]);
    }

    private static AccessPoint loadAccessPoint() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card unavailable; starting open JunoAP");
            return DEFAULT_ACCESS_POINT;
        }
        InputStream file = SD_CARD.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found; starting open JunoAP");
            return DEFAULT_ACCESS_POINT;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();
        String ssid = properties.getProperty("ap.ssid", Wifi.DEFAULT_ACCESS_POINT_SSID);
        String password = properties.getProperty("ap.password", Wifi.DEFAULT_ACCESS_POINT_PASSWORD);
        return new AccessPoint(ssid, password);
    }

    private record AccessPoint(String ssid, String password) {
    }
}
