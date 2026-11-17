package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.Wifi;

/**
 * On the UNO Q a network call waits inside Arduino_RouterBridge for Linux to answer; the call runs on the I/O worker
 * so the other threads keep going. The harness's stand-in bridge (src/test/docker/qemu/Arduino_RouterBridge.h) takes
 * 30 ms to start, waiting by polling as the real library does. While the main thread is in {@code Wifi.begin}, a
 * virtual thread watches the clock: it must see time pass, so it ran. BridgeWhileWaiting.expected holds the output by
 * hand.
 */
@Board(ArduinoUnoQBoard.class)
public final class BridgeWhileWaiting {
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
        Thread watcher = Thread.startVirtualThread(BridgeWhileWaiting::watch);

        begin();
        MCU.getWifi().begin("juno", "secret");
        String bridge = verdict();
        Serial.println((MCU.getWifi().status() == Wifi.STATUS_CONNECTED ? "bridge up: " : "bridge down: ") + bridge);
        stop = true;
        watcher.join();
    }
}
