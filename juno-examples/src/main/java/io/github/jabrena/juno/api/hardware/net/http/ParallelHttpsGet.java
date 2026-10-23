package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.time.Duration;
import java.util.concurrent.StructuredTaskScope;

/**
 * Three independently owned HTTPS request rounds demonstrate each supported structured-concurrency joiner.
 * {@code allSuccessfulOrThrow} waits for both requests, {@code anySuccessfulOrThrow} returns the first successful
 * response and cancels its sibling, and {@code awaitAllSuccessfulOrThrow} waits for its request under a 20-second deadline.
 * A transport or non-2xx HTTP response fails its subtask so every joiner has meaningful failure semantics.
 *
 * UNO WiFi Rev2 queues their transports because a blocking NINA TLS handshake can starve another response;
 * the structured subtasks remain supported, but this board does not provide wire-level HTTP overlap.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneR3WiFiRev2Board.class, ArduinoOneQBoard.class})
public final class ParallelHttpsGet {
    private static final Wifi WIFI = Arduino.getWifi();

    private final Clock clock = Arduino.getClock();
    private static final String FIRST_URL = "https://example.com/?request=first";
    private static final String SECOND_URL = "https://example.com/?request=second";

    private final FirstRequest first = new FirstRequest();
    private final SecondRequest second = new SecondRequest();

    private static final class FirstRequest {
        private final byte[] body = new byte[1];
        private final byte[] headers = new byte[256];
        private final int[] statusAndHeadersLength = new int[2];
        private int bodyLength;

        String execute() {
            bodyLength = HttpClient.get(FIRST_URL, body, body.length, headers, headers.length, statusAndHeadersLength);
            if (!succeeded()) {
                throw new IllegalStateException("first HTTPS request failed");
            }
            return "first";
        }

        void print() {
            Serial.print("first");
            Serial.print(" status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
        }

        private boolean succeeded() {
            return bodyLength >= 0 && statusAndHeadersLength[0] >= 200 && statusAndHeadersLength[0] < 300;
        }
    }

    private static final class SecondRequest {
        private final byte[] body = new byte[1];
        private final byte[] headers = new byte[256];
        private final int[] statusAndHeadersLength = new int[2];
        private int bodyLength;

        String execute() {
            bodyLength = HttpClient.get(SECOND_URL, body, body.length, headers, headers.length, statusAndHeadersLength);
            if (!succeeded()) {
                throw new IllegalStateException("second HTTPS request failed");
            }
            return "second";
        }

        void print() {
            Serial.print("second");
            Serial.print(" status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
        }

        private boolean succeeded() {
            return bodyLength >= 0 && statusAndHeadersLength[0] >= 200 && statusAndHeadersLength[0] < 300;
        }
    }

    public static void main(String[] args) throws Exception {
        new ParallelHttpsGet().run();
    }

    private void run() throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        ParallelHttpsGet example = new ParallelHttpsGet();
        example.allSuccessful();
        example.anySuccessful();
        example.awaitAllSuccessfulWithTimeout();
    }

    /** Both requests must succeed; a failing request cancels its still-running sibling. */
    private void allSuccessful() {
        int startedAt = clock.millis();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
            var firstTask = scope.fork(() -> first.execute());
            var secondTask = scope.fork(() -> second.execute());
            scope.join();
            Serial.print("all-successful complete=");
            Serial.print(firstTask.get());
            Serial.print(",");
            Serial.println(secondTask.get());
            printRound("all-successful", scope.isCancelled(), startedAt);
            first.print();
            second.print();
        } catch (Exception failure) {
            printFailure("all-successful", failure.getMessage());
        }
    }

    /** The first successful request wins and the other request is cancelled. */
    private void anySuccessful() {
        int startedAt = clock.millis();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>anySuccessfulOrThrow())) {
            scope.fork(() -> first.execute());
            scope.fork(() -> second.execute());
            String winner = scope.join();
            Serial.print("any-successful winner=");
            Serial.println(winner);
            printRound("any-successful", scope.isCancelled(), startedAt);
        } catch (Exception failure) {
            printFailure("any-successful", failure.getMessage());
        }
    }

    /** This request must succeed before the 20-second scope timeout expires. */
    private void awaitAllSuccessfulWithTimeout() {
        int startedAt = clock.millis();
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>awaitAllSuccessfulOrThrow(),
                configuration -> configuration.withTimeout(Duration.ofSeconds(20)))) {
            var firstTask = scope.fork(() -> first.execute());
            scope.join();
            Serial.print("await-all-successful complete=");
            Serial.println(firstTask.get());
            printRound("await-all-successful", scope.isCancelled(), startedAt);
            first.print();
        } catch (Exception failure) {
            printFailure("await-all-successful", failure.getMessage());
        }
    }

    private void printRound(String policy, boolean cancelled, int startedAt) {
        Serial.print(policy);
        Serial.print(" cancelled=");
        Serial.print(cancelled);
        Serial.print(" elapsed_ms=");
        Serial.println(clock.millis() - startedAt);
    }

    private void printFailure(String policy, String failureMessage) {
        Serial.print(policy);
        Serial.print(" failed: ");
        Serial.println(failureMessage);
    }

    private boolean connectWifi() {
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
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
