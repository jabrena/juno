package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Minimal WiFi connectivity probe for the experimental {@code Thumb2AsmBackend}. Connects using
 * credentials from the ATECC608 {@code SecretStore} and prints
 * {@link Wifi#status} once a second, so a connection attempt is observable over serial even while it
 * has not yet reached {@link Wifi#STATUS_CONNECTED} (unlike {@code MadridWeather}, which prints
 * nothing until connected).
 */
public final class WifiStatus {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Starting WiFi");
        WIFI.begin(
            ExampleSecrets.wifiSsid(),
            ExampleSecrets.wifiPassword());

        while (true) {
            Serial.println("WiFi status: " + WIFI.status());
            Delay.millis(1000);
        }
    }
}
