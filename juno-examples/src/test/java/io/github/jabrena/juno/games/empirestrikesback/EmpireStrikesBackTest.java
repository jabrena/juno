package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Rules of The Empire Strikes Back: lasers, AT-AT armor, asteroids, shields, the JEDI letters, the
 * pilot screen, and the CPU pilot that flies whole waves through the game's own simulation and
 * rendering.
 */
class EmpireStrikesBackTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
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
        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[24 * STRIDE];
        Session.score = 0;
        Session.shields = 6;
        Session.wave = 1;
        Session.jedi = 0;
        DisplayList.shown = 0;
        DisplayList.built = 0;
        Controls.crossX = 160;
        Controls.crossY = 130;
        EmpireStrikesBack.startRound(Round.PROBES, ents);
    }

    @Test
    void lasersDestroyTheProbeDroidUnderTheCrosshair() {
        int probe = spawn(PROBE, 80, 40, 700);
        render();
        aimAt(probe);
        Combat.fire(ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isNotEqualTo(PROBE);
        assertThat(ProbesRound.kills).isEqualTo(1);
        assertThat(Session.score).isEqualTo(500);
    }

    @Test
    void aShotAtEmptySkyMisses() {
        int probe = spawn(PROBE, 300, 40, 700);
        render();
        Controls.crossX = 20;
        Controls.crossY = 60;
        Combat.fire(ents);
        converge();
        assertThat(ents[probe * STRIDE + TYPE]).isEqualTo(PROBE);
    }

    @Test
    void anAtAtOnlyFallsToThreeHitsOnTheHead() {
        EmpireStrikesBack.startRound(Round.WALKERS, ents);
        clearAll();
        int atat = spawn(ATAT, 0, GROUND, 900);
        ents[atat * STRIDE + HP] = 3;
        ents[atat * STRIDE + FLAG] = 1;
        render();
        int body = Combat.targetAt(ents, Camera.projectX(0, 900), Camera.projectY(GROUND + 190, 900));
        assertThat(body).as("the armored body is not a target").isEqualTo(-1);
        for (int shot = 1; shot <= 3; shot++) {
            render();
            aimAt(atat);
            Combat.fire(ents);
            converge();
            if (shot < 3) {
                assertThat(ents[atat * STRIDE + TYPE]).as("after %d head hits", shot).isEqualTo(ATAT);
            }
        }
        assertThat(ents[atat * STRIDE + TYPE]).isNotEqualTo(ATAT);
        assertThat(WalkersRound.atatsDown).isEqualTo(1);
        assertThat(Session.score).isEqualTo(5000);
    }

    @Test
    void anAtStFallsToOneHit() {
        EmpireStrikesBack.startRound(Round.WALKERS, ents);
        clearAll();
        int atst = spawn(ATST, -60, GROUND, 800);
        ents[atst * STRIDE + HP] = 1;
        render();
        aimAt(atst);
        Combat.fire(ents);
        converge();
        assertThat(ents[atst * STRIDE + TYPE]).isNotEqualTo(ATST);
        assertThat(Session.score).isEqualTo(1000);
    }

    @Test
    void flyingIntoAnAsteroidCostsAShieldAndDodgingItDoesNot() {
        EmpireStrikesBack.startRound(Round.ASTEROIDS, ents);
        clearAll();
        int rock = spawn(ASTEROID, 0, 0, 200);
        ents[rock * STRIDE + AUX] = 80;
        int beside = spawn(ASTEROID, 400, 0, 200);
        ents[beside * STRIDE + AUX] = 80;
        for (int i = 0; i < 10; i++) {
            EmpireStrikesBack.step(tft, ents);
        }
        assertThat(Session.shields).isEqualTo(5);
        assertThat((boolean) Session.hitThisRound).isTrue();
    }

    @Test
    void fireballsAimedAtYouHitAndCanBeShotDown() {
        Entities.fireball(ents, 0, 0, 600);
        for (int i = 0; i < 60 && Session.shields == 6; i++) {
            EmpireStrikesBack.step(tft, ents);
        }
        assertThat(Session.shields).isEqualTo(5);

        clearAll();
        int fireball = spawn(FIREBALL, 0, 0, 900);
        render();
        aimAt(fireball);
        Combat.fire(ents);
        converge();
        assertThat(ents[fireball * STRIDE + TYPE]).isNotEqualTo(FIREBALL);
    }

    @Test
    void aHitWithNoShieldsLeftEndsTheGame() {
        Session.shields = 0;
        Session.shieldHit(tft);
        assertThat((boolean) Session.dead).isTrue();
    }

    @Test
    void aFlawlessRoundEarnsAJediLetterAndAllFourPayTheBonus() {
        for (int round = 1; round <= 3; round++) {
            Session.hitThisRound = false;
            EmpireStrikesBack.finishRound(tft);
            assertThat(Session.jedi).isEqualTo(round);
        }
        Session.hitThisRound = true;
        EmpireStrikesBack.finishRound(tft);
        assertThat(Session.jedi).as("a round with a hit earns nothing").isEqualTo(3);
        Session.hitThisRound = false;
        EmpireStrikesBack.finishRound(tft);
        assertThat(Session.jedi).as("JEDI spelled: the letters start again").isZero();
        assertThat(Session.score).isEqualTo(50000);
    }

    @Test
    void everyLineIsClippedToTheView() {
        Camera.camX = 200;
        spawn(PROBE, -300, 0, 60);
        int atat = spawn(ATAT, 150, GROUND, 120);
        ents[atat * STRIDE + HP] = 3;
        EmpireStrikesBack.startRound(Round.PROBES, new int[24 * STRIDE]);
        render();
        int built = DisplayList.shown;
        assertThat(built).isPositive();
        int base = DisplayList.front * 180 * 5;
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
        Session.wave = wave;
        Controls.autopilot = true;
        for (Round round : Round.values()) {
            assertThat(flyRound(round)).as("round %s of wave %d", round, wave).isTrue();
        }
        assertThat(Session.score).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(Round round) {
        EmpireStrikesBack.startRound(round, ents);
        for (int frame = 0; frame < 8000; frame++) {
            Session.frame = frame;
            AutopilotESB.fly(ents);
            EmpireStrikesBack.step(tft, ents);
            Combat.resolveShots(tft, ents);
            SceneRenderer.render(tft, lines, ents);
            if ((boolean) Session.dead) {
                Session.dead = false;
            }
            Session.shields = Math.max(Session.shields, 1);
            if (EmpireStrikesBack.roundOver(ents)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(80, 140)).isZero();
        assertThat(Controls.choiceAt(230, 140)).isEqualTo(1);
        assertThat(Controls.choiceAt(160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(Controls.choiceAt(80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void render() {
        SceneRenderer.render(tft, lines, ents);
    }

    private void aimAt(int slot) {
        Controls.crossX = ents[slot * STRIDE + SX];
        Controls.crossY = ents[slot * STRIDE + SY];
    }

    private void converge() {
        for (int i = 0; i < 5; i++) {
            Combat.resolveShots(tft, ents);
        }
        assertThat(Entities.count(ents, SHOT)).isZero();
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = Entities.freeSlot(ents);
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
