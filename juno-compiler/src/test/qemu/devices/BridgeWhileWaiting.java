package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * On the UNO Q a network call waits inside Arduino_RouterBridge for Linux to answer; the call runs on the I/O worker
 * so the other threads keep going. The harness's stand-in bridge (src/test/qemu/Arduino_RouterBridge.h) takes 30 ms to
 * start and 40 ms to open a TLS connection, waiting by polling as the real library does. While the main thread is in
 * {@code Wifi.begin} and in {@code HttpClient.get}, a virtual thread watches the clock: it must see time pass, so it
 * ran. Then a subtask is cancelled while its request is on the worker, and the next request must still work.
 * BridgeWhileWaiting.expected holds the output by hand.
 */
@Board(ArduinoUnoQ.class)
public final class BridgeWhileWaiting {
    static final int BODY = 32;
    static final int HEADERS = 64;
    static final Clock CLOCK = MCU.getClock();
    static volatile boolean stop;
    static volatile boolean waiting;
    static volatile int started;
    static volatile int longestSeen;

    static void watch() {
        while (!stop) {
            if (waiting) {
                int seen = CLOCK.millis() - started;
                if (seen > longestSeen) {
                    longestSeen = seen;
                }
            }
        }
    }

    static void begin() {
        longestSeen = 0;
        started = CLOCK.millis();
        waiting = true;
    }

    static String verdict() {
        waiting = false;
        return longestSeen >= 15 ? "other threads ran" : "every thread waited";
    }

    public static void main() throws Exception {
        byte[] body = new byte[BODY];
        byte[] headers = new byte[HEADERS];
        int[] status = new int[2];
        Thread watcher = Thread.startVirtualThread(BridgeWhileWaiting::watch);

        begin();
        Wifi.begin("juno", "secret");
        String bridge = verdict();
        Serial.println((Wifi.status() == Wifi.STATUS_CONNECTED ? "bridge up: " : "bridge down: ") + bridge);

        begin();
        int length = HttpClient.get("https://example.com/", body, BODY, headers, HEADERS, status);
        String request = verdict();
        Serial.println(status[0] + " " + length + ": " + request);
        stop = true;
        watcher.join();

        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                HttpClient.get("https://example.com/", body, BODY, headers, HEADERS, status);
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
        length = HttpClient.get("https://example.com/", body, BODY, headers, HEADERS, status);
        Serial.println(status[0] + " " + length);
    }
}
