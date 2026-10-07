package io.github.jabrena.juno.api.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.HttpsClient;
import io.github.jabrena.juno.api.net.http.Json;
import io.github.jabrena.juno.api.io.storage.SdCard;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A desk weather station for the ELEGOO 2.8" TFT touch screen shield, in the style of the small
 * "weather clock" cubes: city, wind speed, a large seven-segment clock, the current condition with
 * an icon, the temperature, the date and weekday, and temperature/humidity bars.
 *
 * <p>WiFi credentials come from {@code application.properties} on the shield's microSD card (see
 * {@link io.github.jabrena.juno.api.net.WifiStatusTFT}). Once connected, the board's public IP
 * is geolocated with <a href="https://ipinfo.io">ipinfo.io</a> ({@code city} and {@code loc}), and
 * those coordinates are passed to <a href="https://open-meteo.com">Open-Meteo</a> for the current
 * weather and the location's UTC offset. The request path depends on runtime data, so it is built
 * in a {@code byte[]} and sent with {@link HttpsClient#get(String, int, byte[], int, byte[], int,
 * byte[], int, int[])}.
 *
 * <p>The clock is set from the HTTPS response's {@code Date} header plus Open-Meteo's
 * {@code utc_offset_seconds}, then kept running with {@link Clock#millis()}. Weather refreshes
 * every ten minutes, or immediately when the screen is tapped.
 * On UNO Q, Linux must already be connected to Wi-Fi; the credentials loaded from the SD card
 * are used only by the UNO R4 WiFi.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class WeatherTFT {
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int REFRESH_MILLIS = 10 * 60 * 1000;
    private static final int RETRY_MILLIS = 60 * 1000;
    // The first HTTPS request right after WiFi connects often fails (DHCP/DNS still settling), so
    // wait briefly first and retry the location lookup quickly.
    private static final int NETWORK_SETTLE_MILLIS = 2000;
    private static final int LOCATION_RETRY_MILLIS = 5000;
    private static final int HTTP_STATUS_OK = 200;
    private static final int HTTPS_PORT = 443;

    // Latest weather.
    private static int temperature;
    private static int humidity;
    private static int windSpeed;
    private static int condition;
    private static boolean isDay;

    // Local time: seconds since the Unix epoch at the location, as of clockBaseMillis.
    private static int clockBaseSeconds;
    private static int clockBaseMillis;
    private static boolean clockSet;

    private WeatherTFT() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);

        byte[] response = new byte[1024];
        byte[] headers = new byte[512];
        int[] statusAndHeaders = new int[2];
        byte[] city = new byte[32];
        byte[] location = new byte[32];
        byte[] path = new byte[256];
        byte[] publicIp = new byte[48];

        WeatherScreen.showStatus("Connecting WiFi...");
        while (!connectWifi()) {
            WeatherScreen.showStatus("WiFi error, retry ");
            Delay.millis(5000);
        }

        WeatherScreen.showStatus("Locating IP...    ");
        Delay.millis(NETWORK_SETTLE_MILLIS);
        int cityLength = -1;
        int locateAttempts = 0;
        while (cityLength < 0) {
            cityLength = locate(response, headers, statusAndHeaders, city, location, publicIp);
            if (cityLength < 0) {
                locateAttempts = locateAttempts + 1;
                WeatherScreen.showStatus("Location retry ");
                TftTouchShield.print(locateAttempts);
                Delay.millis(LOCATION_RETRY_MILLIS);
            }
        }
        int pathLength = buildWeatherPath(path, location);
        WeatherScreen.drawCity(city, cityLength);
        WeatherScreen.drawNetworkInfo(publicIp, location);

        int lastFetchMillis = 0;
        boolean haveWeather = false;
        int shownMinute = -1;
        int shownDay = -1;
        boolean colonOn = false;
        while (true) {
            int now = Clock.millis();
            boolean due = !haveWeather || now - lastFetchMillis >= REFRESH_MILLIS;
            if (!haveWeather && lastFetchMillis != 0 && now - lastFetchMillis < RETRY_MILLIS) {
                due = false;
            }
            if (TftTouchShield.readTouch()) {
                due = true;
            }
            if (due) {
                WeatherScreen.showStatus("Updating...       ");
                if (fetchWeather(path, pathLength, response, headers, statusAndHeaders)) {
                    haveWeather = true;
                    WeatherScreen.drawWeather(temperature, humidity, windSpeed, condition, isDay);
                    WeatherScreen.showStatus("                  ");
                } else {
                    WeatherScreen.showStatus("Weather error     ");
                }
                lastFetchMillis = Clock.millis();
                if (lastFetchMillis == 0) {
                    lastFetchMillis = 1;
                }
            }

            if (clockSet) {
                int localSeconds = clockBaseSeconds + (Clock.millis() - clockBaseMillis) / 1000;
                int days = CivilCalendar.floorDiv(localSeconds, CivilCalendar.SECONDS_PER_DAY);
                int secondOfDay = localSeconds - days * CivilCalendar.SECONDS_PER_DAY;
                int minuteOfDay = secondOfDay / 60;
                if (minuteOfDay != shownMinute) {
                    SevenSegmentClock.drawClock(minuteOfDay / 60, minuteOfDay % 60);
                    shownMinute = minuteOfDay;
                }
                if (days != shownDay) {
                    WeatherScreen.drawDate(days);
                    shownDay = days;
                }
                boolean colon = secondOfDay % 2 == 0;
                if (colon != colonOn) {
                    SevenSegmentClock.drawColon(colon);
                    colonOn = colon;
                }
            }
            Delay.millis(100);
        }
    }

    // ---- Network ----

    private static boolean connectWifi() throws IOException {
        if (!SdCard.begin()) {
            Serial.println("SD card initialization failed");
            return false;
        }
        InputStream file = SdCard.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return false;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();
        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return false;
        }

        Wifi.begin(ssid, password);
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return Wifi.status() == Wifi.STATUS_CONNECTED;
    }

    /**
     * Geolocates the board's public IP, writing the city name (transliterated to ASCII) into
     * {@code city}, the NUL-terminated {@code "lat,lon"} text into {@code location}, and the
     * NUL-terminated public IP address ipinfo.io saw into {@code publicIp}. Returns the city's
     * length, or -1 on failure.
     */
    private static int locate(byte[] response, byte[] headers, int[] statusAndHeaders,
            byte[] city, byte[] location, byte[] publicIp) {
        int bodyLength = HttpsClient.get("ipinfo.io", HTTPS_PORT, "/json", response, 1024,
                headers, 512, statusAndHeaders);
        if (statusAndHeaders[0] != HTTP_STATUS_OK || bodyLength <= 0) {
            Serial.print("ipinfo.io request failed, status ");
            Serial.print(statusAndHeaders[0]);
            Serial.print(", body bytes ");
            Serial.println(bodyLength);
            return -1;
        }
        int locationLength = Json.getString(response, bodyLength, "loc", location, 31);
        if (Json.type(response, bodyLength, "loc") != Json.TYPE_STRING || locationLength <= 0) {
            Serial.println("ipinfo.io response has no location");
            return -1;
        }
        location[locationLength] = 0;
        int ipLength = Json.getString(response, bodyLength, "ip", publicIp, 47);
        if (ipLength < 0) {
            ipLength = 0;
        }
        publicIp[ipLength] = 0;
        Serial.print("Public IP: ");
        Serial.println(Json.getString(response, bodyLength, "ip"));
        Serial.print("Location: ");
        Serial.println(Json.getString(response, bodyLength, "loc"));
        int cityLength = Json.getString(response, bodyLength, "city", city, 32);
        if (cityLength < 0) {
            cityLength = 0;
        }
        return AsciiText.toAscii(city, cityLength);
    }

    /**
     * Writes the Open-Meteo request path for the NUL-terminated {@code "lat,lon"} in
     * {@code location} into {@code path}, returning its length.
     */
    private static int buildWeatherPath(byte[] path, byte[] location) {
        int length = append(path, 0, "/v1/forecast?latitude=");
        int i = 0;
        while (location[i] != 0 && location[i] != ',') {
            path[length] = location[i];
            length = length + 1;
            i = i + 1;
        }
        length = append(path, length, "&longitude=");
        if (location[i] == ',') {
            i = i + 1;
        }
        while (location[i] != 0) {
            path[length] = location[i];
            length = length + 1;
            i = i + 1;
        }
        length = append(path, length, "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,");
        return append(path, length, "weather_code,is_day&timezone=auto");
    }

    private static int append(byte[] buffer, int offset, String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            buffer[offset + i] = (byte) text.charAt(i);
        }
        return offset + length;
    }

    private static boolean fetchWeather(byte[] path, int pathLength, byte[] response, byte[] headers,
            int[] statusAndHeaders) {
        int bodyLength = HttpsClient.get("api.open-meteo.com", HTTPS_PORT, path, pathLength,
                response, 1024, headers, 512, statusAndHeaders);
        if (statusAndHeaders[0] != HTTP_STATUS_OK || bodyLength <= 0) {
            Serial.println("Open-Meteo request failed");
            return false;
        }
        if (Json.type(response, bodyLength, "current.temperature_2m") != Json.TYPE_NUMBER) {
            Serial.println("Open-Meteo response has no temperature");
            return false;
        }
        temperature = round(Json.getDouble(response, bodyLength, "current.temperature_2m"));
        humidity = Json.getInt(response, bodyLength, "current.relative_humidity_2m");
        windSpeed = round(Json.getDouble(response, bodyLength, "current.wind_speed_10m"));
        condition = WeatherConditions.of(Json.getInt(response, bodyLength, "current.weather_code"));
        isDay = Json.getInt(response, bodyLength, "current.is_day") == 1;

        int utcSeconds = HttpDate.parseDateHeader(headers, statusAndHeaders[1]);
        if (utcSeconds != 0) {
            clockBaseSeconds = utcSeconds + Json.getInt(response, bodyLength, "utc_offset_seconds");
            clockBaseMillis = Clock.millis();
            clockSet = true;
        }

        Serial.print("Temperature: ");
        Serial.print(temperature);
        Serial.print(" C, humidity: ");
        Serial.print(humidity);
        Serial.print(" %, wind: ");
        Serial.print(windSpeed);
        Serial.println(" km/h");
        return true;
    }

    private static int round(double value) {
        return (int) Math.floor(value + 0.5);
    }
}
