package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The score/best header, the lives-and-level footer, and the shared centered-text helper. */
final class Hud {
    private Hud() {
    }

    static void drawHeader() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SceneRenderer.SPACE);
        TftTouchShield.setCursor(24, 4);
        TftTouchShield.print("1UP");
        TftTouchShield.setCursor(150, 4);
        TftTouchShield.print("HIGH SCORE");
        TftTouchShield.setTextSize(2);
        TftTouchShield.fillRect(0, 13, Session.WIDTH, 16, SceneRenderer.SPACE);
        TftTouchShield.setCursor(24, 13);
        TftTouchShield.print(Session.score);
        TftTouchShield.setCursor(150, 13);
        TftTouchShield.print(Math.max(Session.best, Session.score));
    }

    /** Just the numbers, over the previous ones: the score only grows, so nothing is left behind. */
    static void drawScore() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, SceneRenderer.SPACE);
        TftTouchShield.setCursor(24, 13);
        TftTouchShield.print(Session.score);
        if (Session.score > Session.best) {
            TftTouchShield.setCursor(150, 13);
            TftTouchShield.print(Session.score);
        }
    }

    static void drawFooter() {
        int y = Maze.MAZE_Y + Maze.ROWS * Maze.TILE + 8;
        TftTouchShield.fillRect(0, y - 2, Session.WIDTH, Session.HEIGHT - y + 2, SceneRenderer.SPACE);
        for (int life = 0; life < Math.min(Session.lives - 1, 6); life++) {
            Sprites.drawPacIcon(24 + life * 18, y + 7, 6, true, true);
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(Controls.autopilot ? TftTouchShield.MAGENTA : TftTouchShield.WHITE,
                SceneRenderer.SPACE);
        TftTouchShield.setCursor(170, y + 4);
        TftTouchShield.print("LEVEL ");
        TftTouchShield.print(Session.level);
        TftTouchShield.print(Controls.autopilot ? " CPU" : "");
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, SceneRenderer.SPACE);
        TftTouchShield.setCursor((Session.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
