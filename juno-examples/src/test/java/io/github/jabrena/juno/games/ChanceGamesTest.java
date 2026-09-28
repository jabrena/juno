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

/** Rules of Rock-Paper-Scissors-Lizard-Spock. */
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

}
