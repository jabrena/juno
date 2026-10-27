package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** A polished cover screen inviting the player into the daily Lichess challenge. */
final class CoverView implements View {
    private static final int BACKGROUND = 0x1082;
    private static final int CONTENT_WIDTH = 240;
    private static final int LOGO_CENTER_X = 120;
    private static final int LOGO_CENTER_Y = 53;
    private static final int LOGO_RADIUS = 42;
    private static final int BUTTON_X = 34;
    private static final int BUTTON_Y = 201;
    private static final int BUTTON_WIDTH = 172;
    private static final int BUTTON_HEIGHT = 48;
    private final Navigator navigator;

    CoverView(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(BACKGROUND);
        drawLogo();

        // The title is one measured row: LICHESS, never split as "LI CHESS".
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BACKGROUND);
        TftTouchShield.setCursor(centeredX("LICHESS", 4), 101);
        TftTouchShield.print("LICHESS");

        // A single centered subtitle row keeps the visual hierarchy compact on the portrait TFT.
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, BACKGROUND);
        TftTouchShield.setCursor(centeredX("DAILY CHALLENGE", 2), 145);
        TftTouchShield.print("DAILY CHALLENGE");

        TftTouchShield.fillRect(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.ORANGE);
        TftTouchShield.drawRect(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.ORANGE);
        TftTouchShield.setCursor(centeredInButton("OK", 3), BUTTON_Y + 12);
        TftTouchShield.print("OK");

        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, BACKGROUND);
        TftTouchShield.setCursor(centeredX("TAP TO FIND YOUR NEXT MOVE", 1), 286);
        TftTouchShield.print("TAP TO FIND YOUR NEXT MOVE");
    }

    @Override
    public void render() {
    }

    @Override
    public void touch(int x, int y) {
        navigator.show(LichessViews.MENU);
    }

    /** A large white-on-navy approximation of the supplied Lichess horse mark. */
    private void drawLogo() {
        TftTouchShield.fillCircle(LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_RADIUS, TftTouchShield.WHITE);
        TftTouchShield.fillCircle(LOGO_CENTER_X, LOGO_CENTER_Y, 34, BACKGROUND);

        // Circular stroke.
        TftTouchShield.fillCircle(LOGO_CENTER_X, LOGO_CENTER_Y, 30, TftTouchShield.WHITE);
        TftTouchShield.fillCircle(LOGO_CENTER_X, LOGO_CENTER_Y, 23, BACKGROUND);
        TftTouchShield.fillRect(120, 29, 30, 52, BACKGROUND);

        // Horse neck, head and pointed ear, built from chunky primitives for the 240x320 display.
        TftTouchShield.fillCircle(115, 54, 12, TftTouchShield.WHITE);
        TftTouchShield.fillRect(110, 50, 26, 28, TftTouchShield.WHITE);
        TftTouchShield.fillRect(120, 42, 18, 28, TftTouchShield.WHITE);
        TftTouchShield.fillRect(130, 34, 13, 20, TftTouchShield.WHITE);
        TftTouchShield.fillRect(138, 32, 13, 8, TftTouchShield.WHITE);
        TftTouchShield.fillRect(136, 39, 8, 10, BACKGROUND);
        TftTouchShield.fillCircle(127, 57, 4, BACKGROUND);
        TftTouchShield.fillRect(105, 73, 34, 8, TftTouchShield.WHITE);
        TftTouchShield.fillRect(97, 78, 42, 7, TftTouchShield.WHITE);
    }

    private static int centeredX(String text, int size) {
        return (CONTENT_WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }

    private static int centeredInButton(String text, int size) {
        return BUTTON_X + (BUTTON_WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }
}
