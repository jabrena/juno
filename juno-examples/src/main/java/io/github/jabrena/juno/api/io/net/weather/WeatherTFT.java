package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.net.http.HttpsClient;
import io.github.jabrena.juno.api.io.net.http.Json;
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
 * {@link io.github.jabrena.juno.api.io.net.WifiStatusTFT}). Once connected, the board's public IP
 * is geolocated with <a href="https://ipinfo.io">ipinfo.io</a> ({@code city} and {@code loc}), and
 * those coordinates are passed to <a href="https://open-meteo.com">Open-Meteo</a> for the current
 * weather and the location's UTC offset. The request path depends on runtime data, so it is built
 * in a {@code byte[]} and sent with {@link HttpsClient#get(String, int, byte[], int, byte[], int,
 * byte[], int, int[])}.
 *
 * <p>The clock is set from the HTTPS response's {@code Date} header plus Open-Meteo's
 * {@code utc_offset_seconds}, then kept running with {@link Clock#millis()}. Weather refreshes
 * every ten minutes, or immediately when the screen is tapped.
 */
@Board(ArduinoUnoR4WiFi.class)
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
    private static final int SECONDS_PER_DAY = 86400;

    private static final int CITY_GREEN = 0x9FEB;
    private static final int CLOCK_HOURS = 0xFE60;
    private static final int CLOCK_MINUTES = 0xF904;
    private static final int TEMPERATURE_BLUE = 0x3D7F;
    private static final int BAR_BACKGROUND = 0x39E7;
    private static final int CLOUD_GRAY = 0xBDF7;

    // Seven-segment clock geometry.
    private static final int DIGIT_WIDTH = 36;
    private static final int DIGIT_HEIGHT = 72;
    private static final int SEGMENT = 8;
    private static final int CLOCK_Y = 66;
    private static final int COLON_X = 94;

    // Layout.
    private static final int MARGIN = 8;
    private static final int CITY_Y = 8;
    private static final int WIND_Y = 34;
    private static final int ICON_X = 280;
    private static final int ICON_Y = 32;
    private static final int CONDITION_X = 204;
    private static final int CONDITION_Y = 76;
    private static final int TEMPERATURE_Y = 104;
    private static final int DATE_Y = 156;
    private static final int TEMPERATURE_BAR_Y = 184;
    private static final int HUMIDITY_BAR_Y = 212;
    private static final int BAR_X = 34;
    private static final int BAR_WIDTH = 150;
    private static final int BAR_HEIGHT = 10;
    private static final int STATUS_Y = 148;
    private static final int FOOTER_Y = 230;

    // Weather conditions, grouped from WMO weather codes.
    private static final int CLEAR = 0;
    private static final int PARTLY_CLOUDY = 1;
    private static final int CLOUDY = 2;
    private static final int FOG = 3;
    private static final int DRIZZLE = 4;
    private static final int RAIN = 5;
    private static final int SNOW = 6;
    private static final int STORM = 7;

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

    // Scratch results of civilFromDays.
    private static int civilYear;
    private static int civilMonth;
    private static int civilDay;

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

        showStatus("Connecting WiFi...");
        while (!connectWifi()) {
            showStatus("WiFi error, retry ");
            Delay.millis(5000);
        }

        showStatus("Locating IP...    ");
        Delay.millis(NETWORK_SETTLE_MILLIS);
        int cityLength = -1;
        int locateAttempts = 0;
        while (cityLength < 0) {
            cityLength = locate(response, headers, statusAndHeaders, city, location, publicIp);
            if (cityLength < 0) {
                locateAttempts = locateAttempts + 1;
                showStatus("Location retry ");
                TftTouchShield.print(locateAttempts);
                Delay.millis(LOCATION_RETRY_MILLIS);
            }
        }
        int pathLength = buildWeatherPath(path, location);
        drawCity(city, cityLength);
        drawNetworkInfo(publicIp, location);

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
                showStatus("Updating...       ");
                if (fetchWeather(path, pathLength, response, headers, statusAndHeaders)) {
                    haveWeather = true;
                    drawWeather();
                    showStatus("                  ");
                } else {
                    showStatus("Weather error     ");
                }
                lastFetchMillis = Clock.millis();
                if (lastFetchMillis == 0) {
                    lastFetchMillis = 1;
                }
            }

            if (clockSet) {
                int localSeconds = clockBaseSeconds + (Clock.millis() - clockBaseMillis) / 1000;
                int days = floorDiv(localSeconds, SECONDS_PER_DAY);
                int secondOfDay = localSeconds - days * SECONDS_PER_DAY;
                int minuteOfDay = secondOfDay / 60;
                if (minuteOfDay != shownMinute) {
                    drawClock(minuteOfDay / 60, minuteOfDay % 60);
                    shownMinute = minuteOfDay;
                }
                if (days != shownDay) {
                    drawDate(days);
                    shownDay = days;
                }
                boolean colon = secondOfDay % 2 == 0;
                if (colon != colonOn) {
                    drawColon(colon);
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
        return toAscii(city, cityLength);
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
        condition = conditionOf(Json.getInt(response, bodyLength, "current.weather_code"));
        isDay = Json.getInt(response, bodyLength, "current.is_day") == 1;

        int utcSeconds = parseDateHeader(headers, statusAndHeaders[1]);
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

    private static int conditionOf(int weatherCode) {
        if (weatherCode == 0) {
            return CLEAR;
        }
        if (weatherCode <= 2) {
            return PARTLY_CLOUDY;
        }
        if (weatherCode == 3) {
            return CLOUDY;
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return FOG;
        }
        if (weatherCode >= 51 && weatherCode <= 57) {
            return DRIZZLE;
        }
        if ((weatherCode >= 61 && weatherCode <= 67) || (weatherCode >= 80 && weatherCode <= 82)) {
            return RAIN;
        }
        if ((weatherCode >= 71 && weatherCode <= 77) || weatherCode == 85 || weatherCode == 86) {
            return SNOW;
        }
        if (weatherCode >= 95) {
            return STORM;
        }
        return CLOUDY;
    }

    // ---- Time ----

    /**
     * Finds the {@code Date: Sun, 27 Sep 2026 17:06:14 GMT} response header and returns it as
     * seconds since the Unix epoch, or 0 when it is missing or malformed.
     */
    private static int parseDateHeader(byte[] headers, int length) {
        int start = -1;
        for (int i = 0; i + 5 < length && start < 0; i++) {
            if ((i == 0 || headers[i - 1] == '\n')
                    && lower(headers[i]) == 'd' && lower(headers[i + 1]) == 'a'
                    && lower(headers[i + 2]) == 't' && lower(headers[i + 3]) == 'e'
                    && headers[i + 4] == ':') {
                start = i + 5;
            }
        }
        if (start < 0) {
            return 0;
        }
        // Skip the space and "Sun, " to reach "27 Sep 2026 17:06:14".
        int p = start;
        while (p < length && headers[p] != ',') {
            p = p + 1;
        }
        p = p + 2;
        if (p + 20 > length) {
            return 0;
        }
        int day = number(headers, p, 2);
        int month = monthOf(lower(headers[p + 3]), lower(headers[p + 4]), lower(headers[p + 5]));
        int year = number(headers, p + 7, 4);
        int hour = number(headers, p + 12, 2);
        int minute = number(headers, p + 15, 2);
        int second = number(headers, p + 18, 2);
        if (day < 1 || month < 1 || year < 2020 || hour < 0 || minute < 0 || second < 0) {
            return 0;
        }
        return daysFromCivil(year, month, day) * SECONDS_PER_DAY + hour * 3600 + minute * 60 + second;
    }

    private static int lower(int character) {
        if (character >= 'A' && character <= 'Z') {
            return character + 32;
        }
        return character;
    }

    private static int number(byte[] text, int offset, int digits) {
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = text[offset + i] - '0';
            if (digit < 0 || digit > 9) {
                return -1;
            }
            value = value * 10 + digit;
        }
        return value;
    }

    private static int monthOf(int a, int b, int c) {
        if (a == 'j' && b == 'a') {
            return 1;
        }
        if (a == 'f') {
            return 2;
        }
        if (a == 'm' && c == 'r') {
            return 3;
        }
        if (a == 'a' && b == 'p') {
            return 4;
        }
        if (a == 'm') {
            return 5;
        }
        if (a == 'j' && c == 'n') {
            return 6;
        }
        if (a == 'j') {
            return 7;
        }
        if (a == 'a') {
            return 8;
        }
        if (a == 's') {
            return 9;
        }
        if (a == 'o') {
            return 10;
        }
        if (a == 'n') {
            return 11;
        }
        if (a == 'd') {
            return 12;
        }
        return 0;
    }

    // Howard Hinnant's days_from_civil: days since 1970-01-01 for a proleptic Gregorian date.
    private static int daysFromCivil(int year, int month, int day) {
        int y = year;
        if (month <= 2) {
            y = y - 1;
        }
        int era = floorDiv(y, 400);
        int yearOfEra = y - era * 400;
        int shiftedMonth = month + 9;
        if (month > 2) {
            shiftedMonth = month - 3;
        }
        int dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1;
        int dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146097 + dayOfEra - 719468;
    }

    // The inverse of daysFromCivil, into civilYear/civilMonth/civilDay.
    private static void civilFromDays(int days) {
        int z = days + 719468;
        int era = floorDiv(z, 146097);
        int dayOfEra = z - era * 146097;
        int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        int shiftedMonth = (5 * dayOfYear + 2) / 153;
        civilDay = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1;
        civilMonth = shiftedMonth + 3;
        if (shiftedMonth >= 10) {
            civilMonth = shiftedMonth - 9;
        }
        civilYear = yearOfEra + era * 400;
        if (civilMonth <= 2) {
            civilYear = civilYear + 1;
        }
    }

    private static int floorDiv(int value, int divisor) {
        int quotient = value / divisor;
        if (value % divisor != 0 && value < 0) {
            quotient = quotient - 1;
        }
        return quotient;
    }

    // ---- Text ----

    /** Transliterates UTF-8 Latin-1 letters (e.g. "ó") to ASCII in place, returning the new length. */
    private static int toAscii(byte[] text, int length) {
        int out = 0;
        int i = 0;
        while (i < length) {
            int value = text[i] & 0xFF;
            if (value == 0xC3 && i + 1 < length) {
                text[out] = (byte) latin1ToAscii(text[i + 1] & 0xFF);
                i = i + 2;
            } else if (value >= 0x80) {
                text[out] = '?';
                i = i + 1;
                while (i < length && (text[i] & 0xC0) == 0x80) {
                    i = i + 1;
                }
            } else {
                text[out] = (byte) value;
                i = i + 1;
            }
            out = out + 1;
        }
        return out;
    }

    // Second byte of a UTF-8 "À".."ÿ" letter (0xC3 0x80..0xBF) to its unaccented ASCII letter.
    private static int latin1ToAscii(int value) {
        int letter = value & 0x1F;
        int base = 'A';
        if (value >= 0xA0) {
            base = 'a';
        }
        if (letter <= 0x05) {
            return base;
        }
        if (letter == 0x07) {
            return base + ('c' - 'a');
        }
        if (letter >= 0x08 && letter <= 0x0B) {
            return base + ('e' - 'a');
        }
        if (letter >= 0x0C && letter <= 0x0F) {
            return base + ('i' - 'a');
        }
        if (letter == 0x11) {
            return base + ('n' - 'a');
        }
        if (letter >= 0x12 && letter <= 0x16 || letter == 0x18) {
            return base + ('o' - 'a');
        }
        if (letter >= 0x19 && letter <= 0x1C) {
            return base + ('u' - 'a');
        }
        if (letter == 0x1D || letter == 0x1F) {
            return base + ('y' - 'a');
        }
        return '?';
    }

    // ---- Drawing ----

    private static void showStatus(String text) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CONDITION_X, STATUS_Y);
        TftTouchShield.print(text);
    }

    // Small gray footer: the public IP ipinfo.io saw and the coordinates sent to Open-Meteo.
    private static void drawNetworkInfo(byte[] publicIp, byte[] location) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, FOOTER_Y);
        TftTouchShield.print("IP ");
        TftTouchShield.print(publicIp, length(publicIp));
        TftTouchShield.print("  ");
        TftTouchShield.print(location, length(location));
    }

    private static int length(byte[] text) {
        int length = 0;
        while (text[length] != 0) {
            length = length + 1;
        }
        return length;
    }

    private static void drawCity(byte[] city, int length) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, CITY_Y);
        TftTouchShield.print(city, Math.min(length, 20));
    }

    private static void drawWeather() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, WIND_Y);
        TftTouchShield.print("Wind ");
        TftTouchShield.print(windSpeed);
        TftTouchShield.print(" km/h   ");

        drawIcon();

        TftTouchShield.fillRect(CONDITION_X, CONDITION_Y - 4, 108, 22, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        TftTouchShield.setCursor(CONDITION_X + 6, CONDITION_Y);
        TftTouchShield.print(conditionLabel());

        TftTouchShield.setTextSize(5);
        TftTouchShield.setTextColor(TEMPERATURE_BLUE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CONDITION_X, TEMPERATURE_Y);
        TftTouchShield.print(temperature);
        TftTouchShield.print(" ");
        int degreeX = TftTouchShield.width() - 20;
        TftTouchShield.drawCircle(degreeX, TEMPERATURE_Y + 5, 4, TEMPERATURE_BLUE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(degreeX + 6, TEMPERATURE_Y);
        TftTouchShield.print("C");

        drawThermometer(MARGIN + 8, TEMPERATURE_BAR_Y + 4);
        drawBar(TEMPERATURE_BAR_Y, (temperature + 10) * BAR_WIDTH / 50, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(BAR_X + BAR_WIDTH + 10, TEMPERATURE_BAR_Y - 2);
        TftTouchShield.print(temperature);
        TftTouchShield.print("C  ");

        drawDrop(MARGIN + 8, HUMIDITY_BAR_Y + 4);
        drawBar(HUMIDITY_BAR_Y, humidity * BAR_WIDTH / 100, TftTouchShield.CYAN);
        TftTouchShield.setCursor(BAR_X + BAR_WIDTH + 10, HUMIDITY_BAR_Y - 2);
        TftTouchShield.print(humidity);
        TftTouchShield.print("%  ");
    }

    private static String conditionLabel() {
        if (condition == CLEAR) {
            return "Clear   ";
        }
        if (condition == PARTLY_CLOUDY) {
            return "Partly  ";
        }
        if (condition == CLOUDY) {
            return "Cloudy  ";
        }
        if (condition == FOG) {
            return "Fog     ";
        }
        if (condition == DRIZZLE) {
            return "Drizzle ";
        }
        if (condition == RAIN) {
            return "Rain    ";
        }
        if (condition == SNOW) {
            return "Snow    ";
        }
        return "Storm   ";
    }

    private static void drawBar(int y, int filled, int color) {
        int width = Math.max(0, Math.min(filled, BAR_WIDTH));
        TftTouchShield.fillRect(BAR_X, y, width, BAR_HEIGHT, color);
        TftTouchShield.fillRect(BAR_X + width, y, BAR_WIDTH - width, BAR_HEIGHT, BAR_BACKGROUND);
    }

    private static void drawThermometer(int x, int y) {
        TftTouchShield.fillRect(x - 2, y - 12, 5, 12, TftTouchShield.RED);
        TftTouchShield.fillCircle(x, y + 2, 5, TftTouchShield.RED);
    }

    private static void drawDrop(int x, int y) {
        TftTouchShield.fillCircle(x, y + 2, 6, TftTouchShield.CYAN);
        TftTouchShield.fillRect(x - 1, y - 10, 3, 6, TftTouchShield.CYAN);
        TftTouchShield.fillRect(x - 3, y - 5, 7, 3, TftTouchShield.CYAN);
    }

    private static void drawIcon() {
        TftTouchShield.fillRect(ICON_X - 32, ICON_Y - 30, 64, 60, TftTouchShield.BLACK);
        if (condition == CLEAR) {
            drawSunOrMoon(ICON_X, ICON_Y, 16);
            return;
        }
        if (condition == PARTLY_CLOUDY) {
            drawSunOrMoon(ICON_X + 8, ICON_Y - 8, 12);
        }
        int cloudColor = CLOUD_GRAY;
        if (condition == STORM || condition == RAIN) {
            cloudColor = TftTouchShield.GRAY;
        }
        drawCloud(ICON_X, ICON_Y + 2, cloudColor);
        if (condition == FOG) {
            for (int i = 0; i < 3; i++) {
                TftTouchShield.fillRect(ICON_X - 24, ICON_Y + 16 + i * 5, 48, 2, CLOUD_GRAY);
            }
        } else if (condition == DRIZZLE || condition == RAIN) {
            for (int i = 0; i < 4; i++) {
                TftTouchShield.fillRect(ICON_X - 18 + i * 11, ICON_Y + 18, 3, 8, TEMPERATURE_BLUE);
            }
        } else if (condition == SNOW) {
            for (int i = 0; i < 4; i++) {
                TftTouchShield.fillCircle(ICON_X - 18 + i * 12, ICON_Y + 22, 2, TftTouchShield.WHITE);
            }
        } else if (condition == STORM) {
            TftTouchShield.fillRect(ICON_X, ICON_Y + 14, 4, 7, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(ICON_X - 4, ICON_Y + 20, 8, 3, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(ICON_X - 4, ICON_Y + 22, 4, 7, TftTouchShield.YELLOW);
        }
    }

    private static void drawSunOrMoon(int x, int y, int r) {
        if (isDay) {
            TftTouchShield.fillCircle(x, y, r, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - 1, y - r - 8, 3, 5, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - 1, y + r + 3, 3, 5, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - r - 8, y - 1, 5, 3, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x + r + 3, y - 1, 5, 3, TftTouchShield.YELLOW);
            return;
        }
        TftTouchShield.fillCircle(x, y, r, TftTouchShield.WHITE);
        TftTouchShield.fillCircle(x + r / 2, y - r / 3, r - 2, TftTouchShield.BLACK);
        drawStar(x + r, y + r / 2, TftTouchShield.WHITE);
        drawStar(x + r / 2 + 8, y + r + 2, TftTouchShield.WHITE);
    }

    private static void drawStar(int x, int y, int color) {
        TftTouchShield.drawHorizontalLine(x - 3, y, 7, color);
        TftTouchShield.drawVerticalLine(x, y - 3, 7, color);
    }

    private static void drawCloud(int x, int y, int color) {
        TftTouchShield.fillCircle(x - 12, y + 4, 9, color);
        TftTouchShield.fillCircle(x + 2, y - 3, 12, color);
        TftTouchShield.fillCircle(x + 15, y + 5, 8, color);
        TftTouchShield.fillRect(x - 12, y + 5, 27, 9, color);
    }

    private static void drawDate(int days) {
        civilFromDays(days);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, DATE_Y);
        TftTouchShield.print(civilYear);
        TftTouchShield.print("/");
        TftTouchShield.print(civilMonth);
        TftTouchShield.print("/");
        TftTouchShield.print(civilDay);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN + 144, DATE_Y);
        TftTouchShield.print(weekdayName(floorMod(days + 4, 7)));
    }

    private static int floorMod(int value, int divisor) {
        return value - floorDiv(value, divisor) * divisor;
    }

    private static String weekdayName(int weekday) {
        if (weekday == 0) {
            return "Sun";
        }
        if (weekday == 1) {
            return "Mon";
        }
        if (weekday == 2) {
            return "Tue";
        }
        if (weekday == 3) {
            return "Wed";
        }
        if (weekday == 4) {
            return "Thu";
        }
        if (weekday == 5) {
            return "Fri";
        }
        return "Sat";
    }

    private static void drawClock(int hour, int minute) {
        drawDigit(MARGIN, hour / 10, CLOCK_HOURS);
        drawDigit(MARGIN + DIGIT_WIDTH + 6, hour % 10, CLOCK_HOURS);
        drawDigit(COLON_X + 14, minute / 10, CLOCK_MINUTES);
        drawDigit(COLON_X + 14 + DIGIT_WIDTH + 6, minute % 10, CLOCK_MINUTES);
    }

    private static void drawColon(boolean on) {
        int color = TftTouchShield.BLACK;
        if (on) {
            color = TftTouchShield.WHITE;
        }
        TftTouchShield.fillRect(COLON_X, CLOCK_Y + 20, 6, 6, color);
        TftTouchShield.fillRect(COLON_X, CLOCK_Y + DIGIT_HEIGHT - 26, 6, 6, color);
    }

    // Segments a-g as bits 0-6: a top, b upper right, c lower right, d bottom, e lower left,
    // f upper left, g middle. Unlit segments are painted black, so no separate erase is needed.
    private static void drawDigit(int x, int digit, int color) {
        int segments = segmentsOf(digit);
        int half = DIGIT_HEIGHT / 2;
        int vertical = half - SEGMENT - SEGMENT / 2;
        int horizontal = DIGIT_WIDTH - 2 * SEGMENT;
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y, horizontal, SEGMENT, segmentColor(segments, 0, color));
        TftTouchShield.fillRect(x + DIGIT_WIDTH - SEGMENT, CLOCK_Y + SEGMENT, SEGMENT, vertical,
                segmentColor(segments, 1, color));
        TftTouchShield.fillRect(x + DIGIT_WIDTH - SEGMENT, CLOCK_Y + half + SEGMENT / 2, SEGMENT, vertical,
                segmentColor(segments, 2, color));
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y + DIGIT_HEIGHT - SEGMENT, horizontal, SEGMENT,
                segmentColor(segments, 3, color));
        TftTouchShield.fillRect(x, CLOCK_Y + half + SEGMENT / 2, SEGMENT, vertical, segmentColor(segments, 4, color));
        TftTouchShield.fillRect(x, CLOCK_Y + SEGMENT, SEGMENT, vertical, segmentColor(segments, 5, color));
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y + half - SEGMENT / 2, horizontal, SEGMENT,
                segmentColor(segments, 6, color));
    }

    private static int segmentColor(int segments, int segment, int color) {
        if (((segments >> segment) & 1) != 0) {
            return color;
        }
        return TftTouchShield.BLACK;
    }

    private static int segmentsOf(int digit) {
        if (digit == 0) {
            return 0x3F;
        }
        if (digit == 1) {
            return 0x06;
        }
        if (digit == 2) {
            return 0x5B;
        }
        if (digit == 3) {
            return 0x4F;
        }
        if (digit == 4) {
            return 0x66;
        }
        if (digit == 5) {
            return 0x6D;
        }
        if (digit == 6) {
            return 0x7D;
        }
        if (digit == 7) {
            return 0x07;
        }
        if (digit == 8) {
            return 0x7F;
        }
        return 0x6F;
    }
}
