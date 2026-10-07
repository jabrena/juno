package io.github.jabrena.juno.api.net;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * {@link WifiStatusSD} plus an LCD Keypad Shield: loads WiFi credentials from {@code application.properties} on the SD card.
 * The SD reader comes from the AZDelivery Data Logger Module Data Recorder Shield, which routes the
 * card's SPI chip-select line to D10; the LCD Keypad Shield is stacked with it for the display.
 *
 * <p>After starting the connection, the example prints the numeric value returned by
 * {@link Wifi#status()} once per second. A value of {@link Wifi#STATUS_CONNECTED} ({@code 3}) means
 * that the UNO R4 WiFi is connected to the network using the credentials loaded from the card.
 * The LCD's first row displays {@code Wifi connection}; its second row changes from
 * {@code Connecting} to either {@code Connected} or {@code Error}.
 *
 * <p>The LCD Keypad Shield uses D10 for its backlight while the data logger shield uses D10 for SD
 * chip select. Electrically isolate or reroute the LCD backlight's D10 connection before stacking
 * the shields. This program never changes the LCD backlight after SD-card initialization.
 */
public final class WifiStatusSDLcd {
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);

        LcdKeypadShield.begin();
        LcdKeypadShield.print("Wifi connection");
        showStatus("Connecting      ");

        Delay.millis(2000);

        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            Serial.println("Not possible to start WiFi: credentials could not be loaded from the SD card");
            showStatus("Error           ");
            return;
        }

        Serial.println("Starting WiFi from SD properties");
        Wifi.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Serial.print("WiFi status: ");
            Serial.println(Wifi.status());
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            showStatus("Error           ");
            return;
        }

        showStatus("Connected       ");
        while (true) {
            Serial.print("WiFi status: ");
            Serial.println(Wifi.status());
            Delay.millis(1000);
        }
    }

    private static void showStatus(String status) {
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print(status);
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
