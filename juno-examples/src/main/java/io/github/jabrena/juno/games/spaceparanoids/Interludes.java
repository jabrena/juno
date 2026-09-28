package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The digitizing scan, rushing grid and materializing title scenes. */
final class Interludes {
    private static final int SCAN_FRAMES = 36;
    private static final int GRID_FRAMES = 64;
    private static final int GRID_HORIZON = 90;
    private static final int GRID_DEPTH = 436;
    private static final String TITLE = "SPACE PARANOIDS";

    private Interludes() {
    }

    static void opening(short[] lines) {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        DisplayList.shown = 0;
        Hud.showCentered("DIGITIZING...", 212, 1, SceneRenderer.WALL_TOP);
        for (int f = 0; f <= SCAN_FRAMES; f++) {
            int scanY = DisplayList.HEADER
                    + (DisplayList.VIEW_BOTTOM - DisplayList.HEADER) * f / SCAN_FRAMES;
            DisplayList.begin();
            drawGrid(lines, 0, scanY);
            DisplayList.addLine(lines, 0, scanY, DisplayList.WIDTH - 1, scanY, TftTouchShield.RED);
            DisplayList.present(lines);
            Delay.millis(30);
        }
        TftTouchShield.fillRect(0, DisplayList.BAR_TOP, DisplayList.WIDTH,
                DisplayList.HEIGHT - DisplayList.BAR_TOP, DisplayList.SPACE);
        for (int f = 0; f < GRID_FRAMES; f++) {
            DisplayList.begin();
            drawGrid(lines, f * 6, DisplayList.VIEW_BOTTOM);
            int size = 4 + f / 3;
            int cx = Camera.CENTER_X + Math.round((float) Math.sin(f * 0.09f) * 40);
            int cy = GRID_HORIZON - 12 - f / 2;
            float spin = f * 0.25f;
            int previousX = 0;
            int previousY = 0;
            for (int k = 0; k <= 4; k++) {
                float a = spin + k * 1.5708f;
                int x = cx + Math.round((float) Math.cos(a) * size);
                int y = cy + Math.round((float) Math.sin(a) * size * 0.3f);
                DisplayList.addLine(lines, cx, cy - size * 6 / 5, x, y, SceneRenderer.HUNTER);
                if (k > 0) {
                    DisplayList.addLine(lines, previousX, previousY, x, y, SceneRenderer.HUNTER);
                }
                if ((k & 1) == 0) {
                    DisplayList.addLine(lines, cx, cy + size * 6 / 5, x, y, TftTouchShield.YELLOW);
                }
                previousX = x;
                previousY = y;
            }
            DisplayList.present(lines);
            Delay.millis(30);
        }
        DisplayList.clearView();
        Delay.millis(200);
    }

    private static void drawGrid(short[] lines, int travel, int limitY) {
        if (limitY <= GRID_HORIZON) {
            return;
        }
        DisplayList.addLine(lines, 0, GRID_HORIZON, DisplayList.WIDTH - 1,
                GRID_HORIZON, SceneRenderer.WALL_TOP);
        for (int k = 0; k < 12; k++) {
            int z = 16 + k * 96 + (96 - travel % 96);
            int y = GRID_HORIZON + GRID_DEPTH * 16 / z;
            if (y < limitY) {
                DisplayList.addLine(lines, 0, y, DisplayList.WIDTH - 1, y, 0x0410);
            }
        }
        for (int col = -8; col <= 8; col++) {
            int nearX = Camera.CENTER_X + col * 20 * (limitY - GRID_HORIZON) / 36;
            DisplayList.addLine(lines, Camera.CENTER_X + col * 2, GRID_HORIZON,
                    nearX, limitY, 0x0410);
        }
    }

    static void drawTitle(byte[] maze, short[] paths, short[] lines, int[] depth, int[] faces,
                          int[] fs, int[] is, byte[] letter) {
        Random.seed(7);
        Maze.generate(maze, paths);
        Camera.placePlayer(maze);
        Entities.clear(fs, is);
        SceneRenderer.render(maze, lines, depth, faces, fs, is);
        int left = (DisplayList.WIDTH - TITLE.length() * 18) / 2;
        TftTouchShield.setTextSize(3);
        for (int k = 0; k < TITLE.length(); k++) {
            int i = k * 7 % TITLE.length();
            letter[0] = (byte) TITLE.charAt(i);
            TftTouchShield.setTextColor(SceneRenderer.WALL_TOP, DisplayList.SPACE);
            TftTouchShield.setCursor(left + i * 18, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(70);
        }
        Delay.millis(150);
        Hud.showCentered(TITLE, 34, 3, TftTouchShield.WHITE);
        Delay.millis(80);
        Hud.showCentered(TITLE, 34, 3, TftTouchShield.ORANGE);
        Hud.showCentered("Destroy every hunter before time runs out", 206, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap to start", 222, 1, TftTouchShield.CYAN);
    }
}
