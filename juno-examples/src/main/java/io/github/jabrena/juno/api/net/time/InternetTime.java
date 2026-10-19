package io.github.jabrena.juno.api.net.time;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.io.serial.BaudRate;
import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.net.InternetClock;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.thirdparty.sd.SdCard;
import io.github.jabrena.juno.api.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Properties;

/**
 * Prints UTC obtained by {@link LocalDateTime#now()}, with Wi-Fi and optional clock settings loaded from the SD card.
 *
 * <p>Example {@code application.properties}:
 * <pre>{@code
 * wifi.ssid=your-network
 * wifi.password=your-password
 * time.api.url=https://gateway.timeapi.world/timezone/Etc/UTC
 * time.api.jsonPath=utc_datetime
 * }</pre>
 *
 * <p>The two {@code time.api.*} entries are optional; without them the compiled environment configuration, or the
 * built-in TimeAPI.io {@code dateTime} default, is used.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class InternetTime {
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private InternetTime() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SdCard.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        InputStream file = SdCard.open("application.properties");
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

        InternetClock.configure(properties);
        Wifi.begin(ssid, password);
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
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
