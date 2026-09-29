package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Pac-Man on the ELEGOO 2.8" TFT touch screen shield, on the arcade's 28x31-tile maze with 8-pixel
 * tiles. Touch and hold beside, above or below Pac-Man to steer: the turn is taken at the next
 * junction where it fits. Eat all 244 dots to clear the level; an energizer turns the ghosts blue
 * for a while so you can eat them (200, 400, 800, 1600). Three lives, an extra one at
 * {@value Session#EXTRA_LIFE_SCORE} points.
 *
 * <p>The cover animates two ghosts chasing Pac-Man across the screen, an energizer turning the
 * tables so he chases them back, and the title zooming into a soft drop shadow. Then choose the
 * pilot: <b>HUMAN</b> to play yourself, or <b>CPU</b> to watch an autopilot that favors dots and
 * energizers, flees an active ghost nearby and hunts down a frightened one instead, missing a turn
 * now and then like a person would; tap the header during a life to switch between the two.
 *
 * <p>The ghosts follow the arcade's rules: at every tile they turn towards a target tile, never
 * reversing; Blinky targets Pac-Man, Pinky four tiles ahead of him, Inky the point that mirrors
 * Blinky around the tile two ahead of him, and Clyde Pac-Man only while more than eight tiles away.
 * Scatter and chase phases alternate (each switch reverses the ghosts), the ghosts slow down in the
 * side tunnel, and they leave the house as Pac-Man eats dots.
 *
 * <p>The package is split by responsibility: {@link Session} owns score, lives, screen geometry and
 * the movement/eating/phase rules; {@link Ghosts} is the ghosts' own targeting and stepping;
 * {@link Maze} owns the tile layout and its pixel-level wall/dot rendering; {@link Controls} owns
 * touch input and pilot selection; {@link AutopilotPacMan} is the CPU player; {@link SceneRenderer}
 * composites Pac-Man and the ghosts over the maze (only the rectangles around moving sprites are
 * redrawn, streamed with {@code beginPixels}/{@code pushPixel}); {@link Hud} and {@link Interludes}
 * own the header/footer and the non-gameplay screens. This class only orchestrates the game loop.
 * Pac-Man targets the <b>Arduino UNO Q</b>.
 */
@Board(ArduinoUnoQ.class)
public final class PacMan {
    private static final int COVER_TIMEOUT_MILLIS = 60_000;

    private PacMan() {
    }

    public static void main(String[] args) {
        byte[] tiles = new byte[Maze.TILES];
        int[] ghosts = new int[Session.GHOSTS * Session.G_STRIDE];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            boolean tapped = false;
            while (!tapped) {
                Interludes.cover();
                tapped = Controls.waitForTap(COVER_TIMEOUT_MILLIS);
            }
            Controls.choosePilot();
            Random.seed(Clock.micros());
            Session.newGame();
            TftTouchShield.fillScreen(SceneRenderer.SPACE);
            while (Session.lives > 0) {
                Maze.loadMaze(tiles);
                Session.dotsEaten = 0;
                SceneRenderer.drawMaze(tiles, ghosts);
                Hud.drawHeader();
                boolean cleared = false;
                while (Session.lives > 0 && !cleared) {
                    cleared = playLife(tiles, ghosts);
                    if (!cleared) {
                        Session.lives = Session.lives - 1;
                    }
                }
                if (cleared) {
                    Interludes.flashMaze(tiles, ghosts);
                    Session.level = Session.level + 1;
                }
            }
            Session.finishGame();
            Interludes.gameOver();
            Controls.waitForTap(COVER_TIMEOUT_MILLIS);
        }
    }

    /** Plays from the starting positions until Pac-Man dies (false) or the maze is cleared (true). */
    private static boolean playLife(byte[] tiles, int[] ghosts) {
        Session.resetActors(ghosts);
        Controls.resetForLife();
        Hud.drawFooter();
        SceneRenderer.redrawActors(tiles, ghosts, true);
        Interludes.readyCard();
        SceneRenderer.redrawArea(tiles, ghosts, 80, 17 * Maze.TILE, 72, Maze.TILE);

        int next = Clock.millis();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + Session.FRAME_MILLIS;

            Controls.steer();
            if (Controls.autopilot) {
                AutopilotPacMan.fly(tiles, ghosts);
            }
            Session.sinceLastDot = Session.sinceLastDot + 1;
            Session.updatePhase(ghosts);
            Session.movePac(tiles, ghosts);
            Ghosts.releaseGhosts(ghosts);
            for (int g = 0; g < Session.GHOSTS; g++) {
                Ghosts.moveGhost(tiles, ghosts, g);
            }
            int collision = Session.checkCollisions(tiles, ghosts);
            SceneRenderer.redrawActors(tiles, ghosts, false);
            if (collision < 0) {
                Interludes.dieAnimation(tiles, ghosts);
                return false;
            }
            if (collision > 0) {
                // Eating a ghost paused the game: do not rush to catch up.
                next = Clock.millis();
            }
            if (Session.dotsEaten == Maze.TOTAL_DOTS) {
                Delay.millis(1000);
                return true;
            }
        }
    }
}
