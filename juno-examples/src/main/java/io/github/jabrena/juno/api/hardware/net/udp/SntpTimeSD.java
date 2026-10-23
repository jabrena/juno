package io.github.jabrena.juno.api.hardware.net.udp;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
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
 * {@link SntpTime} configured from the SD card instead of the build: prints UTC obtained by {@link LocalDateTime#now()}
 * over SNTP, with the Wi-Fi credentials and an optional SNTP server loaded from {@code application.properties} and
 * applied with {@link SntpClock#configure}. The card's chip-select line is D10.
 *
 * <p>Example {@code application.properties}:
 * <pre>{@code
 * wifi.ssid=your-network
 * wifi.password=your-password
 * sntp.host=time.google.com
 * sntp.port=123
 * }</pre>
 *
 * <p>The two {@code sntp.*} entries are optional; without them the build-time {@code JUNO_SNTP_HOST} and
 * {@code JUNO_SNTP_PORT}, or the built-in {@code pool.ntp.org} on port 123, are used.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class, ArduinoOneR3WiFiRev2Board.class})
public final class SntpTimeSD {
    private static final Wifi WIFI = Arduino.getWifi();
    private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private SntpTimeSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        InputStream file = SD_CARD.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();

        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return;
        }

        SntpClock.configure(properties);
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        LocalDateTime current = LocalDateTime.now();
        if (current.equals(LocalDateTime.MIN)) {
            Serial.println("Internet time unavailable");
        } else {
            Serial.println(current.toString());
        }
    }

}
