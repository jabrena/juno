package io.github.jabrena.juno.api.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;

/**
 * Two independently owned HTTPS requests run under one structured task scope.
 * UNO WiFi Rev2 queues their transports because a blocking NINA TLS handshake can starve another response;
 * the structured subtasks remain supported, but this board does not provide wire-level HTTP overlap.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoQ.class})
public final class ParallelHttpsGet {
    private static final String FIRST_URL = "https://example.com/?request=first";
    private static final String SECOND_URL = "https://example.com/?request=second";

    private static final class FirstRequest {
        private final byte[] body = new byte[1];
        private final byte[] headers = new byte[256];
        private final int[] statusAndHeadersLength = new int[2];
        private int bodyLength;

        FirstRequest execute() {
            bodyLength = HttpClient.get(FIRST_URL, body, body.length, headers, headers.length, statusAndHeadersLength);
            return this;
        }

        void print() {
            Serial.print("first");
            Serial.print(" status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
        }
    }

    private static final class SecondRequest {
        private final byte[] body = new byte[1];
        private final byte[] headers = new byte[256];
        private final int[] statusAndHeadersLength = new int[2];
        private int bodyLength;

        SecondRequest execute() {
            bodyLength = HttpClient.get(SECOND_URL, body, body.length, headers, headers.length, statusAndHeadersLength);
            return this;
        }

        void print() {
            Serial.print("second");
            Serial.print(" status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
        }
    }

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        FirstRequest first = new FirstRequest();
        SecondRequest second = new SecondRequest();
        FirstRequest firstResult;
        SecondRequest secondResult;
        int startedAt = Clock.millis();
        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
            var firstTask = scope.fork(() -> first.execute());
            var secondTask = scope.fork(() -> second.execute());
            scope.join();
            firstResult = firstTask.get();
            secondResult = secondTask.get();
        }
        Serial.print("parallel elapsed_ms=");
        Serial.println(Clock.millis() - startedAt);
        firstResult.print();
        secondResult.print();
    }

    private static boolean connectWifi() {
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
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
