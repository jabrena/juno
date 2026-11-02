package io.github.jabrena.juno.games.missilecommand;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.EmulatedShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Rules of Missile Command: interceptors destroying warheads, warheads reaching a target, the
 * pilot-choice screen, and an autopilot that clears whole waves through the game's own simulation.
 */
class MissileCommandTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    private int[] missiles;
    private int[] shots;
    private int[] blasts;
    private boolean[] alive;
    private int[] ammo;

    @BeforeEach
    void newWave() {
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        Controls.autopilot = false;
        Random.seed(7);
        missiles = new int[Session.MISSILES * Session.T_STRIDE];
        shots = new int[Session.SHOTS * Session.T_STRIDE];
        blasts = new int[Session.BLASTS * Session.B_STRIDE];
        alive = new boolean[Session.TARGETS];
        ammo = new int[3];
        Session.wave = 0;
        Session.newGame(alive);
        Session.startWave(missiles, shots, blasts, alive, ammo);
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
        Blasts.updateBlasts(tft, missiles, blasts, alive, ammo);
        assertThat(missiles[Session.T_ACTIVE]).isZero();
        assertThat(Session.score).isEqualTo(Session.MISSILE_POINTS);
    }

    @Test
    void aWarheadReachingACityDestroysIt() {
        SceneRenderer.startTrail(missiles, 0, SceneRenderer.cityX(0), Session.HEADER + 1, SceneRenderer.cityX(0),
                Session.targetY(0), 1000);
        missiles[Session.T_TARGET] = 0;
        for (int i = 0; i < 30 && missiles[Session.T_ACTIVE] != 0; i++) {
            Session.moveMissiles(tft, missiles, blasts, alive, ammo);
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
            Session.moveMissiles(tft, missiles, blasts, alive, ammo);
        }
        assertThat(alive[target]).isFalse();
        assertThat(ammo[0]).isZero();
    }

    @Test
    void firingChoosesTheNearestBaseWithAmmo() {
        ammo[0] = 0;
        ammo[1] = 5;
        ammo[2] = 5;
        Session.fire(tft, shots, ammo, SceneRenderer.baseX(0) + 5, 200);
        assertThat(ammo[1]).isEqualTo(4);
        assertThat(ammo[0]).isZero();
        assertThat(ammo[2]).isEqualTo(5);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(60, 180)).isZero();
        assertThat(Controls.choiceAt(200, 180)).isEqualTo(1);
        assertThat(Controls.choiceAt(120, 180)).isEqualTo(-1);
        assertThat(Controls.choiceAt(60, 40)).isEqualTo(-1);
    }

    @Test
    void theCpuLabelStaysInsideTheHeader() {
        tft.fillScreen(TftTouchShield.BLACK);
        Controls.autopilot = true;

        Hud.drawHeader(tft);

        int pixelsBelowHeader = 0;
        for (int y = Session.HEADER; y < Session.HEADER + 8; y++) {
            for (int x = 0; x < Session.WIDTH; x++) {
                if (EmulatedShield.FRAMEBUFFER[y * EmulatedShield.STRIDE + x] != TftTouchShield.BLACK) {
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
        Session.wave = wave - 1;
        Session.startWave(missiles, shots, blasts, alive, ammo);
        for (int frame = 0; frame < 6000; frame++) {
            AutopilotMissileCommand.fly(tft, missiles, shots, ammo);
            Session.spawnEnemies(missiles, alive);
            Session.moveShots(tft, shots, blasts);
            Session.moveMissiles(tft, missiles, blasts, alive, ammo);
            Blasts.updateBlasts(tft, missiles, blasts, alive, ammo);
            if (Session.waveOver(missiles, shots, blasts)) {
                assertThat(Session.countCities(alive))
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
