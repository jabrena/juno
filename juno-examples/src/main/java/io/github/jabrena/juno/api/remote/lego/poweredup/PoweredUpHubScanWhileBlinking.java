package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two subtasks side by side: one scans for a Powered Up hub with {@code RemotePoweredUpHub.connect}, the other prints
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
    private static final int STARTUP_MILLIS = 5_000;
    private static final int SCAN_TIMEOUT_MILLIS = 30_000;
    private static final int PROGRESS_MILLIS = 500;

    public static void main(String[] args) throws Exception {
        Serial.begin(BaudRate.BAUD_115200);
        // The USB serial drops what is printed before a monitor opens the port.
        Delay.millis(STARTUP_MILLIS);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock());
        Clock clock = MCU.getClock();
        // Shared between the two subtasks; atomic so the same source is also data-race free on the JVM.
        AtomicBoolean scanning = new AtomicBoolean(true);
        AtomicBoolean found = new AtomicBoolean(false);

        // Forks the scan and the progress line; the scope closes when both have finished.
        try (var scope = StructuredTaskScope.open()) {
            // The blocking call; the shim yields while it waits for a hub to advertise.
            scope.fork(() -> {
                found.set(hub.connect(SCAN_TIMEOUT_MILLIS));
                scanning.set(false);
            });
            // Runs until the scan is over; {@code Delay.millis} is a switch point on both boards.
            scope.fork(() -> {
                int started = clock.millis();
                while (scanning.get()) {
                    Serial.println("Scanning... " + (clock.millis() - started) / 1000 + " s");
                    Delay.millis(PROGRESS_MILLIS);
                }
            });
            scope.join();
        }

        if (found.get()) {
            Serial.println("Connected, battery " + hub.batteryPercent() + " %");
        } else {
            Serial.println("No hub found");
        }
    }
}
