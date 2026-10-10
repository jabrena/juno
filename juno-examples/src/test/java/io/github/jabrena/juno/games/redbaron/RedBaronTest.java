package io.github.jabrena.juno.games.redbaron;

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
 * Rules of Red Baron: banking, turning, guns, enemy fire, crashes, the pilot screen, and the CPU
 * pilot that flies whole waves through the game's own simulation and rendering.
 */
class RedBaronTest {
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
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[26 * STRIDE];
        set(Session.class, "score", 0);
        set(Session.class, "planes", 3);
        set(Session.class, "nextExtraPlane", 20000);
        set(Session.class, "wave", 1);
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        set(Camera.class, "heading", 0f);
        set(Session.class, "frame", 0);
        call(GAME, "startRound", Round.DOGFIGHT, ents);
        clearAll();
    }

    @Test
    void levelFlightKeepsTheHorizonLevelAndBankingRollsIt() {
        stick(0, 0);
        call(Camera.class, "steer", (Object) ents);
        assertThat(callInt(Camera.class, "rollY", 0, 132)).isEqualTo(132);
        assertThat(callInt(Camera.class, "rollY", 319, 132)).isEqualTo(132);
        for (int i = 0; i < 20; i++) {
            stick(100, 0);
            call(Camera.class, "steer", (Object) ents);
        }
        assertThat((float) get(Camera.class, "bank")).isEqualTo(35f);
        // Banked right, the right wing drops, so the ground rises on the right of the view: the
        // horizon's right end goes up the screen and its left end down.
        assertThat(callInt(Camera.class, "rollY", 319, 132)).isLessThan(132 - 50);
        assertThat(callInt(Camera.class, "rollY", 0, 132)).isGreaterThan(132 + 50);
        assertThat(callInt(Camera.class, "rollX", 160, 132)).as("the center stays put").isEqualTo(160);
    }

    @Test
    void turningRightSwingsWhatIsAheadToTheLeft() {
        int plane = spawn(PLANE, 0, 300, 1000);
        for (int i = 0; i < 30; i++) {
            stick(100, 0);
            call(Camera.class, "steer", (Object) ents);
        }
        assertThat(ents[plane * STRIDE + X]).isLessThan(-100);
        assertThat((float) get(Camera.class, "heading")).isGreaterThan(20f);
    }

    @Test
    void stickUpClimbsAndTheAltitudeIsBounded() {
        for (int i = 0; i < 200; i++) {
            stick(0, 100);
            call(Camera.class, "steer", (Object) ents);
        }
        assertThat(getInt(Camera.class, "altitude")).isEqualTo(520);
        for (int i = 0; i < 200; i++) {
            stick(0, -100);
            call(Camera.class, "steer", (Object) ents);
        }
        assertThat(getInt(Camera.class, "altitude")).isEqualTo(40);
    }

    @Test
    void theGunsShootDownAPlaneInTheSight() {
        int plane = spawn(PLANE, 0, 300, 500);
        ents[plane * STRIDE + VZ] = 24;
        call(Combat.class, "fire", (Object) ents);
        assertThat(count(BULLET)).isEqualTo(2);
        for (int i = 0; i < 12 && ents[plane * STRIDE + TYPE] == PLANE; i++) {
            call(Entities.class, "move", (Object) ents);
            ents[plane * STRIDE + X] = 0;
            ents[plane * STRIDE + Y] = 300;
            call(Combat.class, "resolveBullets", (Object) ents);
        }
        assertThat(ents[plane * STRIDE + TYPE]).isEqualTo(FALLING);
        assertThat(getInt(DogfightRound.class, "kills")).isEqualTo(1);
        assertThat(getInt(Session.class, "score")).isEqualTo(1000);
    }

    @Test
    void aPlaneOffTheSightIsMissed() {
        int plane = spawn(PLANE, 400, 300, 500);
        call(Combat.class, "fire", (Object) ents);
        for (int i = 0; i < 14; i++) {
            call(Combat.class, "resolveBullets", (Object) ents);
            call(Entities.class, "move", (Object) ents);
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
            call(Combat.class, "hit", ents, blimp);
            if (shot < 4) {
                assertThat(ents[blimp * STRIDE + TYPE]).isEqualTo(BLIMP);
            }
        }
        assertThat(ents[blimp * STRIDE + TYPE]).as("destroyed").isNotEqualTo(BLIMP);
        assertThat(getInt(Session.class, "score")).isEqualTo(5000);
    }

    @Test
    void aTracerThatReachesYouHits() {
        int tracer = spawn(TRACER, 0, 300, 200);
        ents[tracer * STRIDE + VZ] = -40 + 24;
        for (int i = 0; i < 20 && !(boolean) get(Session.class, "shotDown"); i++) {
            call(Entities.class, "move", (Object) ents);
        }
        assertThat((boolean) get(Session.class, "shotDown")).isTrue();
    }

    @Test
    void enemyFireAimsNearYouGiveOrTakeTheScatter() {
        int hits = 0;
        for (int burst = 0; burst < 60; burst++) {
            clearAll();
            set(Session.class, "shotDown", false);
            call(Entities.class, "enemyFires", ents, 0, 300, 600, false);
            for (int i = 0; i < 40 && count(TRACER) > 0; i++) {
                call(Entities.class, "move", (Object) ents);
            }
            if ((boolean) get(Session.class, "shotDown")) {
                hits = hits + 1;
            }
        }
        assertThat(hits).as("most bursts from dead ahead hit, but not all").isBetween(15, 59);
    }

    @Test
    void turningAwayDodgesATracer() {
        call(Entities.class, "enemyFires", ents, 0, 300, 900, false);
        for (int i = 0; i < 60; i++) {
            stick(100, 0);
            call(Camera.class, "steer", (Object) ents);
            call(Entities.class, "move", (Object) ents);
        }
        assertThat((boolean) get(Session.class, "shotDown")).isFalse();
    }

    @Test
    void flyingLowIntoAPyramidCrashes() {
        set(Camera.class, "altitude", 80);
        spawn(PYRAMID, 0, 0, 60);
        call(Entities.class, "move", (Object) ents);
        call(Entities.class, "move", (Object) ents);
        assertThat((boolean) get(Session.class, "shotDown")).isTrue();
    }

    @Test
    void flyingHighClearsAPyramid() {
        set(Camera.class, "altitude", 200);
        spawn(PYRAMID, 0, 0, 60);
        call(Entities.class, "move", (Object) ents);
        call(Entities.class, "move", (Object) ents);
        assertThat((boolean) get(Session.class, "shotDown")).isFalse();
    }

    @Test
    void groundTargetsCanOnlyBeHitFromLowDown() {
        int hangar = spawn(HANGAR, 0, 0, 400);
        set(Camera.class, "altitude", 300);
        assertThat(callInt(Combat.class, "targetHitBy", ents, 0, 274, 400))
                .as("bullets pass high over it").isEqualTo(-1);
        assertThat(callInt(Combat.class, "targetHitBy", ents, 0, 40, 400)).isEqualTo(hangar);
    }

    @Test
    void theLastPlaneLostEndsTheGame() {
        set(Session.class, "planes", 1);
        assertThat(callBoolean(Session.class, "loseAPlane", (Object) ents)).isFalse();
        assertThat((boolean) get(Session.class, "dead")).isTrue();
        set(Session.class, "planes", 3);
        assertThat(callBoolean(Session.class, "loseAPlane", (Object) ents)).isTrue();
        assertThat(getInt(Session.class, "planes")).isEqualTo(2);
    }

    @Test
    void anExtraPlaneEveryTwentyThousandPoints() {
        set(Session.class, "score", 19500);
        call(Session.class, "addScore", 1000);
        assertThat(getInt(Session.class, "planes")).isEqualTo(4);
        call(Session.class, "addScore", 1000);
        assertThat(getInt(Session.class, "planes")).isEqualTo(4);
    }

    @Test
    void everyLineIsClippedToTheView() {
        set(Camera.class, "bank", 30f);
        call(Camera.class, "steer", (Object) ents);
        spawn(PLANE, -200, 250, 80);
        spawn(PYRAMID, 100, 0, 200);
        call(SceneRenderer.class, "render", lines, ents);
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

    /** The CPU pilot chases the nearest target and fires when it is in the sight. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theCpuPilotFliesAWholeWave(int wave) {
        set(Session.class, "wave", wave);
        set(Controls.class, "autopilot", true);
        assertThat(flyRound(Round.DOGFIGHT)).as("dogfight, wave %d", wave).isTrue();
        assertThat(getInt(DogfightRound.class, "kills"))
                .isGreaterThanOrEqualTo(getInt(DogfightRound.class, "killTarget"));
        assertThat(flyRound(Round.GROUND_ATTACK)).as("ground attack, wave %d", wave).isTrue();
        assertThat(getInt(GroundAttackRound.class, "targetsHit")).as("ground targets destroyed").isPositive();
        assertThat(getInt(Session.class, "score")).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(Round round) {
        call(GAME, "startRound", round, ents);
        for (int frame = 0; frame < 6000; frame++) {
            set(Session.class, "frame", frame);
            call(AutopilotRB.class, "fly", (Object) ents);
            call(Camera.class, "steer", (Object) ents);
            call(GAME, "step", (Object) ents);
            call(Combat.class, "resolveBullets", (Object) ents);
            call(SceneRenderer.class, "render", lines, ents);
            if ((boolean) get(Session.class, "shotDown")) {
                set(Session.class, "planes", 3);
                call(Session.class, "loseAPlane", (Object) ents);
            }
            if (callBoolean(GAME, "roundOver", (Object) ents)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theCpuPilotClimbsOverAPyramidAhead() {
        set(Session.class, "round", Round.GROUND_ATTACK);
        set(Camera.class, "altitude", 100);
        spawn(PYRAMID, 0, 0, 400);
        call(AutopilotRB.class, "fly", (Object) ents);
        assertThat(getInt(Controls.class, "stickY")).isEqualTo(100);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void stick(int x, int y) {
        set(Controls.class, "stickX", x);
        set(Controls.class, "stickY", y);
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = callInt(Entities.class, "freeSlot", (Object) ents);
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
        return callInt(Entities.class, "count", ents, type);
    }

    private void clearAll() {
        for (int i = 0; i < ents.length; i++) {
            ents[i] = 0;
        }
    }
}
