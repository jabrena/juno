package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * 2048 on the ELEGOO 2.8" TFT touch screen shield: swipe to slide every tile on the 4x4 grid;
 * two tiles with the same number merge into their sum. Each move adds a new 2 (or, one time in
 * ten, a 4). Reach 2048 to win — you can keep going — and the game ends when no move is left.
 *
 * <p>A short tap also works: tapping near an edge of the grid slides towards that edge, which
 * helps where a resistive panel reports swipes unreliably. {@code NEW} restarts; the header shows
 * the score and the best score since power-up.
 *
 * <p>Tiles hold exponents (1 = 2, 2 = 4, ...). A move slides each of the four lines towards the
 * chosen edge, reading and writing it through a start index and a step, so one routine handles all
 * four directions.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Game2048 {
    private static final int SIZE = 4;
    private static final int CELLS = SIZE * SIZE;
    private static final int WIN_EXPONENT = 11;
    private static final int SWIPE_MIN = 25;

    // Directions.
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int UP = 2;

    // Layout (portrait, 240x320).
    private static final int HEADER_HEIGHT = 44;
    private static final int TILE = 52;
    private static final int GAP = 4;
    private static final int GRID_X = 6;
    private static final int GRID_Y = 52;
    private static final int GRID_SIZE = SIZE * TILE + (SIZE + 1) * GAP;
    private static final int FOOTER_Y = GRID_Y + GRID_SIZE + 6;
    private static final int NEW_X = 172;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 28;

    private static final int GRID_BACKGROUND = 0xBD74;
    private static final int EMPTY = 0xCE16;
    private static final int DARK_TEXT = 0x736C;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int score;
    private static int best;
    private static boolean won;
    private static boolean over;

    private Game2048() {
    }

    public static void main(String[] args) {
        int[] board = new int[CELLS];
        int[] shown = new int[CELLS];
        int[] line = new int[SIZE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        newGame(board, shown);

        boolean seeded = false;
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int startX = TftTouchShield.touchX();
            int startY = TftTouchShield.touchY();
            int endX = startX;
            int endY = startY;
            int misses = 0;
            while (misses < 3) {
                if (TftTouchShield.readTouch()) {
                    endX = TftTouchShield.touchX();
                    endY = TftTouchShield.touchY();
                    misses = 0;
                } else {
                    misses = misses + 1;
                }
                Delay.millis(10);
            }
            if (!seeded) {
                Random.seed(Clock.micros());
                seeded = true;
            }

            if (startY >= FOOTER_Y && startX >= NEW_X) {
                newGame(board, shown);
                continue;
            }
            if (over) {
                continue;
            }
            int direction = swipeDirection(startX, startY, endX, endY);
            if (direction < 0) {
                continue;
            }
            if (move(board, line, direction)) {
                spawn(board);
                refresh(board, shown);
                drawHeader();
                if (!won && highest(board) >= WIN_EXPONENT) {
                    won = true;
                    showMessage("You win! Keep going", TftTouchShield.YELLOW);
                } else if (!canMove(board)) {
                    over = true;
                    showMessage("Game over", TftTouchShield.RED);
                }
            }
        }
    }

    // ---- Game flow ----

    private static void newGame(int[] board, int[] shown) {
        for (int i = 0; i < CELLS; i++) {
            board[i] = 0;
            shown[i] = -1;
        }
        score = 0;
        won = false;
        over = false;
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(GRID_X, GRID_Y, GRID_SIZE, GRID_SIZE, GRID_BACKGROUND);
        TftTouchShield.fillRect(NEW_X, FOOTER_Y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.GRAY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(NEW_X + 12, FOOTER_Y + 7);
        TftTouchShield.print("NEW");
        showMessage("Swipe to play", TftTouchShield.GRAY);
        spawn(board);
        spawn(board);
        refresh(board, shown);
        drawHeader();
    }

    /** A swipe of at least SWIPE_MIN pixels, or else a tap relative to the grid's center. */
    private static int swipeDirection(int startX, int startY, int endX, int endY) {
        int dx = endX - startX;
        int dy = endY - startY;
        if (Math.max(Math.abs(dx), Math.abs(dy)) < SWIPE_MIN) {
            if (startY < GRID_Y || startY >= GRID_Y + GRID_SIZE) {
                return -1;
            }
            dx = startX - (GRID_X + GRID_SIZE / 2);
            dy = startY - (GRID_Y + GRID_SIZE / 2);
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (dx < 0) {
                return LEFT;
            }
            return RIGHT;
        }
        if (dy < 0) {
            return UP;
        }
        return 3;
    }

    // ---- Rules ----

    /** Slides every line towards {@code direction}; returns whether any tile moved. */
    private static boolean move(int[] board, int[] line, int direction) {
        boolean moved = false;
        for (int i = 0; i < SIZE; i++) {
            int start;
            int step;
            if (direction == LEFT) {
                start = i * SIZE;
                step = 1;
            } else if (direction == RIGHT) {
                start = i * SIZE + SIZE - 1;
                step = -1;
            } else if (direction == UP) {
                start = i;
                step = SIZE;
            } else {
                start = CELLS - SIZE + i;
                step = -SIZE;
            }
            if (slide(board, line, start, step)) {
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Slides one line towards its first cell ({@code start}, then {@code start + step}, ...),
     * merging each pair of equal neighbours once. Returns whether the line changed.
     */
    private static boolean slide(int[] board, int[] line, int start, int step) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            int value = board[start + i * step];
            if (value != 0) {
                line[count] = value;
                count = count + 1;
            }
        }
        int out = 0;
        int i = 0;
        boolean changed = false;
        while (i < count) {
            int value = line[i];
            if (i + 1 < count && line[i + 1] == value) {
                value = value + 1;
                score = score + (1 << value);
                i = i + 2;
            } else {
                i = i + 1;
            }
            if (board[start + out * step] != value) {
                changed = true;
            }
            board[start + out * step] = value;
            out = out + 1;
        }
        while (out < SIZE) {
            if (board[start + out * step] != 0) {
                changed = true;
            }
            board[start + out * step] = 0;
            out = out + 1;
        }
        return changed;
    }

    private static void spawn(int[] board) {
        int empty = 0;
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                empty = empty + 1;
            }
        }
        if (empty == 0) {
            return;
        }
        int pick = Random.nextInt(empty);
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                if (pick == 0) {
                    board[i] = 1;
                    if (Random.nextInt(10) == 0) {
                        board[i] = 2;
                    }
                    return;
                }
                pick = pick - 1;
            }
        }
    }

    private static boolean canMove(int[] board) {
        for (int i = 0; i < CELLS; i++) {
            if (board[i] == 0) {
                return true;
            }
            if (i % SIZE < SIZE - 1 && board[i] == board[i + 1]) {
                return true;
            }
            if (i + SIZE < CELLS && board[i] == board[i + SIZE]) {
                return true;
            }
        }
        return false;
    }

    private static int highest(int[] board) {
        int highest = 0;
        for (int i = 0; i < CELLS; i++) {
            highest = Math.max(highest, board[i]);
        }
        return highest;
    }

    // ---- Drawing ----

    private static void drawHeader() {
        if (score > best) {
            best = score;
        }
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 6);
        TftTouchShield.print("SCORE");
        TftTouchShield.setCursor(136, 6);
        TftTouchShield.print("BEST");
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 18);
        TftTouchShield.print(score);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, HEADER_BACKGROUND);
        TftTouchShield.setCursor(136, 22);
        TftTouchShield.print(best);
    }

    private static void showMessage(String text, int color) {
        TftTouchShield.fillRect(0, FOOTER_Y, NEW_X - 4, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, TftTouchShield.BLACK);
        TftTouchShield.setCursor(GRID_X, FOOTER_Y + 10);
        TftTouchShield.print(text);
    }

    private static void refresh(int[] board, int[] shown) {
        for (int i = 0; i < CELLS; i++) {
            if (board[i] != shown[i]) {
                drawTile(i, board[i]);
                shown[i] = board[i];
            }
        }
    }

    private static void drawTile(int cell, int exponent) {
        int x = GRID_X + GAP + (cell % SIZE) * (TILE + GAP);
        int y = GRID_Y + GAP + (cell / SIZE) * (TILE + GAP);
        int color = tileColor(exponent);
        TftTouchShield.fillRect(x, y, TILE, TILE, color);
        if (exponent == 0) {
            return;
        }
        String text = String.valueOf(1 << exponent);
        int length = text.length();
        int size = 3;
        if (length == 3 || length == 4) {
            size = 2;
        } else if (length > 4) {
            size = 1;
        }
        int ink = TftTouchShield.WHITE;
        if (exponent <= 2) {
            ink = DARK_TEXT;
        }
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(ink, color);
        TftTouchShield.setCursor(x + (TILE - length * 6 * size + size) / 2, y + (TILE - 7 * size) / 2);
        TftTouchShield.print(text);
    }

    private static int tileColor(int exponent) {
        if (exponent == 0) {
            return EMPTY;
        }
        if (exponent == 1) {
            return 0xEF3B;
        }
        if (exponent == 2) {
            return 0xEF19;
        }
        if (exponent == 3) {
            return 0xF58F;
        }
        if (exponent == 4) {
            return 0xF4AC;
        }
        if (exponent == 5) {
            return 0xF3EB;
        }
        if (exponent == 6) {
            return 0xF2E7;
        }
        if (exponent == 7) {
            return 0xEE6E;
        }
        if (exponent == 8) {
            return 0xEE6C;
        }
        if (exponent == 9) {
            return 0xEE4A;
        }
        if (exponent == 10) {
            return 0xEE27;
        }
        if (exponent == 11) {
            return 0xEE05;
        }
        return 0x39C6;
    }
}
