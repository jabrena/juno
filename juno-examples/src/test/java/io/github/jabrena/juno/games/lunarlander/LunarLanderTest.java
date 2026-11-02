package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Terrain, touchdown rules, pilot choice, and CPU landings. */
class LunarLanderTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    @BeforeEach
    void portrait() {
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
    }

    @Test
    void terrainHasPadsAndNoCliffs() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        for (int seed = 0; seed < 300; seed++) {
            Random.seed(seed);
            Terrain.generate(ground, pads);
            verifyTerrain(seed, ground, pads);
        }
    }

    private static void verifyTerrain(int seed, short[] ground, byte[] pads) {
        int padCount = 0;
        for (int x = 0; x < 240; x++) {
            assertThat((int) ground[x]).isBetween(72, 269);
            if (x > 0) {
                assertThat(Math.abs(ground[x] - ground[x - 1])).as("slope at %d, seed %d", x, seed)
                        .isLessThanOrEqualTo(12);
                if (pads[x] != 0 && pads[x - 1] == pads[x]) {
                    assertThat(ground[x]).as("pads are flat").isEqualTo(ground[x - 1]);
                }
            }
            if (pads[x] != 0 && (x == 0 || pads[x - 1] != pads[x])) padCount = padCount + 1;
        }
        assertThat(padCount).as("pads, seed %d", seed).isGreaterThanOrEqualTo(2);
    }

    @Test
    void landingNeedsAnUprightSlowTouchdownOnAPad() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        Random.seed(11);
        Terrain.generate(ground, pads);
        int center = padCenter(pads);
        int multiplier = pads[center];
        Flight.x = (float) center;
        Flight.y = (float) (ground[center] - 7);
        Flight.tilt = 0;
        Flight.vx = 0.1f;
        Flight.vy = 0.4f;
        assertThat(LanderRenderer.touchesGround(ground)).isTrue();
        assertThat(Flight.landingMultiplier(ground, pads)).isEqualTo(multiplier);
        Flight.vy = 0.8f;
        assertThat(Flight.landingMultiplier(ground, pads)).isZero();
        Flight.vy = 0.4f;
        Flight.tilt = 1;
        assertThat(Flight.landingMultiplier(ground, pads)).isZero();
        Flight.tilt = 0;
        Flight.y = (float) (ground[center] - 9);
        assertThat(LanderRenderer.touchesGround(ground)).isFalse();
    }

    @Test
    void pilotScreenOffersHumanAndCpu() {
        assertThat(Controls.choiceAt(60, 140)).isZero();
        assertThat(Controls.choiceAt(180, 140)).isEqualTo(1);
        assertThat(Controls.choiceAt(120, 140)).isEqualTo(-1);
    }

    @Test
    void cpuLandsRepeatedly() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        Controls.autopilot = true;
        for (int descent = 0; descent < 3; descent++) {
            Random.seed(40 + descent);
            Flight.fuel = 3000;
            int landed = Flight.fly(tft, ground, pads);
            int x = Math.round(Flight.x);
            assertThat(landed)
                    .as("descent %d at x %.2f (pads %d/%d) with velocity %.2f, %.2f and tilt %d", descent,
                            Flight.x,
                            pads[x - 6], pads[x + 6],
                            Flight.vx,
                            Flight.vy,
                            Flight.tilt)
                    .isPositive();
        }
    }

    private static int padCenter(byte[] pads) {
        for (int x = 8; x < 232; x++) {
            if (pads[x] != 0 && pads[x - 7] == pads[x] && pads[x + 7] == pads[x]) return x;
        }
        return -1;
    }
}
