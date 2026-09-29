package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** All the drawing for trails, blasts, the ground, bases and cities; game rules never draw directly. */
final class SceneRenderer {
    static final int SKY = TftTouchShield.BLACK;
    static final int GROUND = 0xC4A0;
    static final int ENEMY_TRAIL = 0xF800;
    static final int SHOT_TRAIL = 0x07FF;
    static final int CITY_COLOR = 0x04DF;
    static final int CITY_WINDOWS = 0xFFE0;
    static final int RUBBLE = 0x6B4D;

    // The last plotted pixel of the trail step range traceLine was just asked for.
    private static int traceX;
    private static int traceY;

    private SceneRenderer() {
    }

    // ---- Trails ----

    /** Starts tracing a line from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) at {@code speed} pixels per frame (fixed point). */
    static void startTrail(int[] trails, int slot, int x0, int y0, int x1, int y1, int speed) {
        int base = slot * Session.T_STRIDE;
        int dx = x1 - x0;
        int dy = y1 - y0;
        int length = Math.max(1, (int) Math.sqrt((double) (dx * dx + dy * dy)));
        // A Bresenham line takes one step per pixel along its major axis.
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        trails[base + Session.T_ACTIVE] = 1;
        trails[base + Session.T_START_X] = x0;
        trails[base + Session.T_START_Y] = y0;
        trails[base + Session.T_END_X] = x1;
        trails[base + Session.T_END_Y] = y1;
        trails[base + Session.T_STEPS] = steps;
        trails[base + Session.T_PROGRESS] = 0;
        trails[base + Session.T_RATE] = Math.max(1, speed * steps / length);
        trails[base + Session.T_DRAWN] = 0;
        trails[base + Session.T_HEAD_X] = x0;
        trails[base + Session.T_HEAD_Y] = y0;
    }

    /** Draws the steps the trail has advanced this frame; returns true once it reaches its end. */
    static boolean advanceTrail(int[] trails, int slot, int color) {
        int base = slot * Session.T_STRIDE;
        int steps = trails[base + Session.T_STEPS];
        int progress = trails[base + Session.T_PROGRESS] + trails[base + Session.T_RATE];
        trails[base + Session.T_PROGRESS] = progress;
        int reached = Math.min(progress / 64, steps) + 1;
        int drawn = trails[base + Session.T_DRAWN];
        if (reached > drawn) {
            traceLine(trails[base + Session.T_START_X], trails[base + Session.T_START_Y],
                    trails[base + Session.T_END_X], trails[base + Session.T_END_Y], drawn, reached, color);
            trails[base + Session.T_DRAWN] = reached;
            trails[base + Session.T_HEAD_X] = traceX;
            trails[base + Session.T_HEAD_Y] = traceY;
        }
        return reached > steps;
    }

    /** Retraces exactly the pixels the trail has drawn, in the sky's color, and frees its slot. */
    static void eraseTrail(int[] trails, int slot) {
        int base = slot * Session.T_STRIDE;
        trails[base + Session.T_ACTIVE] = 0;
        traceLine(trails[base + Session.T_START_X], trails[base + Session.T_START_Y],
                trails[base + Session.T_END_X], trails[base + Session.T_END_Y], 0, trails[base + Session.T_DRAWN],
                SKY);
    }

    /**
     * Walks the Bresenham line from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) and plots
     * only its steps {@code from} (inclusive) to {@code to} (exclusive), so a trail drawn a few steps
     * per frame and erased in one pass covers exactly the same pixels. Runs of plotted pixels on the
     * same row become one fill; the last plotted pixel is left in {@link #traceX}/{@link #traceY}.
     */
    private static void traceLine(int x0, int y0, int x1, int y1, int from, int to, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 > x1 ? -1 : 1;
        int stepY = y0 > y1 ? -1 : 1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        int runStart = x0;
        for (int step = 0; step < to; step++) {
            if (step == from) {
                runStart = x;
            }
            if (step >= from) {
                traceX = x;
                traceY = y;
            }
            if (step == to - 1 || (x == x1 && y == y1)) {
                if (step >= from) {
                    fillRun(runStart, x, y, color);
                }
                return;
            }
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
            if (nextY != y && step >= from) {
                fillRun(runStart, x, y, color);
            }
            if (nextY != y) {
                runStart = nextX;
            }
            x = nextX;
            y = nextY;
        }
    }

