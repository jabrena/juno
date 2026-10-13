package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Runs the frame loop and routes touches to the active screen. The program starts on the home screen, which opens
 * the other screens; this class owns only the frame timing, the shared FPS label, and which screen is showing.
 */
final class TftConcurrentDashboardView {
    private static final int FRAME_PERIOD_MS = 50;
    private static final int FPS_WINDOW_MS = 1000;

    private final TftDashboardHomeView homeView = new TftDashboardHomeView();

    private TftDashboardScreen activeScreen = homeView;
    private boolean touchWasDown;

    void begin() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        // Draw before the SD start-up, so the screen is never blank; Wi-Fi starts in the background, off this thread.
        activeScreen.show();
        homeView.startBackgroundWork();
    }

    void run() throws InterruptedException {
        int framesInWindow = 0;
        int framesPerSecond = 0;
        int fpsWindowAt = Clock.millis();

        while (true) {
            int now = Clock.millis();
            framesInWindow = framesInWindow + 1;
            int fpsWindowElapsed = now - fpsWindowAt;
            if (fpsWindowElapsed >= FPS_WINDOW_MS) {
                framesPerSecond = framesInWindow;
                framesInWindow = 0;
                fpsWindowAt = now;
                Serial.print("FPS ");
                Serial.println(framesPerSecond);
            }

            handleActions();
            activeScreen.draw();
            drawFps(framesPerSecond);
            Thread.sleep(FRAME_PERIOD_MS);
        }
    }

    /** The FPS label every screen shares, drawn over the active screen on each frame. */
    private void drawFps(int framesPerSecond) {
        TftTouchShield.fillRect(12, 212, 150, 20, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 216);
        TftTouchShield.print("FPS ");
        TftTouchShield.print(framesPerSecond);
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
