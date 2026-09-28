package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Creates, announces, resets and rewards one sector of the game. */
final class Sector {
    private Sector() {
    }

    static void start(byte[] maze, short[] paths, int[] fs, int[] is) {
        Maze.generate(maze, paths);
        Entities.clear(fs, is);
        Camera.placePlayer(maze);
        Session.shield = Session.MAX_SHIELD;
        Session.timeLeft = Session.levelTime();
        Combat.fireCooldown = 0;
        Maze.bfs(maze, paths, Maze.cellOf(Camera.posX, Camera.posZ));
        int hunters = Math.min(2 + Session.level, 8);
        int tanks = Math.min(Session.level - 1, 3);
        int turrets = Math.min(1 + Session.level / 2, 4);
        for (int i = 0; i < hunters; i++) {
            Entities.place(maze, paths, fs, is, Entities.T_HUNTER, 4);
        }
        for (int i = 0; i < tanks; i++) {
            Entities.place(maze, paths, fs, is, Entities.T_TANK, 5);
        }
        for (int i = 0; i < turrets; i++) {
            Entities.place(maze, paths, fs, is, Entities.T_TURRET, 4);
        }
        for (int i = 0; i < 3; i++) {
            Entities.place(maze, paths, fs, is, Entities.T_POOL, 2);
        }
        Session.huntersLeft = Entities.count(is, Entities.T_HUNTER);
    }

    static void announce(byte[] maze, byte[] radar) {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        Hud.drawHeader();
        Hud.drawBar(maze, radar);
        Hud.showCentered("SECTOR", 76, 3, TftTouchShield.ORANGE);
        TftTouchShield.setCursor(Session.level < 10 ? 151 : 142, 108);
        TftTouchShield.print(Session.level);
        Hud.showCentered("Destroy the hunters", 140, 1, TftTouchShield.WHITE);
        Delay.millis(1400);
        DisplayList.clearView();
    }

    static void respawn(byte[] maze, int[] is) {
        Camera.placePlayer(maze);
        Session.shield = Session.MAX_SHIELD;
        Controls.release();
        Entities.clearTransient(is);
    }

    static void awardClear() {
        int bonus = 10 * Session.timeLeft * Session.level;
        Session.addScore(bonus);
        Hud.drawHeader();
        DisplayList.clearView();
        Hud.showCentered("SECTOR CLEAR", 86, 2, TftTouchShield.GREEN);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SPACE);
        TftTouchShield.setCursor(118, 120);
        TftTouchShield.print("TIME BONUS ");
        TftTouchShield.print(bonus);
        Delay.millis(2000);
    }
}
