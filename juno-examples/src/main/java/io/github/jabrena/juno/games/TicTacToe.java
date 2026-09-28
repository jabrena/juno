package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * "Vanishing" tic-tac-toe for the ELEGOO 2.8" TFT touch screen shield, in the style of the
 * handheld electronic versions: each player owns at most three marks, and placing a fourth removes
 * that player's oldest one. The mark that will vanish next for the player to move is drawn dimmed.
 * Because marks keep moving, there are no draws: play continues until someone gets three in a row.
 *
 * <p>You play X (red crosses) against the computer's O (dashed blue rings); tap {@code 1P} to
 * switch to two players sharing the screen ({@code 2P}) and back. Tap an empty cell to play, and
 * {@code NEW} (or the board, once a round is won) to start a new round. The footer keeps the score.
 *
 * <p>The computer runs a {@value #SEARCH_DEPTH}-ply negamax search with alpha-beta pruning. The
 * whole position fits in two ints — each player's marks as a queue of up to three cell indices,
 * oldest first — so the recursive search needs no arrays.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class TicTacToe {
    private static final int SEARCH_DEPTH = 6;
    private static final int WIN_SCORE = 100;

    private static final int X = 0;
    private static final int O = 1;

    // The 8 winning lines as 9-bit cell masks (bit n = cell n, row-major from the top left).
    private static final int LINE_0 = 0x007;
    private static final int LINE_1 = 0x038;
    private static final int LINE_2 = 0x1C0;
    private static final int LINE_3 = 0x049;
    private static final int LINE_4 = 0x092;
    private static final int LINE_5 = 0x124;
    private static final int LINE_6 = 0x111;
    private static final int LINE_7 = 0x054;

    // Screen layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 40;
    private static final int GRID_X = 12;
    private static final int GRID_Y = 56;
    private static final int CELL = 72;
    private static final int GRID_SIZE = 3 * CELL;
    private static final int FOOTER_Y = GRID_Y + GRID_SIZE + 12;
    private static final int BUTTON_HEIGHT = 30;
    private static final int MODE_X = 104;
    private static final int NEW_X = 172;
    private static final int BUTTON_WIDTH = 60;

    private static final int X_COLOR = TftTouchShield.RED;
    private static final int O_COLOR = 0x4D9F;
    private static final int X_DIM = 0x5000;
    private static final int O_DIM = 0x1929;
    private static final int WIN_COLOR = TftTouchShield.YELLOW;
    private static final int HEADER_BACKGROUND = 0x2945;

    // What a cell shows, packed 3 bits per cell into shownCells to redraw only changes.
    private static final int SHOW_EMPTY = 0;
    private static final int SHOW_X = 1;
    private static final int SHOW_O = 2;
    private static final int SHOW_X_DIM = 3;
    private static final int SHOW_O_DIM = 4;
    private static final int SHOW_X_WIN = 5;
    private static final int SHOW_O_WIN = 6;

    // Each player's marks: cell indices in bits 0-3 (oldest), 4-7 and 8-11, count in bits 12-13.
    private static int xMarks;
    private static int oMarks;
    private static int turn;
    private static int winLine;
    private static boolean twoPlayers;
    private static int xScore;
    private static int oScore;
    private static int shownCells;
    private static int bestCell;

    private TicTacToe() {
    }

    public static void main(String[] args) {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        drawScreen();
        newRound();

        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            waitForRelease();

            if (y >= FOOTER_Y && x >= NEW_X) {
                newRound();
                continue;
            }
            if (y >= FOOTER_Y && x >= MODE_X && x < MODE_X + BUTTON_WIDTH) {
                twoPlayers = !twoPlayers;
                xScore = 0;
                oScore = 0;
                drawButtons();
                newRound();
                continue;
            }
            int cell = cellAt(x, y);
            if (cell < 0) {
                continue;
            }
            if (winLine != 0) {
                newRound();
                continue;
            }
            if (((occupied() >> cell) & 1) != 0) {
                continue;
            }

            play(cell);
            if (winLine == 0 && !twoPlayers) {
                showStatus("Thinking...", TftTouchShield.YELLOW);
                Delay.millis(300);
                play(computerMove());
            }
        }
    }

    // ---- Game flow ----

    private static void newRound() {
        xMarks = 0;
        oMarks = 0;
        turn = X;
        winLine = 0;
        refresh();
        showTurn();
    }

    private static void play(int cell) {
        if (turn == X) {
            xMarks = place(xMarks, cell);
            winLine = winningLine(cells(xMarks));
        } else {
            oMarks = place(oMarks, cell);
            winLine = winningLine(cells(oMarks));
        }
        if (winLine != 0) {
            if (turn == X) {
                xScore = xScore + 1;
            } else {
                oScore = oScore + 1;
            }
            refresh();
            showWinner();
            drawScore();
            return;
        }
        if (turn == X) {
            turn = O;
        } else {
            turn = X;
        }
        refresh();
        showTurn();
    }

    private static int occupied() {
        return cells(xMarks) | cells(oMarks);
    }

    // ---- Marks ----

    private static int count(int marks) {
        return marks >> 12;
    }

    private static int cellOf(int marks, int index) {
        return (marks >> (4 * index)) & 15;
    }

    /** Adds {@code cell} as the newest mark, dropping the oldest when three are already placed. */
    private static int place(int marks, int cell) {
        int count = count(marks);
        if (count == 3) {
            return ((marks >> 4) & 0xFF) | (cell << 8) | (3 << 12);
        }
        return (marks & 0xFFF) | (cell << (4 * count)) | ((count + 1) << 12);
    }

    /** The cell that will vanish when this player places another mark, or -1. */
    private static int nextToVanish(int marks) {
        if (count(marks) == 3) {
            return marks & 15;
        }
        return -1;
    }

    private static int cells(int marks) {
        int mask = 0;
        int count = count(marks);
        for (int i = 0; i < count; i++) {
            mask = mask | (1 << cellOf(marks, i));
        }
        return mask;
    }

    private static int winningLine(int mask) {
        if ((mask & LINE_0) == LINE_0) {
            return LINE_0;
        }
        if ((mask & LINE_1) == LINE_1) {
            return LINE_1;
        }
        if ((mask & LINE_2) == LINE_2) {
            return LINE_2;
        }
        if ((mask & LINE_3) == LINE_3) {
            return LINE_3;
        }
        if ((mask & LINE_4) == LINE_4) {
            return LINE_4;
        }
        if ((mask & LINE_5) == LINE_5) {
            return LINE_5;
        }
        if ((mask & LINE_6) == LINE_6) {
            return LINE_6;
        }
        if ((mask & LINE_7) == LINE_7) {
            return LINE_7;
        }
        return 0;
    }

    // ---- Computer ----

    private static int computerMove() {
        Random.seed(Clock.micros());
        bestCell = -1;
        negamax(oMarks, xMarks, SEARCH_DEPTH, true, -1000, 1000);
        return bestCell;
    }

    /**
     * Scores the position for {@code me}, who is to move, looking {@code depth} plies ahead. A win
     * found sooner scores higher. At the root, cells are tried from a random starting point so equal
     * moves vary between games, and the best one is kept in {@link #bestCell}.
     */
    private static int negamax(int me, int opponent, int depth, boolean root, int alpha, int beta) {
        int taken = cells(me) | cells(opponent);
        int start = 0;
        if (root) {
            start = Random.nextInt(9);
        }
        for (int i = 0; i < 9; i++) {
            int cell = (start + i) % 9;
            if (((taken >> cell) & 1) != 0) {
                continue;
            }
            int next = place(me, cell);
            int score;
            if (winningLine(cells(next)) != 0) {
                score = WIN_SCORE + depth;
            } else if (depth <= 1) {
                score = evaluate(next, opponent);
            } else {
                score = -negamax(opponent, next, depth - 1, false, -beta, -alpha);
            }
            if (score > alpha) {
                alpha = score;
                if (root) {
                    bestCell = cell;
                }
            }
            if (alpha >= beta) {
                return alpha;
            }
        }
        return alpha;
    }

    /** Open two-in-a-rows for {@code me} minus those for {@code opponent}. */
    private static int evaluate(int me, int opponent) {
        int mine = cells(me);
        int theirs = cells(opponent);
        return openTwos(mine, theirs) - openTwos(theirs, mine);
    }

    private static int openTwos(int mine, int theirs) {
        return openTwo(mine, theirs, LINE_0) + openTwo(mine, theirs, LINE_1) + openTwo(mine, theirs, LINE_2)
                + openTwo(mine, theirs, LINE_3) + openTwo(mine, theirs, LINE_4) + openTwo(mine, theirs, LINE_5)
                + openTwo(mine, theirs, LINE_6) + openTwo(mine, theirs, LINE_7);
    }

    private static int openTwo(int mine, int theirs, int line) {
        if ((theirs & line) != 0) {
            return 0;
        }
        int hits = mine & line;
        // Clearing the lowest set bit twice leaves zero exactly when two bits were set.
        int rest = hits & (hits - 1);
        if (hits != 0 && rest != 0 && (rest & (rest - 1)) == 0) {
            return 1;
        }
        return 0;
    }

    // ---- Input ----

    private static int cellAt(int x, int y) {
        if (x < GRID_X || y < GRID_Y || x >= GRID_X + GRID_SIZE || y >= GRID_Y + GRID_SIZE) {
            return -1;
        }
        return ((y - GRID_Y) / CELL) * 3 + (x - GRID_X) / CELL;
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Drawing ----

    private static void drawScreen() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        for (int k = 1; k < 3; k++) {
            TftTouchShield.fillRect(GRID_X + k * CELL - 2, GRID_Y, 4, GRID_SIZE, TftTouchShield.WHITE);
            TftTouchShield.fillRect(GRID_X, GRID_Y + k * CELL - 2, GRID_SIZE, 4, TftTouchShield.WHITE);
        }
        drawButtons();
        drawScore();
    }

    private static void drawButtons() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.fillRect(MODE_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(MODE_X + 18, FOOTER_Y + 8);
        if (twoPlayers) {
            TftTouchShield.print("2P");
        } else {
            TftTouchShield.print("1P");
        }
        TftTouchShield.fillRect(NEW_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setCursor(NEW_X + 12, FOOTER_Y + 8);
        TftTouchShield.print("NEW");
    }

    private static void drawScore() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(GRID_X, FOOTER_Y + 8);
        TftTouchShield.setTextColor(X_COLOR, TftTouchShield.BLACK);
        TftTouchShield.print("X");
        TftTouchShield.print(xScore);
        TftTouchShield.print(" ");
        TftTouchShield.setTextColor(O_COLOR, TftTouchShield.BLACK);
        TftTouchShield.print("O");
        TftTouchShield.print(oScore);
    }

    private static void showTurn() {
        if (twoPlayers) {
            if (turn == X) {
                showStatus("X to play", X_COLOR);
            } else {
                showStatus("O to play", O_COLOR);
            }
        } else {
            showStatus("Your turn", TftTouchShield.WHITE);
        }
    }

    private static void showWinner() {
        if (twoPlayers) {
            if (turn == X) {
                showStatus("X wins!", WIN_COLOR);
            } else {
                showStatus("O wins!", WIN_COLOR);
            }
        } else if (turn == X) {
            showStatus("You win!", WIN_COLOR);
        } else {
            showStatus("Computer wins", WIN_COLOR);
        }
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        // Size 3 fits 12 characters after the margin; longer messages drop to size 2 instead of
        // wrapping onto a second line.
        int size = 3;
        if (12 + text.length() * TftTouchShield.CHAR_WIDTH * size > TftTouchShield.width()) {
            size = 2;
        }
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(12, (HEADER_HEIGHT - TftTouchShield.CHAR_HEIGHT * size) / 2);
        TftTouchShield.print(text);
    }

    /** Redraws every cell whose appearance changed. */
    private static void refresh() {
        int xCells = cells(xMarks);
        int oCells = cells(oMarks);
        int vanishing = -1;
        if (winLine == 0) {
            if (turn == X) {
                vanishing = nextToVanish(xMarks);
            } else {
                vanishing = nextToVanish(oMarks);
            }
        }
        int shown = 0;
        for (int cell = 0; cell < 9; cell++) {
            int look = SHOW_EMPTY;
            boolean winning = ((winLine >> cell) & 1) != 0;
            if (((xCells >> cell) & 1) != 0) {
                look = SHOW_X;
                if (winning) {
                    look = SHOW_X_WIN;
                } else if (cell == vanishing) {
                    look = SHOW_X_DIM;
                }
            } else if (((oCells >> cell) & 1) != 0) {
                look = SHOW_O;
                if (winning) {
                    look = SHOW_O_WIN;
                } else if (cell == vanishing) {
                    look = SHOW_O_DIM;
                }
            }
            if (((shownCells >> (3 * cell)) & 7) != look) {
                drawCell(cell, look);
            }
            shown = shown | (look << (3 * cell));
        }
        shownCells = shown;
    }

    private static void drawCell(int cell, int look) {
        int x = GRID_X + (cell % 3) * CELL;
        int y = GRID_Y + (cell / 3) * CELL;
        TftTouchShield.fillRect(x + 4, y + 4, CELL - 8, CELL - 8, TftTouchShield.BLACK);
        int cx = x + CELL / 2;
        int cy = y + CELL / 2;
        if (look == SHOW_X) {
            drawCross(cx, cy, X_COLOR);
        } else if (look == SHOW_X_DIM) {
            drawCross(cx, cy, X_DIM);
        } else if (look == SHOW_X_WIN) {
            drawCross(cx, cy, WIN_COLOR);
        } else if (look == SHOW_O) {
            drawRing(cx, cy, O_COLOR);
        } else if (look == SHOW_O_DIM) {
            drawRing(cx, cy, O_DIM);
        } else if (look == SHOW_O_WIN) {
            drawRing(cx, cy, WIN_COLOR);
        }
    }

    // A thick X made of small squares along both diagonals.
    private static void drawCross(int cx, int cy, int color) {
        for (int i = -20; i <= 20; i++) {
            TftTouchShield.fillRect(cx + i - 3, cy + i - 3, 7, 7, color);
            TftTouchShield.fillRect(cx + i - 3, cy - i - 3, 7, 7, color);
        }
    }

    // A thick dashed ring: concentric midpoint circles, each drawing only alternate 4-pixel runs of
    // every octant.
    private static void drawRing(int cx, int cy, int color) {
        for (int r = 18; r <= 24; r++) {
            int x = r;
            int y = 0;
            int error = 1 - r;
            while (x >= y) {
                if (((y >> 2) & 1) == 0) {
                    TftTouchShield.drawPixel(cx + x, cy + y, color);
                    TftTouchShield.drawPixel(cx + y, cy + x, color);
                    TftTouchShield.drawPixel(cx - y, cy + x, color);
                    TftTouchShield.drawPixel(cx - x, cy + y, color);
                    TftTouchShield.drawPixel(cx - x, cy - y, color);
                    TftTouchShield.drawPixel(cx - y, cy - x, color);
                    TftTouchShield.drawPixel(cx + y, cy - x, color);
                    TftTouchShield.drawPixel(cx + x, cy - y, color);
                }
                y = y + 1;
                if (error < 0) {
                    error = error + 2 * y + 1;
                } else {
                    x = x - 1;
                    error = error + 2 * (y - x) + 1;
                }
            }
        }
    }
}
