package io.github.jabrena.juno.games.missilecommand;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Rules of Missile Command: interceptors destroying warheads, warheads reaching a target, the
 * pilot-choice screen, and an autopilot that clears whole waves through the game's own simulation.
 */
class MissileCommandTest {
    private int[] missiles;
    private int[] shots;
    private int[] blasts;
    private boolean[] alive;
    private int[] ammo;

    @BeforeEach
    void newWave() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Controls.autopilot = false;
        Random.seed(7);
        missiles = new int[Session.MISSILES * Session.T_STRIDE];
        shots = new int[Session.SHOTS * Session.T_STRIDE];
        blasts = new int[Session.BLASTS * Session.B_STRIDE];
        alive = new boolean[Session.TARGETS];
        ammo = new int[3];
        set(Session.class, "wave", 0);
        call(Session.class, "newGame", (Object) alive);
        call(Session.class, "startWave", missiles, shots, blasts, alive, ammo);
    }

    @Test
    void anInterceptorBlastDestroysAWarheadAndScores() {
        SceneRenderer.startTrail(missiles, 0, 60, Session.HEADER + 1, 60, Session.GROUND_Y - 6, 1);
        missiles[Session.T_HEAD_X] = 100;
        missiles[Session.T_HEAD_Y] = 150;
        int blast = firstFreeBlast();
        int base = blast * Session.B_STRIDE;
        blasts[base + Session.B_ACTIVE] = 1;
        blasts[base + Session.B_X] = 100;
        blasts[base + Session.B_Y] = 150;
        blasts[base + Session.B_AGE] = 5;
        blasts[base + Session.B_RADIUS] = 5;
        call(Session.class, "updateBlasts", missiles, blasts, alive, ammo);
        assertThat(missiles[Session.T_ACTIVE]).isZero();
        assertThat(getInt(Session.class, "score")).isEqualTo(Session.MISSILE_POINTS);
    }

    @Test
    void aWarheadReachingACityDestroysIt() {
        SceneRenderer.startTrail(missiles, 0, SceneRenderer.cityX(0), Session.HEADER + 1, SceneRenderer.cityX(0),
                Session.targetY(0), 1000);
        missiles[Session.T_TARGET] = 0;
        for (int i = 0; i < 30 && missiles[Session.T_ACTIVE] != 0; i++) {
            call(Session.class, "moveMissiles", missiles, blasts, alive, ammo);
        }
        assertThat(alive[0]).isFalse();
    }

    @Test
    void aWarheadReachingABaseEmptiesItsAmmo() {
        int target = Session.FIRST_BASE;
        SceneRenderer.startTrail(missiles, 0, SceneRenderer.baseX(0), Session.HEADER + 1, SceneRenderer.baseX(0),
                Session.targetY(target), 1000);
        missiles[Session.T_TARGET] = target;
        for (int i = 0; i < 30 && missiles[Session.T_ACTIVE] != 0; i++) {
            call(Session.class, "moveMissiles", missiles, blasts, alive, ammo);
        }
        assertThat(alive[target]).isFalse();
        assertThat(ammo[0]).isZero();
    }

    @Test
    void firingChoosesTheNearestBaseWithAmmo() {
        ammo[0] = 0;
        ammo[1] = 5;
        ammo[2] = 5;
        Session.fire(shots, ammo, SceneRenderer.baseX(0) + 5, 200);
        assertThat(ammo[1]).isEqualTo(4);
        assertThat(ammo[0]).isZero();
        assertThat(ammo[2]).isEqualTo(5);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 60, 180)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 200, 180)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 120, 180)).isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 60, 40)).isEqualTo(-1);
    }

    @Test
    void theCpuLabelStaysInsideTheHeader() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        Controls.autopilot = true;

        Hud.drawHeader();

        int pixelsBelowHeader = 0;
        for (int y = Session.HEADER; y < Session.HEADER + 8; y++) {
            for (int x = 0; x < Session.WIDTH; x++) {
                if (Gpio.FRAMEBUFFER[y * Gpio.STRIDE + x] != TftTouchShield.BLACK) {
                    pixelsBelowHeader = pixelsBelowHeader + 1;
                }
            }
        }
        assertThat(pixelsBelowHeader).isZero();
    }

    /**
     * An autopilot that intercepts the lowest unclaimed warhead each time it reacts, leading its aim
     * and occasionally missing; it clears whole waves without ever losing every city.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void anAutopilotClearsAWave(int wave) {
        set(Session.class, "wave", wave - 1);
        call(Session.class, "startWave", missiles, shots, blasts, alive, ammo);
        for (int frame = 0; frame < 6000; frame++) {
            call(AutopilotMissileCommand.class, "fly", missiles, shots, ammo);
            call(Session.class, "spawnEnemies", missiles, (Object) alive);
            call(Session.class, "moveShots", shots, blasts);
            call(Session.class, "moveMissiles", missiles, blasts, alive, ammo);
            call(Session.class, "updateBlasts", missiles, blasts, alive, ammo);
            if (callBoolean(Session.class, "waveOver", missiles, shots, blasts)) {
                assertThat(callInt(Session.class, "countCities", (Object) alive))
                        .as("wave %d", wave).isPositive();
                return;
            }
        }
        throw new AssertionError("wave " + wave + " not cleared");
    }

    private int firstFreeBlast() {
        for (int slot = 0; slot < Session.BLASTS; slot++) {
            if (blasts[slot * Session.B_STRIDE + Session.B_ACTIVE] == 0) {
                return slot;
            }
        }
        throw new IllegalStateException("no free blast slot");
    }
}
