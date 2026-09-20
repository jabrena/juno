package io.github.jabrena.juno.api.io.net;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

@Board(ArduinoUnoR4WiFi.class)
public final class HttpMethods {
    private static final String HTTPBIN_HOST = "httpbin.org";
    private static final String HTTPBUN_HOST = "httpbun.com";
    private static final String ANYTHING_PATH = "/anything";

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
        Wifi.begin(
                System.getenv("JUNO_WIFI_SSID"),
                System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return;
        }

        byte[] response = new byte[512];
        byte[] method = new byte[6];

        int responseLength = HttpClient.get(
                HTTPBIN_HOST, 80, ANYTHING_PATH, response, response.length);
        int methodLength = readMethod(response, responseLength, method, method.length);
        Serial.print("GET: ");
        printResult(matches(method, methodLength, 3, 'G', 'E', 'T', 0, 0, 0));
        Delay.millis(500);

        responseLength = HttpClient.post(
                HTTPBIN_HOST, 80, ANYTHING_PATH, "{\"verb\":\"POST\"}",
                response, response.length);
        methodLength = readMethod(response, responseLength, method, method.length);
        Serial.print("POST: ");
        printResult(matches(method, methodLength, 4, 'P', 'O', 'S', 'T', 0, 0));
        Delay.millis(500);

        responseLength = HttpClient.delete(
                HTTPBIN_HOST, 80, ANYTHING_PATH, response, response.length);
        methodLength = readMethod(response, responseLength, method, method.length);
        Serial.print("DELETE: ");
        printResult(matches(method, methodLength, 6, 'D', 'E', 'L', 'E', 'T', 'E'));
        Delay.millis(500);

        responseLength = HttpClient.patch(
                HTTPBIN_HOST, 80, ANYTHING_PATH, "{\"verb\":\"PATCH\"}",
                response, response.length);
        methodLength = readMethod(response, responseLength, method, method.length);
        Serial.print("PATCH: ");
        printResult(matches(method, methodLength, 5, 'P', 'A', 'T', 'C', 'H', 0));
        Delay.millis(500);

        responseLength = HttpClient.query(
                HTTPBUN_HOST, 80, ANYTHING_PATH, "{\"verb\":\"QUERY\"}",
                response, response.length);
        methodLength = readMethod(response, responseLength, method, method.length);
        Serial.print("QUERY: ");
        printResult(matches(method, methodLength, 5, 'Q', 'U', 'E', 'R', 'Y', 0));
    }
}
