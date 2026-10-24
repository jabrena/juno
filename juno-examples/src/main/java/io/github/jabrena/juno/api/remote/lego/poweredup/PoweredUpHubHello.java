package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
import io.github.jabrena.juno.util.Delay;

/**
 * The smallest Powered Up example: connects to a LEGO Powered Up hub (Technic Hub, Move Hub or City
 * Hub) over Bluetooth LE, prints its battery level, and then, forever, cycles the hub's LED through
 * several colors, so you can see that the connection works. No motors are used. If the link drops it
 * waits for the hub again. Switch the hub on (press its button) and keep it close to the board; the
 * Arduino connects to the first hub it finds.
 * Pick the board with {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class PoweredUpHubHello {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final int COLOR_MILLIS = 600;
    private static final int[] COLORS = {
        PoweredUpHubRemote.COLOR_RED,
        PoweredUpHubRemote.COLOR_ORANGE,
        PoweredUpHubRemote.COLOR_YELLOW,
        PoweredUpHubRemote.COLOR_GREEN,
        PoweredUpHubRemote.COLOR_CYAN,
        PoweredUpHubRemote.COLOR_BLUE,
        PoweredUpHubRemote.COLOR_PURPLE,
        PoweredUpHubRemote.COLOR_PINK,
        PoweredUpHubRemote.COLOR_WHITE
    };

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("PoweredUpHubHello started");
        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!HUB.connect(10000)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                Serial.print("Connected, battery ");
                Serial.print(HUB.batteryPercent());
                Serial.println(" %");
            }
            for (int i = 0; i < COLORS.length; i++) {
                if (!HUB.isConnected()) {
                    Serial.println("Hub disconnected");
                    break;
                }
                HUB.setLedColor(COLORS[i]);
                Delay.millis(COLOR_MILLIS);
            }
        }
    }
}
