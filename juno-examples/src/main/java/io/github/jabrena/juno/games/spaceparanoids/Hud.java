package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Header, drive-button bar, radar and centered messages. */
final class Hud {
    static final int BUTTON = 0x3A7F;
    private static final int RADAR_WALL = 0x2945;
    private static final int RADAR_X = 179;
    private static final int RADAR_Y = 201;
    private static final int RADAR_CELL = 3;

    private Hud() {
    }

    static void drawBar(byte[] maze, byte[] radar) {
        TftTouchShield.fillRect(0, DisplayList.BAR_TOP, DisplayList.WIDTH,
                DisplayList.HEIGHT - DisplayList.BAR_TOP, DisplayList.SPACE);
        button(0, 50, "<");
        button(50, 50, "^");
        button(100, 76, "FIRE");
        button(222, 49, "v");
        button(271, 49, ">");
        for (int cell = 0; cell < Maze.CELLS; cell++) {
            radar[cell] = 0;
            if (maze[cell] != Maze.OPEN) {
                radarCell(cell, RADAR_WALL);
            }
        }
    }

    private static void button(int x, int width, String label) {
        TftTouchShield.drawRect(x + 2, DisplayList.BAR_TOP + 3, width - 4,
                DisplayList.HEIGHT - DisplayList.BAR_TOP - 5, BUTTON);
        showAt(label, x + (width - label.length() * 12) / 2,
                DisplayList.BAR_TOP + 13, 2, TftTouchShield.WHITE);
    }

    private static void radarCell(int cell, int color) {
        TftTouchShield.fillRect(RADAR_X + (cell % Maze.SIZE) * RADAR_CELL,
                RADAR_Y + (cell / Maze.SIZE) * RADAR_CELL, RADAR_CELL, RADAR_CELL, color);
    }

    static void updateRadar(byte[] radar, int[] fs, int[] is) {
        for (int cell = 0; cell < Maze.CELLS; cell++) {
            if (radar[cell] != 0) {
                radar[cell] = 0;
                radarCell(cell, DisplayList.SPACE);
            }
        }
        for (int slot = 0; slot < Entities.ENTITIES; slot++) {
            int type = is[slot * Entities.I_STRIDE + Entities.I_TYPE];
            int color = entityColor(type);
            if (color >= 0) {
                int cell = Entities.entityCell(fs, slot);
                radar[cell] = 1;
                radarCell(cell, color);
            }
        }
        int me = Maze.cellOf(Camera.posX, Camera.posZ);
        radar[me] = 1;
        radarCell(me, TftTouchShield.WHITE);
    }

    private static int entityColor(int type) {
        if (type == Entities.T_HUNTER) {
            return SceneRenderer.HUNTER;
        }
        if (type == Entities.T_TANK) {
            return SceneRenderer.TANK;
        }
        if (type == Entities.T_TURRET) {
            return SceneRenderer.TURRET;
        }
        if (type == Entities.T_POOL) {
            return SceneRenderer.POOL;
        }
        return -1;
    }

    static void drawHeader() {
        TftTouchShield.fillRect(0, 0, DisplayList.WIDTH, DisplayList.HEADER - 1, DisplayList.SPACE);
        TftTouchShield.drawHorizontalLine(0, DisplayList.HEADER - 1, DisplayList.WIDTH, BUTTON);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, DisplayList.SPACE);
        TftTouchShield.setCursor(4, 2);
        TftTouchShield.print("SCORE ");
        TftTouchShield.print(Session.score);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, DisplayList.SPACE);
        TftTouchShield.setCursor(124, 2);
        TftTouchShield.print("HI ");
        TftTouchShield.print(Session.best);
        TftTouchShield.setCursor(250, 2);
        TftTouchShield.print("SECTOR ");
        TftTouchShield.print(Session.level);
        if (Controls.autopilot) {
            TftTouchShield.setTextColor(TftTouchShield.MAGENTA, DisplayList.SPACE);
            TftTouchShield.setCursor(208, 2);
            TftTouchShield.print("CPU");
        }
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        TftTouchShield.setCursor(4, 11);
        TftTouchShield.print("LIVES ");
        TftTouchShield.print(Session.lives);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, DisplayList.SPACE);
        TftTouchShield.setCursor(196, 11);
        TftTouchShield.print("SHIELD");
        int color = TftTouchShield.GREEN;
        if (Session.shield <= 25) {
            color = TftTouchShield.RED;
        } else if (Session.shield <= 50) {
            color = TftTouchShield.YELLOW;
        }
        int bar = Session.shield * 76 / Session.MAX_SHIELD;
        TftTouchShield.drawRect(236, 10, 80, 8, BUTTON);
        TftTouchShield.fillRect(238, 12, bar, 4, color);
        drawStatus();
    }

    static void drawStatus() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(Session.timeLeft <= 15 ? TftTouchShield.RED : TftTouchShield.WHITE,
                DisplayList.SPACE);
        TftTouchShield.setCursor(58, 11);
        TftTouchShield.print("TIME ");
        TftTouchShield.print(Math.max(0, Session.timeLeft));
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(SceneRenderer.HUNTER, DisplayList.SPACE);
        TftTouchShield.setCursor(118, 11);
        TftTouchShield.print("HUNTERS ");
        TftTouchShield.print(Session.huntersLeft);
        TftTouchShield.print(" ");
    }

    static void showCentered(String text, int y, int size, int color) {
        showAt(text, (DisplayList.WIDTH - text.length() * 6 * size) / 2, y, size, color);
    }

    static void showAt(String text, int x, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.SPACE);
        TftTouchShield.setCursor(x, y);
        TftTouchShield.print(text);
    }
}
