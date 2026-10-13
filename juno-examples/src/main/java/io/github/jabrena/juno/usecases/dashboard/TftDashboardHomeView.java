package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Renders the dashboard home screen. */
final class TftDashboardHomeView {
    private static final int BUTTON_X = 72;
    private static final int STATUS_BUTTON_Y = 92;
    private static final int STORAGE_BUTTON_Y = 146;
    private static final int BUTTON_WIDTH = 176;
    private static final int BUTTON_HEIGHT = 42;

    void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("JUNO DASHBOARD");
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);

        TftTouchShield.setCursor(12, 64);
        TftTouchShield.print("Select a view");
        drawButton(STATUS_BUTTON_Y);
        TftTouchShield.setCursor(94, STATUS_BUTTON_Y + 13);
        TftTouchShield.print("VIEW STATUS");
        drawButton(STORAGE_BUTTON_Y);
        TftTouchShield.setCursor(88, STORAGE_BUTTON_Y + 13);
        TftTouchShield.print("VIEW STORAGE");
    }

    void draw(int framesPerSecond) {
        drawFps(framesPerSecond);
    }

    boolean dashboardButtonContains(int x, int y) {
        return buttonContains(x, y, STATUS_BUTTON_Y);
    }

    boolean storageButtonContains(int x, int y) {
        return buttonContains(x, y, STORAGE_BUTTON_Y);
    }

    private boolean buttonContains(int x, int y, int buttonY) {
        return x >= BUTTON_X
                && x < BUTTON_X + BUTTON_WIDTH
                && y >= buttonY
                && y < buttonY + BUTTON_HEIGHT;
    }

    private void drawButton(int buttonY) {
        TftTouchShield.fillRect(BUTTON_X, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.BLUE);
        TftTouchShield.drawRect(BUTTON_X, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLUE);
    }

    private void drawFps(int framesPerSecond) {
        TftTouchShield.fillRect(12, 212, 150, 20, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 216);
        TftTouchShield.print("FPS ");
        TftTouchShield.print(framesPerSecond);
    }
}
