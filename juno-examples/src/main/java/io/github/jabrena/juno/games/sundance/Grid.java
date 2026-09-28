package io.github.jabrena.juno.games.sundance;

/** Perspective projection and touch hit-testing for Sundance's paired three-by-three grids. */
final class Grid {
    static final int CELLS = 9;
    private static final int CENTER_X = 160;
    private static final int FLOOR_NEAR = 232;
    private static final int CEILING_NEAR = 28;
    private static final int GRID_DEPTH = 72;
    private static final int NEAR_HALF_WIDTH = 150;
    private static final float PERSPECTIVE = 0.25f;
    private static final float FAR_FRACTION = 0.42857143f;

    private Grid() {
    }

    /** The square (row * 3 + column, row 0 nearest) of either grid under a point, or -1. */
    static int cellAt(int x, int y) {
        for (int grid = 0; grid < 2; grid++) {
            int cell = cellAtGrid(x, y, grid);
            if (cell >= 0) {
                return cell;
            }
        }
        return -1;
    }

    private static int cellAtGrid(int x, int y, int grid) {
        for (int row = 0; row < 3; row++) {
            int near = screenY(row, grid);
            int far = screenY(row + 1, grid);
            if (y >= Math.min(near, far) && y <= Math.max(near, far)) {
                float depth = row + (y - near) / (float) (far - near);
                float across = (x - CENTER_X) * 1.5f * (1 + PERSPECTIVE * depth) / NEAR_HALF_WIDTH + 1.5f;
                if (across >= 0 && across < 3) {
                    return row * 3 + (int) across;
                }
            }
        }
        return -1;
    }

    /** Column position of a flying sun, 0..3 across the grids. */
    static float sunU(int[] suns, int base) {
        float progress = suns[base + Session.S_T] / (float) suns[base + Session.S_DUR];
        float from = suns[base + Session.S_FROM] % 3 + 0.5f;
        float to = suns[base + Session.S_TO] % 3 + 0.5f;
        return from + (to - from) * progress;
    }

    /** Depth of a flying sun, 0..3 from the near edge of the grids. */
    static float sunD(int[] suns, int base) {
        float progress = suns[base + Session.S_T] / (float) suns[base + Session.S_DUR];
        float from = suns[base + Session.S_FROM] / 3 + 0.5f;
        float to = suns[base + Session.S_TO] / 3 + 0.5f;
        return from + (to - from) * progress;
    }

    /** Height of a flying sun: 0 on the lower grid, 1 on the upper one. */
    static float sunH(int[] suns, int base) {
        float progress = suns[base + Session.S_T] / (float) suns[base + Session.S_DUR];
        return suns[base + Session.S_UP] == 1 ? progress : 1 - progress;
    }

    static int sunScreenX(int[] suns, int base) {
        return screenX(sunU(suns, base), sunD(suns, base));
    }

    static int sunScreenY(int[] suns, int base) {
        return screenY(sunD(suns, base), sunH(suns, base));
    }

    static int screenX(float across, float depth) {
        return CENTER_X
                + Math.round((across - 1.5f) * NEAR_HALF_WIDTH / (1.5f * (1 + PERSPECTIVE * depth)));
    }

    /** Screen row of a point at depth and height (0 lower grid, 1 upper grid). */
    static int screenY(float depth, float height) {
        float recede = (1 - 1 / (1 + PERSPECTIVE * depth)) / FAR_FRACTION * GRID_DEPTH;
        float lower = FLOOR_NEAR - recede;
        float upper = CEILING_NEAR + recede;
        return Math.round(lower + (upper - lower) * height);
    }

    static int sunRadius(float depth) {
        return Math.round(13 / (1 + PERSPECTIVE * depth));
    }
}
