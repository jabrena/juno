package io.github.jabrena.juno.api.tft;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of Yahtzee, Rock-Paper-Scissors-Lizard-Spock, Russian roulette and Lunar Lander. */
class ChanceGamesTest {
    @BeforeEach
    void portrait() {
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
    }

    @AfterEach
    void releaseThePanel() {
        Internals.stopTapping();
    }

    // ---- Yahtzee ----

    /** Dice, then the expected score of each of the 13 boxes (aces ... chance) on an empty card. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "1,1,1,2,2 | 3,4,0,0,0,0,7,0,25,0,0,0,7",
        "2,3,4,5,6 | 0,2,3,4,5,6,0,0,0,30,40,0,20",
        "1,2,3,4,6 | 1,2,3,4,0,6,0,0,0,30,0,0,16",
        "3,4,5,6,6 | 0,0,3,4,5,12,0,0,0,30,0,0,24",
        "4,4,4,4,2 | 0,2,0,16,0,0,18,18,0,0,0,0,18",
        "5,5,5,5,5 | 0,0,0,0,25,0,25,25,0,0,0,50,25",
        "1,3,4,5,5 | 1,0,3,4,10,0,0,0,0,0,0,0,18"})
    void yahtzeeScoresEveryBox(String dice, String expected) {
        int[] scores = openCard();
        int[] roll = ints(dice);
        int[] want = ints(expected);
        for (int box = 0; box < 13; box++) {
            assertThat(callInt(Yahtzee.class, "score", box, roll, scores)).as("box %d", box).isEqualTo(want[box]);
        }
    }

    @Test
    void yahtzeeJokerRules() {
        int[] fives = {5, 5, 5, 5, 5};
        int[] card = openCard();
        card[11] = 50;
        assertThat(callBoolean(Yahtzee.class, "allowed", 4, fives, card)).as("open matching upper box first").isTrue();
        assertThat(callBoolean(Yahtzee.class, "allowed", 8, fives, card)).isFalse();
        card[4] = 25;
        assertThat(callBoolean(Yahtzee.class, "allowed", 8, fives, card)).as("then any lower box").isTrue();
        assertThat(callInt(Yahtzee.class, "score", 8, fives, card)).isEqualTo(25);
        assertThat(callInt(Yahtzee.class, "score", 10, fives, card)).isEqualTo(40);
        assertThat(callBoolean(Yahtzee.class, "allowed", 0, fives, card)).isFalse();
        for (int box = 6; box < 13; box++) {
            card[box] = 0;
        }
        assertThat(callBoolean(Yahtzee.class, "allowed", 0, fives, card)).as("finally any upper box").isTrue();
    }

    @Test
    void yahtzeeTotalsIncludeBothBonuses() {
        int[] card = new int[13];
        card[0] = 3;
        card[1] = 6;
        card[2] = 9;
        card[3] = 12;
        card[4] = 15;
        card[5] = 18;
        set(Yahtzee.class, "yahtzeeBonus", 100);
        assertThat(callInt(Yahtzee.class, "total", (Object) card)).isEqualTo(63 + 35 + 100);
        set(Yahtzee.class, "yahtzeeBonus", 0);
    }

    private static int[] openCard() {
        int[] card = new int[13];
        Arrays.fill(card, -1);
        return card;
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

    // ---- Russian roulette ----

    /** A player who always pulls: every round loads the chosen bullets and ends with one loser. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void everyRouletteRoundHasExactlyOneLoser(int bullets) {
        Internals.tapEvery(4, () -> new int[] {180, 290});
        set(RussianRoulette.class, "bullets", bullets);
        boolean[] loaded = new boolean[6];
        byte[] seen = new byte[6];
        for (int round = 0; round < 10; round++) {
            Random.seed(round * 7 + bullets);
            int human = getInt(RussianRoulette.class, "humanWins");
            int computer = getInt(RussianRoulette.class, "computerWins");
            call(RussianRoulette.class, "playRound", loaded, seen);
            int count = 0;
            for (boolean chamber : loaded) {
                count = count + (chamber ? 1 : 0);
            }
            assertThat(count).isEqualTo(bullets);
            int losers = getInt(RussianRoulette.class, "humanWins") - human
                    + getInt(RussianRoulette.class, "computerWins") - computer;
            assertThat(losers).isEqualTo(1);
        }
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
