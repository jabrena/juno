package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The tube's perspective geometry, its lane spokes, and the shared line rasterizer.
 *
 * <p>The rim and near-end points of all {@value #LANES} spokes live in one flat {@code tube[]}
 * array ({@link #OUTER_X}/{@link #OUTER_Y}/{@link #INNER_X}/{@link #INNER_Y} offsets), the same
 * stride idiom {@link Session} uses for enemies and shots, rather than four separate {@code int[]}
 * parameters threaded through every call — keeping every method at or under the four arguments ARM
 * passes in registers, with nothing spilled onto the stack.
 */
final class Tube {
    static final int LANES = 16;
    static final int DEPTH = 1024;
    static final int INNER_SCALE = 20;
    static final int CENTER_X = 120;
    static final int CENTER_Y = 180;
    static final int ZAPPER_RADIUS = 22;

    static final int OUTER_X = 0;
    static final int OUTER_Y = LANES;
    static final int INNER_X = 2 * LANES;
    static final int INNER_Y = 3 * LANES;
    static final int SIZE = 4 * LANES;

    private Tube() {
    }

    /**
     * Fills the rim and far-end points of lane spokes for one of four tube shapes. The radius stays
     * inline: the assembly backend passes each call argument and result as one 32-bit word, so a
     * helper taking or returning a {@code double} would receive and return only half of it.
     */
    static void build(int shape, int[] tube) {
        for (int i = 0; i < LANES; i++) {
            double angle = 2.0 * Math.PI * i / LANES - Math.PI / 2.0;
            double radius = 108.0;
            if (shape == 1) {
                // Square: the radius that puts the point on a square of half-side 98.
                double c = Math.abs(Math.cos(angle));
                double s = Math.abs(Math.sin(angle));
                radius = Math.min(98.0 / Math.max(c, s), 118.0);
            } else if (shape == 2) {
                radius = (i & 1) == 0 ? 110.0 : 70.0;
            } else if (shape == 3) {
                radius = 84.0 + 26.0 * Math.cos(4.0 * angle);
            }
            int outerX = CENTER_X + (int) Math.round(radius * Math.cos(angle));
            int outerY = CENTER_Y + (int) Math.round(radius * Math.sin(angle));
            tube[OUTER_X + i] = outerX;
            tube[OUTER_Y + i] = outerY;
            tube[INNER_X + i] = CENTER_X + (outerX - CENTER_X) * INNER_SCALE / 100;
            tube[INNER_Y + i] = CENTER_Y + (outerY - CENTER_Y) * INNER_SCALE / 100;
        }
    }

    /** Perspective interpolation along spoke {@code spoke} at {@code depth} (0 = far end, DEPTH = rim). */
    static int spokeX(int[] tube, int spoke, int depth) {
        int s = spoke % LANES;
        return tube[INNER_X + s] + (tube[OUTER_X + s] - tube[INNER_X + s]) * perspective(depth) / (3 * DEPTH);
    }

    static int spokeY(int[] tube, int spoke, int depth) {
        int s = spoke % LANES;
        return tube[INNER_Y + s] + (tube[OUTER_Y + s] - tube[INNER_Y + s]) * perspective(depth) / (3 * DEPTH);
    }

    static int perspective(int depth) {
        int d = Math.max(0, Math.min(depth, DEPTH));
        return d * (2 * d + DEPTH) / DEPTH;
    }

    /** The lane whose rim midpoint is nearest ({@code x}, {@code y}). */
    static int nearestLane(int[] tube, int x, int y) {
        int nearest = 0;
        int bestDistance = 1 << 30;
        for (int lane = 0; lane < LANES; lane++) {
            int next = (lane + 1) % LANES;
            int mx = (tube[OUTER_X + lane] + tube[OUTER_X + next]) / 2 - x;
            int my = (tube[OUTER_Y + lane] + tube[OUTER_Y + next]) / 2 - y;
            int distance = mx * mx + my * my;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = lane;
            }
        }
        return nearest;
    }

    /** One lane step from {@code from} towards {@code to}, the short way around the rim. */
    static int stepToward(int from, int to) {
        int forward = (to - from + LANES) % LANES;
        return forward > LANES / 2 ? (from + LANES - 1) % LANES : (from + 1) % LANES;
    }

    /** Bresenham line; runs of pixels on the same row become one fill. */
    static void drawLine(int x0, int y0, int x1, int y1, int color) {
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
}
