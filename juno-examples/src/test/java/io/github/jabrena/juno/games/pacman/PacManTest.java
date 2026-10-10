package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules of Pac-Man: eating dots and energizers, ghost collisions, the pilot-choice screen, and an
 * autopilot that clears a whole maze through the game's own simulation.
 */
class PacManTest {
    private byte[] tiles;
    private int[] ghosts;

    @BeforeEach
    void newLife() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Controls.autopilot = false;
        Random.seed(7);
        tiles = new byte[Maze.TILES];
        ghosts = new int[Session.GHOSTS * Session.G_STRIDE];
        Maze.loadMaze(tiles);
        call(Session.class, "newGame");
        set(Session.class, "dotsEaten", 0);
        call(Session.class, "resetActors", (Object) ghosts);
    }

    @Test
    void eatingAnEnergizerFrightensGhostsAndScores() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        // Row 3, column 1 is the top-left energizer: "#o####.#####.##.#####.####o#".
        call(Session.class, "eat", tiles, ghosts, 1, 3);
        assertThat(getInt(Session.class, "score")).isEqualTo(50);
        assertThat(ghosts[Session.G_FRIGHTENED]).isEqualTo(1);
        assertThat(getInt(Session.class, "frightTimer")).isPositive();
    }

    @Test
    void eatingADotScoresTenAndCountsTowardsTheMaze() {
        // Row 5 is an open corridor of dots: "#..........................#".
        call(Session.class, "eat", tiles, ghosts, 5, 5);
        assertThat(getInt(Session.class, "score")).isEqualTo(10);
        assertThat(getInt(Session.class, "dotsEaten")).isEqualTo(1);
    }

    @Test
    void anActiveGhostCatchingPacManEndsTheLife() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        ghosts[Session.G_X] = Session.pacX;
        ghosts[Session.G_Y] = Session.pacY;
        assertThat(callInt(Session.class, "checkCollisions", tiles, ghosts)).isEqualTo(-1);
    }

    @Test
    void eatingAFrightenedGhostScoresAndSendsItsEyesHome() {
        ghosts[Session.G_STATE] = Session.ACTIVE;
        ghosts[Session.G_FRIGHTENED] = 1;
        ghosts[Session.G_X] = Session.pacX;
        ghosts[Session.G_Y] = Session.pacY;

        int eaten = callInt(Session.class, "checkCollisions", tiles, ghosts);

        assertThat(eaten).isEqualTo(1);
        assertThat(ghosts[Session.G_STATE]).isEqualTo(Session.EYES);
        assertThat(getInt(Session.class, "score")).isEqualTo(200);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 60, 180)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 180, 180)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 120, 180)).isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 60, 40)).isEqualTo(-1);
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
            call(Session.class, "updatePhase", (Object) ghosts);
            AutopilotPacMan.fly(tiles, ghosts);
            call(Session.class, "movePac", tiles, ghosts);
            call(Ghosts.class, "releaseGhosts", (Object) ghosts);
            for (int g = 0; g < Session.GHOSTS; g++) {
                call(Ghosts.class, "moveGhost", tiles, ghosts, g);
            }
            int collision = callInt(Session.class, "checkCollisions", tiles, ghosts);
            if (collision < 0) {
                call(Session.class, "resetActors", (Object) ghosts);
            }
        }
        assertThat(Session.dotsEaten).as("dots eaten within %d frames", frame).isGreaterThanOrEqualTo(230);
    }
}
