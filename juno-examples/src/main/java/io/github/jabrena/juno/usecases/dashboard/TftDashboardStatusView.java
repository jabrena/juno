package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Renders the network status screen and exposes its button hit areas. */
final class TftDashboardStatusView {
    private static final int HTTPS_BUTTON_X = 12;
    private static final int BACK_BUTTON_X = 166;
    private static final int BUTTON_Y = 144;
    private static final int BUTTON_WIDTH = 142;
    private static final int BUTTON_HEIGHT = 34;

    private final TftConcurrentDashboardController controller;

    TftDashboardStatusView(TftConcurrentDashboardController controller) {
        this.controller = controller;
    }

    void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("NETWORK STATUS");
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);
    }

    void draw(int frame, int framesPerSecond) {
        TftTouchShield.fillRect(12, 52, 296, 180, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);

        drawHttpsState();
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 80);
        TftTouchShield.print("status ");
        TftTouchShield.print(controller.httpStatus());

        TftTouchShield.setCursor(166, 80);
        TftTouchShield.print("body ");
        TftTouchShield.print(controller.responseBytes());
        TftTouchShield.print(" B");

        drawState("SD", controller.sdState(), 112);
        TftTouchShield.setCursor(142, 112);
        TftTouchShield.print("file ");
        TftTouchShield.print(controller.statusFileBytes());
        TftTouchShield.print(" B");

        drawHttpsButton();
        drawBackButton();
        TftTouchShield.fillRect(12, 190, 296, 12, TftTouchShield.BLACK);
        TftTouchShield.fillRect(14 + frame * 4, 192, 16, 8, TftTouchShield.CYAN);
        drawFps(framesPerSecond);
    }

    boolean httpsButtonContains(int x, int y) {
        return buttonContains(x, y, HTTPS_BUTTON_X);
    }

    boolean backButtonContains(int x, int y) {
        return buttonContains(x, y, BACK_BUTTON_X);
    }

    private boolean buttonContains(int x, int y, int buttonX) {
        return x >= buttonX
                && x < buttonX + BUTTON_WIDTH
                && y >= BUTTON_Y
                && y < BUTTON_Y + BUTTON_HEIGHT;
    }

    private void drawHttpsState() {
        int httpsState = controller.httpsState();
        TftTouchShield.setTextColor(stateColor(httpsState), TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 52);
        TftTouchShield.print("HTTPS");
        TftTouchShield.setCursor(90, 52);
        if (httpsState == TftConcurrentDashboardController.STATUS_WAITING) {
            TftTouchShield.print("READY");
        } else if (httpsState == TftConcurrentDashboardController.STATUS_RUNNING) {
            TftTouchShield.print("IN PROGRESS");
        } else if (httpsState == TftConcurrentDashboardController.STATUS_SUCCEEDED) {
            TftTouchShield.print("DONE");
        } else {
            TftTouchShield.print("FAILED");
        }
    }

    private void drawHttpsButton() {
        int httpsState = controller.httpsState();
        int color = TftTouchShield.BLUE;
        if (!controller.wifiReady() || httpsState == TftConcurrentDashboardController.STATUS_FAILED) {
            color = TftTouchShield.RED;
        } else if (httpsState == TftConcurrentDashboardController.STATUS_RUNNING) {
            color = TftTouchShield.ORANGE;
        } else if (httpsState == TftConcurrentDashboardController.STATUS_SUCCEEDED) {
            color = TftTouchShield.GREEN;
        }
        TftTouchShield.fillRect(HTTPS_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(
                HTTPS_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(20, 153);
        if (!controller.wifiReady()) {
            TftTouchShield.print("NO WIFI");
        } else if (httpsState == TftConcurrentDashboardController.STATUS_RUNNING) {
            TftTouchShield.print("IN PROGRESS");
        } else if (httpsState == TftConcurrentDashboardController.STATUS_SUCCEEDED) {
            TftTouchShield.print("RUN AGAIN");
        } else {
            TftTouchShield.print("RUN HTTPS");
        }
    }

    private void drawBackButton() {
        TftTouchShield.fillRect(BACK_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.drawRect(BACK_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(202, 153);
        TftTouchShield.print("BACK");
    }

    private void drawState(String label, int state, int y) {
        TftTouchShield.setTextColor(stateColor(state), TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, y);
        TftTouchShield.print(label);
        TftTouchShield.setCursor(72, y);
        if (state == TftConcurrentDashboardController.STATUS_WAITING) {
            TftTouchShield.print("WAIT");
        } else if (state == TftConcurrentDashboardController.STATUS_RUNNING) {
            TftTouchShield.print("RUN ");
        } else if (state == TftConcurrentDashboardController.STATUS_SUCCEEDED) {
            TftTouchShield.print("OK  ");
        } else {
            TftTouchShield.print("FAIL");
        }
    }

    private int stateColor(int state) {
        if (state == TftConcurrentDashboardController.STATUS_SUCCEEDED) {
            return TftTouchShield.GREEN;
        }
        if (state == TftConcurrentDashboardController.STATUS_FAILED) {
            return TftTouchShield.RED;
        }
        if (state == TftConcurrentDashboardController.STATUS_RUNNING) {
            return TftTouchShield.YELLOW;
        }
        return TftTouchShield.GRAY;
    }

    private void drawFps(int framesPerSecond) {
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 216);
        TftTouchShield.print("FPS ");
        TftTouchShield.print(framesPerSecond);
    }
}
