package io.github.jabrena.juno.api.hardware.net.udp;

import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.time.LocalDateTime;

/**
 * Prints UTC from {@link LocalDateTime#now()} every ten seconds at 115200 baud. Joins Wi-Fi with
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}; the clock synchronizes over SNTP with {@code JUNO_SNTP_HOST}
 * (default {@code pool.ntp.org}) and {@code JUNO_SNTP_PORT} (default 123). Small enough for a UNO R3 with the Arduino
 * WiFi Shield (build it with {@code -Djuno.Xmx=512}).
 */
@SuppressWarnings("java:S2189")
public final class SntpTime {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private SntpTime() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connection failed");
            return;
        }
        Serial.println("wifi connected");

        while (true) {
            LocalDateTime now = LocalDateTime.now();
            if (now.equals(LocalDateTime.MIN)) {
                Serial.println("sntp unavailable");
            } else {
                Serial.println(now.toString());
            }
            Delay.millis(10000);
        }
    }
}
