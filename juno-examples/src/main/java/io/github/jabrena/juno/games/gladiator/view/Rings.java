package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.arena.Coliseum;

/**
 * The coliseum round the sand, for a {@link View}: the elliptical wall as a ring of {@link Coliseum#WALL_SEGMENTS}
 * straight pieces with buttresses, then a tier of seats, a wider and higher ring. A ring above the wall shows only
 * above the rings already drawn in front of it (the per-column {@linkplain View#horizon horizon}), and every ring only
 * where no pillar hides it.
 */
public final class Rings {
    private static final int TIERS = 1;
    private static final int TIER_STEP = 90;
    private static final int TIER_RISE = 70;

    // One repeated byte each, like the view's other colors.
    public static final int SAND = 0xE6E6;
    public static final int SEATS = 0xC5C5;
    public static final int SEATS_FAR = 0xA4A4;

    /** What a ring's line has to clear: only the pillars, or also the rings already drawn in front of it. */
    private static final int FLOOR = 0;
    private static final int PARAPET = 1;
    private static final int STANDS = 2;

    private final View view;
    // The piece being drawn, in screen space.
    private float leftX;
    private float rightX;
    private float leftDepth;
    private float slope;
    private int firstColumn;
    private int lastColumn;

    public Rings(View view) {
        this.view = view;
    }

    public void draw() {
        int wall = view.coliseum().wallHeight();
        ring(0, 0, SAND, FLOOR);
        ring(0, wall, View.WALL, PARAPET);
        buttresses(wall);
        // One tier of seats above the wall: the upper tiers, the arches and the rim cost about a third of every
        // frame's pixels on the shield's bus, for decoration far from the fight.
        for (int tier = 1; tier <= TIERS; tier++) {
            ring(tier * TIER_STEP, wall + tier * TIER_RISE, tier == 1 ? SEATS : SEATS_FAR, STANDS);
        }
    }

    /** The wall's ellipse grown by {@code growth} on both radii, at {@code height}, as straight pieces. */
    private void ring(int growth, int height, int color, int kind) {
        float radiusX = view.coliseum().radiusX() + growth;
        float radiusY = view.coliseum().radiusY() + growth;
        for (int i = 0; i < Coliseum.WALL_SEGMENTS; i++) {
            int j = (i + 1) % Coliseum.WALL_SEGMENTS;
            if (project(radiusX * Coliseum.CORNER_COS[i] / Coliseum.UNIT,
                    radiusY * Coliseum.CORNER_SIN[i] / Coliseum.UNIT,
                    radiusX * Coliseum.CORNER_COS[j] / Coliseum.UNIT,
                    radiusY * Coliseum.CORNER_SIN[j] / Coliseum.UNIT)) {
                edge(height, color, kind);
            }
        }
    }

    /** Projects a horizontal piece into {@link #leftX}..{@link #rightX}; false when it is out of view. */
    private boolean project(float wx1, float wy1, float wx2, float wy2) {
        float x1 = wx1 - view.viewX();
        float y1 = wy1 - view.viewY();
        float x2 = wx2 - view.viewX();
        float y2 = wy2 - view.viewY();
        float depth1 = x1 * view.cos() + y1 * view.sin();
        float depth2 = x2 * view.cos() + y2 * view.sin();
        if (depth1 < View.NEAR && depth2 < View.NEAR) {
            return false;
        }
        float side1 = x1 * view.sin() - y1 * view.cos();
        float side2 = x2 * view.sin() - y2 * view.cos();
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
    private void edge(int height, int color, int kind) {
        int runStart = -1;
        int runRow = 0;
        int previousRow = 0;
        for (int x = firstColumn; x <= lastColumn + 1; x++) {
            boolean visible = false;
            int y = 0;
            if (x <= lastColumn) {
                float inverse = leftDepth + slope * (x + 0.5f - leftX);
                y = View.row(height, View.FOCAL * inverse);
                visible = y >= View.TOP && y <= View.BOTTOM && view.shows(x, y, 1f / inverse)
                        && (kind != STANDS || y < view.horizon(x));
                if (kind != FLOOR && y < view.horizon(x)) {
                    view.raiseHorizon(x, Math.max(View.TOP - 1, y));
                }
            }
            if (visible && runStart < 0) {
                runStart = x;
                runRow = y;
            } else if (!visible && runStart >= 0) {
                view.list().add(runStart, runRow, x - 1, previousRow, color);
                runStart = -1;
            }
            previousRow = y;
        }
    }

    /** Upright lines at every other corner of the wall, from the sand up to {@code high}: its buttresses. */
    private void buttresses(int high) {
        float radiusX = view.coliseum().radiusX();
        float radiusY = view.coliseum().radiusY();
        for (int i = 0; i < Coliseum.WALL_SEGMENTS; i += 2) {
            float x = radiusX * Coliseum.CORNER_COS[i] / Coliseum.UNIT - view.viewX();
            float y = radiusY * Coliseum.CORNER_SIN[i] / Coliseum.UNIT - view.viewY();
            float depth = x * view.cos() + y * view.sin();
            if (depth < View.NEAR) {
                continue;
            }
            float scale = View.FOCAL / depth;
            int column = Math.round(View.CX + (x * view.sin() - y * view.cos()) * scale);
            if (column >= 0 && column < ViewList.WIDTH) {
                view.vertical(column, View.row(high, scale), View.row(0, scale), depth, View.WALL);
            }
        }
    }
}
