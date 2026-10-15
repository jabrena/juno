package io.github.jabrena.juno.usecases.miner.commons;

import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;

/** Draws Miner2's title and live Wi-Fi and SD-card indicators without redrawing an unchanged header. */
public final class Miner2Header {
    private static final int SD_X = 209;
    private static final int SD_Y = 8;
    private static final int WIFI_X = 183;
    /** The arcs' centre: the outer arc (radius 13) tops out on the SD card's top edge. */
    private static final int WIFI_Y = SD_Y + 13;

    private final Miner2State model;
    private int shownStatus = -1;

    public Miner2Header(Miner2State model) {
        this.model = model;
    }

    /** Clears the screen, draws the title, and forces both status icons to be drawn. */
    public void enter() {
        GameScreen.header("MINER", TftTouchShield.GREEN);
        redrawIcons();
    }

    /** Draws both status icons over whatever the screen shows, in the same place as under the title. */
    public void redrawIcons() {
        shownStatus = -1;
        render();
    }

    /** Whether ({@code x}, {@code y}) is on the Wi-Fi icon, which stops the node and returns to the cover. */
    public static boolean isWifi(int x, int y) {
        return x >= WIFI_X - 14 && x < WIFI_X + 15 && y >= 7 && y < 38;
    }

    /** Redraws the status icons only when either device's availability changed. */
    public void render() {
        int status = statusKey(model.wifiConnected(), model.sdCardReady());
        if (status == shownStatus) {
            return;
        }
        shownStatus = status;
        drawWifi(model.wifiConnected() ? TftTouchShield.GREEN : TftTouchShield.GRAY);
        drawSdCard(model.sdCardReady() ? TftTouchShield.GREEN : TftTouchShield.GRAY);
    }

    static int statusKey(boolean wifiConnected, boolean sdCardReady) {
        return (wifiConnected ? 1 : 0) | (sdCardReady ? 2 : 0);
    }

    private static void drawWifi(int color) {
        TftTouchShield.fillRect(WIFI_X - 14, 7, 29, 31, GameScreen.PANEL);
        TftTouchShield.drawCircle(WIFI_X, WIFI_Y, 13, color);
        TftTouchShield.drawCircle(WIFI_X, WIFI_Y, 12, color);
        TftTouchShield.drawCircle(WIFI_X, WIFI_Y, 8, color);
        TftTouchShield.drawCircle(WIFI_X, WIFI_Y, 7, color);
        TftTouchShield.fillRect(WIFI_X - 14, WIFI_Y, 29, 10, GameScreen.PANEL);
        TftTouchShield.fillCircle(WIFI_X, WIFI_Y + 3, 2, color);
    }

    private static void drawSdCard(int color) {
        TftTouchShield.fillRect(SD_X - 1, SD_Y - 1, 25, 30, GameScreen.PANEL);
        TftTouchShield.drawHorizontalLine(SD_X, SD_Y, 14, color);
        TftTouchShield.drawPixel(SD_X + 14, SD_Y + 1, color);
        TftTouchShield.drawPixel(SD_X + 15, SD_Y + 2, color);
        TftTouchShield.drawPixel(SD_X + 16, SD_Y + 3, color);
        TftTouchShield.drawPixel(SD_X + 17, SD_Y + 4, color);
        TftTouchShield.drawPixel(SD_X + 18, SD_Y + 5, color);
        TftTouchShield.drawVerticalLine(SD_X + 19, SD_Y + 6, 21, color);
        TftTouchShield.drawHorizontalLine(SD_X, SD_Y + 27, 20, color);
        TftTouchShield.drawVerticalLine(SD_X, SD_Y, 28, color);
        for (int x = SD_X + 4; x <= SD_X + 13; x = x + 3) {
            TftTouchShield.drawVerticalLine(x, SD_Y + 4, 5, color);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, GameScreen.PANEL);
        TftTouchShield.setCursor(SD_X + 4, SD_Y + 16);
        TftTouchShield.print("SD");
    }
}
