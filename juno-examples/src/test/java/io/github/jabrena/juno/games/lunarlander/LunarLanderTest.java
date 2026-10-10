package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

/** Terrain, touchdown rules, pilot choice, and CPU landings. */
class LunarLanderTest {
    @BeforeEach
    void portrait() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
    }

    @Test
    void terrainHasPadsAndNoCliffs() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        for (int seed = 0; seed < 300; seed++) {
            Random.seed(seed);
            call(Terrain.class, "generate", ground, pads);
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
        call(Terrain.class, "generate", ground, pads);
        int center = padCenter(pads);
        int multiplier = pads[center];
        set(Flight.class, "x", (float) center);
        set(Flight.class, "y", (float) (ground[center] - 7));
        set(Flight.class, "tilt", 0);
        set(Flight.class, "vx", 0.1f);
        set(Flight.class, "vy", 0.4f);
        assertThat(callBoolean(LanderRenderer.class, "touchesGround", (Object) ground)).isTrue();
        assertThat(callInt(Flight.class, "landingMultiplier", ground, pads)).isEqualTo(multiplier);
        set(Flight.class, "vy", 0.8f);
        assertThat(callInt(Flight.class, "landingMultiplier", ground, pads)).isZero();
        set(Flight.class, "vy", 0.4f);
        set(Flight.class, "tilt", 1);
        assertThat(callInt(Flight.class, "landingMultiplier", ground, pads)).isZero();
        set(Flight.class, "tilt", 0);
        set(Flight.class, "y", (float) (ground[center] - 9));
        assertThat(callBoolean(LanderRenderer.class, "touchesGround", (Object) ground)).isFalse();
    }

    @Test
    void pilotScreenOffersHumanAndCpu() {
        assertThat(callInt(Controls.class, "choiceAt", 60, 140)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 180, 140)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 120, 140)).isEqualTo(-1);
    }

    @Test
    void cpuLandsRepeatedly() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        set(Controls.class, "autopilot", true);
        for (int descent = 0; descent < 3; descent++) {
            Random.seed(40 + descent);
            set(Flight.class, "fuel", 3000);
            int landed = callInt(Flight.class, "fly", ground, pads);
            int x = Math.round((float) io.github.jabrena.juno.api.tft.Internals.get(Flight.class, "x"));
            assertThat(landed)
                    .as("descent %d at x %.2f (pads %d/%d) with velocity %.2f, %.2f and tilt %d", descent,
                            (float) io.github.jabrena.juno.api.tft.Internals.get(Flight.class, "x"),
                            pads[x - 6], pads[x + 6],
                            (float) io.github.jabrena.juno.api.tft.Internals.get(Flight.class, "vx"),
                            (float) io.github.jabrena.juno.api.tft.Internals.get(Flight.class, "vy"),
                            io.github.jabrena.juno.api.tft.Internals.getInt(Flight.class, "tilt"))
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
