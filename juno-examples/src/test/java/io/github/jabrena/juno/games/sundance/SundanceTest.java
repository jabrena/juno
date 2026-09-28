package io.github.jabrena.juno.games.sundance;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Rules of Sundance: tapping squares, hatches, trapping and bouncing suns, collisions, rounds, and an
 * autopilot that plays whole rounds through the game's own simulation and rendering.
 */
class SundanceTest {
    private static final Class<?> GAME = Sundance.class;
    private static final int STRIDE = 8;
    private static final int STATE = 0;
    private static final int FROM = 1;
    private static final int TO = 2;
    private static final int T = 3;
    private static final int DUR = 4;
    private static final int UP = 5;
    private static final int FLYING = 1;
    private static final int TRAPPED = 2;
    private static final int BURST = 3;

    private short[] lines;
    private int[] suns;
    private int[] hatches;

    @BeforeEach
    void newGame() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(7);
        lines = new short[2 * 180 * 5];
        suns = new int[6 * STRIDE];
        hatches = new int[9];
        set(Session.class, "score", 0);
        set(Session.class, "round", 1);
        set(Session.class, "lives", 3);
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        call(Session.class, "startRound", suns, hatches);
    }

    @Test
    void tappingASquareOfEitherGridFindsItsHatch() {
        for (int cell = 0; cell < 9; cell++) {
            for (int h = 0; h <= 1; h++) {
                float u = cell % 3 + 0.5f;
                float d = cell / 3 + 0.5f;
                int x = callInt(Grid.class, "screenX", u, d);
                int y = callInt(Grid.class, "screenY", d, (float) h);
                assertThat(callInt(Grid.class, "cellAt", x, y)).as("square %d of grid %d", cell, h).isEqualTo(cell);
            }
        }
        assertThat(callInt(Grid.class, "cellAt", 160, 130)).as("between the grids").isEqualTo(-1);
        assertThat(callInt(Grid.class, "cellAt", 4, 225)).as("beside the lower grid").isEqualTo(-1);
    }

    @Test
    void aSunLandingOnAnOpenHatchIsTrapped() {
        noMoreSuns();
        int sun = place(0, 4, 4, 0);
        suns[sun * STRIDE + T] = 39;
        call(Controls.class, "openHatch", hatches, 4);
        step();
        assertThat(suns[sun * STRIDE + STATE]).isEqualTo(TRAPPED);
        assertThat(getInt(Session.class, "trapped")).isEqualTo(1);
        assertThat(getInt(Session.class, "score")).isEqualTo(100);
    }

    @Test
    void aSunLandingOnAClosedHatchBouncesBackToASquareNextToIt() {
        noMoreSuns();
        int sun = place(0, 0, 0, 1);
        suns[sun * STRIDE + T] = 39;
        call(Controls.class, "openHatch", hatches, 8);
        step();
        int b = sun * STRIDE;
        assertThat(suns[b + STATE]).isEqualTo(FLYING);
        assertThat(suns[b + FROM]).isZero();
        assertThat(suns[b + UP]).as("now heading for the lower grid").isZero();
        assertThat(suns[b + T]).isZero();
        assertThat(suns[b + TO]).isIn(0, 1, 3, 4);
    }

    @Test
    void aHatchStaysOpenForAMoment() {
        call(Controls.class, "openHatch", hatches, 2);
        for (int i = 0; i < 11; i++) {
            step();
        }
        assertThat(hatches[2]).isPositive();
        step();
        assertThat(hatches[2]).isZero();
    }

    @Test
    void openingAThirdHatchClosesTheOldest() {
        call(Controls.class, "openHatch", hatches, 0);
        step();
        call(Controls.class, "openHatch", hatches, 1);
        step();
        call(Controls.class, "openHatch", hatches, 2);
        assertThat(hatches[0]).isZero();
        assertThat(hatches[1]).isPositive();
        assertThat(hatches[2]).isPositive();
    }

    @Test
    void sunsThatMeetBurstAndCostALife() {
        noMoreSuns();
        int a = place(0, 0, 2, 1);
        int b = place(1, 2, 0, 0);
        for (int i = 0; i < 40 && getInt(Session.class, "lives") == 3; i++) {
            step();
        }
        assertThat(getInt(Session.class, "lives")).isEqualTo(2);
        assertThat(suns[a * STRIDE + STATE]).isEqualTo(BURST);
        assertThat(suns[b * STRIDE + STATE]).isEqualTo(BURST);
    }

    @Test
    void sunsInDifferentSquaresPassEachOther() {
        noMoreSuns();
        place(0, 0, 0, 1);
        place(1, 8, 8, 0);
        for (int i = 0; i < 39; i++) {
            step();
        }
        assertThat(callInt(Session.class, "count", suns, FLYING)).isEqualTo(2);
        assertThat(getInt(Session.class, "lives")).isEqualTo(3);
    }

    @Test
    void sunsAreReleasedUpToTheRoundsLimit() {
        set(Session.class, "round", 5);
        call(Session.class, "startRound", suns, hatches);
        assertThat(getInt(Session.class, "quota")).isEqualTo(8);
        int most = 0;
        for (int i = 0; i < 1000; i++) {
            step();
            most = Math.max(most, callInt(Session.class, "count", suns, FLYING));
        }
        assertThat(most).isEqualTo(4);
        assertThat(getInt(Session.class, "released")).isEqualTo(8);
    }

    @Test
    void aRoundIsClearedOnceEverySunIsGone() {
        noMoreSuns();
        place(0, 4, 4, 0);
        assertThat(callBoolean(Session.class, "roundCleared", (Object) suns)).isFalse();
        suns[STATE] = 0;
        assertThat(callBoolean(Session.class, "roundCleared", (Object) suns)).isTrue();
    }

    @Test
    void theClockRunsDown() {
        for (int i = 0; i < 1500; i++) {
            step();
        }
        assertThat(getInt(Session.class, "timeLeft")).isZero();
    }

    @Test
    void everyLineStaysBelowTheHeader() {
        noMoreSuns();
        place(0, 6, 7, 1);
        place(1, 0, 3, 0);
        suns[1 * STRIDE + STATE] = BURST;
        suns[1 * STRIDE + FROM] = 10;
        suns[1 * STRIDE + TO] = 225;
        suns[1 * STRIDE + 7] = 1;
        call(Controls.class, "openHatch", hatches, 6);
        call(SceneRenderer.class, "render", lines, suns, hatches);
        int built = getInt(DisplayList.class, "shown");
        assertThat(built).isPositive();
        int base = getInt(DisplayList.class, "front") * 180 * 5;
        for (int i = 0; i < built; i++) {
            for (int k = 0; k < 4; k = k + 2) {
                assertThat((int) lines[base + i * 5 + k]).isBetween(0, 319);
                assertThat((int) lines[base + i * 5 + k + 1]).isBetween(20, 239);
            }
        }
    }

    /**
     * An autopilot that taps like a player, at most a few times a second, opening the hatch a sun is
     * heading for shortly before it lands, and tapping the wrong square almost one time in three; it
     * clears every round without losing all three lives.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void anAutopilotClearsWholeRounds(int round) {
        set(Session.class, "round", round);
        call(Session.class, "startRound", suns, hatches);
        set(Controls.class, "autopilot", true);
        int lost = 0;
        for (int frame = 0; frame < 1500; frame++) {
            set(Session.class, "frame", frame);
            call(Controls.class, "fly", suns, hatches);
            int before = getInt(Session.class, "lives");
            step();
            call(SceneRenderer.class, "render", lines, suns, hatches);
            lost = lost + before - getInt(Session.class, "lives");
            set(Session.class, "lives", 3);
            if (callBoolean(Session.class, "roundCleared", (Object) suns)) {
                assertThat(lost).as("lives lost in round %d", round).isLessThan(3);
                return;
            }
        }
        throw new AssertionError("round " + round + " not cleared");
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 160, 140)).isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 80, 40)).isEqualTo(-1);
    }

    private void step() {
        call(Session.class, "step", suns, hatches);
    }

    private void noMoreSuns() {
        set(Session.class, "released", getInt(Session.class, "quota"));
    }

    /** Puts a sun at the start of a crossing from one square to another. */
    private int place(int slot, int from, int to, int up) {
        int b = slot * STRIDE;
        suns[b + STATE] = FLYING;
        suns[b + FROM] = from;
        suns[b + TO] = to;
        suns[b + T] = 0;
        suns[b + DUR] = 40;
        suns[b + UP] = up;
        return slot;
    }
}
