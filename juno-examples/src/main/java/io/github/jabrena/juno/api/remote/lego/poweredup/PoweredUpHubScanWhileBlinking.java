package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two subtasks side by side: one scans for a Powered Up hub with {@code PoweredUpHubRemote.connect}, the other prints
 * "Scanning... N s" every half second, so the program shows signs of life while the Arduino looks for the hub. Switch
 * the hub on before the scan starts or during it.
 *
 * <p>On the JVM the two subtasks run in parallel. On the board the runtime never preempts a hardware call, so the
 * progress line can only appear because the shim's scan loop yields to the scheduler while no hub has answered yet.
 * The one stretch that stays silent is the second or so inside ArduinoBLE's own blocking connect and service
 * discovery, once a hub is found.
 *
 * <p>Expected output (one line per 500 ms, then the result):
 * <pre>
 * Scanning... 0 s
 * Scanning... 0 s
 * Scanning... 1 s
 * Connected, battery 100 %
 * </pre>
 * The program waits a few seconds before scanning so a serial monitor can attach first. On the UNO R4 WiFi the
 * default runtime configuration does not fit with ArduinoBLE: build it with three scheduler slots, as the
 * configuration analysis suggests. Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubScanWhileBlinking {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final Clock CLOCK = ArduinoOne.getClock();
    private static final int STARTUP_MILLIS = 5_000;
    private static final int SCAN_TIMEOUT_MILLIS = 30_000;
    private static final int PROGRESS_MILLIS = 500;

    // Shared between the two subtasks; atomic so the same source is also data-race free on the JVM.
    private final AtomicBoolean scanning = new AtomicBoolean(true);
    private final AtomicBoolean found = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        // The USB serial drops what is printed before a monitor opens the port.
        Delay.millis(STARTUP_MILLIS);
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

    /** The blocking call; the shim yields while it waits for a hub to advertise. */
    private void connect() {
        found.set(HUB.connect(SCAN_TIMEOUT_MILLIS));
        scanning.set(false);
    }

    /** Runs until the scan is over; {@code Delay.millis} is a switch point on both boards. */
    private void showProgress() {
        int started = CLOCK.millis();
        while (scanning.get()) {
            Serial.println("Scanning... " + (CLOCK.millis() - started) / 1000 + " s");
            Delay.millis(PROGRESS_MILLIS);
        }
    }

    private void report() {
        if (found.get()) {
            Serial.println("Connected, battery " + HUB.batteryPercent() + " %");
        } else {
            Serial.println("No hub found");
        }
    }
}
