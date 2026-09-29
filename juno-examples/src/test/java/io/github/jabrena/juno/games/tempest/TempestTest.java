package io.github.jabrena.juno.games.tempest;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Rules of Tempest: the tube's geometry, shots destroying flippers and bullets reaching the claw,
 * the pilot-choice screen, and an autopilot that clears whole levels through the game's own
 * simulation.
 */
class TempestTest {
    private int[] tube;
    private int[] enemies;
    private int[] shots;
    private int[] bullets;

    @BeforeEach
    void newLevel() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Random.seed(7);
        tube = new int[Tube.SIZE];
        enemies = new int[Session.ENEMIES * Session.E_STRIDE];
        shots = new int[Session.SHOTS * Session.S_STRIDE];
        bullets = new int[Session.BULLETS * Session.S_STRIDE];
        set(Session.class, "level", 1);
        set(Session.class, "lives", 3);
        Tube.build(0, tube);
        call(Session.class, "startLevel", enemies, shots, bullets);
    }

    @Test
    void theNearestLaneIsTheOneWhoseRimMidpointIsClosest() {
        int x = tube[Tube.OUTER_X + 5];
        int y = tube[Tube.OUTER_Y + 5];
        assertThat(callInt(Tube.class, "nearestLane", tube, x, y)).isEqualTo(5);
    }

    @Test
    void stepTowardMovesOneLaneTheShortWayAroundTheRim() {
        assertThat(callInt(Tube.class, "stepToward", 0, 2)).isEqualTo(1);
        assertThat(callInt(Tube.class, "stepToward", 0, Tube.LANES - 2)).isEqualTo(Tube.LANES - 1);
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
        call(Session.class, "moveShots", tube, shots, enemies, bullets);
        assertThat(enemies[Session.E_ACTIVE]).isZero();
        assertThat(getInt(Session.class, "score")).isEqualTo(Session.FLIPPER_POINTS);
    }

    @Test
    void aBulletReachingTheClawIsACatch() {
        set(Session.class, "playerLane", 0);
        bullets[Session.S_ACTIVE] = 1;
        bullets[Session.S_LANE] = 0;
        bullets[Session.S_DEPTH] = Tube.DEPTH - 1;
        bullets[Session.S_SHOWN_X] = -1;
        assertThat(callBoolean(Session.class, "moveBullets", tube, bullets)).isTrue();
    }

    @Test
    void everyPhaseTransitionRunsToCompletion() {
        assertThatCode(() -> {
            call(Interludes.class, "cover", (Object) tube);
            call(Interludes.class, "emergeFromTube", tube, 0);
            call(Interludes.class, "levelCard", tube, 7, 0);
            call(Interludes.class, "levelCleared", tube, 3, 700);
            call(SceneRenderer.class, "zapFlash", (Object) tube);
            call(Interludes.class, "clawLost", tube, 5, 2);
            call(Interludes.class, "gameOver", (Object) tube);
        }).doesNotThrowAnyException();
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 60, 180)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 200, 180)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 120, 180)).isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 60, 40)).isEqualTo(-1);
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
            call(Session.class, "tickCooldowns");
            call(AutopilotTempest.class, "fly", tube, enemies, shots);
            if (callBoolean(Session.class, "consumeZapRequest")) {
                call(Session.class, "zapAllEnemies", tube, enemies);
            }
            call(Session.class, "moveShots", tube, shots, enemies, bullets);
            call(Session.class, "spawnEnemies", enemies);
            boolean caught = callBoolean(Session.class, "moveEnemies", tube, enemies, bullets, frame);
            if (callBoolean(Session.class, "moveBullets", tube, bullets)) {
                caught = true;
            }
            if (caught) {
                set(Session.class, "lives", getInt(Session.class, "lives") - 1);
                call(Session.class, "recycleAfterDeath", enemies, shots, bullets);
                assertThat(getInt(Session.class, "lives")).as("tube shape %d", shape).isPositive();
            }
            if (callBoolean(Session.class, "levelCleared", (Object) enemies)) {
                return;
            }
        }
        throw new AssertionError("tube shape " + shape + " not cleared");
    }
}
