package io.github.jabrena.juno.api.net;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads WiFi credentials from {@code application.properties} on the SD card in an AZDelivery Data
 * Logger Module Data Recorder Shield. The shield routes the card's SPI chip-select line to D10.
 *
 * <p>After starting the connection, the example prints the numeric value returned by
 * {@link Wifi#status()} once per second. A value of {@link Wifi#STATUS_CONNECTED} ({@code 3}) means
 * that the UNO R4 WiFi is connected to the network using the credentials loaded from the card.
 * Everything is reported over Serial at 115200 baud.
 */
public final class WifiStatusSD {
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            Serial.println("Not possible to start WiFi: credentials could not be loaded from the SD card");
            return;
        }

        Serial.println("Starting WiFi from SD properties");
        Wifi.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Serial.println("WiFi status: " + Wifi.status());
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return;
        }

        Serial.println("WiFi connected");
        while (true) {
            Serial.println("WiFi status: " + Wifi.status());
            Delay.millis(1000);
        }
    }

    private static WifiCredentials loadCredentials() throws IOException {
        if (!SdCard.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }

        InputStream file = SdCard.open("application.properties");
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