    /** Draws a whole line in one call, for a static tableau like the cover, never a per-frame trail. */
    static void drawFullLine(int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        traceLine(x0, y0, x1, y1, 0, steps, color);
    }

    private static void fillRun(int from, int to, int y, int color) {
        int left = Math.min(from, to);
        int right = Math.max(from, to);
        TftTouchShield.fillRect(left, y, right - left + 1, 1, color);
    }

    static void drawMarker(int x, int y, int color) {
        TftTouchShield.drawPixel(x - 2, y - 2, color);
        TftTouchShield.drawPixel(x + 2, y - 2, color);
        TftTouchShield.drawPixel(x - 2, y + 2, color);
        TftTouchShield.drawPixel(x + 2, y + 2, color);
    }

    // ---- Explosions ----

    static void drawBlast(int x, int y, int shown, int radius, int age) {
        if (radius < shown) {
            TftTouchShield.fillCircle(x, y, shown, SKY);
        }
        if (radius != shown || (age & 3) == 0) {
            TftTouchShield.fillCircle(x, y, radius, blastColor(age));
        }
    }

    private static int blastColor(int age) {
        int phase = (age / 2) % 4;
        if (phase == 0) {
            return TftTouchShield.WHITE;
        }
        if (phase == 1) {
            return TftTouchShield.YELLOW;
        }
        if (phase == 2) {
            return TftTouchShield.ORANGE;
        }
        return TftTouchShield.MAGENTA;
    }

    // ---- Ground, bases and cities ----

    static int baseX(int base) {
        return 18 + base * 102;
    }

    static int cityX(int city) {
        return city < 3 ? 46 + city * 24 : 146 + (city - 3) * 24;
    }

    static void drawGround(boolean[] alive, int[] ammo) {
        TftTouchShield.fillRect(0, Session.GROUND_Y, Session.WIDTH, 320 - Session.GROUND_Y, GROUND);
        TftTouchShield.fillRect(0, Session.GROUND_Y - 30, Session.WIDTH, 30, SKY);
        for (int base = 0; base < 3; base++) {
            drawBase(base, ammo);
        }
        for (int city = 0; city < Session.CITIES; city++) {
            drawCity(city, alive[city]);
        }
    }

    static void drawBase(int base, int[] ammo) {
        int x = baseX(base);
        for (int row = 0; row < 12; row++) {
            int half = 9 + row;
            TftTouchShield.fillRect(x - half, Session.GROUND_Y - 12 + row, 2 * half + 1, 1, GROUND);
        }
        drawAmmo(base, ammo);
    }

    static void drawAmmo(int base, int[] ammo) {
        int x = baseX(base);
        TftTouchShield.fillRect(x - 9, Session.GROUND_Y - 11, 19, 9, GROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.NAVY, GROUND);
        if (ammo[base] == 0) {
            TftTouchShield.setTextColor(TftTouchShield.RED, GROUND);
            TftTouchShield.setCursor(x - 8, Session.GROUND_Y - 10);
            TftTouchShield.print("OUT");
            return;
        }
        TftTouchShield.setCursor(ammo[base] < 10 ? x - 2 : x - 5, Session.GROUND_Y - 10);
        TftTouchShield.print(ammo[base]);
    }

    static void drawCity(int city, boolean standing) {
        int x = cityX(city) - 9;
        TftTouchShield.fillRect(x, Session.GROUND_Y - 14, 19, 14, SKY);
        if (!standing) {
            TftTouchShield.fillRect(x + 1, Session.GROUND_Y - 3, 17, 3, RUBBLE);
            TftTouchShield.fillRect(x + 5, Session.GROUND_Y - 5, 5, 2, RUBBLE);
            return;
        }
        TftTouchShield.fillRect(x, Session.GROUND_Y - 6, 19, 6, CITY_COLOR);
        TftTouchShield.fillRect(x + 2, Session.GROUND_Y - 11, 4, 5, CITY_COLOR);
        TftTouchShield.fillRect(x + 8, Session.GROUND_Y - 14, 4, 8, CITY_COLOR);
        TftTouchShield.fillRect(x + 14, Session.GROUND_Y - 9, 3, 3, CITY_COLOR);
        TftTouchShield.drawPixel(x + 9, Session.GROUND_Y - 12, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 9, Session.GROUND_Y - 9, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 3, Session.GROUND_Y - 9, CITY_WINDOWS);
        TftTouchShield.drawPixel(x + 15, Session.GROUND_Y - 4, CITY_WINDOWS);
    }
}
