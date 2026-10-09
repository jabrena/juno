package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * The smallest Powered Up example: connects to a LEGO Powered Up hub (Technic Hub, Move Hub or City
 * Hub) over Bluetooth LE, prints its battery level, and then, forever, cycles the hub's LED through
 * several colors, so you can see that the connection works. No motors are used. If the link drops it
 * waits for the hub again. Switch the hub on (press its button) and keep it close to the board; the
 * Arduino connects to the first hub it finds.
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubHello {
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
        while (true) {
            if (!PoweredUpHubRemote.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHubRemote.connect(0);
                Serial.println("Connected, battery " + PoweredUpHubRemote.batteryPercent() + " %");
            }
            for (int i = 0; i < COLORS.length; i++) {
                PoweredUpHubRemote.setLedColor(COLORS[i]);
                Delay.millis(COLOR_MILLIS);
            }
        }
    }
}
