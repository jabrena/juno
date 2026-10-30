package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.games.chess.ChessPieceRenderer;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Draws the puzzle position with the same sprite and repaint strategy as the chess game. */
final class LichessBoardView {
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int CELL = 28;
    private static final int BOARD_SIZE = CELL * 8;
    private static final int FOOTER_Y = BOARD_Y + BOARD_SIZE + 8;
    private static final int MENU_X = 172;
    private static final int MENU_Y = 3;
    private static final int MENU_WIDTH = 60;
    private static final int MENU_HEIGHT = 30;
    private static final int FRESH_HINT_X = 168;
    private static final int LIGHT = 0xF6D6;
    private static final int DARK = 0xB44C;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int LIGHT_PLAYER_MOVE = 0xF7B0;
    private static final int DARK_PLAYER_MOVE = 0xBE48;
    private static final int LIGHT_REPLY_MOVE = 0xB7D7;
    private static final int DARK_REPLY_MOVE = 0x5D73;
    private static final int SELECTED = 0x07E0;
    private static final int TARGET = 0x07E0;
    private static final int CAPTURE_TARGET = 0xF800;
    private static final int DECO_PLAYER_MOVE = 1;
    private static final int DECO_REPLY_MOVE = 2;
    private static final int DECO_SELECTED = 4;
    private static final int DECO_TARGET = 8;

    private final LichessModel model;
    private final byte[] shownPiece = new byte[64];
    private final byte[] shownDeco = new byte[64];
    private final byte[] deco = new byte[64];
    private final int[] legalTargets = new int[32];
    private boolean initialized;

    LichessBoardView(LichessModel model) {
        this.model = model;
    }

    void reset() {
        initialized = false;
    }

    void draw(int selected, int feedback) {
        boolean fullRedraw = !initialized;
        if (!initialized) {
            TftTouchShield.fillScreen(TftTouchShield.BLACK);
            drawHeader();
            initialized = true;
        }
        prepareDecorations(selected);
        for (int square = 0; square < 64; square++) {
            if (fullRedraw || model.board().pieceAt(square) != shownPiece[square] || deco[square] != shownDeco[square]) {
                drawSquare(square, model.board().pieceAt(square), deco[square]);
                shownPiece[square] = (byte) model.board().pieceAt(square);
                shownDeco[square] = deco[square];
            }
        }
        drawFooter(feedback);
    }

    int squareAt(int x, int y) {
        if (x < BOARD_X || x >= BOARD_X + BOARD_SIZE || y < BOARD_Y || y >= BOARD_Y + BOARD_SIZE) {
            return -1;
        }
        int row = (y - BOARD_Y) / CELL;
        int column = (x - BOARD_X) / CELL;
        return model.playerColor() > 0 ? row * 8 + column : (7 - row) * 8 + (7 - column);
    }

    boolean isMenuTouch(int x, int y) {
        return x >= MENU_X - 12 && x < 240 && y >= 0 && y < 36;
    }

    private void prepareDecorations(int selected) {
        for (int square = 0; square < 64; square++) {
            deco[square] = 0;
        }
        markMove(model.lastFrom(), model.lastTo(), DECO_PLAYER_MOVE);
        markMove(model.replyFrom(), model.replyTo(), DECO_REPLY_MOVE);
        if (selected >= 0) {
            deco[selected] = (byte) (deco[selected] | DECO_SELECTED);
            int count = model.board().legalDestinations(selected, legalTargets);
            for (int index = 0; index < count; index++) {
                deco[legalTargets[index]] = (byte) (deco[legalTargets[index]] | DECO_TARGET);
            }
        }
    }

    private void markMove(int from, int to, int decoration) {
        if (from >= 0 && to >= 0) {
            deco[from] = (byte) (deco[from] | decoration);
            deco[to] = (byte) (deco[to] | decoration);
        }
    }

