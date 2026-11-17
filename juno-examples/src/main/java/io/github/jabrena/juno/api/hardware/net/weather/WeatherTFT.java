package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonValue;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

/**
 * A desk weather station for the ELEGOO 2.8" TFT touch screen shield, in the style of the small
 * "weather clock" cubes: city, wind speed, a large seven-segment clock, the current condition with
 * an icon, the temperature, the date and weekday, and temperature/humidity bars.
 *
 * <p>WiFi credentials come from {@code application.properties} on the shield's microSD card (see
 * {@link io.github.jabrena.juno.api.hardware.net.wifi.WifiStatusSDTFT}). Once connected, the board's public IP
 * is geolocated with <a href="https://ipinfo.io">ipinfo.io</a> ({@code city} and {@code loc}), and
 * those coordinates are passed to <a href="https://open-meteo.com">Open-Meteo</a> for the current
 * weather and the location's UTC offset, with the JDK's {@code java.net.http} client. The weather URI depends on
 * the coordinates, so it is built at runtime.
 *
 * <p>The clock is set from the HTTPS response's {@code Date} header plus Open-Meteo's
 * {@code utc_offset_seconds}, then kept running with {@link Clock#millis()}. Weather refreshes
 * every ten minutes, or immediately when the screen is tapped.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class WeatherTFT {
    private static final Wifi WIFI = MCU.getWifi();

    private final Clock clock = MCU.getClock();
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest locationRequest = HttpRequest.newBuilder(URI.create("https://ipinfo.io/json")).build();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int REFRESH_MILLIS = 10 * 60 * 1000;
    private static final int RETRY_MILLIS = 60 * 1000;
    // The first HTTPS request right after WiFi connects often fails (DHCP/DNS still settling), so
    // wait briefly first and retry the location lookup quickly.
    private static final int NETWORK_SETTLE_MILLIS = 2000;
    private static final int LOCATION_RETRY_MILLIS = 5000;
    private static final int HTTP_STATUS_OK = 200;

    // Latest weather.
    private int temperature;
    private int humidity;
    private int windSpeed;
    private int condition;
    private boolean isDay;

    // Local time: seconds since the Unix epoch at the location, as of clockBaseMillis.
    private int clockBaseSeconds;
    private int clockBaseMillis;
    private boolean clockSet;

    private WeatherTFT() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        TftTouchShield tft = new TftTouchShield();
        new WeatherTFT().run(tft);
    }

    private void run(TftTouchShield tft) throws IOException, InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        tft.setRotation(TftTouchShield.LANDSCAPE);
        tft.fillScreen(TftTouchShield.BLACK);

        byte[] city = new byte[32];
        byte[] location = new byte[32];
        byte[] publicIp = new byte[48];

        WeatherScreen.showStatus(tft, "Connecting WiFi...");
        while (!connectWifi(tft.getSdCard())) {
            WeatherScreen.showStatus(tft, "WiFi error, retry ");
            Delay.millis(5000);
        }

        WeatherScreen.showStatus(tft, "Locating IP...    ");
        Delay.millis(NETWORK_SETTLE_MILLIS);
        int cityLength = -1;
        int locateAttempts = 0;
        while (cityLength < 0) {
            cityLength = locate(city, location, publicIp);
            if (cityLength < 0) {
                locateAttempts = locateAttempts + 1;
                WeatherScreen.showStatus(tft, "Location retry ");
                tft.print(locateAttempts);
                Delay.millis(LOCATION_RETRY_MILLIS);
            }
        }
        HttpRequest weatherRequest = HttpRequest.newBuilder(URI.create(weatherUri(location))).build();
        WeatherScreen.drawCity(tft, city, cityLength);
        WeatherScreen.drawNetworkInfo(tft, publicIp, location);

        int lastFetchMillis = 0;
        boolean haveWeather = false;
        int shownMinute = -1;
        int shownDay = -1;
        boolean colonOn = false;
        while (true) {
            int now = clock.millis();
            boolean due = !haveWeather || now - lastFetchMillis >= REFRESH_MILLIS;
            if (!haveWeather && lastFetchMillis != 0 && now - lastFetchMillis < RETRY_MILLIS) {
                due = false;
            }
            if (tft.readTouch()) {
                due = true;
            }
            if (due) {
                WeatherScreen.showStatus(tft, "Updating...       ");
                if (fetchWeather(weatherRequest)) {
                    haveWeather = true;
                    WeatherScreen.drawWeather(tft, temperature, humidity, windSpeed, condition, isDay);
                    WeatherScreen.showStatus(tft, "                  ");
                } else {
                    WeatherScreen.showStatus(tft, "Weather error     ");
                }
                lastFetchMillis = clock.millis();
                if (lastFetchMillis == 0) {
                    lastFetchMillis = 1;
                }
            }

            if (clockSet) {
                int localSeconds = clockBaseSeconds + (clock.millis() - clockBaseMillis) / 1000;
                int days = CivilCalendar.floorDiv(localSeconds, CivilCalendar.SECONDS_PER_DAY);
                int secondOfDay = localSeconds - days * CivilCalendar.SECONDS_PER_DAY;
                int minuteOfDay = secondOfDay / 60;
                if (minuteOfDay != shownMinute) {
                    SevenSegmentClock.drawClock(tft, minuteOfDay / 60, minuteOfDay % 60);
                    shownMinute = minuteOfDay;
                }
                if (days != shownDay) {
                    WeatherScreen.drawDate(tft, days);
                    shownDay = days;
                }
                boolean colon = secondOfDay % 2 == 0;
                if (colon != colonOn) {
                    SevenSegmentClock.drawColon(tft, colon);
                    colonOn = colon;
                }
            }
            Delay.millis(100);
        }
    }

    // ---- Network ----

    private boolean connectWifi(SdCard sdCard) throws IOException {
        try (InputStream file = sdCard.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return false;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return false;
            }

            WIFI.begin(ssid, password);
            int attempts = 0;
            while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
                Delay.millis(1000);
                attempts = attempts + 1;
            }
            return WIFI.status() == Wifi.STATUS_CONNECTED;
        }
    }

    /**
     * Geolocates the board's public IP, writing the city name (transliterated to ASCII) into
     * {@code city}, the NUL-terminated {@code "lat,lon"} text into {@code location}, and the
     * NUL-terminated public IP address ipinfo.io saw into {@code publicIp}. Returns the city's
     * length, or -1 on failure.
     */
    private int locate(byte[] city, byte[] location, byte[] publicIp) throws InterruptedException {
        HttpResponse<String> reply;
        try {
            reply = client.send(locationRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            Serial.println("ipinfo.io request failed");
            return -1;
        }
        String body = reply.body();
        if (reply.statusCode() != HTTP_STATUS_OK || body.length() == 0) {
            Serial.print("ipinfo.io request failed, status ");
            Serial.print(reply.statusCode());
            Serial.print(", body bytes ");
            Serial.println(body.length());
            return -1;
        }
        JsonValue ipInfo = Json.parse(body);
        int locationLength = ipInfo.get("loc").copyString(location, 31);
        if (!ipInfo.get("loc").isString() || locationLength <= 0) {
            Serial.println("ipinfo.io response has no location");
            return -1;
        }
        location[locationLength] = 0;
        int ipLength = ipInfo.get("ip").copyString(publicIp, 47);
        if (ipLength < 0) {
            ipLength = 0;
        }
        publicIp[ipLength] = 0;
        Serial.print("Public IP: ");
        Serial.println(ipInfo.get("ip").asString());
        Serial.print("Location: ");
        Serial.println(ipInfo.get("loc").asString());
        int cityLength = ipInfo.get("city").copyString(city, 32);
        if (cityLength < 0) {
            cityLength = 0;
        }
        return AsciiText.toAscii(city, cityLength);
    }

    /** The Open-Meteo URI for the NUL-terminated {@code "lat,lon"} in {@code location}. */
    private String weatherUri(byte[] location) {
        StringBuilder uri = new StringBuilder(192);
        uri.append("https://api.open-meteo.com/v1/forecast?latitude=");
        int i = 0;
        while (location[i] != 0 && location[i] != ',') {
            uri.append((char) location[i]);
            i = i + 1;
        }
        uri.append("&longitude=");
        if (location[i] == ',') {
            i = i + 1;
        }
        while (location[i] != 0) {
            uri.append((char) location[i]);
            i = i + 1;
        }
        uri.append("&current=temperature_2m,relative_humidity_2m,wind_speed_10m,");
        uri.append("weather_code,is_day&timezone=auto");
        return uri.toString();
    }

    private boolean fetchWeather(HttpRequest request) throws InterruptedException {
        HttpResponse<String> reply;
        try {
            reply = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            Serial.println("Open-Meteo request failed");
            return false;
        }
        String body = reply.body();
        if (reply.statusCode() != HTTP_STATUS_OK || body.length() == 0) {
            Serial.println("Open-Meteo request failed");
            return false;
        }
        JsonValue forecast = Json.parse(body);
        JsonValue current = forecast.get("current");
        if (!current.get("temperature_2m").isNumber()) {
            Serial.println("Open-Meteo response has no temperature");
            return false;
        }
        temperature = round(current.get("temperature_2m").asDouble());
        humidity = current.get("relative_humidity_2m").asInt();
        windSpeed = round(current.get("wind_speed_10m").asDouble());
        condition = WeatherConditions.of(current.get("weather_code").asInt());
        isDay = current.get("is_day").asInt() == 1;

        int utcSeconds = HttpDate.parseDate(reply.headers().firstValue("date").orElse(""));
        if (utcSeconds != 0) {
            clockBaseSeconds = utcSeconds + forecast.get("utc_offset_seconds").asInt();
            clockBaseMillis = clock.millis();
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

    private int round(double value) {
        return (int) Math.floor(value + 0.5);
    }
}
