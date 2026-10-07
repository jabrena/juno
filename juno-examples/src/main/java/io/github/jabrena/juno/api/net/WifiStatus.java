package io.github.jabrena.juno.api.net;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Minimal WiFi connectivity probe for the experimental {@code Thumb2AsmBackend}. Connects using
 * build-time credentials from {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} and prints
 * {@link Wifi#status} once a second, so a connection attempt is observable over serial even while it
 * has not yet reached {@link Wifi#STATUS_CONNECTED} (unlike {@code MadridWeather}, which prints
 * nothing until connected).
 */
public final class WifiStatus {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Starting WiFi");
        Wifi.begin(
            System.getenv("JUNO_WIFI_SSID"), 
            System.getenv("JUNO_WIFI_PASSWORD"));

        while (true) {
            Serial.println("WiFi status: " + Wifi.status());
            Delay.millis(1000);
        }
    }
}
