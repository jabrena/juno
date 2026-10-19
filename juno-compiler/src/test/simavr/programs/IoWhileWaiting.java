package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.HttpClient;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * A hardware call that waits lets the other threads run. The harness's server on port 8080 answers 40 ms after the
 * connection opens: while the main thread waits for that response inside {@code HttpClient.get}, a virtual thread
 * keeps running and sees the clock move well past the request's start. Then a subtask is cancelled while its request
 * waits, and the network must be free again for the next request rather than stay locked by a thread that will never
 * run again. No JVM can run these APIs, so IoWhileWaiting.expected holds the output by hand.
 */
@Board(ArduinoUnoR3WiFiRev2.class)
public final class IoWhileWaiting {
    static final Clock CLOCK = MCU.getClock();
    static volatile boolean stop;
    static volatile boolean inRequest;
    static volatile int started;
    static volatile int longestSeen;
    static final int BODY = 48;
    static final int HEADERS = 64;

    static void watch() {
        while (!stop) {
            if (inRequest) {
                int seen = CLOCK.millis() - started;
                if (seen > longestSeen) {
                    longestSeen = seen;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Wifi.begin("juno", "secret");
        byte[] body = new byte[BODY];
        byte[] headers = new byte[HEADERS];
        int[] status = new int[2];

        Thread watcher = Thread.startVirtualThread(IoWhileWaiting::watch);
        started = CLOCK.millis();
        inRequest = true;
        int length = HttpClient.get("http://example.com:8080/slow", body, body.length, headers, headers.length, status);
        inRequest = false;
        stop = true;
        watcher.join();
        Serial.println(status[0] + " " + length);
        Serial.println(longestSeen >= 20 ? "another thread ran during the request" : "the request held every thread");

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                HttpClient.get("http://example.com:8080/slow", body, BODY, headers, HEADERS, status);
            });
            Callable<String> failing = () -> {
                Thread.sleep(5);
                throw new IllegalStateException("cancel the request");
            };
            scope.fork(failing);
            scope.join();
            Serial.println("not cancelled");
        } catch (ExecutionException cancelled) {
            Serial.println("request cancelled");
        }

        status[0] = 0;
        length = HttpClient.get("http://example.com:8080/slow", body, body.length, headers, headers.length, status);
        Serial.println(status[0] + " " + length);
    }
}
