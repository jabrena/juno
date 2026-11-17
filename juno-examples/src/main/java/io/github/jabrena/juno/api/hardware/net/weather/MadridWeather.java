package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Connects to WiFi, then periodically requests Madrid's current temperature and local time from
 * the free, no-API-key Open-Meteo forecast API over certificate-validated HTTPS, and scrolls
 * "Madrid Weather: &lt;temp&gt;C" followed by "Madrid Time: HH:MM" across the built-in LED
 * matrix. It uses the same right-to-left one-pixel-per-tick idea as {@code LedMatrixScrollingText},
 * adapted for messages built at runtime
 * instead of a compile-time literal. Credentials come from the board's secret store.
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
 * io.github.jabrena.juno.hardware.net.http.JsonView#asString()} rather than parsing it
 * to a {@code double} and reformatting, so the displayed value matches Open-Meteo's own text
 * exactly rather than being subject to binary-floating-point rounding.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class MadridWeather {
    private static final Wifi WIFI = MCU.getWifi();

    private static void processWeather(DisplayData display, WeatherClient weather) throws InterruptedException {
        String temperature = weather.fetchTemperature();
        if (temperature != null) {
            Serial.print("Weather OK len=");
            Serial.println(temperature.length());
            display.showTemperature(temperature);
        } else {
            Serial.println("Weather request failed");
            display.showMessage("Weather failed");
        }
    }

    private static void processTime(DisplayData display, TimeClient clock) throws InterruptedException {
        String time = clock.fetchTime();
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
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));

        int waitedSeconds = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(1000);
            waitedSeconds = waitedSeconds + 1;
            if (waitedSeconds % 5 == 0) {
                Serial.print("Still connecting, WIFI.status()=");
                Serial.println(WIFI.status());
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

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        DisplayData display = new DisplayData();
        connectWifi(display);

        // Each service owns its client, created once, outside the loop; each response body is a String the
        // collector reclaims once the value read from it has been shown.
        WeatherClient weather = new WeatherClient();
        TimeClient clock = new TimeClient();

        while (true) {
            processWeather(display, weather);
            processTime(display, clock);
            Delay.millis(1000);
        }
    }
}
