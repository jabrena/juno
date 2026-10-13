package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

import java.io.IOException;

/**
 * Keeps a TFT dashboard responsive while on-demand HTTPS requests run in the background.
 *
 * <p>The view owns the display and touch panel on the main thread. A controller performs SD,
 * Wi-Fi, and HTTPS operations and publishes only primitive results. Both objects and all buffers
 * are allocated once during startup; the display and request loops allocate nothing.
 *
 * <p>Put {@code application.properties}, containing {@code wifi.ssid} and {@code wifi.password},
 * in the SD-card root. At startup the example creates or appends to {@code STATUS.TXT}. Tap the
 * on-screen button to run or repeat the HTTPS request.
 *
 * <p>The storage view draws flash, RAM-arena and SD-card occupancy as segmented bars. The controller counts the
 * card's free space once at startup, off the display thread, because that scan reads the card's whole allocation
 * table.
 */
@Board(ArduinoUnoQ.class)
public final class TftConcurrentDashboard {
    private static final TftConcurrentDashboardView VIEW = new TftConcurrentDashboardView();

    private TftConcurrentDashboard() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        VIEW.begin();
        VIEW.run();
    }
}
