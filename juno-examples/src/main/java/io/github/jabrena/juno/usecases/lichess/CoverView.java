package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.games.chess.ChessPieceRenderer;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** A polished cover screen inviting the player into the daily Lichess challenge. */
final class CoverView implements View {
    private static final int BACKGROUND = 0x1082;
    private static final int CONTENT_WIDTH = 240;
    private static final int KNIGHT_X = 80;
    private static final int KNIGHT_Y = 15;
    private static final int KNIGHT_SCALE = 4;
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

    /** The same knight bitmap used by the chess game, enlarged for the cover. */
    private void drawLogo() {
        ChessPieceRenderer.drawKnight(KNIGHT_X, KNIGHT_Y, KNIGHT_SCALE, BACKGROUND);
    }

    private static int centeredX(String text, int size) {
        return (CONTENT_WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }

    private static int centeredInButton(String text, int size) {
        return BUTTON_X + (BUTTON_WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }
}
