package io.github.jabrena.juno.games;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.Internals;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of Rock-Paper-Scissors-Lizard-Spock and Lunar Lander. */
class ChanceGamesTest {
    @BeforeEach
    void portrait() {
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
    }

    @AfterEach
    void releaseThePanel() {
        Internals.stopTapping();
    }

    private static int[] ints(String csv) {
        return Arrays.stream(csv.trim().split(",")).mapToInt(value -> Integer.parseInt(value.trim())).toArray();
    }

    // ---- Rock, Paper, Scissors, Lizard, Spock ----

    @Test
    void everyMoveBeatsExactlyTwoOthers() {
        for (int a = 0; a < 5; a++) {
            int beats = 0;
            int losesTo = 0;
            for (int b = 0; b < 5; b++) {
                boolean ab = callBoolean(RockPaperScissorsLizardSpock.class, "beats", a, b);
                boolean ba = callBoolean(RockPaperScissorsLizardSpock.class, "beats", b, a);
                if (a == b) {
                    assertThat(ab || ba).isFalse();
                } else {
                    assertThat(ab).as("%d vs %d: exactly one wins", a, b).isNotEqualTo(ba);
                }
                beats = beats + (ab ? 1 : 0);
                losesTo = losesTo + (ba ? 1 : 0);
            }
            assertThat(beats).isEqualTo(2);
            assertThat(losesTo).isEqualTo(2);
        }
    }

    /** Always Spock, or strictly alternating Rock and Paper: the computer should see through both. */
    @ParameterizedTest
    @ValueSource(strings = {"4", "0,1"})
    void theComputerPunishesAPredictablePlayer(String pattern) {
        int[] moves = ints(pattern);
        Random.seed(1);
        set(RockPaperScissorsLizardSpock.class, "previous", -1);
        int[] habits = new int[25];
        int computerWins = 0;
        int humanWins = 0;
        for (int round = 0; round < 300; round++) {
            int human = moves[round % moves.length];
            int computer = callInt(RockPaperScissorsLizardSpock.class, "chooseMove", (Object) habits);
            if (callBoolean(RockPaperScissorsLizardSpock.class, "beats", computer, human)) {
                computerWins = computerWins + 1;
            } else if (callBoolean(RockPaperScissorsLizardSpock.class, "beats", human, computer)) {
                humanWins = humanWins + 1;
            }
            int previous = getInt(RockPaperScissorsLizardSpock.class, "previous");
            if (previous >= 0) {
                habits[previous * 5 + human] = habits[previous * 5 + human] + 1;
            }
            set(RockPaperScissorsLizardSpock.class, "previous", human);
        }
        assertThat(computerWins).isGreaterThan(3 * humanWins);
    }

    // ---- Lunar Lander ----

    @Test
    void lunarTerrainHasPadsAndNoCliffs() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        for (int seed = 0; seed < 300; seed++) {
            Random.seed(seed);
            call(LunarLander.class, "generateTerrain", ground, pads);
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
                if (pads[x] != 0 && (x == 0 || pads[x - 1] != pads[x])) {
                    padCount = padCount + 1;
                }
            }
            assertThat(padCount).as("pads, seed %d", seed).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void landingNeedsAnUprightSlowTouchdownOnAPad() {
        short[] ground = new short[240];
        byte[] pads = new byte[240];
        Random.seed(11);
        call(LunarLander.class, "generateTerrain", ground, pads);
        int padCenter = -1;
        int multiplier = 0;
        for (int x = 8; x < 232 && padCenter < 0; x++) {
            if (pads[x] != 0 && pads[x - 7] == pads[x] && pads[x + 7] == pads[x]) {
                padCenter = x;
                multiplier = pads[x];
            }
        }
        assertThat(padCenter).isPositive();
        set(LunarLander.class, "x", (float) padCenter);
        set(LunarLander.class, "y", (float) (ground[padCenter] - 7));
        set(LunarLander.class, "tilt", 0);
        set(LunarLander.class, "vx", 0.1f);
        set(LunarLander.class, "vy", 0.4f);
        assertThat(callBoolean(LunarLander.class, "touchesGround", (Object) ground)).isTrue();
        assertThat(callInt(LunarLander.class, "landingMultiplier", ground, pads)).isEqualTo(multiplier);
        set(LunarLander.class, "vy", 0.8f);
        assertThat(callInt(LunarLander.class, "landingMultiplier", ground, pads)).as("too fast").isZero();
        set(LunarLander.class, "vy", 0.4f);
        set(LunarLander.class, "tilt", 1);
        assertThat(callInt(LunarLander.class, "landingMultiplier", ground, pads)).as("tilted").isZero();
        set(LunarLander.class, "tilt", 0);
        set(LunarLander.class, "y", (float) (ground[padCenter] - 9));
        assertThat(callBoolean(LunarLander.class, "touchesGround", (Object) ground)).as("hovering").isFalse();
    }
}
