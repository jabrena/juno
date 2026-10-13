package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import java.io.IOException;

/** Coordinates dashboard navigation, touch actions, and frame timing. */
final class TftConcurrentDashboardView {
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_DASHBOARD = 1;
    private static final int SCREEN_STORAGE = 2;
    private static final int FRAME_PERIOD_MS = 50;
    private static final int ANIMATION_STEPS = 60;
    private static final int FPS_WINDOW_MS = 1000;

    private final TftConcurrentDashboardController controller = new TftConcurrentDashboardController();
    private final TftDashboardHomeView homeView = new TftDashboardHomeView();
    private final TftDashboardStatusView dashboardView = new TftDashboardStatusView(controller);
    private final TftDashboardStorageView storageView = new TftDashboardStorageView(controller);

    private int activeScreen = SCREEN_HOME;
    private boolean touchWasDown;

    void begin() throws IOException {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        if (controller.beginSdCard()) {
            controller.writeStartupStatus();
        } else {
            controller.markSdUnavailable();
        }
        controller.beginNetworkAndWorker();
        homeView.show();
    }

    void run() throws InterruptedException {
        int frame = 0;
        int framesInWindow = 0;
        int framesPerSecond = 0;
        int fpsWindowAt = Clock.millis();
        int reportedRequests = 0;

        while (true) {
            int now = Clock.millis();
            framesInWindow = framesInWindow + 1;
            int fpsWindowElapsed = now - fpsWindowAt;
            if (fpsWindowElapsed >= FPS_WINDOW_MS) {
                framesPerSecond = framesInWindow;
                framesInWindow = 0;
                fpsWindowAt = now;
            }

            handleActions();
            if (activeScreen == SCREEN_HOME) {
                homeView.draw(framesPerSecond);
            } else if (activeScreen == SCREEN_STORAGE) {
                storageView.draw(framesPerSecond);
            } else {
                dashboardView.draw(frame, framesPerSecond);
            }
            frame = (frame + 1) % ANIMATION_STEPS;

            if (controller.completedRequests() != reportedRequests) {
                controller.printMetrics(framesPerSecond);
                reportedRequests = controller.completedRequests();
            }
            Thread.sleep(FRAME_PERIOD_MS);
        }
    }

    private void handleActions() {
        boolean touched = TftTouchShield.readTouch();
        if (touched && !touchWasDown) {
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            if (activeScreen == SCREEN_HOME && homeView.dashboardButtonContains(x, y)) {
                activeScreen = SCREEN_DASHBOARD;
                dashboardView.show();
            } else if (activeScreen == SCREEN_HOME && homeView.storageButtonContains(x, y)) {
                activeScreen = SCREEN_STORAGE;
                storageView.show();
            } else if (activeScreen == SCREEN_STORAGE && storageView.homeButtonContains(x, y)) {
                activeScreen = SCREEN_HOME;
                homeView.show();
            } else if (activeScreen == SCREEN_DASHBOARD && dashboardView.backButtonContains(x, y)) {
                activeScreen = SCREEN_HOME;
                homeView.show();
            } else if (activeScreen == SCREEN_DASHBOARD && dashboardView.httpsButtonContains(x, y)) {
                controller.requestHttps();
            }
        }
        touchWasDown = touched;
    }
}
