package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoUnoR4WiFi.class)
public final class HttpsMethods {
    private static final Wifi WIFI = MCU.getWifi();

    private static final String HTTPBIN_URL = "https://httpbin.org/anything";
    private static final String HTTPBUN_URL = "https://httpbun.com/anything";

    private static int readMethod(byte[] response, int responseLength, byte[] method, int methodLength) {
        if (responseLength <= 0) {
            return 0;
        }
        return Json.getString(response, responseLength, "method", method, methodLength);
    }

    private static boolean matches(byte[] value, int length, int expectedLength,
            int c0, int c1, int c2, int c3, int c4, int c5) {
        if (length != expectedLength) {
            return false;
        }
        if (length > 0 && value[0] != c0) {
            return false;
        }
        if (length > 1 && value[1] != c1) {
            return false;
        }
        if (length > 2 && value[2] != c2) {
            return false;
        }
        if (length > 3 && value[3] != c3) {
            return false;
        }
        if (length > 4 && value[4] != c4) {
            return false;
        }
        return length <= 5 || value[5] == c5;
    }

    private static void printResult(boolean successful) {
        if (successful) {
            Serial.println("OK");
        } else {
            Serial.println("FAILED");
        }
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        WIFI.begin(
                System.getenv("JUNO_WIFI_SSID"),
                System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return;
        }

        byte[] response = new byte[HttpClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        byte[] headers = new byte[HttpClient.DEFAULT_RESPONSE_BUFFER_SIZE];
        byte[] method = new byte[6];
        int[] statusAndHeadersLength = new int[2];

        int bodyLength = HttpClient.get(HTTPBIN_URL,
                response, response.length, headers, headers.length, statusAndHeadersLength);
        int methodLength = readMethod(response, bodyLength, method, method.length);
        Serial.print("HTTPS GET: ");
        printResult(matches(method, methodLength, 3, 'G', 'E', 'T', 0, 0, 0));
        Delay.millis(500);

        bodyLength = HttpClient.post(HTTPBIN_URL, "{\"verb\":\"POST\"}",
                response, response.length, headers, headers.length, statusAndHeadersLength);
        methodLength = readMethod(response, bodyLength, method, method.length);
        Serial.print("HTTPS POST: ");
        printResult(matches(method, methodLength, 4, 'P', 'O', 'S', 'T', 0, 0));
        Delay.millis(500);

        bodyLength = HttpClient.delete(HTTPBIN_URL,
                response, response.length, headers, headers.length, statusAndHeadersLength);
        methodLength = readMethod(response, bodyLength, method, method.length);
        Serial.print("HTTPS DELETE: ");
        printResult(matches(method, methodLength, 6, 'D', 'E', 'L', 'E', 'T', 'E'));
        Delay.millis(500);

        bodyLength = HttpClient.patch(HTTPBIN_URL, "{\"verb\":\"PATCH\"}",
                response, response.length, headers, headers.length, statusAndHeadersLength);
        methodLength = readMethod(response, bodyLength, method, method.length);
        Serial.print("HTTPS PATCH: ");
        printResult(matches(method, methodLength, 5, 'P', 'A', 'T', 'C', 'H', 0));
        Delay.millis(500);

        bodyLength = HttpClient.query(HTTPBUN_URL, "{\"verb\":\"QUERY\"}",
                response, response.length, headers, headers.length, statusAndHeadersLength);
        methodLength = readMethod(response, bodyLength, method, method.length);
        Serial.print("HTTPS QUERY: ");
        printResult(matches(method, methodLength, 5, 'Q', 'U', 'E', 'R', 'Y', 0));
    }
}
