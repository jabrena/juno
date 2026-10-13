package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardScreen;
import io.github.jabrena.juno.usecases.dashboard.home.TftDashboardHomeView;

/**
 * A touch dashboard that stays responsive while slow SD-card and Wi-Fi work runs in the background.
 *
 * <p>The display and touch panel belong to the main thread, which runs the frame loop: it routes each new touch to
 * the active screen and lets it redraw what changed, frame rate included, which is also printed over serial once a
 * second. It starts on the home screen, which opens a network status view and a storage view. Two virtual threads do the slow
 * work, one on the SD card and one on Wi-Fi, and publish what they find into one shared state that both views read.
 * The screens and all buffers are allocated once during startup; the frame loop allocates nothing.
 *
 * <p>Put {@code application.properties}, containing {@code wifi.ssid} and {@code wifi.password}, in the SD-card
 * root. At startup the example creates or appends to {@code STATUS.TXT}.
 *
 * <p>The storage view draws flash, RAM-arena and SD-card occupancy as segmented bars, measuring the card's free
 * space once at startup, off the display thread.
 */
@Board(ArduinoUnoQ.class)
public final class DashboardTFT {
    private static final int FRAME_PERIOD_MS = 50;

    private final TftDashboardHomeView homeView = new TftDashboardHomeView();

    private TftDashboardScreen activeScreen = homeView;
    private boolean touchWasDown;

    private DashboardTFT() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        DashboardTFT dashboard = new DashboardTFT();
        dashboard.begin();
        dashboard.run();
    }

    private void begin() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        // Draw before the SD start-up, so the screen is never blank; Wi-Fi starts in the background, off this thread.
        activeScreen.show();
        homeView.startBackgroundWork();
    }

    private void run() throws InterruptedException {
        while (true) {
            handleActions();
            activeScreen.draw();
            Thread.sleep(FRAME_PERIOD_MS);
        }
    }

    private void handleActions() {
        boolean touched = TftTouchShield.readTouch();
        if (touched && !touchWasDown) {
            TftDashboardScreen next = activeScreen.touch(TftTouchShield.touchX(), TftTouchShield.touchY());
            if (next != activeScreen) {
                activeScreen = next;
                activeScreen.show();
            }
        }
        touchWasDown = touched;
    }
}
