package io.github.jabrena.juno.games.redbaron;

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
 * Rules of Red Baron: banking, turning, guns, enemy fire, crashes, the pilot screen, and the CPU
 * pilot that flies whole waves through the game's own simulation and rendering.
 */
class RedBaronTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    private static final Class<?> GAME = RedBaron.class;
    private static final int STRIDE = 12;
    private static final int TYPE = 0;
    private static final int X = 1;
    private static final int Y = 2;
    private static final int Z = 3;
    private static final int VX = 4;
    private static final int VY = 5;
    private static final int VZ = 6;
    private static final int PLANE = 1;
    private static final int BLIMP = 2;
    private static final int FALLING = 3;
    private static final int BULLET = 4;
    private static final int TRACER = 5;
    private static final int HANGAR = 6;
    private static final int FLAK = 7;
    private static final int PYRAMID = 8;
    private short[] lines;
    private int[] ents;

    @BeforeEach
    void newGame() {
        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[26 * STRIDE];
        Session.score = 0;
        Session.planes = 3;
        Session.nextExtraPlane = 20000;
        Session.wave = 1;
        DisplayList.shown = 0;
        DisplayList.built = 0;
        Camera.heading = 0f;
        Session.frame = 0;
        RedBaron.startRound(Round.DOGFIGHT, ents);
        clearAll();
    }

    @Test
    void levelFlightKeepsTheHorizonLevelAndBankingRollsIt() {
        stick(0, 0);
        Camera.steer(ents);
        assertThat(Camera.rollY(0, 132)).isEqualTo(132);
        assertThat(Camera.rollY(319, 132)).isEqualTo(132);
        for (int i = 0; i < 20; i++) {
            stick(100, 0);
            Camera.steer(ents);
        }
        assertThat((float) Camera.bank).isEqualTo(35f);
        // Banked right, the right wing drops, so the ground rises on the right of the view: the
        // horizon's right end goes up the screen and its left end down.
        assertThat(Camera.rollY(319, 132)).isLessThan(132 - 50);
        assertThat(Camera.rollY(0, 132)).isGreaterThan(132 + 50);
        assertThat(Camera.rollX(160, 132)).as("the center stays put").isEqualTo(160);
    }

    @Test
    void turningRightSwingsWhatIsAheadToTheLeft() {
        int plane = spawn(PLANE, 0, 300, 1000);
        for (int i = 0; i < 30; i++) {
            stick(100, 0);
            Camera.steer(ents);
        }
        assertThat(ents[plane * STRIDE + X]).isLessThan(-100);
        assertThat((float) Camera.heading).isGreaterThan(20f);
    }

    @Test
    void stickUpClimbsAndTheAltitudeIsBounded() {
        for (int i = 0; i < 200; i++) {
            stick(0, 100);
            Camera.steer(ents);
        }
        assertThat(Camera.altitude).isEqualTo(520);
        for (int i = 0; i < 200; i++) {
            stick(0, -100);
            Camera.steer(ents);
        }
        assertThat(Camera.altitude).isEqualTo(40);
    }

    @Test
    void theGunsShootDownAPlaneInTheSight() {
        int plane = spawn(PLANE, 0, 300, 500);
        ents[plane * STRIDE + VZ] = 24;
        Combat.fire(ents);
        assertThat(count(BULLET)).isEqualTo(2);
        for (int i = 0; i < 12 && ents[plane * STRIDE + TYPE] == PLANE; i++) {
            Entities.move(ents);
            ents[plane * STRIDE + X] = 0;
            ents[plane * STRIDE + Y] = 300;
            Combat.resolveBullets(tft, ents);
        }
        assertThat(ents[plane * STRIDE + TYPE]).isEqualTo(FALLING);
        assertThat(DogfightRound.kills).isEqualTo(1);
        assertThat(Session.score).isEqualTo(1000);
    }

    @Test
    void aPlaneOffTheSightIsMissed() {
        int plane = spawn(PLANE, 400, 300, 500);
        Combat.fire(ents);
        for (int i = 0; i < 14; i++) {
            Combat.resolveBullets(tft, ents);
            Entities.move(ents);
            ents[plane * STRIDE + X] = 400;
            ents[plane * STRIDE + Z] = 500;
        }
        assertThat(ents[plane * STRIDE + TYPE]).isEqualTo(PLANE);
        assertThat(count(BULLET)).as("bullets expire").isZero();
    }

    @Test
    void theBlimpTakesSeveralHits() {
        int blimp = spawn(BLIMP, 0, 300, 600);
        ents[blimp * STRIDE + 9] = 4;
        for (int shot = 1; shot <= 4; shot++) {
            Combat.hit(tft, ents, blimp);
            if (shot < 4) {
                assertThat(ents[blimp * STRIDE + TYPE]).isEqualTo(BLIMP);
            }
        }
        assertThat(ents[blimp * STRIDE + TYPE]).as("destroyed").isNotEqualTo(BLIMP);
        assertThat(Session.score).isEqualTo(5000);
    }

    @Test
    void aTracerThatReachesYouHits() {
        int tracer = spawn(TRACER, 0, 300, 200);
        ents[tracer * STRIDE + VZ] = -40 + 24;
        for (int i = 0; i < 20 && !(boolean) Session.shotDown; i++) {
            Entities.move(ents);
        }
        assertThat((boolean) Session.shotDown).isTrue();
    }

    @Test
    void enemyFireAimsNearYouGiveOrTakeTheScatter() {
        int hits = 0;
        for (int burst = 0; burst < 60; burst++) {
            clearAll();
            Session.shotDown = false;
            Entities.enemyFires(ents, 0, 300, 600, false);
            for (int i = 0; i < 40 && count(TRACER) > 0; i++) {
                Entities.move(ents);
            }
            if ((boolean) Session.shotDown) {
                hits = hits + 1;
            }
        }
        assertThat(hits).as("most bursts from dead ahead hit, but not all").isBetween(15, 59);
    }

    @Test
    void turningAwayDodgesATracer() {
        Entities.enemyFires(ents, 0, 300, 900, false);
        for (int i = 0; i < 60; i++) {
            stick(100, 0);
            Camera.steer(ents);
            Entities.move(ents);
        }
        assertThat((boolean) Session.shotDown).isFalse();
    }

    @Test
    void flyingLowIntoAPyramidCrashes() {
        Camera.altitude = 80;
        spawn(PYRAMID, 0, 0, 60);
        Entities.move(ents);
        Entities.move(ents);
        assertThat((boolean) Session.shotDown).isTrue();
    }

    @Test
    void flyingHighClearsAPyramid() {
        Camera.altitude = 200;
        spawn(PYRAMID, 0, 0, 60);
        Entities.move(ents);
        Entities.move(ents);
        assertThat((boolean) Session.shotDown).isFalse();
    }

    @Test
    void groundTargetsCanOnlyBeHitFromLowDown() {
        int hangar = spawn(HANGAR, 0, 0, 400);
        Camera.altitude = 300;
        assertThat(Combat.targetHitBy(ents, 0, 274, 400))
                .as("bullets pass high over it").isEqualTo(-1);
        assertThat(Combat.targetHitBy(ents, 0, 40, 400)).isEqualTo(hangar);
    }

    @Test
    void theLastPlaneLostEndsTheGame() {
        Session.planes = 1;
        assertThat(Session.loseAPlane(tft, ents)).isFalse();
        assertThat((boolean) Session.dead).isTrue();
        Session.planes = 3;
        assertThat(Session.loseAPlane(tft, ents)).isTrue();
        assertThat(Session.planes).isEqualTo(2);
    }

    @Test
    void anExtraPlaneEveryTwentyThousandPoints() {
        Session.score = 19500;
        Session.addScore(tft, 1000);
        assertThat(Session.planes).isEqualTo(4);
        Session.addScore(tft, 1000);
        assertThat(Session.planes).isEqualTo(4);
    }

    @Test
    void everyLineIsClippedToTheView() {
        Camera.bank = 30f;
        Camera.steer(ents);
        spawn(PLANE, -200, 250, 80);
        spawn(PYRAMID, 100, 0, 200);
        SceneRenderer.render(tft, lines, ents);
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

    /** The CPU pilot chases the nearest target and fires when it is in the sight. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theCpuPilotFliesAWholeWave(int wave) {
        Session.wave = wave;
        Controls.autopilot = true;
        assertThat(flyRound(Round.DOGFIGHT)).as("dogfight, wave %d", wave).isTrue();
        assertThat(DogfightRound.kills)
                .isGreaterThanOrEqualTo(DogfightRound.killTarget);
        assertThat(flyRound(Round.GROUND_ATTACK)).as("ground attack, wave %d", wave).isTrue();
        assertThat(GroundAttackRound.targetsHit).as("ground targets destroyed").isPositive();
        assertThat(Session.score).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(Round round) {
        RedBaron.startRound(round, ents);
        for (int frame = 0; frame < 6000; frame++) {
            Session.frame = frame;
            AutopilotRB.fly(ents);
            Camera.steer(ents);
            RedBaron.step(ents);
            Combat.resolveBullets(tft, ents);
            SceneRenderer.render(tft, lines, ents);
            if ((boolean) Session.shotDown) {
                Session.planes = 3;
                Session.loseAPlane(tft, ents);
            }
            if (RedBaron.roundOver(ents)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theCpuPilotClimbsOverAPyramidAhead() {
        Session.round = Round.GROUND_ATTACK;
        Camera.altitude = 100;
        spawn(PYRAMID, 0, 0, 400);
        AutopilotRB.fly(ents);
        assertThat(Controls.stickY).isEqualTo(100);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(80, 140)).isZero();
        assertThat(Controls.choiceAt(230, 140)).isEqualTo(1);
        assertThat(Controls.choiceAt(160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(Controls.choiceAt(80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void stick(int x, int y) {
        Controls.stickX = x;
        Controls.stickY = y;
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = Entities.freeSlot(ents);
        int b = slot * STRIDE;
        ents[b + TYPE] = type;
        ents[b + X] = x;
        ents[b + Y] = y;
        ents[b + Z] = z;
        ents[b + VX] = 0;
        ents[b + VY] = 0;
        ents[b + VZ] = 0;
        return slot;
    }

    private int count(int type) {
        return Entities.count(ents, type);
    }

    private void clearAll() {
        for (int i = 0; i < ents.length; i++) {
            ents[i] = 0;
        }
    }
}
