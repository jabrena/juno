package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * The Open-Meteo request from {@code MadridWeather}'s {@code WeatherClient}, sent over plain HTTP so a board without
 * TLS can make it. Credentials come from the ATECC608 {@code SecretStore}; the buffers are sized for this one response.
 * Prints the HTTP status, the body length and Madrid's current temperature every ten seconds, at 115200 baud. The
 * temperature is read straight from the bytes, since {@code Json} is more code than this one value needs.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoUnoR3WiFiRev2Board.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class
})
public final class PlainHttpWeather {
    private static final Wifi WIFI = MCU.getWifi();

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
        WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() == Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connected");
            return true;
        }
        Serial.println("wifi connection failed");
        return false;
    }
}
