package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The score/best header, the lives-and-level footer, and the shared centered-text helper. */
final class Hud {
    private Hud() {
    }

    static void drawHeader(TftTouchShield tft) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, SceneRenderer.SPACE);
        tft.setCursor(24, 4);
        tft.print("1UP");
        tft.setCursor(150, 4);
        tft.print("HIGH SCORE");
        tft.setTextSize(2);
        tft.fillRect(0, 13, Session.WIDTH, 16, SceneRenderer.SPACE);
        tft.setCursor(24, 13);
        tft.print(Session.score);
        tft.setCursor(150, 13);
        tft.print(Math.max(Session.best, Session.score));
    }

    /** Just the numbers, over the previous ones: the score only grows, so nothing is left behind. */
    static void drawScore(TftTouchShield tft) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, SceneRenderer.SPACE);
        tft.setCursor(24, 13);
        tft.print(Session.score);
        if (Session.score > Session.best) {
            tft.setCursor(150, 13);
            tft.print(Session.score);
        }
    }

    static void drawFooter(TftTouchShield tft) {
        int y = Maze.MAZE_Y + Maze.ROWS * Maze.TILE + 8;
        tft.fillRect(0, y - 2, Session.WIDTH, Session.HEIGHT - y + 2, SceneRenderer.SPACE);
        for (int life = 0; life < Math.min(Session.lives - 1, 6); life++) {
            Sprites.drawPacIcon(tft, 24 + life * 18, y + 7, 6, true, true);
        }
        tft.setTextSize(1);
        tft.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.WHITE,
                SceneRenderer.SPACE);
        tft.setCursor(170, y + 4);
        tft.print("LEVEL ");
        tft.print(Session.level);
        tft.print(Controls.autopilot ? " CPU" : "");
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, SceneRenderer.SPACE);
        tft.setCursor((Session.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }
}
