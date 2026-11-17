package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Three independently owned HTTPS request rounds demonstrate each supported structured-concurrency joiner.
 * {@code allSuccessfulOrThrow} waits for both requests, {@code anySuccessfulOrThrow} returns the first successful
 * response and cancels its sibling, and {@code awaitAllSuccessfulOrThrow} waits for its request under a 20-second deadline.
 * A transport or non-2xx HTTP response fails its subtask so every joiner has meaningful failure semantics. Each
 * request owns a JDK {@code java.net.http} client, so the two never share a response; only the status matters, so
 * the body is read with {@code ofByteArray()}, whose fixed buffer keeps the first bytes of a page of any size.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoNanoEsp32Board.class, AiThinkerEsp32CamBoard.class,
        M5StackCardputerBoard.class})
public final class ParallelHttpsGet {
    private static final Wifi WIFI = MCU.getWifi();

    private final Clock clock = MCU.getClock();
    // A host whose chain ends in a root every board trusts (ISRG Root X1). The Nano ESP32's core checks only the
    // issuer of the topmost certificate the server sends, so a chain cross-signed by a root outside the bundle fails.
    private static final String FIRST_URL = "https://letsencrypt.org/?request=first";
    private static final String SECOND_URL = "https://letsencrypt.org/?request=second";

    private final FirstRequest first = new FirstRequest();
    private final SecondRequest second = new SecondRequest();

    private static final class FirstRequest {
        private final HttpClient client = HttpClient.newHttpClient();
        private final HttpRequest request = HttpRequest.newBuilder(URI.create(FIRST_URL)).build();
        private int status;

        String execute() throws IOException, InterruptedException {
            status = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("first HTTPS request failed");
            }
            return "first";
        }

        void print() {
            Serial.print("first");
            Serial.print(" status=");
            Serial.println(status);
        }
    }

    private static final class SecondRequest {
        private final HttpClient client = HttpClient.newHttpClient();
        private final HttpRequest request = HttpRequest.newBuilder(URI.create(SECOND_URL)).build();
        private int status;

        String execute() throws IOException, InterruptedException {
            status = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("second HTTPS request failed");
            }
            return "second";
        }

        void print() {
            Serial.print("second");
            Serial.print(" status=");
            Serial.println(status);
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
    private void allSuccessful() throws InterruptedException {
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
        } catch (ExecutionException failure) {
            printFailure("all-successful", failure.getMessage());
        }
    }

    /** The first successful request wins and the other request is cancelled. */
    private void anySuccessful() throws InterruptedException {
        int startedAt = clock.millis();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>anySuccessfulOrThrow())) {
            scope.fork(() -> first.execute());
            scope.fork(() -> second.execute());
            String winner = scope.join();
            Serial.print("any-successful winner=");
            Serial.println(winner);
            printRound("any-successful", scope.isCancelled(), startedAt);
        } catch (ExecutionException failure) {
            printFailure("any-successful", failure.getMessage());
        }
    }

    /** This request must succeed before the 20-second scope timeout expires. */
    private void awaitAllSuccessfulWithTimeout() throws InterruptedException {
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
        } catch (ExecutionException failure) {
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
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
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
