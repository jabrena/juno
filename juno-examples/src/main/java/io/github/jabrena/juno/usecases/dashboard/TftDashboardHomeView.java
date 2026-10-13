package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The home screen and the dashboard's entry point: a menu that opens the status and storage views. Both views are
 * created once here, at startup, and return to this screen. Home also starts the SD card and the one background
 * thread that runs the views' slow work, one step after another: the card is not safe to share between threads.
 */
final class TftDashboardHomeView implements TftDashboardScreen {
    private static final int BUTTON_X = 72;
    private static final int STATUS_BUTTON_Y = 92;
    private static final int STORAGE_BUTTON_Y = 146;
    private static final int BUTTON_WIDTH = 176;
    private static final int BUTTON_HEIGHT = 42;

    private final TftDashboardStatusView statusView;
    private final TftDashboardStorageView storageView;

    TftDashboardHomeView() {
        this.statusView = new TftDashboardStatusView(this);
        this.storageView = new TftDashboardStorageView(this);
    }

    /** Starts the SD card, then hands the views' slow work to a background thread so the display never waits. */
    void startBackgroundWork() {
        boolean cardReady = SdCard.begin();
        statusView.writeStartupStatus(cardReady);
        if (cardReady) {
            Thread.startVirtualThread(this::runBackgroundWork);
        } else {
            storageView.markSdUnavailable();
            Thread.startVirtualThread(statusView::connectWifi);
        }
    }

    private void runBackgroundWork() {
        statusView.readStatusFile();
        storageView.scanSdCapacity();
        statusView.connectWifi();
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("JUNO DASHBOARD");
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);

        TftTouchShield.setCursor(12, 64);
        TftTouchShield.print("Select a view");
        drawButton(STATUS_BUTTON_Y, "VIEW STATUS", 94);
        drawButton(STORAGE_BUTTON_Y, "VIEW STORAGE", 88);
    }

    @Override
    public void draw() {
        // Static screen: only the shared FPS label changes.
    }

    @Override
    public TftDashboardScreen touch(int x, int y) {
        if (buttonContains(x, y, STATUS_BUTTON_Y)) {
            return statusView;
        }
        if (buttonContains(x, y, STORAGE_BUTTON_Y)) {
            return storageView;
        }
        return this;
    }

    private void drawButton(int buttonY, String label, int labelX) {
        TftTouchShield.fillRect(BUTTON_X, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.BLUE);
        TftTouchShield.drawRect(BUTTON_X, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLUE);
        TftTouchShield.setCursor(labelX, buttonY + 13);
        TftTouchShield.print(label);
    }

    private boolean buttonContains(int x, int y, int buttonY) {
        return x >= BUTTON_X
                && x < BUTTON_X + BUTTON_WIDTH
                && y >= buttonY
                && y < buttonY + BUTTON_HEIGHT;
    }
}
