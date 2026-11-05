package io.github.jabrena.juno.usecases.lichess.tv;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.lichess.challenge.PuzzleBoard;
import io.github.jabrena.juno.usecases.lichess.chess.ChessPieceRenderer;
import io.github.jabrena.juno.usecases.lichess.commons.LichessViews;
import io.github.jabrena.juno.usecases.lichess.services.LichessApi;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Read-only board monitor for the current game on Lichess TV. */
public final class LichessTvView implements View {

    private final TftTouchShield tft;
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int CELL = 28;
    private static final int BOARD_SIZE = CELL * 8;
    private static final int FOOTER_Y = BOARD_Y + BOARD_SIZE + 8;
    private static final int MENU_X = 172;
    private static final int MENU_Y = 3;
    private static final int MENU_WIDTH = 60;
    private static final int MENU_HEIGHT = 30;
    private static final int LIGHT = 0xF6D6;
    private static final int DARK = 0xB44C;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int LIGHT_LAST_MOVE = 0xF7B0;
    private static final int DARK_LAST_MOVE = 0xBE48;
    private static final int POLL_MILLIS = 6000;

    private final Navigator navigator;
    private final LichessApi api;
    private final TvModel model;
    private final Clock clock = ArduinoUno.getClock();
    private int drawnVersion = -1;
    private int lastPoll;

    public LichessTvView(TftTouchShield tft, Navigator navigator, LichessApi api, TvModel model) {
        this.tft = tft;
        this.navigator = navigator;
        this.api = api;
        this.model = model;
    }

    @Override
    public void enter() {
        drawnVersion = -1;
        lastPoll = clock.millis();
    }

    @Override
    public void render() {
        int now = clock.millis();
        if (now - lastPoll >= POLL_MILLIS) {
            lastPoll = now;
            api.loadTv(model);
        }
        if (drawnVersion != model.version()) {
            draw(tft);
            drawnVersion = model.version();
        }
    }

    @Override
    public void touch(int x, int y) {
        if (isMenuTouch(x, y)) {
            navigator.show(LichessViews.MENU);
        }
    }

    private void draw(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.BLACK);
        drawHeader(tft);
        for (int square = 0; square < 64; square++) {
            int piece = model.board().pieceAt(square);
            boolean lastMove = square == model.lastFrom() || square == model.lastTo();
            drawSquare(tft, square, piece, lastMove);
        }
        drawFooter(tft);
    }

    private void drawHeader(TftTouchShield tft) {
        tft.fillRect(0, 0, 240, 36, HEADER_BACKGROUND);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(8, 2);
        tft.print("LICHESS");
        tft.setTextSize(2);
        tft.setCursor(8, 17);
        tft.print("LICHESS TV");
        drawMenuButton(tft);
    }

    private void drawFooter(TftTouchShield tft) {
        tft.fillRect(0, FOOTER_Y, 240, 320 - FOOTER_Y, TftTouchShield.BLACK);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(8, FOOTER_Y + 1);
        printClock(tft, "WHITE ", model.whiteSeconds());
        tft.setCursor(88, FOOTER_Y + 1);
        printClock(tft, "BLACK ", model.blackSeconds());
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(8, FOOTER_Y + 17);
        tft.print("UPDATES EVERY FEW SECONDS");
    }

    private void drawMenuButton(TftTouchShield tft) {
        tft.fillRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.GRAY);
        tft.drawRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        tft.setCursor(MENU_X + (MENU_WIDTH - 4 * TftTouchShield.CHAR_WIDTH) / 2, MENU_Y + 11);
        tft.print("MENU");
    }

    private void printClock(TftTouchShield tft, String label, int seconds) {
        tft.print(label);
        tft.print(seconds / 60);
        tft.print(":");
        if (seconds % 60 < 10) {
            tft.print("0");
        }
        tft.print(seconds % 60);
    }

    private void drawSquare(TftTouchShield tft, int square, int piece, boolean lastMove) {
        int row = square >> 3;
        int column = square & 7;
        boolean light = ((row + column) & 1) == 0;
        int color = light ? LIGHT : DARK;
        if (lastMove) {
            color = light ? LIGHT_LAST_MOVE : DARK_LAST_MOVE;
        }
        int displayRow = model.orientation() > 0 ? row : 7 - row;
        int displayColumn = model.orientation() > 0 ? column : 7 - column;
        int x = BOARD_X + displayColumn * CELL;
        int y = BOARD_Y + displayRow * CELL;
        tft.fillRect(x, y, CELL, CELL, color);
        if (piece != PuzzleBoard.EMPTY) {
            ChessPieceRenderer.draw(tft, x + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2,
                    y + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2, piece, color);
        }
        if (square == 0) {
            tft.drawRect(BOARD_X - 2, BOARD_Y - 2, BOARD_SIZE + 4, BOARD_SIZE + 4,
                    TftTouchShield.ORANGE);
        }
    }

    private boolean isMenuTouch(int x, int y) {
        return x >= MENU_X - 12 && x < 240 && y >= 0 && y < 36;
    }
}
