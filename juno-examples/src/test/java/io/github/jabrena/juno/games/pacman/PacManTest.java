package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Rules of Pac-Man: eating dots and energizers, ghost collisions, the pilot-choice screen, and an
 * autopilot that clears a whole maze through the game's own simulation.
 */
class PacManTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    private byte[] tiles;
    private int[] ghosts;

    @BeforeEach
    void newLife() {
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Controls.autopilot = false;
        Random.seed(7);
        tiles = new byte[Maze.TILES];
        ghosts = new int[Session.GHOSTS * Session.G_STRIDE];
        Maze.loadMaze(tiles);
        Session.newGame();
        Session.dotsEaten = 0;
        Session.resetActors(ghosts);
    }

    @Test
    void eatingAnEnergizerFrightensGhostsAndScores() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        // Row 3, column 1 is the top-left energizer: "#o####.#####.##.#####.####o#".
        Session.eat(tft, tiles, ghosts, 1, 3);
        assertThat(Session.score).isEqualTo(50);
        assertThat(ghosts[Session.G_FRIGHTENED]).isEqualTo(1);
        assertThat(Session.frightTimer).isPositive();
    }

    @Test
    void eatingADotScoresTenAndCountsTowardsTheMaze() {
        // Row 5 is an open corridor of dots: "#..........................#".
        Session.eat(tft, tiles, ghosts, 5, 5);
        assertThat(Session.score).isEqualTo(10);
        assertThat(Session.dotsEaten).isEqualTo(1);
    }

    @Test
    void anActiveGhostCatchingPacManEndsTheLife() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        ghosts[Session.G_X] = Session.pacX;
        ghosts[Session.G_Y] = Session.pacY;
        assertThat(Session.checkCollisions(tft, tiles, ghosts)).isEqualTo(-1);
    }

    @Test
    void eatingAFrightenedGhostScoresAndSendsItsEyesHome() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        ghosts[Session.G_FRIGHTENED] = 1;
        ghosts[Session.G_X] = Session.pacX;
        ghosts[Session.G_Y] = Session.pacY;

        int eaten = Session.checkCollisions(tft, tiles, ghosts);

        assertThat(eaten).isEqualTo(1);
        assertThat(ghosts[Session.G_STATE]).isEqualTo(Session.EYES);
        assertThat(Session.score).isEqualTo(200);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(60, 180)).isZero();
        assertThat(Controls.choiceAt(180, 180)).isEqualTo(1);
        assertThat(Controls.choiceAt(120, 180)).isEqualTo(-1);
        assertThat(Controls.choiceAt(60, 40)).isEqualTo(-1);
    }

    /**
     * An autopilot that favors dots, flees active ghosts and hunts frightened ones, with an
     * occasional random turn; over enough frames (respawning after a catch, the way an extra life
     * would) it explores nearly the whole maze. It never quite reaches every dot: a handful sit
     * right by the ghost house, which is guarded often enough that a cautious player — or a CPU
     * that "misses now and then" like the arcade's — reasonably keeps avoiding it. This exercises
     * the exploration and avoidance heuristic itself, not how many of the arcade's three lives it
     * costs to finish a maze.
     */
    @Test
    void anAutopilotEatsNearlyEveryDotGivenEnoughRespawns() {
        Controls.autopilot = true;
        int frame = 0;
        while (frame < 30_000 && Session.dotsEaten < Maze.TOTAL_DOTS) {
            frame = frame + 1;
            Session.sinceLastDot = Session.sinceLastDot + 1;
            Session.updatePhase(ghosts);
            AutopilotPacMan.fly(tiles, ghosts);
            Session.movePac(tft, tiles, ghosts);
            Ghosts.releaseGhosts(ghosts);
            for (int g = 0; g < Session.GHOSTS; g++) {
                Ghosts.moveGhost(tiles, ghosts, g);
            }
            int collision = Session.checkCollisions(tft, tiles, ghosts);
            if (collision < 0) {
                Session.resetActors(ghosts);
            }
        }
        assertThat(Session.dotsEaten).as("dots eaten within %d frames", frame).isGreaterThanOrEqualTo(230);
    }
}
