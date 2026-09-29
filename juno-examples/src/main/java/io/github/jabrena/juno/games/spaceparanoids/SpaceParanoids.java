package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Space Paranoids on the ELEGOO 2.8" TFT touch screen shield, after the arcade game from TRON.
 * Drive a tank through a wireframe maze and destroy every flying hunter before time runs out.
 * Tap the header to switch between human control and the CPU autopilot.
 */
@Board(ArduinoUnoQ.class)
public final class SpaceParanoids {
    private static final int FRAME_MILLIS = 33;
    private static final int COVER_TIMEOUT_MILLIS = 60_000;

    private SpaceParanoids() {
    }

    public static void main(String[] args) {
        byte[] maze = new byte[Maze.CELLS];
        short[] paths = new short[2 * Maze.CELLS];
        byte[] radar = new byte[Maze.CELLS];
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        int[] depth = new int[SceneRenderer.SAMPLES];
        int[] faces = new int[SceneRenderer.SAMPLES];
        int[] fs = new int[Entities.ENTITIES * Entities.F_STRIDE];
        int[] is = new int[Entities.ENTITIES * Entities.I_STRIDE];
        short[] route = new short[2 * Maze.CELLS];
        byte[] letter = new byte[1];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.opening(lines);
                Interludes.drawTitle(maze, paths, lines, depth, faces, fs, is, letter);
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Random.seed(Clock.micros());
            Controls.choosePilot();
            Session.newGame();
            while (playLevel(maze, paths, route, radar, lines, depth, faces, fs, is)) {
                Session.level = Session.level + 1;
            }
            Session.endGame();
            Hud.drawHeader();
            DisplayList.clearView();
            Hud.showCentered("GAME OVER", 90, 3, TftTouchShield.RED);
            Delay.millis(3000);
        }
    }

    /** Plays one sector; returns true when it is cleared, false when the last life is lost. */
    private static boolean playLevel(byte[] maze, short[] paths, short[] route, byte[] radar,
            short[] lines, int[] depth, int[] faces, int[] fs, int[] is) {
        Sector.start(maze, paths, fs, is);
        Sector.announce(maze, radar);

        int next = Clock.millis();
        int second = next + 1000;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            Session.frame = Session.frame + 1;
            Controls.handleTouch(maze, fs, is);
            if (Controls.autopilot) {
                AutopilotSP.fly(maze, route, fs, is);
            }
            Entities.move(maze, paths, fs, is);
            SceneRenderer.render(maze, lines, depth, faces, fs, is);
            if (Session.frame % 6 == 0) {
                Hud.updateRadar(radar, fs, is);
            }
            if (Clock.millis() - second >= 0) {
                second = second + 1000;
                Session.timeLeft = Session.timeLeft - 1;
                Hud.drawStatus();
            }
            if (Session.huntersLeft == 0) {
                Sector.awardClear();
                return true;
            }
            if (Session.shield <= 0 || Session.timeLeft <= 0) {
                boolean timeUp = Session.timeLeft <= 0;
                Session.loseLife(timeUp);
                if (Session.lives == 0) {
                    return false;
                }
                Sector.respawn(maze, is);
                if (timeUp) {
                    Session.timeLeft = Session.levelTime();
                }
                DisplayList.clearView();
                Hud.drawHeader();
                next = Clock.millis();
                second = next + 1000;
            }
        }
    }
}
