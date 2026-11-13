package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

/**
 * The smallest Powered Up example: connects to a LEGO Powered Up hub (Technic Hub, Move Hub or City
 * Hub) over Bluetooth LE, prints its battery level, and then, forever, cycles the hub's LED through
 * several colors, so you can see that the connection works. No motors are used. If the link drops it
 * waits for the hub again. Switch the hub on (press its button) and keep it close to the board; the
 * Arduino connects to the first hub it finds.
 * This copy targets the M5Stack Cardputer, whose core uses the NimBLE Bluetooth stack.
 */
@Board(M5StackCardputerBoard.class)
public class M5CardputerPoweredUpHubHello {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int COLOR_MILLIS = 600;
    private static final HubColor[] COLORS = {
        HubColor.RED,
        HubColor.ORANGE,
        HubColor.YELLOW,
        HubColor.GREEN,
        HubColor.CYAN,
        HubColor.BLUE,
        HubColor.PURPLE,
        HubColor.PINK,
        HubColor.WHITE
    };

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("M5CardputerPoweredUpHubHello started");
        RemotePoweredUpHub hub = new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock());
        while (true) {
            if (!hub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                if (!hub.connect(CONNECT_TIMEOUT_MILLIS)) {
                    Serial.println("No connection; retrying");
                    Delay.millis(1000);
                    continue;
                }
                Serial.print("Connected, battery ");
                Serial.print(hub.batteryPercent());
                Serial.println(" %");
            }
            for (int i = 0; i < COLORS.length; i++) {
                if (!hub.isConnected()) {
                    Serial.println("Hub disconnected");
                    break;
                }
                Serial.print("color index=");
                Serial.println(i);
                hub.setLedColor(COLORS[i]);
                Delay.millis(COLOR_MILLIS);
            }
        }
    }
}
