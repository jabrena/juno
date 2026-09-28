package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Minesweeper for the ELEGOO 2.8" TFT touch screen shield: a 10x11 field with 16 mines.
 *
 * <ul>
 *   <li>Tap a covered cell to open it. The first tap is always safe: mines are placed only
 *       afterwards, away from it, and its timing seeds the random generator.</li>
 *   <li>Press and hold a covered cell to plant or remove a flag.</li>
 *   <li>Tap an opened number whose neighbouring flags match it to open the rest of its
 *       neighbours.</li>
 *   <li>Tap the face button in the header to start a new game.</li>
 * </ul>
 *
 * <p>The header shows the mines left to flag (mines minus flags) and the elapsed seconds.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Minesweeper {
    private static final int COLUMNS = 10;
    private static final int ROWS = 11;
    private static final int CELLS = COLUMNS * ROWS;
    private static final int MINES = 16;

    private static final int CELL = 24;
    private static final int GRID_Y = 40;
    private static final int HEADER_HEIGHT = 36;
    private static final int BUTTON_X = 100;
    private static final int BUTTON_SIZE = 40;
    private static final int FOOTER_Y = GRID_Y + ROWS * CELL + 4;

    private static final int LONG_PRESS_MILLIS = 450;
    private static final int RELEASE_SAMPLES = 3;

    // Cell states.
    private static final int COVERED = 0;
    private static final int OPEN = 1;
    private static final int FLAGGED = 2;

    // Game states.
    private static final int READY = 0;
    private static final int PLAYING = 1;
    private static final int WON = 2;
    private static final int LOST = 3;

    private static final int TILE = 0xBDF7;
    private static final int TILE_LIGHT = TftTouchShield.WHITE;
    private static final int TILE_SHADOW = 0x7BEF;
    private static final int OPEN_BACKGROUND = 0xDEFB;
    private static final int OPEN_BORDER = 0x9CD3;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int COUNTER_RED = 0xF800;
    private static final int MAROON = 0x7800;
    private static final int TEAL = 0x0410;

    private static int gameState;
    private static int flags;
    private static int opened;
    private static int startMillis;
    private static int shownSeconds;

    private Minesweeper() {
    }

    public static void main(String[] args) {
        byte[] mine = new byte[CELLS];
        byte[] count = new byte[CELLS];
        byte[] state = new byte[CELLS];
        int[] pending = new int[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(mine, count, state);

        while (true) {
            if (gameState == PLAYING) {
                drawTimer();
            }
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }

            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            int pressedAt = Clock.millis();
            boolean longPressHandled = false;
            int misses = 0;
            while (misses < RELEASE_SAMPLES) {
                if (TftTouchShield.readTouch()) {
                    misses = 0;
                } else {
                    misses = misses + 1;
                }
                if (!longPressHandled && Clock.millis() - pressedAt >= LONG_PRESS_MILLIS) {
                    longPressHandled = true;
                    longPress(x, y, state);
                }
                Delay.millis(10);
            }
            if (!longPressHandled) {
                tap(x, y, mine, count, state, pending);
            }
        }
    }

    // ---- Input ----

    private static void tap(int x, int y, byte[] mine, byte[] count, byte[] state, int[] pending) {
        if (y < HEADER_HEIGHT) {
            if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE) {
                newGame(mine, count, state);
            }
            return;
        }
        int cell = cellAt(x, y);
        if (cell < 0) {
            return;
        }
        if (gameState == WON || gameState == LOST) {
            newGame(mine, count, state);
            return;
        }
        if (gameState == READY) {
            placeMines(cell, mine, count);
            gameState = PLAYING;
            startMillis = Clock.millis();
            shownSeconds = -1;
        }
        if (state[cell] == COVERED) {
            open(cell, mine, count, state, pending);
        } else if (state[cell] == OPEN && count[cell] > 0 && flagsAround(cell, state) == count[cell]) {
            openNeighbours(cell, mine, count, state, pending);
        }
        if (gameState == PLAYING && opened == CELLS - MINES) {
            win(mine, state);
        }
    }

    private static void longPress(int x, int y, byte[] state) {
        int cell = cellAt(x, y);
        if (cell < 0 || gameState == WON || gameState == LOST) {
            return;
        }
        if (state[cell] == COVERED) {
            state[cell] = FLAGGED;
            flags = flags + 1;
        } else if (state[cell] == FLAGGED) {
            state[cell] = COVERED;
            flags = flags - 1;
        } else {
            return;
        }
        drawCell(cell, 0, 0, state, false);
        drawMinesLeft();
    }

    private static int cellAt(int x, int y) {
        int column = x / CELL;
        int row = (y - GRID_Y) / CELL;
        if (y < GRID_Y || column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return -1;
        }
        return row * COLUMNS + column;
    }

    // ---- Game logic ----

    private static void newGame(byte[] mine, byte[] count, byte[] state) {
        for (int i = 0; i < CELLS; i++) {
            mine[i] = 0;
            count[i] = 0;
            state[i] = COVERED;
        }
        gameState = READY;
        flags = 0;
        opened = 0;
        shownSeconds = -1;

        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        drawFace();
        drawMinesLeft();
        drawSeconds(0);
        for (int i = 0; i < CELLS; i++) {
            drawCell(i, 0, 0, state, false);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(12, FOOTER_Y + 4);
        TftTouchShield.print("Tap: open   Hold: flag   Face: new");
    }

    // Places MINES mines uniformly at random, keeping the first tapped cell and its neighbours clear.
    private static void placeMines(int safeCell, byte[] mine, byte[] count) {
        Random.seed(Clock.micros());
        int placed = 0;
        while (placed < MINES) {
            int cell = Random.nextInt(CELLS);
            if (mine[cell] == 0 && !isNeighbourOrSelf(cell, safeCell)) {
                mine[cell] = 1;
                placed = placed + 1;
            }
        }
        for (int cell = 0; cell < CELLS; cell++) {
            int row = cell / COLUMNS;
            int column = cell % COLUMNS;
            int mines = 0;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int r = row + dr;
                    int c = column + dc;
                    if (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && mine[r * COLUMNS + c] != 0) {
                        mines = mines + 1;
                    }
                }
            }
            count[cell] = (byte) mines;
        }
    }

    private static boolean isNeighbourOrSelf(int a, int b) {
        int rowDistance = a / COLUMNS - b / COLUMNS;
        int columnDistance = a % COLUMNS - b % COLUMNS;
        return rowDistance >= -1 && rowDistance <= 1 && columnDistance >= -1 && columnDistance <= 1;
    }

    // Opens one covered cell; an empty cell flood-fills its region with an explicit stack.
    private static void open(int start, byte[] mine, byte[] count, byte[] state, int[] pending) {
        if (state[start] != COVERED) {
            return;
        }
        if (mine[start] != 0) {
            lose(start, mine, state);
            return;
        }
        int size = 0;
        state[start] = OPEN;
        opened = opened + 1;
        pending[size] = start;
        size = size + 1;
        while (size > 0) {
            size = size - 1;
            int cell = pending[size];
            drawCell(cell, mine[cell], count[cell], state, false);
            if (count[cell] != 0) {
                continue;
            }
            int row = cell / COLUMNS;
            int column = cell % COLUMNS;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int r = row + dr;
                    int c = column + dc;
                    if (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS) {
                        int neighbour = r * COLUMNS + c;
                        if (state[neighbour] == COVERED) {
                            state[neighbour] = OPEN;
                            opened = opened + 1;
                            pending[size] = neighbour;
                            size = size + 1;
                        }
                    }
                }
            }
        }
    }

    private static void openNeighbours(int cell, byte[] mine, byte[] count, byte[] state, int[] pending) {
        int row = cell / COLUMNS;
        int column = cell % COLUMNS;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr;
                int c = column + dc;
                if (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && gameState == PLAYING) {
                    open(r * COLUMNS + c, mine, count, state, pending);
                }
            }
        }
    }

    private static int flagsAround(int cell, byte[] state) {
        int row = cell / COLUMNS;
        int column = cell % COLUMNS;
        int flagged = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr;
                int c = column + dc;
                if (r >= 0 && r < ROWS && c >= 0 && c < COLUMNS && state[r * COLUMNS + c] == FLAGGED) {
                    flagged = flagged + 1;
                }
            }
        }
        return flagged;
    }

    private static void lose(int exploded, byte[] mine, byte[] state) {
        gameState = LOST;
        for (int cell = 0; cell < CELLS; cell++) {
            if (mine[cell] != 0 && state[cell] != FLAGGED) {
                state[cell] = OPEN;
                drawCell(cell, 1, 0, state, cell == exploded);
            } else if (mine[cell] == 0 && state[cell] == FLAGGED) {
                drawWrongFlag(cell);
            }
        }
        drawFace();
    }

    private static void win(byte[] mine, byte[] state) {
        gameState = WON;
        for (int cell = 0; cell < CELLS; cell++) {
            if (mine[cell] != 0 && state[cell] != FLAGGED) {
                state[cell] = FLAGGED;
                drawCell(cell, 0, 0, state, false);
            }
        }
        flags = MINES;
        drawMinesLeft();
        drawFace();
    }

    // ---- Drawing ----

    private static void drawCell(int cell, int isMine, int mines, byte[] state, boolean exploded) {
        int x = (cell % COLUMNS) * CELL;
        int y = GRID_Y + (cell / COLUMNS) * CELL;
        if (state[cell] != OPEN) {
            TftTouchShield.fillRect(x, y, CELL, CELL, TILE);
            TftTouchShield.drawHorizontalLine(x, y, CELL, TILE_LIGHT);
            TftTouchShield.drawVerticalLine(x, y, CELL, TILE_LIGHT);
            TftTouchShield.drawHorizontalLine(x, y + CELL - 1, CELL, TILE_SHADOW);
            TftTouchShield.drawVerticalLine(x + CELL - 1, y, CELL, TILE_SHADOW);
            if (state[cell] == FLAGGED) {
                drawFlag(x, y);
            }
            return;
        }
        int background = OPEN_BACKGROUND;
        if (exploded) {
            background = TftTouchShield.RED;
        }
        TftTouchShield.fillRect(x, y, CELL, CELL, background);
        TftTouchShield.drawRect(x, y, CELL, CELL, OPEN_BORDER);
        if (isMine != 0) {
            drawMine(x, y);
        } else if (mines > 0) {
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(numberColor(mines), background);
            TftTouchShield.setCursor(x + 6, y + 4);
            TftTouchShield.print(mines);
        }
    }

    private static void drawFlag(int x, int y) {
        TftTouchShield.fillRect(x + 11, y + 5, 2, 14, TftTouchShield.BLACK);
        TftTouchShield.fillRect(x + 6, y + 17, 12, 2, TftTouchShield.BLACK);
        TftTouchShield.fillRect(x + 5, y + 5, 6, 7, TftTouchShield.RED);
    }

    private static void drawMine(int x, int y) {
        int cx = x + CELL / 2;
        int cy = y + CELL / 2;
        TftTouchShield.drawHorizontalLine(cx - 8, cy, 17, TftTouchShield.BLACK);
        TftTouchShield.drawVerticalLine(cx, cy - 8, 17, TftTouchShield.BLACK);
        TftTouchShield.fillCircle(cx, cy, 6, TftTouchShield.BLACK);
        TftTouchShield.fillRect(cx - 3, cy - 3, 2, 2, TftTouchShield.WHITE);
    }

    private static void drawWrongFlag(int cell) {
        int x = (cell % COLUMNS) * CELL;
        int y = GRID_Y + (cell / COLUMNS) * CELL;
        for (int i = 4; i < CELL - 4; i++) {
            TftTouchShield.fillRect(x + i, y + i, 2, 2, TftTouchShield.RED);
            TftTouchShield.fillRect(x + CELL - 2 - i, y + i, 2, 2, TftTouchShield.RED);
        }
    }

    private static int numberColor(int mines) {
        if (mines == 1) {
            return TftTouchShield.BLUE;
        }
        if (mines == 2) {
            return 0x03E0;
        }
        if (mines == 3) {
            return TftTouchShield.RED;
        }
        if (mines == 4) {
            return TftTouchShield.NAVY;
        }
        if (mines == 5) {
            return MAROON;
        }
        if (mines == 6) {
            return TEAL;
        }
        if (mines == 7) {
            return TftTouchShield.BLACK;
        }
        return TftTouchShield.GRAY;
    }

    // A yellow face button: smiling while playing, sunglasses on a win, crosses on a loss.
    private static void drawFace() {
        int x = BUTTON_X;
        int cx = x + BUTTON_SIZE / 2;
        int cy = HEADER_HEIGHT / 2;
        TftTouchShield.fillRect(x + 2, 2, BUTTON_SIZE - 4, HEADER_HEIGHT - 4, TILE);
        TftTouchShield.drawRect(x + 2, 2, BUTTON_SIZE - 4, HEADER_HEIGHT - 4, TILE_SHADOW);
        TftTouchShield.fillCircle(cx, cy, 13, TftTouchShield.YELLOW);
        TftTouchShield.drawCircle(cx, cy, 13, TftTouchShield.BLACK);
        if (gameState == LOST) {
            for (int i = -2; i <= 2; i++) {
                TftTouchShield.drawPixel(cx - 5 + i, cy - 4 + i, TftTouchShield.BLACK);
                TftTouchShield.drawPixel(cx - 5 + i, cy - 4 - i, TftTouchShield.BLACK);
                TftTouchShield.drawPixel(cx + 5 + i, cy - 4 + i, TftTouchShield.BLACK);
                TftTouchShield.drawPixel(cx + 5 + i, cy - 4 - i, TftTouchShield.BLACK);
            }
            TftTouchShield.drawHorizontalLine(cx - 5, cy + 6, 11, TftTouchShield.BLACK);
            TftTouchShield.drawHorizontalLine(cx - 6, cy + 7, 2, TftTouchShield.BLACK);
            TftTouchShield.drawHorizontalLine(cx + 5, cy + 7, 2, TftTouchShield.BLACK);
            return;
        }
        if (gameState == WON) {
            TftTouchShield.fillRect(cx - 10, cy - 6, 8, 5, TftTouchShield.BLACK);
            TftTouchShield.fillRect(cx + 2, cy - 6, 8, 5, TftTouchShield.BLACK);
            TftTouchShield.drawHorizontalLine(cx - 12, cy - 6, 25, TftTouchShield.BLACK);
        } else {
            TftTouchShield.fillRect(cx - 6, cy - 5, 3, 3, TftTouchShield.BLACK);
            TftTouchShield.fillRect(cx + 4, cy - 5, 3, 3, TftTouchShield.BLACK);
        }
        TftTouchShield.drawHorizontalLine(cx - 5, cy + 7, 11, TftTouchShield.BLACK);
        TftTouchShield.drawHorizontalLine(cx - 6, cy + 6, 2, TftTouchShield.BLACK);
        TftTouchShield.drawHorizontalLine(cx + 5, cy + 6, 2, TftTouchShield.BLACK);
    }

    private static void drawMinesLeft() {
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(COUNTER_RED, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 6);
        printPadded(MINES - flags);
    }

    private static void drawTimer() {
        int seconds = (Clock.millis() - startMillis) / 1000;
        if (seconds != shownSeconds) {
            drawSeconds(seconds);
            shownSeconds = seconds;
        }
    }

    private static void drawSeconds(int seconds) {
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(COUNTER_RED, HEADER_BACKGROUND);
        TftTouchShield.setCursor(TftTouchShield.width() - 8 - 3 * 18, 6);
        printPadded(Math.min(seconds, 999));
    }

    // Prints a value in a fixed three-character field, zero-padded like a classic counter.
    private static void printPadded(int value) {
        if (value < 0) {
            TftTouchShield.print("-");
            if (value > -10) {
                TftTouchShield.print("0");
            }
            TftTouchShield.print(-value);
            return;
        }
        if (value < 100) {
            TftTouchShield.print("0");
        }
        if (value < 10) {
            TftTouchShield.print("0");
        }
        TftTouchShield.print(value);
    }
}
