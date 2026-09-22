package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.http.HttpsClient;
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
 * and formats the local time, and {@link DisplayData} builds and scrolls the runtime messages on
 * the matrix. Both the temperature and the formatted time flow between these components as
 * runtime {@link String}s, built with {@link StringBuilder} in {@code TimeClient}'s case.
 *
 * <p>Open-Meteo's {@code current.time} is a fixed-width ISO-8601 string
 * ({@code "2026-09-20T16:00"}); the hour/minute are read directly out of its known character
 * positions (11-12 and 14-15) rather than parsed generically. Open-Meteo's
 * {@code current.temperature_2m} is a JSON number with a decimal fraction (e.g. {@code 21.2});
 * {@link WeatherClient} reads its raw JSON text directly via {@link
 * io.github.jabrena.juno.api.io.net.http.Json#getString(byte[], int, String)} rather than parsing it
 * to a {@code double} and reformatting, so the displayed value matches Open-Meteo's own text
 * exactly rather than being subject to binary-floating-point rounding.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class MadridWeather {
    private static final int TIME_TEXT_BUFFER_SIZE = 32;

    private static void processWeather(DisplayData display, byte[] response, byte[] headers,
            int[] statusAndHeadersLength) {
        String temperature = WeatherClient.fetchTemperature(response, HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE,
                headers, HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE, statusAndHeadersLength);
        if (temperature != null) {
            Serial.print("Weather OK len=");
            Serial.println(temperature.length());
            display.showTemperature(temperature);
        } else {
            Serial.println("Weather request failed");
            display.showMessage("Weather failed");
        }
    }

    private static void processTime(DisplayData display, byte[] response, byte[] headers, byte[] timeText,
            int[] statusAndHeadersLength) {
        String time = TimeClient.fetchTime(response, HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE,
                headers, HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE, statusAndHeadersLength,
                timeText, TIME_TEXT_BUFFER_SIZE);
        if (time != null) {
            Serial.print("Time OK len=");
            Serial.println(time.length());
            display.showTime(time);
        } else {
            Serial.println("Time request failed");
            display.showMessage("Time failed");
        }
    }

    private static void connectWifi(DisplayData display) {
        display.showMessage("Connecting");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        int waitedSeconds = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(1000);
            waitedSeconds = waitedSeconds + 1;
            if (waitedSeconds % 5 == 0) {
                Serial.print("Still connecting, Wifi.status()=");
                Serial.println(Wifi.status());
            }
        }
        Serial.println("WiFi connected");
        display.showMessage("Connected");
        // WiFi.status() reports connected before the module's network stack (DHCP/DNS) is actually
        // ready for outbound connections — the first HTTPS request right after this point reliably
        // fails with client.connect() itself returning false (confirmed on real hardware: status=0,
        // bodyLength=-1), while every later request succeeds. A short settle delay here avoids
        // spending that first request on a connection that was never going to work.
        Delay.millis(2000);
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        DisplayData display = new DisplayData();
        display.begin();
        connectWifi(display);

        // Allocated once, outside the loop, and overwritten every iteration: Juno's arena never frees
        // memory, so fresh allocations inside a while(true) would exhaust it after a few hundred cycles.
        // Each service gets its own buffer/scratch pair rather than sharing one, so a weather request
        // can never leave stale bytes behind for the time request (or vice versa) to read.
        byte[] weatherResponse = new byte[HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        byte[] weatherHeaders = new byte[HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        int[] weatherStatusAndHeadersLength = new int[2];
        
        byte[] timeResponse = new byte[HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        byte[] timeHeaders = new byte[HttpsClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        int[] timeStatusAndHeadersLength = new int[2];
        
        byte[] timeText = new byte[TIME_TEXT_BUFFER_SIZE];

        while (true) {
            processWeather(display, weatherResponse, weatherHeaders, weatherStatusAndHeadersLength);
            processTime(display, timeResponse, timeHeaders, timeText, timeStatusAndHeadersLength);
            Delay.millis(1000);
        }
    }
}
