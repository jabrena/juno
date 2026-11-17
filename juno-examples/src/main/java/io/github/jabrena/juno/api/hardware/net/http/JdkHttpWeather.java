package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Madrid's current temperature from Open-Meteo, over plain HTTP, with the JDK's {@code java.net.http} client. On the
 * board it runs Juno's board-side implementation of that API (package {@code io.github.jabrena.juno.jdk.net.http}),
 * and only the plain-HTTP request is linked, since the one URI is {@code http}. Credentials come from
 * {@code ExampleSecrets}. The client, request and response are created once, before the loop, so each request
 * allocates nothing. Prints the HTTP status, the body length and the temperature every ten seconds, at 115200 baud.
 * On the board {@code body()} is a fixed-capacity buffer, so the length printed is that capacity; the temperature is
 * read straight from the bytes, since {@code Json} is more code than this one value needs. The UNO R4 WiFi is the
 * smallest board that runs {@code java.net.http}: Juno rejects it on the UNO WiFi Rev2.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class
})
public final class JdkHttpWeather {
    private static final Wifi WIFI = MCU.getWifi();

    private static final String API_URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final byte[] TEMPERATURE_KEY = {'"', 't', 'e', 'm', 'p', 'e', 'r', 'a', 't', 'u', 'r', 'e', '_', '2', 'm',
        '"', ':'};
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).GET().build();
        while (true) {
            try {
                HttpResponse<byte[]> reply = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = reply.body();
                Serial.print("status=");
                Serial.print(reply.statusCode());
                Serial.print(" body=");
                Serial.println(body.length);
                printTemperature(body, body.length);
            } catch (IOException e) {
                Serial.println("request failed");
            }
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
