package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A discussion example for yielding inside the shim's blocking waits: one subtask scans for a Powered Up hub
 * with {@code PoweredUpHubRemote.connect}, a second one keeps printing "Scanning... N s" every half second, so
 * the program shows signs of life while the Arduino looks for the hub. Switch the hub on first.
 *
 * <p>On the JVM the two subtasks run side by side, so the progress line appears all through the scan. Under the
 * cooperative ASM runtime the line only appears while {@code connect} lets the scheduler run, and today it never
 * does: the scan is a C++ busy loop in the shim, which is not a switch point, so nothing is printed until
 * {@code connect} returns (the hub is found or the timeout ends). With the scan's wait loop calling the shim's
 * yield, the ASM output matches the JVM's, except for the final stretch inside ArduinoBLE's own blocking
 * {@code connect()}/{@code discoverService()} (about a second or two), which stays atomic.
 *
 * <p>Expected output once the wait loop yields (one line per 500 ms, then the result):
 * <pre>
 * Scanning... 0 s
 * Scanning... 1 s
 * Connected, battery 87 %
 * </pre>
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubScanWhileBlinking {
    private static final int SCAN_TIMEOUT_MILLIS = 10_000;
    private static final int PROGRESS_MILLIS = 500;

    // Shared between the two subtasks; atomic so the same source is also data-race free on the JVM.
    private final AtomicBoolean scanning = new AtomicBoolean(true);
    private final AtomicBoolean found = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpHubScanWhileBlinking example = new PoweredUpHubScanWhileBlinking();
        example.scan();
        example.report();
    }

    /** Forks the scan and the progress line; the scope closes when both have finished. */
    private void scan() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> connect());
            scope.fork(() -> showProgress());
            scope.join();
        }
    }

    /** The blocking call: in the ASM runtime it must yield while it waits for a hub to advertise. */
    private void connect() {
        found.set(PoweredUpHubRemote.connect(SCAN_TIMEOUT_MILLIS));
        scanning.set(false);
    }

    /** Runs until the scan is over; {@code Delay.millis} is a switch point on both boards. */
    private void showProgress() {
        int started = Clock.millis();
        while (scanning.get()) {
            Serial.println("Scanning... " + (Clock.millis() - started) / 1000 + " s");
            Delay.millis(PROGRESS_MILLIS);
        }
    }

    private void report() {
        if (found.get()) {
            Serial.println("Connected, battery " + PoweredUpHubRemote.batteryPercent() + " %");
        } else {
            Serial.println("No hub found");
        }
    }
}
