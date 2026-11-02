package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Rules of Tempest: the tube's geometry, shots destroying flippers and bullets reaching the claw,
 * the pilot-choice screen, and an autopilot that clears whole levels through the game's own
 * simulation.
 */
class TempestTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    private int[] tube;
    private int[] enemies;
    private int[] shots;
    private int[] bullets;

    @BeforeEach
    void newLevel() {
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Random.seed(7);
        tube = new int[Tube.SIZE];
        enemies = new int[Session.ENEMIES * Session.E_STRIDE];
        shots = new int[Session.SHOTS * Session.S_STRIDE];
        bullets = new int[Session.BULLETS * Session.S_STRIDE];
        Session.level = 1;
        Session.lives = 3;
        Tube.build(0, tube);
        Session.startLevel(enemies, shots, bullets);
    }

    @Test
    void theNearestLaneIsTheOneWhoseRimMidpointIsClosest() {
        int x = tube[Tube.OUTER_X + 5];
        int y = tube[Tube.OUTER_Y + 5];
        assertThat(Tube.nearestLane(tube, x, y)).isEqualTo(5);
    }

    @Test
    void stepTowardMovesOneLaneTheShortWayAroundTheRim() {
        assertThat(Tube.stepToward(0, 2)).isEqualTo(1);
        assertThat(Tube.stepToward(0, Tube.LANES - 2)).isEqualTo(Tube.LANES - 1);
    }

    @Test
    void aShotDestroysAnEnemyInItsLaneAndScores() {
        enemies[Session.E_ACTIVE] = 1;
        enemies[Session.E_LANE] = 0;
        enemies[Session.E_DEPTH] = 500;
        enemies[Session.E_SHOWN_LANE] = -1;
        shots[Session.S_ACTIVE] = 1;
        shots[Session.S_LANE] = 0;
        shots[Session.S_DEPTH] = 500 + Session.SHOT_SPEED;
        shots[Session.S_SHOWN_X] = -1;
        Session.moveShots(tft, tube, shots, enemies, bullets);
        assertThat(enemies[Session.E_ACTIVE]).isZero();
        assertThat(Session.score).isEqualTo(Session.FLIPPER_POINTS);
    }

    @Test
    void aBulletReachingTheClawIsACatch() {
        Session.playerLane = 0;
        bullets[Session.S_ACTIVE] = 1;
        bullets[Session.S_LANE] = 0;
        bullets[Session.S_DEPTH] = Tube.DEPTH - 1;
        bullets[Session.S_SHOWN_X] = -1;
        assertThat(Session.moveBullets(tft, tube, bullets)).isTrue();
    }

    @Test
    void everyPhaseTransitionRunsToCompletion() {
        assertThatCode(() -> {
            Interludes.cover(tft, tube);
            Interludes.emergeFromTube(tft, tube, 0);
            Interludes.levelCard(tft, tube, 7, 0);
            Interludes.levelCleared(tft, tube, 3, 700);
            SceneRenderer.zapFlash(tft, tube);
            Interludes.clawLost(tft, tube, 5, 2);
            Interludes.gameOver(tft, tube);
        }).doesNotThrowAnyException();
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(60, 180)).isZero();
        assertThat(Controls.choiceAt(200, 180)).isEqualTo(1);
        assertThat(Controls.choiceAt(120, 180)).isEqualTo(-1);
        assertThat(Controls.choiceAt(60, 40)).isEqualTo(-1);
    }

    /**
     * An autopilot that chases whichever flipper is closest to the rim, fires whenever it can, and
     * zaps when swarmed; it clears every tube shape without losing all three lives.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void anAutopilotClearsALevel(int shape) {
        Tube.build(shape, tube);
        for (int frame = 1; frame <= 4000; frame++) {
            Session.tickCooldowns();
            AutopilotTempest.fly(tft, tube, enemies, shots);
            if (Session.consumeZapRequest()) {
                Session.zapAllEnemies(tft, tube, enemies);
            }
            Session.moveShots(tft, tube, shots, enemies, bullets);
            Enemies.spawnEnemies(enemies);
            boolean caught = Enemies.moveEnemies(tft, tube, enemies, bullets, frame);
            if (Session.moveBullets(tft, tube, bullets)) {
                caught = true;
            }
            if (caught) {
                Session.lives = Session.lives - 1;
                Session.recycleAfterDeath(enemies, shots, bullets);
                assertThat(Session.lives).as("tube shape %d", shape).isPositive();
            }
            if (Session.levelCleared(enemies)) {
                return;
            }
        }
        throw new AssertionError("tube shape " + shape + " not cleared");
    }
}
