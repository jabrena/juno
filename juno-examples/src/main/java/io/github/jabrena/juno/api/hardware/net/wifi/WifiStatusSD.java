package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

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
    private static final Wifi WIFI = ArduinoUno.getWifi();
    private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();

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

        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }

            Properties properties = new Properties();
            properties.load(file);

            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }

            return new WifiCredentials(ssid, password);
        }
    }

    private record WifiCredentials(String ssid, String password) {
    }
}
