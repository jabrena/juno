package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Connects to WiFi, then periodically requests Madrid's current temperature and local time from
 * the free, no-API-key Open-Meteo forecast API over certificate-validated HTTPS, and scrolls
 * "Madrid Weather: &lt;temp&gt;C" followed by "Madrid Time: HH:MM" across the UNO R4 WiFi's 12x8 LED
 * matrix. It uses the same right-to-left one-pixel-per-tick idea as {@code LedMatrixScrollingText},
 * adapted for messages built at runtime
 * instead of a compile-time literal. Credentials come from {@code JUNO_WIFI_SSID}/
 * {@code JUNO_WIFI_PASSWORD} (resolved by Juno itself at compile time, never written to a file).
 *
 * <p>{@link WeatherClient} fetches and parses the Open-Meteo response, {@link TimeClient} extracts
 * the local time, and {@link DisplayData} builds and scrolls the runtime messages on the matrix.
 * The temperature flows between those components as a bounded runtime {@link String}.
 *
 * <p>Open-Meteo's {@code current.time} is a fixed-width ISO-8601 string
 * ({@code "2026-09-20T16:00"}); the hour/minute are read directly out of its known character
 * positions (11-12 and 14-15) rather than parsed generically. Open-Meteo's
 * {@code current.temperature_2m} is a JSON number with a decimal fraction (e.g. {@code 21.2});
 * {@link WeatherClient} reads its raw JSON text directly via {@link
 * io.github.jabrena.juno.api.io.net.Json#getString(byte[], int, String)} rather than parsing it
 * to a {@code double} and reformatting, so the displayed value matches Open-Meteo's own text
 * exactly rather than being subject to binary-floating-point rounding.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class MadridWeather {
    private static final int RESPONSE_BUFFER_SIZE = 512;
    private static final int TIME_TEXT_BUFFER_SIZE = 32;

    private static void processWeather(DisplayData display, byte[] response) {
        int responseBytes = WeatherClient.fetch(response, RESPONSE_BUFFER_SIZE);
        if (responseBytes <= 0) {
            Serial.println("Weather request failed");
            return;
        }

        Serial.print("Weather response bytes: ");
        Serial.println(responseBytes);
        String temperature = WeatherClient.getTemperature(response, responseBytes);
        if (temperature != null) {
            display.showTemperature(temperature);
        } else {
            Serial.println("Unexpected weather JSON");
        }
    }

    private static void processTime(DisplayData display, byte[] response, byte[] timeText) {
        int responseBytes = TimeClient.fetch(response, RESPONSE_BUFFER_SIZE);
        if (responseBytes <= 0) {
            Serial.println("Time request failed");
            return;
        }

        Serial.print("Time response bytes: ");
        Serial.println(responseBytes);
        int timeTextLength = TimeClient.read(response, responseBytes, timeText, TIME_TEXT_BUFFER_SIZE);
        if (TimeClient.isAvailable(timeTextLength)) {
            display.showTime(timeText);
        } else {
            Serial.println("Unexpected time JSON");
        }
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(1000);
        }
        Serial.println("WiFi connected");

        DisplayData display = new DisplayData();
        display.begin();
        // Allocated once, outside the loop, and overwritten every iteration: Juno's arena never frees
        // memory, so fresh allocations inside a while(true) would exhaust it after a few hundred cycles.
        byte[] response = new byte[RESPONSE_BUFFER_SIZE];
        byte[] timeText = new byte[TIME_TEXT_BUFFER_SIZE];

        while (true) {
            processWeather(display, response);
            processTime(display, response, timeText);
            Delay.millis(1000);
        }
    }
}