    private void drawHeader() {
        TftTouchShield.fillRect(0, 0, 240, 36, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 2);
        TftTouchShield.print("LICHESS");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, 17);
        TftTouchShield.print(model.source() == LichessModel.DAILY ? "DAILY PUZZLE" : "FRESH PUZZLE");
        drawMenuButton();
    }

    private void drawFooter(int feedback) {
        TftTouchShield.fillRect(0, FOOTER_Y, 240, 320 - FOOTER_Y, TftTouchShield.BLACK);
        if (model.source() == LichessModel.FRESH) {
            drawFreshFooter(feedback);
            return;
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(8, FOOTER_Y + 1);
        TftTouchShield.print("LEFT: ");
        TftTouchShield.print(model.remainingPlayerMoves());
        TftTouchShield.setCursor(132, FOOTER_Y + 1);
        TftTouchShield.print("RATING: ");
        TftTouchShield.print(model.rating());
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(8, FOOTER_Y + 12);
        TftTouchShield.print("THEME: ");
        if (model.themeLength() > 0) {
            TftTouchShield.print(model.theme(), model.themeLength());
        } else {
            TftTouchShield.print("TACTICS");
        }
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, FOOTER_Y + 26);
        TftTouchShield.setTextColor(feedback == LichessModel.MOVE_WRONG ? TftTouchShield.RED : TftTouchShield.GREEN,
                TftTouchShield.BLACK);
        if (feedback == LichessModel.MOVE_SOLVED) {
            TftTouchShield.print("SOLVED!");
        } else if (feedback == LichessModel.MOVE_WRONG) {
            TftTouchShield.print("TRY AGAIN");
        } else {
            TftTouchShield.print("YOUR MOVE");
        }
        if (feedback == LichessModel.MOVE_WRONG && model.hintFrom() >= 0 && model.hintTo() >= 0) {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
            TftTouchShield.setCursor(112, FOOTER_Y + 27);
            TftTouchShield.print("HINT: ");
            printSquare(model.hintFrom());
            TftTouchShield.print("-");
            printSquare(model.hintTo());
        }
    }

    private void drawFreshFooter(int feedback) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(8, FOOTER_Y + 1);
        TftTouchShield.print("RATING: ");
        TftTouchShield.print(model.rating());
        TftTouchShield.setCursor(8, FOOTER_Y + 12);
        TftTouchShield.print("LEFT: ");
        TftTouchShield.print(model.remainingPlayerMoves());

        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, FOOTER_Y + 26);
        TftTouchShield.setTextColor(feedback == LichessModel.MOVE_WRONG ? TftTouchShield.RED : TftTouchShield.GREEN,
                TftTouchShield.BLACK);
        if (feedback == LichessModel.MOVE_SOLVED) {
            TftTouchShield.print("SOLVED!");
        } else if (feedback == LichessModel.MOVE_WRONG) {
            TftTouchShield.print("TRY AGAIN");
        } else {
            TftTouchShield.print("YOUR MOVE");
        }
        if (feedback == LichessModel.MOVE_WRONG && model.hintFrom() >= 0 && model.hintTo() >= 0) {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
            TftTouchShield.setCursor(FRESH_HINT_X, FOOTER_Y + 27);
            TftTouchShield.print("HINT: ");
            printSquare(model.hintFrom());
            TftTouchShield.print("-");
            printSquare(model.hintTo());
        }
    }

    private void drawMenuButton() {
        TftTouchShield.fillRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.drawRect(MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(MENU_X + (MENU_WIDTH - 4 * TftTouchShield.CHAR_WIDTH) / 2, MENU_Y + 11);
        TftTouchShield.print("MENU");
    }

    private void printSquare(int square) {
        TftTouchShield.print(fileName(square & 7));
        TftTouchShield.print(8 - (square >> 3));
    }

    private static String fileName(int column) {
        return switch (column) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            case 3 -> "D";
            case 4 -> "E";
            case 5 -> "F";
            case 6 -> "G";
            default -> "H";
        };
    }

    private void drawSquare(int square, int piece, int decoration) {
        int row = square >> 3;
        int column = square & 7;
        boolean light = ((row + column) & 1) == 0;
        int color = light ? LIGHT : DARK;
        if ((decoration & DECO_PLAYER_MOVE) != 0) {
            color = light ? LIGHT_PLAYER_MOVE : DARK_PLAYER_MOVE;
        } else if ((decoration & DECO_REPLY_MOVE) != 0) {
            color = light ? LIGHT_REPLY_MOVE : DARK_REPLY_MOVE;
        }
        int displayRow = model.playerColor() > 0 ? row : 7 - row;
        int displayColumn = model.playerColor() > 0 ? column : 7 - column;
        int x = BOARD_X + displayColumn * CELL;
        int y = BOARD_Y + displayRow * CELL;
        TftTouchShield.fillRect(x, y, CELL, CELL, color);
        if ((decoration & DECO_SELECTED) != 0) {
            TftTouchShield.drawRect(x, y, CELL, CELL, SELECTED);
            TftTouchShield.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, SELECTED);
        }
        if (piece != PuzzleBoard.EMPTY) {
            ChessPieceRenderer.draw(x + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2,
                    y + (CELL - ChessPieceRenderer.PIECE_SIZE) / 2, piece, color);
        }
        if ((decoration & DECO_TARGET) != 0) {
            if (piece == PuzzleBoard.EMPTY) {
                TftTouchShield.fillCircle(x + CELL / 2, y + CELL / 2, 4, TARGET);
            } else {
                TftTouchShield.drawRect(x, y, CELL, CELL, CAPTURE_TARGET);
                TftTouchShield.drawRect(x + 1, y + 1, CELL - 2, CELL - 2, CAPTURE_TARGET);
            }
        }
        if (square == 0) {
            TftTouchShield.drawRect(BOARD_X - 2, BOARD_Y - 2, BOARD_SIZE + 4, BOARD_SIZE + 4,
                    TftTouchShield.ORANGE);
        }
    }
}
