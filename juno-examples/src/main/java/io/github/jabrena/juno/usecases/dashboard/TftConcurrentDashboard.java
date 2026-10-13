package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * A touch dashboard that stays responsive while slow SD-card and Wi-Fi work runs in the background.
 *
 * <p>The display and touch panel belong to the main thread, which starts on the home screen; home opens a network
 * status view and a storage view. Each view does its own slow SD and Wi-Fi work, which home runs one step after
 * another on a virtual thread, and publishes only primitive results. The screens and all buffers are allocated once
 * during startup; the display loop allocates nothing, and prints its frame rate over serial once a second.
 *
 * <p>Put {@code application.properties}, containing {@code wifi.ssid} and {@code wifi.password}, in the SD-card
 * root. At startup the example creates or appends to {@code STATUS.TXT}.
 *
 * <p>The storage view draws flash, RAM-arena and SD-card occupancy as segmented bars, measuring the card's free
 * space once at startup, off the display thread.
 */
@Board(ArduinoUnoQ.class)
public final class TftConcurrentDashboard {
    private static final TftConcurrentDashboardView VIEW = new TftConcurrentDashboardView();

    private TftConcurrentDashboard() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        VIEW.begin();
        VIEW.run();
    }
}
