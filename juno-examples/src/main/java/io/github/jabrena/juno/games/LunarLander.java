package io.github.jabrena.juno.games;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Lunar Lander on the ELEGOO 2.8" TFT touch screen shield. Each descent starts drifting in from the
 * left above a fresh random moonscape; press and hold {@code <} or {@code >} to rotate the lander
 * in 15-degree steps and {@code BURN} to fire the main engine against gravity.
 *
 * <p>Set down upright, with both feet on one of the yellow pads, slowly enough (the speeds in the
 * header turn red when they are too fast) to score 50 points times the pad's multiplier; the
 * narrowest pads pay the most. Anything else is a crash that costs {@value #CRASH_PENALTY} units of
 * fuel. Fuel carries over between descents and the game ends when it runs out, so a good landing
 * is also a cheap one. The header keeps the best score since power-up.
 *
 * <p>The lander is a 15x15 bitmap rotated on the fly: every frame streams a 17x17 block with
 * {@code beginPixels}/{@code pushPixel}, mapping each screen pixel back into the bitmap, so no line
 * primitive is needed. The terrain is one surface height per screen column, and erasing the lander
 * repaints only the terrain columns its old block covered.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LunarLander {
    private static final int FRAME_MILLIS = 30;
    private static final int START_FUEL = 1500;
    private static final int BURN_PER_FRAME = 3;
    private static final int CRASH_PENALTY = 250;
    private static final float GRAVITY = 0.010f;
    private static final float THRUST = 0.028f;
    private static final float SAFE_VERTICAL = 0.55f;
    private static final float SAFE_HORIZONTAL = 0.35f;
    private static final int MAX_TILT = 6;
    private static final int ROTATE_FRAMES = 4;

    // Layout (portrait, 240x320).
    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 30;
    private static final int PLAY_TOP = 32;
    private static final int PLAY_BOTTOM = 270;
    private static final int BUTTON_Y = 274;
    private static final int BUTTON_HEIGHT = 44;
    private static final int SPRITE = 17;
    private static final int HALF = 8;
    private static final int SEGMENT = 16;
    private static final int PADS = 3;

    private static final int SKY = TftTouchShield.BLACK;
    private static final int GROUND = 0x6B4D;
    private static final int PAD = TftTouchShield.YELLOW;
    private static final int HULL = 0xC618;
    private static final int WINDOW = TftTouchShield.CYAN;
    private static final int FLAME = TftTouchShield.ORANGE;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_PRESSED = TftTouchShield.YELLOW;

    // Buttons.
    private static final int NONE = -1;
    private static final int LEFT = 0;
    private static final int BURN = 1;
    private static final int RIGHT = 2;

    private static float x;
    private static float y;
    private static float vx;
    private static float vy;
    private static int tilt;
    private static int fuel;
    private static int score;
    private static int best;
    private static boolean burning;
    private static int shownX;
    private static int shownY;
    private static int pressed;
    private static int shownPressed;
    private static int descent;

    private LunarLander() {
    }

    public static void main(String[] args) {
        short[] ground = new short[WIDTH];
        byte[] padAt = new byte[WIDTH];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(SKY);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        showMessage("LUNAR LANDER", TftTouchShield.WHITE, 120);
        showMessage("Tap to start", TftTouchShield.YELLOW, 150);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            fuel = START_FUEL;
            score = 0;
            descent = 0;
            while (fuel > 0) {
                descent = descent + 1;
                int landed = fly(ground, padAt);
                if (landed > 0) {
                    score = score + 50 * landed;
                    showMessage("Landed!", TftTouchShield.GREEN, 100);
                    TftTouchShield.setCursor(96, 124);
                    TftTouchShield.print("+");
                    TftTouchShield.print(50 * landed);
                } else {
                    explode(ground, padAt);
                    fuel = Math.max(0, fuel - CRASH_PENALTY);
                    showMessage("Crashed", TftTouchShield.RED, 100);
                }
                drawHeader();
                Delay.millis(1500);
            }
            best = Math.max(best, score);
            drawHeader();
            showMessage("Out of fuel", TftTouchShield.WHITE, 124);
            showMessage("Tap to play again", TftTouchShield.YELLOW, 148);
            waitForTap();
        }
    }

    // ---- Game flow ----

    /** Flies one descent: returns the pad multiplier on a safe landing, or 0 on a crash. */
    private static int fly(short[] ground, byte[] padAt) {
        generateTerrain(ground, padAt);
        TftTouchShield.fillScreen(SKY);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, HEADER_BACKGROUND);
        drawTerrain(ground, padAt);
        x = 24;
        y = PLAY_TOP + 16;
        vx = 0.6f + Random.nextInt(5) * 0.1f;
        vy = 0;
        tilt = 0;
        burning = false;
        shownX = -1;
        pressed = NONE;
        shownPressed = -2;
        drawButtons();
        drawHeader();

        int frame = 0;
        int rotateWait = 0;
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() < next) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            frame = frame + 1;

            pressed = readButton();
            if (pressed != shownPressed) {
                drawButtons();
            }
            if (pressed == LEFT || pressed == RIGHT) {
                if (rotateWait == 0) {
                    if (pressed == LEFT) {
                        tilt = Math.max(-MAX_TILT, tilt - 1);
                    } else {
                        tilt = Math.min(MAX_TILT, tilt + 1);
                    }
                    rotateWait = ROTATE_FRAMES;
                }
                rotateWait = rotateWait - 1;
            } else {
                rotateWait = 0;
            }

            burning = pressed == BURN && fuel > 0;
            float angle = (float) Math.toRadians(tilt * 15);
            if (burning) {
                vx = vx + (float) Math.sin(angle) * THRUST;
                vy = vy - (float) Math.cos(angle) * THRUST;
                fuel = Math.max(0, fuel - BURN_PER_FRAME);
            }
            vy = vy + GRAVITY;
            x = x + vx;
            y = y + vy;
            if (x < HALF) {
                x = HALF;
                vx = 0;
            } else if (x > WIDTH - 1 - HALF) {
                x = WIDTH - 1 - HALF;
                vx = 0;
            }
            if (y < PLAY_TOP + HALF) {
                y = PLAY_TOP + HALF;
                vy = Math.max(vy, 0);
            }

            boolean touching = touchesGround(ground);
            if (touching) {
                burning = false;
            }
            drawLander(ground, padAt);
            if (touching) {
                drawHeader();
                return landingMultiplier(ground, padAt);
            }
            if (frame % 5 == 0) {
                drawHeader();
            }
        }
    }

    /** 0 unless upright, slow, and with both feet on the same pad. */
    private static int landingMultiplier(short[] ground, byte[] padAt) {
        if (tilt != 0 || vy > SAFE_VERTICAL || Math.abs(vx) > SAFE_HORIZONTAL) {
            return 0;
        }
        int left = Math.round(x) - 6;
        int right = Math.round(x) + 6;
        if (padAt[left] == 0 || padAt[left] != padAt[right] || ground[left] != ground[right]) {
            return 0;
        }
        return padAt[left];
    }

    // ---- Terrain ----

    /**
     * Random heights every {@value #SEGMENT} columns, linearly interpolated, with three flat pads:
     * 22 pixels wide (x5; the lander's feet span 15), 32 wide (x3) and 48 wide (x2), each starting
     * on a segment boundary with at least one free segment between them.
     */
    private static void generateTerrain(short[] ground, byte[] padAt) {
        int points = WIDTH / SEGMENT + 1;
        int previous = 0;
        for (int p = 0; p < points; p++) {
            int height = Random.nextInt(PLAY_BOTTOM - 110, PLAY_BOTTOM - 20);
            // Keep the start area high enough to fly over.
            if (p < 2) {
                height = Math.max(height, PLAY_BOTTOM - 50);
            }
            if (p > 0) {
                interpolate(ground, (p - 1) * SEGMENT, previous, height);
            }
            previous = height;
        }
        for (int column = 0; column < WIDTH; column++) {
            padAt[column] = 0;
        }
        // Widest pad first, while there is the most room for it.
        for (int pad = PADS - 1; pad >= 0; pad--) {
            int width = 22;
            int multiplier = 5;
            if (pad == 1) {
                width = 32;
                multiplier = 3;
            } else if (pad == 2) {
                width = 48;
                multiplier = 2;
            }
            // Pads start at segment 2 or later and end at least one segment before the right edge.
            int start = 0;
            int tries = 0;
            do {
                start = Random.nextInt(2, (WIDTH - width) / SEGMENT - 1) * SEGMENT;
                tries = tries + 1;
            } while (tries < 50 && !padFits(padAt, start, width));
            if (tries >= 50) {
                continue;
            }
            int end = start + width;
            int level = ground[start];
            for (int column = start; column < end; column++) {
                level = Math.max(level, ground[column]);
            }
            for (int column = start; column < end; column++) {
                ground[column] = (short) level;
                padAt[column] = (byte) multiplier;
            }
            // Re-slope the terrain on either side so it meets the pad without a cliff.
            smooth(ground, start - SEGMENT, start, level);
            smooth(ground, end + SEGMENT - 1, end - 1, level);
        }
    }

    private static void interpolate(short[] ground, int from, int fromHeight, int toHeight) {
        for (int i = 0; i < SEGMENT && from + i < WIDTH; i++) {
            ground[from + i] = (short) (fromHeight + (toHeight - fromHeight) * i / SEGMENT);
        }
    }

    private static boolean padFits(byte[] padAt, int start, int width) {
        for (int column = start - SEGMENT; column < start + width + SEGMENT; column++) {
            if (column >= 0 && column < WIDTH && padAt[column] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Re-slopes the segment between {@code far} and the pad edge {@code near} to meet the pad. */
    private static void smooth(short[] ground, int far, int near, int level) {
        if (far < 0 || far >= WIDTH) {
            return;
        }
        int farHeight = ground[far];
        int steps = Math.abs(near - far);
        int direction = 1;
        if (near < far) {
            direction = -1;
        }
        for (int i = 1; i < steps; i++) {
            int column = far + direction * i;
            if (column >= 0 && column < WIDTH) {
                ground[column] = (short) (farHeight + (level - farHeight) * i / steps);
            }
        }
    }

    private static void drawTerrain(short[] ground, byte[] padAt) {
        for (int column = 0; column < WIDTH; column++) {
            drawColumn(ground, padAt, column, ground[column]);
        }
        TftTouchShield.setTextSize(1);
        int column = 0;
        while (column < WIDTH) {
            if (padAt[column] != 0) {
                int start = column;
                while (column < WIDTH && padAt[column] == padAt[start]) {
                    column = column + 1;
                }
                TftTouchShield.setTextColor(PAD, GROUND);
                TftTouchShield.setCursor((start + column) / 2 - 6, ground[start] + 6);
                TftTouchShield.print("x");
                TftTouchShield.print(padAt[start]);
            } else {
                column = column + 1;
            }
        }
    }

    /** Paints terrain column {@code column} from {@code fromY} down to the bottom of the field. */
    private static void drawColumn(short[] ground, byte[] padAt, int column, int fromY) {
        int top = ground[column];
        int start = Math.max(fromY, top);
        if (start >= PLAY_BOTTOM) {
            return;
        }
        if (padAt[column] != 0 && start < top + 2) {
            TftTouchShield.drawVerticalLine(column, start, top + 2 - start, PAD);
            start = top + 2;
        }
        TftTouchShield.drawVerticalLine(column, start, PLAY_BOTTOM - start, GROUND);
    }

    // ---- Lander ----

    /**
     * Rows of the 15x15 lander, bit n = column n. {@code hullRow} is the body and legs,
     * {@code windowRow} the cockpit window inside it, {@code flameRow} the exhaust below the nozzle.
     */
    private static int hullRow(int row) {
        if (row == 0) {
            return 0x01C0;
        }
        if (row == 1 || row == 6) {
            return 0x03E0;
        }
        if (row >= 2 && row <= 5) {
            return 0x07F0;
        }
        if (row == 7) {
            return 0x0FF8;
        }
        if (row == 8) {
            return 0x1FFC;
        }
        if (row == 9) {
            return 0x1BEC;
        }
        if (row == 10) {
            // Legs start at the hull's corners, around the engine bell.
            return 0x0808 | 0x01C0;
        }
        if (row == 11 || row == 12) {
            return 0x1004;
        }
        if (row == 13) {
            return 0x2002;
        }
        return 0x7007;
    }

    private static int windowRow(int row) {
        if (row == 2 || row == 4) {
            return 0x0180;
        }
        if (row == 3) {
            return 0x03C0;
        }
        return 0;
    }

    private static int flameRow(int row) {
        if (row == 11 || row == 12) {
            return 0x01C0;
        }
        if (row == 13 || row == 14) {
            return 0x0080;
        }
        return 0;
    }

    /** Color of screen offset (dx, dy) from the lander's center, or -1 for empty space. */
    private static int spriteColor(int dx, int dy, float sin, float cos, boolean flame) {
        int sx = Math.round(cos * dx + sin * dy) + 7;
        int sy = Math.round(-sin * dx + cos * dy) + 7;
        if (sx < 0 || sx > 14 || sy < 0 || sy > 14) {
            return -1;
        }
        int bit = 1 << (14 - sx);
        if ((windowRow(sy) & bit) != 0) {
            return WINDOW;
        }
        if ((hullRow(sy) & bit) != 0) {
            return HULL;
        }
        if (flame && (flameRow(sy) & bit) != 0) {
            return FLAME;
        }
        return -1;
    }

    /** Whether any hull pixel of the lander, at its current position and tilt, is in the ground. */
    private static boolean touchesGround(short[] ground) {
        float angle = (float) Math.toRadians(tilt * 15);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        int cx = Math.round(x);
        int cy = Math.round(y);
        for (int dy = HALF; dy >= -HALF; dy--) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                int column = cx + dx;
                if (column >= 0 && column < WIDTH && cy + dy >= ground[column]
                        && spriteColor(dx, dy, sin, cos, false) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Streams the 17x17 block around the lander, repairing the sky and terrain it moved away from. */
    private static void drawLander(short[] ground, byte[] padAt) {
        int left = Math.round(x) - HALF;
        int top = Math.round(y) - HALF;
        if (shownX >= 0 && (shownX != left || shownY != top)) {
            eraseLander(ground, padAt, left, top);
        }
        float angle = (float) Math.toRadians(tilt * 15);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        if (!TftTouchShield.beginPixels(left, top, SPRITE, SPRITE)) {
            return;
        }
        for (int dy = -HALF; dy <= HALF; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                int color = spriteColor(dx, dy, sin, cos, burning);
                if (color < 0) {
                    color = background(ground, padAt, left + dx + HALF, top + dy + HALF);
                }
                TftTouchShield.pushPixel(color);
            }
        }
        shownX = left;
        shownY = top;
    }

    /** Clears the parts of the previous block that the new one at (left, top) does not cover. */
    private static void eraseLander(short[] ground, byte[] padAt, int left, int top) {
        for (int column = shownX; column < shownX + SPRITE; column++) {
            boolean covered = column >= left && column < left + SPRITE;
            int from = shownY;
            int to = shownY + SPRITE;
            if (covered) {
                // Only the rows above or below the new block need repainting.
                if (top > shownY) {
                    to = Math.min(to, top);
                } else {
                    from = Math.max(from, top + SPRITE);
                }
            }
            if (from < to && column >= 0 && column < WIDTH) {
                int sky = Math.min(to, ground[column]);
                if (sky > from) {
                    TftTouchShield.drawVerticalLine(column, from, sky - from, SKY);
                }
                if (to > ground[column]) {
                    repaintColumn(ground, padAt, column, Math.max(from, ground[column]), to);
                }
            }
        }
    }

    private static void repaintColumn(short[] ground, byte[] padAt, int column, int from, int to) {
        for (int row = from; row < to; row++) {
            TftTouchShield.drawPixel(column, row, background(ground, padAt, column, row));
        }
    }

    private static int background(short[] ground, byte[] padAt, int column, int row) {
        if (column < 0 || column >= WIDTH || row < ground[column]) {
            return SKY;
        }
        if (padAt[column] != 0 && row < ground[column] + 2) {
            return PAD;
        }
        return GROUND;
    }

    private static void explode(short[] ground, byte[] padAt) {
        int cx = Math.round(x);
        int cy = Math.min(Math.round(y), PLAY_BOTTOM - 16);
        for (int r = 3; r <= 15; r = r + 3) {
            TftTouchShield.fillCircle(cx, cy, r, TftTouchShield.YELLOW);
            TftTouchShield.fillCircle(cx, cy, r - 2, TftTouchShield.RED);
            Delay.millis(60);
        }
        // Leave a scorched gap in the sky only: repaint the terrain under the blast.
        int left = Math.max(0, cx - 15);
        int right = Math.min(WIDTH - 1, cx + 15);
        if (TftTouchShield.beginPixels(left, cy - 15, right - left + 1, 31)) {
            for (int row = cy - 15; row <= cy + 15; row++) {
                for (int column = left; column <= right; column++) {
                    TftTouchShield.pushPixel(background(ground, padAt, column, row));
                }
            }
        }
    }

    // ---- Input ----

    private static int readButton() {
        if (!TftTouchShield.readTouch()) {
            return NONE;
        }
        if (TftTouchShield.touchY() < BUTTON_Y - 10) {
            return NONE;
        }
        return Math.min(2, TftTouchShield.touchX() * 3 / WIDTH);
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
    }

    // ---- Drawing ----

    private static void drawHeader() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 4);
        TftTouchShield.print("SCORE ");
        printPadded(score, 5);
        TftTouchShield.print("   FUEL ");
        int fuelColor = TftTouchShield.WHITE;
        if (fuel < 300) {
            fuelColor = TftTouchShield.RED;
        }
        TftTouchShield.setTextColor(fuelColor, HEADER_BACKGROUND);
        printPadded(fuel, 4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print("  BEST ");
        printPadded(best, 5);

        // Speeds in tenths of a pixel per frame; red when too fast to land.
        TftTouchShield.setCursor(4, 18);
        TftTouchShield.print("H-SPEED ");
        speedColor(Math.abs(vx) > SAFE_HORIZONTAL);
        printPadded(Math.round(vx * 10), 4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print("  V-SPEED ");
        speedColor(vy > SAFE_VERTICAL);
        printPadded(Math.round(vy * 10), 4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print("  #");
        TftTouchShield.print(descent);
        TftTouchShield.print(" ");
    }

    private static void speedColor(boolean tooFast) {
        if (tooFast) {
            TftTouchShield.setTextColor(TftTouchShield.RED, HEADER_BACKGROUND);
        } else {
            TftTouchShield.setTextColor(TftTouchShield.GREEN, HEADER_BACKGROUND);
        }
    }

    /** Prints {@code value} right-aligned in {@code width} characters. */
    private static void printPadded(int value, int width) {
        int digits = 1;
        int rest = Math.abs(value);
        while (rest >= 10) {
            rest = rest / 10;
            digits = digits + 1;
        }
        if (value < 0) {
            digits = digits + 1;
        }
        for (int i = digits; i < width; i++) {
            TftTouchShield.print(" ");
        }
        TftTouchShield.print(value);
    }

    private static void drawButtons() {
        drawButton(LEFT, "<");
        drawButton(BURN, "BURN");
        drawButton(RIGHT, ">");
        shownPressed = pressed;
    }

    private static void drawButton(int index, String label) {
        int x = 4 + index * 80;
        int color = BUTTON;
        if (index == pressed) {
            color = BUTTON_PRESSED;
        }
        TftTouchShield.fillRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (72 - label.length() * 12) / 2, BUTTON_Y + 15);
        TftTouchShield.print(label);
    }

    private static void showMessage(String text, int color, int y) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, SKY);
        TftTouchShield.setCursor((WIDTH - text.length() * 12) / 2, y);
        TftTouchShield.print(text);
    }
}
