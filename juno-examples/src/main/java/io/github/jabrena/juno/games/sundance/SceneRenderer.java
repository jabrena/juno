package io.github.jabrena.juno.games.sundance;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Builds the vector scene for the grids, hatches, flying suns, traps, and collisions. */
final class SceneRenderer {
    private static final int GRID_COLOR = 0x051F;
    private static final int HATCH_COLOR = TftTouchShield.GREEN;

    private SceneRenderer() {
    }

    static void render(short[] lines, int[] suns, int[] hatches) {
        DisplayList.begin();
        drawGrid(lines, 0);
        drawGrid(lines, 1);
        for (int cell = 0; cell < Grid.CELLS; cell++) {
            if (hatches[cell] > 0) {
                drawHatch(lines, cell, 0);
                drawHatch(lines, cell, 1);
            }
        }
        for (int slot = 0; slot < Session.SUNS; slot++) {
            drawSun(lines, suns, slot);
        }
        DisplayList.present(lines);
    }

    static void drawGrid(short[] lines, int height) {
        for (int index = 0; index <= 3; index++) {
            int y = Grid.screenY(index, height);
            DisplayList.addLine(lines, Grid.screenX(0, index), y, Grid.screenX(3, index), y, GRID_COLOR);
            DisplayList.addLine(lines, Grid.screenX(index, 0), Grid.screenY(0, height),
                    Grid.screenX(index, 3), Grid.screenY(3, height), GRID_COLOR);
        }
    }

    static void drawHatch(short[] lines, int cell, int height) {
        int column = cell % 3;
        int row = cell / 3;
        int x0 = Grid.screenX(column, row);
        int x1 = Grid.screenX(column + 1, row);
        int x2 = Grid.screenX(column + 1, row + 1);
        int x3 = Grid.screenX(column, row + 1);
        int nearY = Grid.screenY(row, height);
        int farY = Grid.screenY(row + 1, height);
        DisplayList.addLine(lines, x0, nearY, x1, nearY, HATCH_COLOR);
        DisplayList.addLine(lines, x1, nearY, x2, farY, HATCH_COLOR);
        DisplayList.addLine(lines, x2, farY, x3, farY, HATCH_COLOR);
        DisplayList.addLine(lines, x3, farY, x0, nearY, HATCH_COLOR);
        DisplayList.addLine(lines, x0, nearY, x2, farY, HATCH_COLOR);
        DisplayList.addLine(lines, x1, nearY, x3, farY, HATCH_COLOR);
    }

    private static void drawSun(short[] lines, int[] suns, int slot) {
        int base = slot * Session.S_STRIDE;
        int state = suns[base + Session.S_STATE];
        int color = sunColor(suns[base + Session.S_COLOR]);
        if (state == Session.FLYING) {
            drawFlyingSun(lines, suns, base, color);
        } else if (state == Session.TRAPPED) {
            drawTrappedSun(lines, suns, base, color);
        } else if (state == Session.BURST) {
            drawBurst(lines, suns, base, color);
        }
    }

    private static void drawFlyingSun(short[] lines, int[] suns, int base, int color) {
        float depth = Grid.sunD(suns, base);
        drawStar(lines, Grid.sunScreenX(suns, base), Grid.sunScreenY(suns, base), Grid.sunRadius(depth), color,
                Session.frame / 3 % 2);
        int target = suns[base + Session.S_TO];
        float targetDepth = target / 3 + 0.5f;
        int x = Grid.screenX(target % 3 + 0.5f, targetDepth);
        int y = Grid.screenY(targetDepth, suns[base + Session.S_UP]);
        DisplayList.addLine(lines, x - 3, y, x + 3, y, color);
        DisplayList.addLine(lines, x, y - 2, x, y + 2, color);
    }

    private static void drawTrappedSun(short[] lines, int[] suns, int base, int color) {
        int cell = suns[base + Session.S_FROM];
        float depth = cell / 3 + 0.5f;
        int x = Grid.screenX(cell % 3 + 0.5f, depth);
        int y = Grid.screenY(depth, suns[base + Session.S_UP]);
        int beyond = (Session.TRAP_FRAMES - suns[base + Session.S_TIMER]) * 2;
        y = suns[base + Session.S_UP] == 1 ? y - beyond : y + beyond;
        int radius = Math.max(1, Grid.sunRadius(depth) * suns[base + Session.S_TIMER] / Session.TRAP_FRAMES);
        drawStar(lines, x, y, radius, color, 0);
    }

    private static void drawBurst(short[] lines, int[] suns, int base, int color) {
        int radius = 4 + (Session.BURST_FRAMES - suns[base + Session.S_TIMER]) * 3;
        int x = suns[base + Session.S_FROM];
        int y = suns[base + Session.S_TO];
        for (int ray = 0; ray < 8; ray++) {
            DisplayList.addLine(lines, x + directionX(ray) * radius / 200, y + directionY(ray) * radius / 200,
                    x + directionX(ray) * radius / 100, y + directionY(ray) * radius / 100,
                    (ray & 1) == 0 ? TftTouchShield.WHITE : color);
        }
    }

    /** An octagon with four rays that flicker between straight and diagonal directions. */
    static void drawStar(short[] lines, int x, int y, int radius, int color, int spin) {
        for (int point = 0; point < 8; point++) {
            DisplayList.addLine(lines, x + directionX(point) * radius / 100,
                    y + directionY(point) * radius / 100, x + directionX(point + 1) * radius / 100,
                    y + directionY(point + 1) * radius / 100, color);
        }
        for (int ray = spin; ray < 8; ray = ray + 2) {
            DisplayList.addLine(lines, x + directionX(ray) * (radius + 2) / 100,
                    y + directionY(ray) * (radius + 2) / 100, x + directionX(ray) * (radius + 6) / 100,
                    y + directionY(ray) * (radius + 6) / 100, color);
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

    private static int directionX(int point) {
        int index = point % 8;
        if (index == 0) {
            return 100;
        }
        if (index == 1 || index == 7) {
            return 71;
        }
        if (index == 2 || index == 6) {
            return 0;
        }
        if (index == 3 || index == 5) {
            return -71;
        }
        return -100;
    }

    private static int directionY(int point) {
        return directionX(point + 6);
    }
}
