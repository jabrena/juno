package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Sundance on the ELEGOO 2.8" TFT touch screen shield, after Cinematronics' 1979 vector arcade game,
 * in landscape. Two grids of three by three squares face each other, one above the other, and suns
 * bounce between them, drifting from square to square with every bounce.
 *
 * <p>Every square has a hatch, the same one in both grids. Tap a square of either grid to open its
 * hatch for a moment: a sun that lands on an open hatch falls through and is trapped. Only two
 * hatches can be open at once (opening a third closes the oldest), so time your taps; a small cross
 * in each sun's color marks the square it is heading for. Trap every sun of the round before the
 * time runs out. If two suns collide they burst in a sundance of rays and cost a life, and so does
 * running out of time; every third round cleared earns a life back. Each round brings more suns,
 * more of them at once, bouncing faster.
 *
 * <p>The grids are drawn in perspective, and everything is drawn in vector style with the double
 * display lists of {@link StarWars}: only lines that changed since the previous frame are erased and
 * drawn again.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Sundance {
    private static final int FRAME_MILLIS = 40;

    // Screen: landscape, header on top, the two grids below it.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int HEADER = 20;

    // The grids, in perspective: depth 0 is their near edge, 3 their far edge.
    private static final int CENTER_X = 160;
    private static final int FLOOR_NEAR = 232;
    private static final int CEILING_NEAR = 28;
    /** Screen rows between the near and the far edge of a grid. */
    private static final int GRID_DEPTH = 72;
    private static final int NEAR_HALF_WIDTH = 150;
    private static final float PERSPECTIVE = 0.25f;
    /** {@code 1 - 1 / (1 + 3 * PERSPECTIVE)}: how far the far edge recedes. */
    private static final float FAR_FRACTION = 0.42857143f;
    /** Height between the grids, in squares, when measuring how close two suns are. */
    private static final float GAP = 2.0f;
    private static final float COLLISION = 0.45f;
    private static final int CELLS = 9;

    // Display lists: two of MAX_LINES lines (x0, y0, x1, y1, color), the one on screen and the next.
    private static final int MAX_LINES = 180;
    private static final int L_STRIDE = 5;
    private static final int LIST_SIZE = MAX_LINES * L_STRIDE;

    // Suns. A flying sun crosses from square FROM of one grid to square TO of the other in DUR frames.
    private static final int SUNS = 6;
    private static final int S_STATE = 0;
    private static final int S_FROM = 1;
    private static final int S_TO = 2;
    private static final int S_T = 3;
    private static final int S_DUR = 4;
    /** 1 while flying up to the upper grid, 0 while falling to the lower one. */
    private static final int S_UP = 5;
    private static final int S_COLOR = 6;
    private static final int S_TIMER = 7;
    private static final int S_STRIDE = 8;

    private static final int FREE = 0;
    private static final int FLYING = 1;
    /** Trapped: falling through its hatch. FROM and TO hold its screen position. */
    private static final int TRAPPED = 2;
    /** Collided: bursting. FROM and TO hold its screen position. */
    private static final int BURST = 3;

    // Rules.
    private static final int HATCH_FRAMES = 12;
    private static final int MAX_OPEN = 2;
    private static final int ROUND_FRAMES = 1500;
    private static final int RELEASE_FRAMES = 24;
    private static final int TRAP_FRAMES = 8;
    private static final int BURST_FRAMES = 16;
    private static final int LIVES = 3;
    private static final int MAX_LIVES = 5;

    // Colors.
    private static final int SPACE = TftTouchShield.BLACK;
    private static final int GRID = 0x051F;
    private static final int HATCH = TftTouchShield.GREEN;
    private static final int FRAME = 0x4208;

    private static int score;
    private static int best;
    private static int round;
    private static int lives;
    private static int frame;
    private static int timeLeft;
    private static int quota;
    private static int released;
    private static int trapped;
    private static int releaseTimer;
    private static int nextColor;
    private static boolean touching;

    private static int front;
    private static int shown;
    private static int built;
    private static int status;
    private static int shownScore;

    private Sundance() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * LIST_SIZE];
        int[] suns = new int[SUNS * S_STRIDE];
        int[] hatches = new int[CELLS];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        TftTouchShield.fillScreen(SPACE);
        drawTitle(lines, suns, hatches);
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            score = 0;
            round = 1;
            lives = LIVES;
            while (lives > 0) {
                if (playRound(lines, suns, hatches)) {
                    if (round % 3 == 0) {
                        lives = Math.min(lives + 1, MAX_LIVES);
                    }
                    round = round + 1;
                }
            }
            if (score > best) {
                best = score;
            }
            drawHeader(suns);
            clearView();
            showCentered("GAME OVER", 100, 3, TftTouchShield.RED);
            showCentered("Tap to play again", 150, 1, TftTouchShield.WHITE);
            Delay.millis(1500);
            waitForTap();
        }
    }

    // ---- Game flow ----

    /** Plays one round; true when it was cleared, false when time ran out or the last life was lost. */
    private static boolean playRound(short[] lines, int[] suns, int[] hatches) {
        startRound(suns, hatches);
        drawHeader(suns);
        clearView();
        showCentered("ROUND", 84, 3, TftTouchShield.YELLOW);
        TftTouchShield.setCursor(round < 10 ? 151 : 142, 114);
        TftTouchShield.print(round);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(106, 150);
        TftTouchShield.print("Trap ");
        TftTouchShield.print(quota);
        TftTouchShield.print(" suns");
        Delay.millis(1500);
        clearView();

        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                // A slow frame: carry on from now rather than rushing to catch up.
                next = Clock.millis();
            }
            frame = frame + 1;
            handleTouch(hatches);
            step(suns, hatches);
            render(lines, suns, hatches);
            drawStatus(suns);
            if (lives == 0 && count(suns, BURST) == 0) {
                Delay.millis(600);
                return false;
            }
            if (timeLeft == 0) {
                lives = lives - 1;
                clearView();
                showCentered("OUT OF TIME", 100, 3, TftTouchShield.RED);
                Delay.millis(1500);
                return false;
            }
            if (roundCleared(suns)) {
                finishRound();
                return true;
            }
        }
    }

    private static void startRound(int[] suns, int[] hatches) {
        clear(suns, SUNS * S_STRIDE);
        clear(hatches, CELLS);
        quota = Math.min(3 + round, 12);
        released = 0;
        trapped = 0;
        releaseTimer = 10;
        timeLeft = ROUND_FRAMES;
        touching = false;
    }

    /** Suns of this round still to come or in the air. */
    private static int remaining(int[] suns) {
        return quota - released + count(suns, FLYING);
    }

    private static boolean roundCleared(int[] suns) {
        return released == quota && count(suns, FLYING) + count(suns, TRAPPED) + count(suns, BURST) == 0;
    }

    private static void finishRound() {
        int bonus = timeLeft / 25 * 10 * round;
        score = score + bonus;
        clearView();
        showCentered("ROUND CLEARED", 90, 2, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(118, 122);
        TftTouchShield.print("TIME BONUS ");
        TftTouchShield.print(bonus);
        Delay.millis(1800);
    }

    /** How many suns are in the air at once in this round. */
    private static int maxActive() {
        return Math.min(2 + (round - 1) / 2, SUNS - 1);
    }

    /** Frames a sun takes to cross from one grid to the other in this round. */
    private static int crossing() {
        return Math.max(22, 46 - 2 * round);
    }

    // ---- Input ----

    /** A touch opens the hatch of the square under it, once per touch. */
    private static void handleTouch(int[] hatches) {
        if (!TftTouchShield.readTouch()) {
            touching = false;
            return;
        }
        if (touching) {
            return;
        }
        touching = true;
        int cell = cellAt(TftTouchShield.touchX(), TftTouchShield.touchY());
        if (cell >= 0) {
            openHatch(hatches, cell);
        }
    }

    /** Opens a hatch; if that makes too many open, the one closest to closing shuts first. */
    private static void openHatch(int[] hatches, int cell) {
        if (hatches[cell] == 0 && openCount(hatches) >= MAX_OPEN) {
            int oldest = -1;
            for (int c = 0; c < CELLS; c++) {
                if (hatches[c] > 0 && (oldest < 0 || hatches[c] < hatches[oldest])) {
                    oldest = c;
                }
            }
            hatches[oldest] = 0;
        }
        hatches[cell] = HATCH_FRAMES;
    }

    private static int openCount(int[] hatches) {
        int n = 0;
        for (int c = 0; c < CELLS; c++) {
            if (hatches[c] > 0) {
                n = n + 1;
            }
        }
        return n;
    }

    /** The square (row * 3 + column, row 0 nearest) of either grid under a screen point, or -1. */
    private static int cellAt(int x, int y) {
        for (int g = 0; g < 2; g++) {
            for (int row = 0; row < 3; row++) {
                int near = screenY(row, g);
                int far = screenY(row + 1, g);
                if (y >= Math.min(near, far) && y <= Math.max(near, far)) {
                    float d = row + (y - near) / (float) (far - near);
                    float u = (x - CENTER_X) * 1.5f * (1 + PERSPECTIVE * d) / NEAR_HALF_WIDTH + 1.5f;
                    if (u >= 0 && u < 3) {
                        return row * 3 + (int) u;
                    }
                }
            }
        }
        return -1;
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

    // ---- Simulation ----

    private static void step(int[] suns, int[] hatches) {
        if (timeLeft > 0) {
            timeLeft = timeLeft - 1;
        }
        for (int c = 0; c < CELLS; c++) {
            if (hatches[c] > 0) {
                hatches[c] = hatches[c] - 1;
            }
        }
        if (releaseTimer > 0) {
            releaseTimer = releaseTimer - 1;
        } else if (released < quota && count(suns, FLYING) < maxActive()) {
            if (release(suns)) {
                releaseTimer = RELEASE_FRAMES;
            }
        }
        for (int slot = 0; slot < SUNS; slot++) {
            int b = slot * S_STRIDE;
            int state = suns[b + S_STATE];
            if (state == FLYING) {
                suns[b + S_T] = suns[b + S_T] + 1;
                if (suns[b + S_T] >= suns[b + S_DUR]) {
                    land(suns, b, hatches);
                }
            } else if (state == TRAPPED || state == BURST) {
                suns[b + S_TIMER] = suns[b + S_TIMER] - 1;
                if (suns[b + S_TIMER] <= 0) {
                    suns[b + S_STATE] = FREE;
                }
            }
        }
        collide(suns);
    }

    /**
     * Sends a new sun down from a square of the upper grid, away from the other suns; false if every
     * square tried was too crowded.
     */
    private static boolean release(int[] suns) {
        int slot = find(suns, FREE);
        if (slot < 0) {
            return false;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            int from = Random.nextInt(CELLS);
            if (clearOfSuns(suns, from)) {
                int b = slot * S_STRIDE;
                suns[b + S_STATE] = FLYING;
                suns[b + S_FROM] = from;
                suns[b + S_TO] = pickTarget(from);
                suns[b + S_T] = 0;
                suns[b + S_DUR] = crossing();
                suns[b + S_UP] = 0;
                suns[b + S_COLOR] = nextColor;
                nextColor = (nextColor + 1) % 4;
                released = released + 1;
                return true;
            }
        }
        return false;
    }

    /** Whether no flying sun is near the upper grid over or next to a square. */
    private static boolean clearOfSuns(int[] suns, int cell) {
        for (int slot = 0; slot < SUNS; slot++) {
            int b = slot * S_STRIDE;
            if (suns[b + S_STATE] == FLYING && sunH(suns, b) > 0.5f) {
                float du = sunU(suns, b) - (cell % 3 + 0.5f);
                float dd = sunD(suns, b) - (cell / 3 + 0.5f);
                if (du * du + dd * dd < 2.5f) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The square a sun bounces towards: this one or any next to it. */
    private static int pickTarget(int cell) {
        int column = Math.clamp(cell % 3 + Random.nextInt(-1, 2), 0, 2);
        int row = Math.clamp(cell / 3 + Random.nextInt(-1, 2), 0, 2);
        return row * 3 + column;
    }

    /** A sun reaches a grid: through an open hatch it is trapped, otherwise it bounces back. */
    private static void land(int[] suns, int b, int[] hatches) {
        int cell = suns[b + S_TO];
        if (hatches[cell] > 0) {
            suns[b + S_STATE] = TRAPPED;
            suns[b + S_TIMER] = TRAP_FRAMES;
            suns[b + S_FROM] = cell;
            trapped = trapped + 1;
            score = score + 100 * round;
            return;
        }
        suns[b + S_FROM] = cell;
        suns[b + S_TO] = pickTarget(cell);
        suns[b + S_T] = 0;
        suns[b + S_UP] = 1 - suns[b + S_UP];
    }

    /** Two flying suns that meet burst, and cost a life. */
    private static void collide(int[] suns) {
        for (int a = 0; a < SUNS; a++) {
            int ba = a * S_STRIDE;
            if (suns[ba + S_STATE] != FLYING) {
                continue;
            }
            for (int c = a + 1; c < SUNS; c++) {
                int bc = c * S_STRIDE;
                if (suns[bc + S_STATE] != FLYING) {
                    continue;
                }
                float du = sunU(suns, ba) - sunU(suns, bc);
                float dd = sunD(suns, ba) - sunD(suns, bc);
                float dh = (sunH(suns, ba) - sunH(suns, bc)) * GAP;
                if (du * du + dd * dd + dh * dh < COLLISION * COLLISION) {
                    burst(suns, ba);
                    burst(suns, bc);
                    lives = Math.max(0, lives - 1);
                }
            }
        }
    }

    private static void burst(int[] suns, int b) {
        int x = sunScreenX(suns, b);
        int y = sunScreenY(suns, b);
        suns[b + S_STATE] = BURST;
        suns[b + S_TIMER] = BURST_FRAMES;
        suns[b + S_FROM] = x;
        suns[b + S_TO] = y;
    }

    // ---- Geometry ----

    /** Column position of a flying sun, 0..3 across the grids. */
    private static float sunU(int[] suns, int b) {
        float p = suns[b + S_T] / (float) suns[b + S_DUR];
        float from = suns[b + S_FROM] % 3 + 0.5f;
        float to = suns[b + S_TO] % 3 + 0.5f;
        return from + (to - from) * p;
    }

    /** Depth of a flying sun, 0..3 from the near edge of the grids. */
    private static float sunD(int[] suns, int b) {
        float p = suns[b + S_T] / (float) suns[b + S_DUR];
        float from = suns[b + S_FROM] / 3 + 0.5f;
        float to = suns[b + S_TO] / 3 + 0.5f;
        return from + (to - from) * p;
    }

    /** Height of a flying sun: 0 on the lower grid, 1 on the upper one. */
    private static float sunH(int[] suns, int b) {
        float p = suns[b + S_T] / (float) suns[b + S_DUR];
        return suns[b + S_UP] == 1 ? p : 1 - p;
    }

    private static int sunScreenX(int[] suns, int b) {
        return screenX(sunU(suns, b), sunD(suns, b));
    }

    private static int sunScreenY(int[] suns, int b) {
        return screenY(sunD(suns, b), sunH(suns, b));
    }

    private static int screenX(float u, float d) {
        return CENTER_X + Math.round((u - 1.5f) * NEAR_HALF_WIDTH / (1.5f * (1 + PERSPECTIVE * d)));
    }

    /** Screen row of a point at depth d and height h (0 the lower grid, 1 the upper one). */
    private static int screenY(float d, float h) {
        float recede = (1 - 1 / (1 + PERSPECTIVE * d)) / FAR_FRACTION * GRID_DEPTH;
        float lower = FLOOR_NEAR - recede;
        float upper = CEILING_NEAR + recede;
        return Math.round(lower + (upper - lower) * h);
    }

    private static int sunRadius(float d) {
        return Math.round(13 / (1 + PERSPECTIVE * d));
    }

    // ---- Rendering ----

    private static void render(short[] lines, int[] suns, int[] hatches) {
        built = 0;
        drawGrid(lines, 0);
        drawGrid(lines, 1);
        for (int c = 0; c < CELLS; c++) {
            if (hatches[c] > 0) {
                drawHatch(lines, c, 0);
                drawHatch(lines, c, 1);
            }
        }
        for (int slot = 0; slot < SUNS; slot++) {
            drawSun(lines, suns, slot);
        }
        present(lines);
    }

    /** One grid: h 0 is the lower one, 1 the upper one. */
    private static void drawGrid(short[] lines, int h) {
        for (int i = 0; i <= 3; i++) {
            int y = screenY(i, h);
            addLine(lines, screenX(0, i), y, screenX(3, i), y, GRID);
            addLine(lines, screenX(i, 0), screenY(0, h), screenX(i, 3), screenY(3, h), GRID);
        }
    }

    /** An open hatch: the square outlined and crossed. */
    private static void drawHatch(short[] lines, int cell, int h) {
        int column = cell % 3;
        int row = cell / 3;
        int x0 = screenX(column, row);
        int x1 = screenX(column + 1, row);
        int x2 = screenX(column + 1, row + 1);
        int x3 = screenX(column, row + 1);
        int yNear = screenY(row, h);
        int yFar = screenY(row + 1, h);
        addLine(lines, x0, yNear, x1, yNear, HATCH);
        addLine(lines, x1, yNear, x2, yFar, HATCH);
        addLine(lines, x2, yFar, x3, yFar, HATCH);
        addLine(lines, x3, yFar, x0, yNear, HATCH);
        addLine(lines, x0, yNear, x2, yFar, HATCH);
        addLine(lines, x1, yNear, x3, yFar, HATCH);
    }

    private static void drawSun(short[] lines, int[] suns, int slot) {
        int b = slot * S_STRIDE;
        int state = suns[b + S_STATE];
        int color = sunColor(suns[b + S_COLOR]);
        if (state == FLYING) {
            float d = sunD(suns, b);
            drawStar(lines, sunScreenX(suns, b), sunScreenY(suns, b), sunRadius(d), color, frame / 3 % 2);
            // Where it will land: a small cross on the grid it is heading for.
            int to = suns[b + S_TO];
            float td = to / 3 + 0.5f;
            int tx = screenX(to % 3 + 0.5f, td);
            int ty = screenY(td, suns[b + S_UP]);
            addLine(lines, tx - 3, ty, tx + 3, ty, color);
            addLine(lines, tx, ty - 2, tx, ty + 2, color);
        } else if (state == TRAPPED) {
            // Falling through the hatch, out of sight beyond the grid.
            int cell = suns[b + S_FROM];
            float d = cell / 3 + 0.5f;
            int x = screenX(cell % 3 + 0.5f, d);
            int y = screenY(d, suns[b + S_UP]);
            int beyond = (TRAP_FRAMES - suns[b + S_TIMER]) * 2;
            y = suns[b + S_UP] == 1 ? y - beyond : y + beyond;
            drawStar(lines, x, y, Math.max(1, sunRadius(d) * suns[b + S_TIMER] / TRAP_FRAMES), color, 0);
        } else if (state == BURST) {
            int r = 4 + (BURST_FRAMES - suns[b + S_TIMER]) * 3;
            int x = suns[b + S_FROM];
            int y = suns[b + S_TO];
            for (int k = 0; k < 8; k++) {
                addLine(lines, x + dirX(k) * r / 200, y + dirY(k) * r / 200, x + dirX(k) * r / 100,
                        y + dirY(k) * r / 100, (k & 1) == 0 ? TftTouchShield.WHITE : color);
            }
        }
    }

    /** A sun: an octagon with four rays that flicker between the straight and the diagonal directions. */
    private static void drawStar(short[] lines, int x, int y, int r, int color, int spin) {
        for (int k = 0; k < 8; k++) {
            addLine(lines, x + dirX(k) * r / 100, y + dirY(k) * r / 100, x + dirX(k + 1) * r / 100,
                    y + dirY(k + 1) * r / 100, color);
        }
        for (int k = spin; k < 8; k = k + 2) {
            addLine(lines, x + dirX(k) * (r + 2) / 100, y + dirY(k) * (r + 2) / 100, x + dirX(k) * (r + 6) / 100,
                    y + dirY(k) * (r + 6) / 100, color);
        }
    }

    private static int sunColor(int index) {
        if (index == 0) {
            return TftTouchShield.YELLOW;
        }
        if (index == 1) {
            return TftTouchShield.ORANGE;
        }
        if (index == 2) {
            return 0xFFF0;
        }
        return 0xFB00;
    }

    /** Cosine of k x 45 degrees, x 100. */
    private static int dirX(int k) {
        int i = k % 8;
        if (i == 0) {
            return 100;
        }
        if (i == 1 || i == 7) {
            return 71;
        }
        if (i == 2 || i == 6) {
            return 0;
        }
        if (i == 3 || i == 5) {
            return -71;
        }
        return -100;
    }

    /** Sine of k x 45 degrees, x 100. */
    private static int dirY(int k) {
        return dirX(k + 6);
    }

    // ---- Display lists ----

    /** Clips a screen line to the view below the header (Cohen-Sutherland) and appends it to the list being built. */
    private static void addLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        int code0 = outCode(x0, y0);
        int code1 = outCode(x1, y1);
        int rounds = 0;
        while ((code0 | code1) != 0) {
            if ((code0 & code1) != 0 || rounds == 4) {
                return;
            }
            rounds = rounds + 1;
            int code = code0 != 0 ? code0 : code1;
            int x;
            int y;
            if ((code & 8) != 0) {
                x = x0 + (x1 - x0) * (HEADER - y0) / (y1 - y0);
                y = HEADER;
            } else if ((code & 4) != 0) {
                x = x0 + (x1 - x0) * (HEIGHT - 1 - y0) / (y1 - y0);
                y = HEIGHT - 1;
            } else if ((code & 2) != 0) {
                y = y0 + (y1 - y0) * (WIDTH - 1 - x0) / (x1 - x0);
                x = WIDTH - 1;
            } else {
                y = y0 + (y1 - y0) * (0 - x0) / (x1 - x0);
                x = 0;
            }
            if (code == code0) {
                x0 = x;
                y0 = y;
                code0 = outCode(x0, y0);
            } else {
                x1 = x;
                y1 = y;
                code1 = outCode(x1, y1);
            }
        }
        if (built == MAX_LINES) {
            return;
        }
        int at = (1 - front) * LIST_SIZE + built * L_STRIDE;
        lines[at] = (short) x0;
        lines[at + 1] = (short) y0;
        lines[at + 2] = (short) x1;
        lines[at + 3] = (short) y1;
        lines[at + 4] = (short) color;
        built = built + 1;
    }

    private static int outCode(int x, int y) {
        int code = 0;
        if (x < 0) {
            code = code | 1;
        } else if (x > WIDTH - 1) {
            code = code | 2;
        }
        if (y < HEADER) {
            code = code | 8;
        } else if (y > HEIGHT - 1) {
            code = code | 4;
        }
        return code;
    }

    /**
     * Shows the list just built: erases the previous frame's lines that changed, then draws the new
     * ones plus any unchanged line an erased one may have crossed.
     */
    private static void present(short[] lines) {
        int oldBase = front * LIST_SIZE;
        int newBase = (1 - front) * LIST_SIZE;
        for (int i = 0; i < shown; i++) {
            if (changed(lines, i)) {
                drawListed(lines, oldBase + i * L_STRIDE, SPACE);
            }
        }
        for (int j = 0; j < built; j++) {
            int at = newBase + j * L_STRIDE;
            boolean draw = changed(lines, j);
            for (int i = 0; i < shown && !draw; i++) {
                if (changed(lines, i) && overlaps(lines, oldBase + i * L_STRIDE, at)) {
                    draw = true;
                }
            }
            if (draw) {
                drawListed(lines, at, lines[at + 4] & 0xFFFF);
            }
        }
        front = 1 - front;
        shown = built;
    }

    /** Whether entry {@code i} differs between the list on screen and the one just built. */
    private static boolean changed(short[] lines, int i) {
        if (i >= shown || i >= built) {
            return true;
        }
        int a = front * LIST_SIZE + i * L_STRIDE;
        int b = (1 - front) * LIST_SIZE + i * L_STRIDE;
        for (int k = 0; k < L_STRIDE; k++) {
            if (lines[a + k] != lines[b + k]) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(short[] lines, int a, int b) {
        return Math.min(lines[a], lines[a + 2]) <= Math.max(lines[b], lines[b + 2])
                && Math.min(lines[b], lines[b + 2]) <= Math.max(lines[a], lines[a + 2])
                && Math.min(lines[a + 1], lines[a + 3]) <= Math.max(lines[b + 1], lines[b + 3])
                && Math.min(lines[b + 1], lines[b + 3]) <= Math.max(lines[a + 1], lines[a + 3]);
    }

    private static void drawListed(short[] lines, int at, int color) {
        drawLine(lines[at], lines[at + 1], lines[at + 2], lines[at + 3], color);
    }

    /** Blanks the view and forgets what the display list had drawn there. */
    private static void clearView() {
        TftTouchShield.fillRect(0, HEADER, WIDTH, HEIGHT - HEADER, SPACE);
        shown = 0;
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    private static void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 > x1 ? -1 : 1;
        int stepY = y0 > y1 ? -1 : 1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        int runStart = x0;
        while (x != x1 || y != y1) {
            int twice = 2 * error;
            int nextX = x;
            int nextY = y;
            if (twice >= dy) {
                error = error + dy;
                nextX = x + stepX;
            }
            if (twice <= dx) {
                error = error + dx;
                nextY = y + stepY;
            }
            if (nextY != y) {
                fillRun(runStart, x, y, color);
                runStart = nextX;
            }
            x = nextX;
            y = nextY;
        }
        fillRun(runStart, x, y, color);
    }

    private static void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }

    // ---- Header and text ----

    private static void drawHeader(int[] suns) {
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER - 1, SPACE);
        TftTouchShield.drawHorizontalLine(0, HEADER - 1, WIDTH, FRAME);
        status = -1;
        drawStatus(suns);
    }

    /** Score, round, lives, suns still to trap, and the time left. */
    private static void drawStatus(int[] suns) {
        int seconds = (timeLeft + 24) / 25;
        int left = remaining(suns);
        int combined = (lives * 16 + left) * 128 + seconds;
        if (combined == status && score == shownScore) {
            return;
        }
        status = combined;
        shownScore = score;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, SPACE);
        TftTouchShield.setCursor(136, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(best);
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("ROUND ");
        TftTouchShield.print(round);
        TftTouchShield.setTextColor(lives == 1 ? TftTouchShield.RED : TftTouchShield.GREEN, SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(lives);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(136, 11);
        TftTouchShield.print("SUNS ");
        TftTouchShield.print(left);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(seconds <= 10 ? TftTouchShield.RED : TftTouchShield.WHITE, SPACE);
        TftTouchShield.setCursor(250, 11);
        TftTouchShield.print("TIME ");
        TftTouchShield.print(seconds);
        TftTouchShield.print(" ");
    }

    private static void drawTitle(short[] lines, int[] suns, int[] hatches) {
        round = 1;
        clear(suns, SUNS * S_STRIDE);
        clear(hatches, CELLS);
        hatches[4] = HATCH_FRAMES;
        placeSun(suns, 0, 0, 1, 1, 30, 0);
        placeSun(suns, 1, 2, 2, 1, 36, 1);
        placeSun(suns, 2, 1, 4, 0, 34, 2);
        built = 0;
        drawGrid(lines, 0);
        drawGrid(lines, 1);
        drawHatch(lines, 4, 0);
        drawHatch(lines, 4, 1);
        for (int slot = 0; slot < SUNS; slot++) {
            drawSun(lines, suns, slot);
        }
        present(lines);
        clear(suns, SUNS * S_STRIDE);
        clear(hatches, CELLS);
        showCentered("SUNDANCE", 112, 4, TftTouchShield.YELLOW);
        TftTouchShield.fillRect(0, 150, WIDTH, 9, SPACE);
        showCentered("Tap a square to open its hatch", 151, 1, TftTouchShield.WHITE);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER, SPACE);
        showCentered("Trap the suns before they collide", 4, 1, TftTouchShield.CYAN);
    }

    /** Puts a sun partway across, for the title screen. */
    private static void placeSun(int[] suns, int slot, int from, int to, int up, int t, int color) {
        int b = slot * S_STRIDE;
        suns[b + S_STATE] = FLYING;
        suns[b + S_FROM] = from;
        suns[b + S_TO] = to;
        suns[b + S_T] = t;
        suns[b + S_DUR] = 40;
        suns[b + S_UP] = up;
        suns[b + S_COLOR] = color;
    }

    private static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SPACE);
        TftTouchShield.setCursor((WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }

    // ---- Records ----

    private static int find(int[] suns, int state) {
        for (int slot = 0; slot < SUNS; slot++) {
            if (suns[slot * S_STRIDE + S_STATE] == state) {
                return slot;
            }
        }
        return -1;
    }

    private static int count(int[] suns, int state) {
        int n = 0;
        for (int slot = 0; slot < SUNS; slot++) {
            if (suns[slot * S_STRIDE + S_STATE] == state) {
                n = n + 1;
            }
        }
        return n;
    }

    private static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }
}
