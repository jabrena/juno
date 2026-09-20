package io.github.jabrena.juno.api.io.net;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Minimal WiFi connectivity probe for the experimental {@code CortexM4AsmBackend}. Connects using
 * build-time credentials from {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} and prints
 * {@link Wifi#status} once a second, so a connection attempt is observable over serial even while it
 * has not yet reached {@link Wifi#STATUS_CONNECTED} (unlike {@code MadridWeather}, which prints
 * nothing until connected).
 */
public final class WifiStatus {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("Starting WiFi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        while (true) {
            Serial.print("WiFi status: ");
            Serial.println(Wifi.status());
            Delay.millis(1000);
        }
    }
}
