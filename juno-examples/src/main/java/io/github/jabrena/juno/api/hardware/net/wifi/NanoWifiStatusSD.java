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
 * Loads WiFi credentials from {@code application.properties} on the microSD card of an Arduino Nano Connector
 * Carrier, then joins the network with the Nano ESP32's onboard Wi-Fi. The card's chip select is the carrier's D4.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password} entries (see the SD card storage page). After
 * connecting, the example prints {@link Wifi#status()} once per second; {@link Wifi#STATUS_CONNECTED} ({@code 3})
 * means the Nano ESP32 is on the network. Everything is reported over Serial at 115200 baud.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.wifi.NanoWifiStatusSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoWifiStatusSD {
    private static final Wifi WIFI = Arduino.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private NanoWifiStatusSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            Serial.println("Not possible to start WiFi: credentials could not be loaded from the SD card");
            return;
        }

        Serial.println("Starting WiFi from SD properties");
        WIFI.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Serial.println("WiFi status: " + WIFI.status());
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return;
        }

        Serial.println("WiFi connected");
        while (true) {
            Serial.println("WiFi status: " + WIFI.status());
            Delay.millis(1000);
        }
    }

    private static WifiCredentials loadCredentials() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }

        InputStream file = SD_CARD.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return null;
        }

        Properties properties = new Properties();
        properties.load(file);
        file.close();

        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return null;
        }

        return new WifiCredentials(ssid, password);
    }

    private record WifiCredentials(String ssid, String password) {
    }
}
