package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.get;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

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
        set(Session.class, "score", 0);
        set(Session.class, "shields", 6);
        set(Session.class, "wave", 1);
        set(Session.class, "jedi", 0);
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        set(Controls.class, "crossX", 160);
        set(Controls.class, "crossY", 130);
        call(GAME, "startRound", Round.PROBES, ents);
    }

    @Test
    void lasersDestroyTheProbeDroidUnderTheCrosshair() {
        int probe = spawn(PROBE, 80, 40, 700);
        render();
        aimAt(probe);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isNotEqualTo(PROBE);
        assertThat(getInt(ProbesRound.class, "kills")).isEqualTo(1);
        assertThat(getInt(Session.class, "score")).isEqualTo(500);
    }

    @Test
    void aShotAtEmptySkyMisses() {
        int probe = spawn(PROBE, 300, 40, 700);
        render();
        set(Controls.class, "crossX", 20);
        set(Controls.class, "crossY", 60);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isEqualTo(PROBE);
    }

    @Test
    void anAtAtOnlyFallsToThreeHitsOnTheHead() {
        call(GAME, "startRound", Round.WALKERS, ents);
        clearAll();
        int atat = spawn(ATAT, 0, GROUND, 900);
        ents[atat * STRIDE + HP] = 3;
        ents[atat * STRIDE + FLAG] = 1;
        render();
        int body = callInt(Combat.class, "targetAt", ents, callInt(Camera.class, "projectX", 0, 900),
                callInt(Camera.class, "projectY", GROUND + 190, 900));
        assertThat(body).as("the armored body is not a target").isEqualTo(-1);
        for (int shot = 1; shot <= 3; shot++) {
            render();
            aimAt(atat);
            call(Combat.class, "fire", (Object) ents);
            converge();
            if (shot < 3) {
                assertThat(ents[atat * STRIDE + TYPE]).as("after %d head hits", shot).isEqualTo(ATAT);
            }
        }
        assertThat(ents[atat * STRIDE + TYPE]).isNotEqualTo(ATAT);
        assertThat(getInt(WalkersRound.class, "atatsDown")).isEqualTo(1);
        assertThat(getInt(Session.class, "score")).isEqualTo(5000);
    }

    @Test
    void anAtStFallsToOneHit() {
        call(GAME, "startRound", Round.WALKERS, ents);
        clearAll();
        int atst = spawn(ATST, -60, GROUND, 800);
        ents[atst * STRIDE + HP] = 1;
        render();
        aimAt(atst);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[atst * STRIDE + TYPE]).isNotEqualTo(ATST);
        assertThat(getInt(Session.class, "score")).isEqualTo(1000);
    }

    @Test
    void flyingIntoAnAsteroidCostsAShieldAndDodgingItDoesNot() {
        call(GAME, "startRound", Round.ASTEROIDS, ents);
        clearAll();
        int rock = spawn(ASTEROID, 0, 0, 200);
        ents[rock * STRIDE + AUX] = 80;
        int beside = spawn(ASTEROID, 400, 0, 200);
        ents[beside * STRIDE + AUX] = 80;
        for (int i = 0; i < 10; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Session.class, "shields")).isEqualTo(5);
        assertThat((boolean) get(Session.class, "hitThisRound")).isTrue();
    }

    @Test
    void fireballsAimedAtYouHitAndCanBeShotDown() {
        call(Entities.class, "fireball", ents, 0, 0, 600);
        for (int i = 0; i < 60 && getInt(Session.class, "shields") == 6; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Session.class, "shields")).isEqualTo(5);

        clearAll();
        int fireball = spawn(FIREBALL, 0, 0, 900);
        render();
        aimAt(fireball);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[fireball * STRIDE + TYPE]).isNotEqualTo(FIREBALL);
    }

    @Test
    void aHitWithNoShieldsLeftEndsTheGame() {
        set(Session.class, "shields", 0);
        call(Session.class, "shieldHit");
        assertThat((boolean) get(Session.class, "dead")).isTrue();
    }

    @Test
    void aFlawlessRoundEarnsAJediLetterAndAllFourPayTheBonus() {
        for (int round = 1; round <= 3; round++) {
            set(Session.class, "hitThisRound", false);
            call(GAME, "finishRound");
            assertThat(getInt(Session.class, "jedi")).isEqualTo(round);
        }
        set(Session.class, "hitThisRound", true);
        call(GAME, "finishRound");
        assertThat(getInt(Session.class, "jedi")).as("a round with a hit earns nothing").isEqualTo(3);
        set(Session.class, "hitThisRound", false);
        call(GAME, "finishRound");
        assertThat(getInt(Session.class, "jedi")).as("JEDI spelled: the letters start again").isZero();
        assertThat(getInt(Session.class, "score")).isEqualTo(50000);
    }

    @Test
    void everyLineIsClippedToTheView() {
        set(Camera.class, "camX", 200);
        spawn(PROBE, -300, 0, 60);
        int atat = spawn(ATAT, 150, GROUND, 120);
        ents[atat * STRIDE + HP] = 3;
        call(GAME, "startRound", Round.PROBES, new int[24 * STRIDE]);
        render();
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

    /** The CPU pilot sweeps the crosshair onto the nearest threat and fires: it flies whole waves. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theCpuPilotFliesAWholeWave(int wave) {
        set(Session.class, "wave", wave);
        set(Controls.class, "autopilot", true);
        for (Round round : Round.values()) {
            assertThat(flyRound(round)).as("round %s of wave %d", round, wave).isTrue();
        }
        assertThat(getInt(Session.class, "score")).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(Round round) {
        call(GAME, "startRound", round, ents);
        for (int frame = 0; frame < 8000; frame++) {
            set(Session.class, "frame", frame);
            call(AutopilotESB.class, "fly", (Object) ents);
            call(GAME, "step", (Object) ents);
            call(Combat.class, "resolveShots", (Object) ents);
            call(SceneRenderer.class, "render", lines, ents);
            if ((boolean) get(Session.class, "dead")) {
                set(Session.class, "dead", false);
            }
            set(Session.class, "shields", Math.max(getInt(Session.class, "shields"), 1));
            if (callBoolean(GAME, "roundOver", (Object) ents)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void render() {
        call(SceneRenderer.class, "render", lines, ents);
    }

    private void aimAt(int slot) {
        set(Controls.class, "crossX", ents[slot * STRIDE + SX]);
        set(Controls.class, "crossY", ents[slot * STRIDE + SY]);
    }

    private void converge() {
        for (int i = 0; i < 5; i++) {
            call(Combat.class, "resolveShots", (Object) ents);
        }
        assertThat(callInt(Entities.class, "count", ents, SHOT)).isZero();
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = callInt(Entities.class, "freeSlot", (Object) ents);
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
