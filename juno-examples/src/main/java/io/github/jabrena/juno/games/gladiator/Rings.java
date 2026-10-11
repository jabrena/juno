package io.github.jabrena.juno.games.gladiator;

/**
 * The coliseum round the sand, for {@link View}: the elliptical wall as a ring of {@link Coliseum#WALL_SEGMENTS}
 * straight pieces with buttresses, then three tiers of seats and the outer rim with its arches, each a wider and
 * higher ring. A ring above the wall shows only above the rings already drawn in front of it (the per-column
 * {@link View#horizon}), and every ring only where no pillar hides it.
 */
final class Rings {
    private static final int TIERS = 3;
    private static final int TIER_STEP = 90;
    private static final int TIER_RISE = 70;
    private static final int RIM_STEP = 300;
    private static final int RIM_RISE = 360;

    // One repeated byte each, like the view's other colors.
    static final int SAND = 0xE6E6;
    static final int SEATS = 0xC5C5;
    static final int SEATS_FAR = 0xA4A4;
    static final int ARCH = 0x8383;
    static final int RIM = 0x6363;

    /** What a ring's line has to clear: only the pillars, or also the rings already drawn in front of it. */
    private static final int FLOOR = 0;
    private static final int PARAPET = 1;
    private static final int STANDS = 2;

    // The piece being drawn, in screen space.
    private static float leftX;
    private static float rightX;
    private static float leftDepth;
    private static float slope;
    private static int firstColumn;
    private static int lastColumn;

    private Rings() {
    }

    static void draw(short[] lines) {
        int wall = Coliseum.wallHeight;
        ring(lines, 0, 0, SAND, FLOOR);
        ring(lines, 0, wall, View.WALL, PARAPET);
        corners(lines, 0, 0, wall, View.WALL, 2, false);
        for (int tier = 1; tier <= TIERS; tier++) {
            ring(lines, tier * TIER_STEP, wall + tier * TIER_RISE, tier == 1 ? SEATS : SEATS_FAR, STANDS);
        }
        corners(lines, RIM_STEP, wall + TIERS * TIER_RISE, wall + RIM_RISE, ARCH, 1, true);
        ring(lines, RIM_STEP, wall + RIM_RISE, RIM, STANDS);
    }

    /** The wall's ellipse grown by {@code growth} on both radii, at {@code height}, as straight pieces. */
    private static void ring(short[] lines, int growth, int height, int color, int kind) {
        float radiusX = Coliseum.radiusX + growth;
        float radiusY = Coliseum.radiusY + growth;
        for (int i = 0; i < Coliseum.WALL_SEGMENTS; i++) {
            int j = (i + 1) % Coliseum.WALL_SEGMENTS;
            if (project(radiusX * Coliseum.cornerCos[i] / Coliseum.UNIT,
                    radiusY * Coliseum.cornerSin[i] / Coliseum.UNIT,
                    radiusX * Coliseum.cornerCos[j] / Coliseum.UNIT,
                    radiusY * Coliseum.cornerSin[j] / Coliseum.UNIT)) {
                edge(lines, height, color, kind);
            }
        }
    }

    /** Projects a horizontal piece into {@link #leftX}..{@link #rightX}; false when it is out of view. */
    private static boolean project(float wx1, float wy1, float wx2, float wy2) {
        float x1 = wx1 - View.viewX;
        float y1 = wy1 - View.viewY;
        float x2 = wx2 - View.viewX;
        float y2 = wy2 - View.viewY;
        float depth1 = x1 * View.cos + y1 * View.sin;
        float depth2 = x2 * View.cos + y2 * View.sin;
        if (depth1 < View.NEAR && depth2 < View.NEAR) {
            return false;
        }
        float side1 = x1 * View.sin - y1 * View.cos;
        float side2 = x2 * View.sin - y2 * View.cos;
        if (depth1 < View.NEAR) {
            side1 = side1 + (side2 - side1) * (View.NEAR - depth1) / (depth2 - depth1);
            depth1 = View.NEAR;
        } else if (depth2 < View.NEAR) {
            side2 = side2 + (side1 - side2) * (View.NEAR - depth2) / (depth1 - depth2);
            depth2 = View.NEAR;
        }
        float screen1 = View.CX + side1 * View.FOCAL / depth1;
        float screen2 = View.CX + side2 * View.FOCAL / depth2;
        // Seen from inside, the ring's pieces come out right to left: either order will do.
        if (screen1 > screen2) {
            float swap = screen1;
            screen1 = screen2;
            screen2 = swap;
            swap = depth1;
            depth1 = depth2;
            depth2 = swap;
        }
        if (screen2 - screen1 < 0.01f) {
            return false;
        }
        leftX = screen1;
        rightX = screen2;
        firstColumn = Math.max(0, (int) Math.ceil(leftX));
        lastColumn = Math.min(ViewList.WIDTH - 1, (int) Math.ceil(rightX) - 1);
        if (firstColumn > lastColumn) {
            return false;
        }
        leftDepth = 1f / depth1;
        slope = (1f / depth2 - leftDepth) / (rightX - leftX);
        return true;
    }

    /** Draws the projected piece at {@code height} wherever it shows, raising the horizon above the wall. */
    private static void edge(short[] lines, int height, int color, int kind) {
        int runStart = -1;
        int runRow = 0;
        int previousRow = 0;
        for (int x = firstColumn; x <= lastColumn + 1; x++) {
            boolean visible = false;
            int y = 0;
            if (x <= lastColumn) {
                float inverse = leftDepth + slope * (x + 0.5f - leftX);
                y = View.row(height, View.FOCAL * inverse);
                visible = y >= View.TOP && y <= View.BOTTOM && View.shows(x, y, 1f / inverse)
                        && (kind != STANDS || y < (View.horizon[x] & 0xFF));
                if (kind != FLOOR && y < (View.horizon[x] & 0xFF)) {
                    View.horizon[x] = (byte) Math.max(View.TOP - 1, y);
                }
            }
            if (visible && runStart < 0) {
                runStart = x;
                runRow = y;
            } else if (!visible && runStart >= 0) {
                ViewList.add(lines, runStart, runRow, x - 1, previousRow, color);
                runStart = -1;
            }
            previousRow = y;
        }
    }

    /**
     * Upright lines at every {@code every}-th corner of a ring, from {@code low} up to {@code high}: the wall's
     * buttresses, or the arches of the outer rim, which stand behind the seats ({@code behindSeats}).
     */
    private static void corners(short[] lines, int growth, int low, int high, int color, int every,
                                boolean behindSeats) {
        float radiusX = Coliseum.radiusX + growth;
        float radiusY = Coliseum.radiusY + growth;
        for (int i = 0; i < Coliseum.WALL_SEGMENTS; i += every) {
            float x = radiusX * Coliseum.cornerCos[i] / Coliseum.UNIT - View.viewX;
            float y = radiusY * Coliseum.cornerSin[i] / Coliseum.UNIT - View.viewY;
            float depth = x * View.cos + y * View.sin;
            if (depth < View.NEAR) {
                continue;
            }
            float scale = View.FOCAL / depth;
            int column = Math.round(View.CX + (x * View.sin - y * View.cos) * scale);
            if (column < 0 || column >= ViewList.WIDTH) {
                continue;
            }
            int bottom = View.row(low, scale);
            if (behindSeats) {
                bottom = Math.min(bottom, (View.horizon[column] & 0xFF) - 1);
            }
            View.vertical(lines, column, View.row(high, scale), bottom, depth, color);
        }
    }
}
