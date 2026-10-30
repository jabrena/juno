package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.games.chess.ChessPieceRenderer;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Read-only board monitor for the current game on Lichess TV. */
final class LichessTvView implements View {
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

    LichessTvView(Navigator navigator, LichessApi api, TvModel model) {
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
            draw();
            drawnVersion = model.version();
        }
    }

    @Override
    public void touch(int x, int y) {
        if (isMenuTouch(x, y)) {
            navigator.show(LichessViews.MENU);
        }
    }

    private void draw() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        drawHeader();
        for (int square = 0; square < 64; square++) {
            int piece = model.board().pieceAt(square);
            boolean lastMove = square == model.lastFrom() || square == model.lastTo();
            drawSquare(square, piece, lastMove);
        }
        drawFooter();
    }

    private void drawHeader() {
        TftTouchShield.fillRect(0, 0, 240, 36, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 2);
        TftTouchShield.print("LICHESS");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, 17);
        TftTouchShield.print("LICHESS TV");
        drawMenuButton();
    }

    private void drawFooter() {
        TftTouchShield.fillRect(0, FOOTER_Y, 240, 320 - FOOTER_Y, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(8, FOOTER_Y + 1);
        printClock("WHITE ", model.whiteSeconds());
        TftTouchShield.setCursor(88, FOOTER_Y + 1);
        printClock("BLACK ", model.blackSeconds());
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(8, FOOTER_Y + 17);
        TftTouchShield.print("UPDATES EVERY FEW SECONDS");
    }

    private void drawMenuButton() {
        TftTouchShield.fillRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.drawRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(MENU_X + (MENU_WIDTH - 4 * TftTouchShield.CHAR_WIDTH) / 2, MENU_Y + 11);
        TftTouchShield.print("MENU");
    }

    private void printClock(String label, int seconds) {
        TftTouchShield.print(label);
        TftTouchShield.print(seconds / 60);
        TftTouchShield.print(":");
        if (seconds % 60 < 10) {
            TftTouchShield.print("0");
        }
        TftTouchShield.print(seconds % 60);
    }

    private void drawSquare(int square, int piece, boolean lastMove) {
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
        TftTouchShield.fillRect(x, y, CELL, CELL, color);
        if (piece != PuzzleBoard.EMPTY) {
            ChessPieceRenderer.draw(x + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2,
                    y + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2, piece, color);
        }
        if (square == 0) {
            TftTouchShield.drawRect(BOARD_X - 2, BOARD_Y - 2, BOARD_SIZE + 4, BOARD_SIZE + 4,
                    TftTouchShield.ORANGE);
        }
    }

    private boolean isMenuTouch(int x, int y) {
        return x >= MENU_X - 12 && x < 240 && y >= 0 && y < 36;
    }
}
