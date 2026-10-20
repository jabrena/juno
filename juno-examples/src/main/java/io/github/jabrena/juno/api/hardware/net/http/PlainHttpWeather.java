package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * The Open-Meteo request from {@code MadridWeather}'s {@code WeatherClient}, sent over plain HTTP so a board without
 * TLS, such as the UNO R3 with the retired Arduino WiFi Shield, can make it. Credentials come from
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}; the buffers are sized for this one response to fit 2 KB of RAM
 * (build with {@code -Djuno.Xmx=512} there). Prints the HTTP status, the body length and Madrid's current temperature
 * every ten seconds, at 115200 baud. The temperature is read straight from the bytes, since {@code Json} does not fit
 * the R3's 32 KB of flash next to the WiFi library.
 */
public final class PlainHttpWeather {
    private static final String API_URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final byte[] TEMPERATURE_KEY = {'"', 't', 'e', 'm', 'p', 'e', 'r', 'a', 't', 'u', 'r', 'e', '_', '2', 'm',
        '"', ':'};
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        byte[] response = new byte[360];
        byte[] headers = new byte[16];
        int[] statusAndHeadersLength = new int[2];
        while (true) {
            int bodyLength = HttpClient.get(API_URL, response, response.length, headers, headers.length,
                    statusAndHeadersLength);
            Serial.print("status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
            printTemperature(response, bodyLength);
            Delay.millis(10000);
        }
    }

    /**
     * Prints the number after the last {@code "temperature_2m":} in the body: the {@code current} object follows
     * {@code current_units}, whose entry for the same key is the unit text.
     */
    private static void printTemperature(byte[] body, int length) {
        int start = -1;
        for (int i = 0; i + TEMPERATURE_KEY.length <= length; i++) {
            if (matchesKey(body, i)) {
                start = i + TEMPERATURE_KEY.length;
            }
        }
        if (start < 0 || start >= length) {
            Serial.println("temperature not found");
            return;
        }
        Serial.print("temperature=");
        for (int i = start; i < length && isNumberByte(body[i]); i++) {
            if (body[i] == '-') {
                Serial.print("-");
            } else if (body[i] == '.') {
                Serial.print(".");
            } else {
                Serial.print(body[i] - '0');
            }
        }
        Serial.println(" C");
    }

    private static boolean matchesKey(byte[] body, int offset) {
        for (int k = 0; k < TEMPERATURE_KEY.length; k++) {
            if (body[offset + k] != TEMPERATURE_KEY[k]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumberByte(byte value) {
        return value == '-' || value == '.' || (value >= '0' && value <= '9');
    }

    private static boolean connectWifi() {
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connected");
            return true;
        }
        Serial.println("wifi connection failed");
        return false;
    }
}
