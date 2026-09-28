package io.github.jabrena.juno.games.empirestrikesback;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.get;
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
 * Rules of The Empire Strikes Back: lasers, AT-AT armor, asteroids, shields, the JEDI letters, the
 * pilot screen, and the CPU pilot that flies whole waves through the game's own simulation and
 * rendering.
 */
class EmpireStrikesBackTest {
    private static final Class<?> GAME = EmpireStrikesBack.class;
    private static final int STRIDE = 14;
    private static final int TYPE = 0;
    private static final int X = 1;
    private static final int Y = 2;
    private static final int Z = 3;
    private static final int SX = 8;
    private static final int SY = 9;
    private static final int SR = 10;
    private static final int AUX = 11;
    private static final int FLAG = 12;
    private static final int HP = 13;
    private static final int PROBE = 1;
    private static final int ATAT = 2;
    private static final int ATST = 3;
    private static final int ASTEROID = 4;
    private static final int TIE = 5;
    private static final int FIREBALL = 6;
    private static final int SHOT = 8;
    private static final int PROBES = 0;
    private static final int WALKERS = 1;
    private static final int ASTEROIDS = 2;
    private static final int GROUND = -150;

    private short[] lines;
    private int[] ents;

    @BeforeEach
    void newGame() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[24 * STRIDE];
        set(GAME, "score", 0);
        set(GAME, "shields", 6);
        set(GAME, "wave", 1);
        set(GAME, "jedi", 0);
        set(GAME, "shown", 0);
        set(GAME, "built", 0);
        set(GAME, "crossX", 160);
        set(GAME, "crossY", 130);
        call(GAME, "startRound", PROBES, ents);
    }

    @Test
    void lasersDestroyTheProbeDroidUnderTheCrosshair() {
        int probe = spawn(PROBE, 80, 40, 700);
        render();
        aimAt(probe);
        call(GAME, "fire", (Object) ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isNotEqualTo(PROBE);
        assertThat(getInt(GAME, "kills")).isEqualTo(1);
        assertThat(getInt(GAME, "score")).isEqualTo(500);
    }

    @Test
    void aShotAtEmptySkyMisses() {
        int probe = spawn(PROBE, 300, 40, 700);
        render();
        set(GAME, "crossX", 20);
        set(GAME, "crossY", 60);
        call(GAME, "fire", (Object) ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isEqualTo(PROBE);
    }

    @Test
    void anAtAtOnlyFallsToThreeHitsOnTheHead() {
        call(GAME, "startRound", WALKERS, ents);
        clearAll();
        int atat = spawn(ATAT, 0, GROUND, 900);
        ents[atat * STRIDE + HP] = 3;
        ents[atat * STRIDE + FLAG] = 1;
        render();
        int body = callInt(GAME, "targetAt", ents, callInt(GAME, "projectX", 0, 900),
                callInt(GAME, "projectY", GROUND + 190, 900));
        assertThat(body).as("the armored body is not a target").isEqualTo(-1);
        for (int shot = 1; shot <= 3; shot++) {
            render();
            aimAt(atat);
            call(GAME, "fire", (Object) ents);
            converge();
            if (shot < 3) {
                assertThat(ents[atat * STRIDE + TYPE]).as("after %d head hits", shot).isEqualTo(ATAT);
            }
        }
        assertThat(ents[atat * STRIDE + TYPE]).isNotEqualTo(ATAT);
        assertThat(getInt(GAME, "atatsDown")).isEqualTo(1);
        assertThat(getInt(GAME, "score")).isEqualTo(5000);
    }

    @Test
    void anAtStFallsToOneHit() {
        call(GAME, "startRound", WALKERS, ents);
        clearAll();
        int atst = spawn(ATST, -60, GROUND, 800);
        ents[atst * STRIDE + HP] = 1;
        render();
        aimAt(atst);
        call(GAME, "fire", (Object) ents);
        converge();
        assertThat(ents[atst * STRIDE + TYPE]).isNotEqualTo(ATST);
        assertThat(getInt(GAME, "score")).isEqualTo(1000);
    }

    @Test
    void flyingIntoAnAsteroidCostsAShieldAndDodgingItDoesNot() {
        call(GAME, "startRound", ASTEROIDS, ents);
        clearAll();
        int rock = spawn(ASTEROID, 0, 0, 200);
        ents[rock * STRIDE + AUX] = 80;
        int beside = spawn(ASTEROID, 400, 0, 200);
        ents[beside * STRIDE + AUX] = 80;
        for (int i = 0; i < 10; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(GAME, "shields")).isEqualTo(5);
        assertThat((boolean) get(GAME, "hitThisRound")).isTrue();
    }

    @Test
    void fireballsAimedAtYouHitAndCanBeShotDown() {
        call(GAME, "fireball", ents, 0, 0, 600);
        for (int i = 0; i < 60 && getInt(GAME, "shields") == 6; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(GAME, "shields")).isEqualTo(5);

        clearAll();
        int fireball = spawn(FIREBALL, 0, 0, 900);
        render();
        aimAt(fireball);
        call(GAME, "fire", (Object) ents);
        converge();
        assertThat(ents[fireball * STRIDE + TYPE]).isNotEqualTo(FIREBALL);
    }

    @Test
    void aHitWithNoShieldsLeftEndsTheGame() {
        set(GAME, "shields", 0);
        call(GAME, "shieldHit");
        assertThat((boolean) get(GAME, "dead")).isTrue();
    }

    @Test
    void aFlawlessRoundEarnsAJediLetterAndAllFourPayTheBonus() {
        for (int round = 1; round <= 3; round++) {
            set(GAME, "hitThisRound", false);
            call(GAME, "finishRound");
            assertThat(getInt(GAME, "jedi")).isEqualTo(round);
        }
        set(GAME, "hitThisRound", true);
        call(GAME, "finishRound");
        assertThat(getInt(GAME, "jedi")).as("a round with a hit earns nothing").isEqualTo(3);
        set(GAME, "hitThisRound", false);
        call(GAME, "finishRound");
        assertThat(getInt(GAME, "jedi")).as("JEDI spelled: the letters start again").isZero();
        assertThat(getInt(GAME, "score")).isEqualTo(50000);
    }

    @Test
    void everyLineIsClippedToTheView() {
        set(GAME, "camX", 200);
        spawn(PROBE, -300, 0, 60);
        int atat = spawn(ATAT, 150, GROUND, 120);
        ents[atat * STRIDE + HP] = 3;
        call(GAME, "startRound", PROBES, new int[24 * STRIDE]);
        render();
        int built = getInt(GAME, "shown");
        assertThat(built).isPositive();
        int base = getInt(GAME, "front") * 180 * 5;
        for (int i = 0; i < built; i++) {
            for (int k = 0; k < 4; k = k + 2) {
                assertThat((int) lines[base + i * 5 + k]).isBetween(0, 319);
                assertThat((int) lines[base + i * 5 + k + 1]).isBetween(20, 239);
            }
        }
    }

    /** The CPU pilot sweeps the crosshair onto the nearest threat and fires: it flies whole waves. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theCpuPilotFliesAWholeWave(int wave) {
        set(GAME, "wave", wave);
        set(GAME, "autopilot", true);
        for (int round = PROBES; round <= ASTEROIDS; round++) {
            assertThat(flyRound(round)).as("round %d of wave %d", round, wave).isTrue();
        }
        assertThat(getInt(GAME, "score")).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(int round) {
        call(GAME, "startRound", round, ents);
        for (int frame = 0; frame < 8000; frame++) {
            set(GAME, "frame", frame);
            call(GAME, "flyAutopilot", (Object) ents);
            call(GAME, "step", (Object) ents);
            call(GAME, "resolveShots", (Object) ents);
            call(GAME, "render", lines, ents);
            if ((boolean) get(GAME, "dead")) {
                set(GAME, "dead", false);
            }
            set(GAME, "shields", Math.max(getInt(GAME, "shields"), 1));
            if (callBoolean(GAME, "roundOver", (Object) ents)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(GAME, "choiceAt", 80, 140)).isZero();
        assertThat(callInt(GAME, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat(callInt(GAME, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(callInt(GAME, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void render() {
        call(GAME, "render", lines, ents);
    }

    private void aimAt(int slot) {
        set(GAME, "crossX", ents[slot * STRIDE + SX]);
        set(GAME, "crossY", ents[slot * STRIDE + SY]);
    }

    private void converge() {
        for (int i = 0; i < 5; i++) {
            call(GAME, "resolveShots", (Object) ents);
        }
        assertThat(callInt(GAME, "count", ents, SHOT)).isZero();
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = callInt(GAME, "freeSlot", (Object) ents);
        int b = slot * STRIDE;
        ents[b + TYPE] = type;
        ents[b + X] = x;
        ents[b + Y] = y;
        ents[b + Z] = z;
        return slot;
    }

    private void clearAll() {
        for (int i = 0; i < ents.length; i++) {
            ents[i] = 0;
        }
    }
}
