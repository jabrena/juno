package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Tetris on the ELEGOO 2.8" TFT touch screen shield: a 10x20 well, the seven tetrominoes, line
 * clears (100/300/500/800 points times the level) and a level every ten lines, each one falling
 * faster. The panel on the right shows the next piece, the score, lines and level, and the touch
 * buttons: {@code <} and {@code >} move (and repeat while held), {@code ROT} rotates clockwise
 * (also tapping the well), and {@code DROP} drops the piece straight down. Tap to start or restart.
 *
 * <p>Pieces are 4x4 bit masks (bit r * 4 + c). Rotation turns the piece inside its 3x3 box (4x4
 * for the I piece), trying small sideways kicks when the rotated piece does not fit. Each frame the
 * well plus the falling piece is composed into a buffer and compared with what is on screen, so
 * only changed cells are redrawn.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Tetris {
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int CELLS = WIDTH * HEIGHT;

    // Layout (portrait, 240x320).
    private static final int CELL = 13;
    private static final int WELL_X = 8;
    private static final int WELL_Y = 38;
    private static final int PANEL_X = 146;
    private static final int PREVIEW_CELL = 10;
    private static final int PREVIEW_Y = 52;
    private static final int BUTTON_LEFT_Y = 184;
    private static final int BUTTON_ROTATE_Y = 230;
    private static final int BUTTON_DROP_Y = 274;
    private static final int BUTTON_HEIGHT = 40;
    private static final int HALF_BUTTON = 44;
    private static final int FULL_BUTTON = 92;

    // Buttons.
    private static final int NONE = -1;
    private static final int MOVE_LEFT = 0;
    private static final int MOVE_RIGHT = 1;
    private static final int ROTATE = 2;
    private static final int DROP = 3;

    private static final int REPEAT_DELAY = 250;
    private static final int REPEAT_INTERVAL = 90;

    private static final int WELL_BACKGROUND = TftTouchShield.BLACK;
    private static final int WELL_BORDER = 0x7BEF;
    private static final int BUTTON_COLOR = 0x4A69;
    private static final int HEADER_BACKGROUND = 0x2945;

    private static int piece;
    private static int mask;
    private static int pieceX;
    private static int pieceY;
    private static int nextPiece;
    private static int score;
    private static int lines;
    private static int level;
    private static int best;
    private static boolean over;

    // Touch state for press detection and auto-repeat.
    private static int heldButton;
    private static int candidateButton;
    private static int pressedAt;
    private static int repeatedAt;
    private static int releaseMisses;

    private Tetris() {
    }

    public static void main(String[] args) {
        byte[] well = new byte[CELLS];
        byte[] shown = new byte[CELLS];
        byte[] frame = new byte[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        drawScreen();
        showStatus("Tap to start", TftTouchShield.WHITE);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            newGame(well, shown, frame);
            int lastFall = Clock.millis();
            while (!over) {
                int button = pollButtons();
                boolean changed = false;
                if (button == MOVE_LEFT) {
                    changed = tryMove(well, -1, 0);
                } else if (button == MOVE_RIGHT) {
                    changed = tryMove(well, 1, 0);
                } else if (button == ROTATE) {
                    changed = tryRotate(well);
                } else if (button == DROP) {
                    while (tryMove(well, 0, 1)) {
                        score = score + 2;
                    }
                    lock(well);
                    lastFall = Clock.millis();
                    changed = true;
                }
                if (!over && Clock.millis() - lastFall >= fallInterval()) {
                    lastFall = Clock.millis();
                    if (!tryMove(well, 0, 1)) {
                        lock(well);
                    }
                    changed = true;
                }
                if (changed) {
                    refresh(well, shown, frame);
                }
                Delay.millis(5);
            }
            if (score > best) {
                best = score;
            }
            drawStats();
            showStatus("Game over - tap", TftTouchShield.RED);
            Delay.millis(600);
            waitForTap();
        }
    }

    // ---- Game flow ----

    private static void newGame(byte[] well, byte[] shown, byte[] frame) {
        for (int i = 0; i < CELLS; i++) {
            well[i] = 0;
            shown[i] = -1;
        }
        score = 0;
        lines = 0;
        level = 0;
        over = false;
        nextPiece = Random.nextInt(7);
        spawn(well);
        showStatus("Tetris", TftTouchShield.CYAN);
        drawStats();
        refresh(well, shown, frame);
    }

    private static int fallInterval() {
        return Math.max(90, 700 - level * 60);
    }

    private static void spawn(byte[] well) {
        piece = nextPiece;
        nextPiece = Random.nextInt(7);
        mask = shape(piece);
        pieceX = 3;
        pieceY = 0;
        if (piece == 0) {
            pieceY = -1;
        }
        drawPreview();
        if (!fits(well, mask, pieceX, pieceY)) {
            over = true;
        }
    }

    private static boolean tryMove(byte[] well, int dx, int dy) {
        if (!fits(well, mask, pieceX + dx, pieceY + dy)) {
            return false;
        }
        pieceX = pieceX + dx;
        pieceY = pieceY + dy;
        return true;
    }

    private static boolean tryRotate(byte[] well) {
        if (piece == 1) {
            return false;
        }
        int size = 3;
        if (piece == 0) {
            size = 4;
        }
        int rotated = rotate(mask, size);
        for (int kick = 0; kick < 5; kick++) {
            int dx = kickOffset(kick);
            if (fits(well, rotated, pieceX + dx, pieceY)) {
                mask = rotated;
                pieceX = pieceX + dx;
                return true;
            }
        }
        return false;
    }

    private static int kickOffset(int kick) {
        if (kick == 1) {
            return -1;
        }
        if (kick == 2) {
            return 1;
        }
        if (kick == 3) {
            return -2;
        }
        if (kick == 4) {
            return 2;
        }
        return 0;
    }

    /** Writes the piece into the well, clears full lines, scores them and spawns the next piece. */
    private static void lock(byte[] well) {
        for (int bit = 0; bit < 16; bit++) {
            if (((mask >> bit) & 1) != 0) {
                int y = pieceY + bit / 4;
                if (y < 0) {
                    over = true;
                    return;
                }
                well[y * WIDTH + pieceX + bit % 4] = (byte) (piece + 1);
            }
        }
        int cleared = 0;
        for (int row = HEIGHT - 1; row >= 0; row--) {
            boolean full = true;
            for (int column = 0; column < WIDTH; column++) {
                if (well[row * WIDTH + column] == 0) {
                    full = false;
                }
            }
            if (full) {
                for (int r = row; r > 0; r--) {
                    for (int column = 0; column < WIDTH; column++) {
                        well[r * WIDTH + column] = well[(r - 1) * WIDTH + column];
                    }
                }
                for (int column = 0; column < WIDTH; column++) {
                    well[column] = 0;
                }
                cleared = cleared + 1;
                row = row + 1;
            }
        }
        if (cleared > 0) {
            score = score + lineScore(cleared) * (level + 1);
            lines = lines + cleared;
            level = lines / 10;
        }
        drawStats();
        spawn(well);
    }

    private static int lineScore(int cleared) {
        if (cleared == 1) {
            return 100;
        }
        if (cleared == 2) {
            return 300;
        }
        if (cleared == 3) {
            return 500;
        }
        return 800;
    }

    // ---- Pieces ----

    /** The spawn mask of a piece: I, O, T, S, Z, J, L. */
    private static int shape(int type) {
        if (type == 0) {
            return 0x00F0;
        }
        if (type == 1) {
            return 0x0066;
        }
        if (type == 2) {
            return 0x0072;
        }
        if (type == 3) {
            return 0x0036;
        }
        if (type == 4) {
            return 0x0063;
        }
        if (type == 5) {
            return 0x0071;
        }
        return 0x0074;
    }

    /** Rotates a mask clockwise inside its top-left size x size box: (r, c) -> (c, size - 1 - r). */
    private static int rotate(int value, int size) {
        int result = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (((value >> (r * 4 + c)) & 1) != 0) {
                    result = result | (1 << (c * 4 + size - 1 - r));
                }
            }
        }
        return result;
    }

    private static boolean fits(byte[] well, int value, int x, int y) {
        for (int bit = 0; bit < 16; bit++) {
            if (((value >> bit) & 1) == 0) {
                continue;
            }
            int column = x + bit % 4;
            int row = y + bit / 4;
            if (column < 0 || column >= WIDTH || row >= HEIGHT) {
                return false;
            }
            if (row >= 0 && well[row * WIDTH + column] != 0) {
                return false;
            }
        }
        return true;
    }

    private static int pieceColor(int type) {
        if (type == 0) {
            return TftTouchShield.CYAN;
        }
        if (type == 1) {
            return TftTouchShield.YELLOW;
        }
        if (type == 2) {
            return 0xA01F;
        }
        if (type == 3) {
            return TftTouchShield.GREEN;
        }
        if (type == 4) {
            return TftTouchShield.RED;
        }
        if (type == 5) {
            return 0x333F;
        }
        return TftTouchShield.ORANGE;
    }

    // ---- Input ----

    /**
     * Returns the button pressed this frame: once per press, plus auto-repeat for {@code <} and
     * {@code >} while held. Tapping the well rotates.
     */
    private static int pollButtons() {
        if (!TftTouchShield.readTouch()) {
            releaseMisses = releaseMisses + 1;
            candidateButton = NONE;
            if (releaseMisses >= 3) {
                heldButton = NONE;
            }
            return NONE;
        }
        releaseMisses = 0;
        int button = buttonAt(TftTouchShield.touchX(), TftTouchShield.touchY());
        int now = Clock.millis();
        if (button != heldButton && button != candidateButton) {
            // Act only when the same button is seen on two polls in a row, so a single stray
            // reading never moves or rotates the piece.
            candidateButton = button;
            return NONE;
        }
        if (button != heldButton) {
            candidateButton = NONE;
            heldButton = button;
            pressedAt = now;
            repeatedAt = now;
            return button;
        }
        if ((button == MOVE_LEFT || button == MOVE_RIGHT) && now - pressedAt >= REPEAT_DELAY
                && now - repeatedAt >= REPEAT_INTERVAL) {
            repeatedAt = now;
            return button;
        }
        return NONE;
    }

    private static int buttonAt(int x, int y) {
        if (x < PANEL_X) {
            if (y >= WELL_Y) {
                return ROTATE;
            }
            return NONE;
        }
        if (y >= BUTTON_LEFT_Y && y < BUTTON_LEFT_Y + BUTTON_HEIGHT) {
            if (x < PANEL_X + HALF_BUTTON + 2) {
                return MOVE_LEFT;
            }
            return MOVE_RIGHT;
        }
        if (y >= BUTTON_ROTATE_Y && y < BUTTON_ROTATE_Y + BUTTON_HEIGHT) {
            return ROTATE;
        }
        if (y >= BUTTON_DROP_Y) {
            return DROP;
        }
        return NONE;
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
        heldButton = NONE;
        candidateButton = NONE;
    }

    // ---- Drawing ----

    private static void drawScreen() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.drawRect(WELL_X - 2, WELL_Y - 2, WIDTH * CELL + 4, HEIGHT * CELL + 4, WELL_BORDER);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(PANEL_X, 40);
        TftTouchShield.print("NEXT");
        drawButton(PANEL_X, BUTTON_LEFT_Y, HALF_BUTTON, "<");
        drawButton(PANEL_X + HALF_BUTTON + 4, BUTTON_LEFT_Y, HALF_BUTTON, ">");
        drawButton(PANEL_X, BUTTON_ROTATE_Y, FULL_BUTTON, "ROT");
        drawButton(PANEL_X, BUTTON_DROP_Y, FULL_BUTTON, "DROP");
    }

    private static void drawButton(int x, int y, int width, String label) {
        TftTouchShield.fillRect(x, y, width, BUTTON_HEIGHT, BUTTON_COLOR);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BUTTON_COLOR);
        TftTouchShield.setCursor(x + (width - label.length() * 12) / 2, y + 12);
        TftTouchShield.print(label);
    }

    private static void drawPreview() {
        TftTouchShield.fillRect(PANEL_X, PREVIEW_Y, 4 * PREVIEW_CELL, 4 * PREVIEW_CELL, TftTouchShield.BLACK);
        int preview = shape(nextPiece);
        int color = pieceColor(nextPiece);
        for (int bit = 0; bit < 16; bit++) {
            if (((preview >> bit) & 1) != 0) {
                TftTouchShield.fillRect(PANEL_X + (bit % 4) * PREVIEW_CELL, PREVIEW_Y + (bit / 4) * PREVIEW_CELL,
                        PREVIEW_CELL - 1, PREVIEW_CELL - 1, color);
            }
        }
    }

    private static void drawStats() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(PANEL_X, 100);
        TftTouchShield.print("SCORE");
        TftTouchShield.setCursor(PANEL_X, 128);
        TftTouchShield.print("LINES");
        TftTouchShield.setCursor(PANEL_X + 50, 128);
        TftTouchShield.print("LEVEL");
        TftTouchShield.setCursor(PANEL_X, 156);
        TftTouchShield.print("BEST ");
        TftTouchShield.print(best);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(PANEL_X, 110);
        TftTouchShield.print(score);
        TftTouchShield.setCursor(PANEL_X, 138);
        TftTouchShield.print(lines);
        TftTouchShield.setCursor(PANEL_X + 50, 138);
        TftTouchShield.print(level + 1);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), 30, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 8);
        TftTouchShield.print(text);
    }

    /** Composes the well and the falling piece, then redraws only the cells that changed. */
    private static void refresh(byte[] well, byte[] shown, byte[] frame) {
        for (int i = 0; i < CELLS; i++) {
            frame[i] = well[i];
        }
        if (!over) {
            for (int bit = 0; bit < 16; bit++) {
                if (((mask >> bit) & 1) != 0) {
                    int row = pieceY + bit / 4;
                    if (row >= 0) {
                        frame[row * WIDTH + pieceX + bit % 4] = (byte) (piece + 1);
                    }
                }
            }
        }
        for (int i = 0; i < CELLS; i++) {
            if (frame[i] != shown[i]) {
                int x = WELL_X + (i % WIDTH) * CELL;
                int y = WELL_Y + (i / WIDTH) * CELL;
                if (frame[i] == 0) {
                    TftTouchShield.fillRect(x, y, CELL, CELL, WELL_BACKGROUND);
                } else {
                    TftTouchShield.fillRect(x, y, CELL - 1, CELL - 1, pieceColor(frame[i] - 1));
                }
                shown[i] = frame[i];
            }
        }
    }
}
