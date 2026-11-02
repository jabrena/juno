package io.github.jabrena.juno.api.hardware.net.udp;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.SntpClock;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the Cardputer's microSD card, then reads the
 * clock over SNTP with {@link LocalDateTime#now()} and prints the UTC time every five seconds at 115200 baud. The SNTP
 * server can be set on the card with {@code sntp.host} and {@code sntp.port}, as in {@link SntpTimeSD}.
 *
 * <p>A time of {@code LocalDateTime.MIN} means the clock is not set yet, and the program says so instead of printing it.
 */
@Board({M5StackCardputerBoard.class})
public final class M5CardputerSntpTime {
    private static final Wifi WIFI = M5StackCardputer.getWifi();
    private static final SdCard SD_CARD = M5StackCardputer.getSd();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int PRINT_INTERVAL_MS = 5000;

    private M5CardputerSntpTime() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        if (!loadAndConnect()) {
            return;
        }

        while (true) {
            LocalDateTime current = LocalDateTime.now();
            if (current.equals(LocalDateTime.MIN)) {
                Serial.println("Internet time unavailable");
            } else {
                Serial.println(current.toString());
            }
            Delay.millis(PRINT_INTERVAL_MS);
        }
    }

    /** Reads the Wi-Fi credentials and the optional SNTP server from the card, then joins the network. */
    private static boolean loadAndConnect() throws IOException {
        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return false;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return false;
            }
            SntpClock.configure(properties);
            WIFI.begin(ssid, password);
        }
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }
}
