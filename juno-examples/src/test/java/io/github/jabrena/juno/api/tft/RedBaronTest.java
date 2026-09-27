package io.github.jabrena.juno.api.tft;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.callInt;
import static io.github.jabrena.juno.api.tft.Internals.get;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Rules of Red Baron: banking, turning, guns, enemy fire, crashes, and an autopilot that flies
 * whole waves through the game's own simulation and rendering.
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
    private static final int DOGFIGHT = 0;
    private static final int GROUND_ATTACK = 1;

    private short[] lines;
    private int[] ents;

    @BeforeEach
    void newGame() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[26 * STRIDE];
        set(GAME, "score", 0);
        set(GAME, "planes", 3);
        set(GAME, "nextExtraPlane", 20000);
        set(GAME, "wave", 1);
        set(GAME, "shown", 0);
        set(GAME, "built", 0);
        set(GAME, "heading", 0f);
        set(GAME, "frame", 0);
        call(GAME, "startRound", DOGFIGHT, ents);
        clearAll();
    }

    @Test
    void levelFlightKeepsTheHorizonLevelAndBankingRollsIt() {
        stick(0, 0);
        call(GAME, "fly", (Object) ents);
        assertThat(callInt(GAME, "rollY", 0, 132)).isEqualTo(132);
        assertThat(callInt(GAME, "rollY", 319, 132)).isEqualTo(132);
        for (int i = 0; i < 20; i++) {
            stick(100, 0);
            call(GAME, "fly", (Object) ents);
        }
        assertThat((float) get(GAME, "bank")).isEqualTo(35f);
        // Banked right, the right wing drops, so the ground rises on the right of the view: the
        // horizon's right end goes up the screen and its left end down.
        assertThat(callInt(GAME, "rollY", 319, 132)).isLessThan(132 - 50);
        assertThat(callInt(GAME, "rollY", 0, 132)).isGreaterThan(132 + 50);
        assertThat(callInt(GAME, "rollX", 160, 132)).as("the center stays put").isEqualTo(160);
    }

    @Test
    void turningRightSwingsWhatIsAheadToTheLeft() {
        int plane = spawn(PLANE, 0, 300, 1000);
        for (int i = 0; i < 30; i++) {
            stick(100, 0);
            call(GAME, "fly", (Object) ents);
        }
        assertThat(ents[plane * STRIDE + X]).isLessThan(-100);
        assertThat((float) get(GAME, "heading")).isGreaterThan(20f);
    }

    @Test
    void stickUpClimbsAndTheAltitudeIsBounded() {
        for (int i = 0; i < 200; i++) {
            stick(0, 100);
            call(GAME, "fly", (Object) ents);
        }
        assertThat(getInt(GAME, "altitude")).isEqualTo(520);
        for (int i = 0; i < 200; i++) {
            stick(0, -100);
            call(GAME, "fly", (Object) ents);
        }
        assertThat(getInt(GAME, "altitude")).isEqualTo(40);
    }

    @Test
    void theGunsShootDownAPlaneInTheSight() {
        int plane = spawn(PLANE, 0, 300, 500);
        ents[plane * STRIDE + VZ] = 24;
        call(GAME, "fire", (Object) ents);
        assertThat(count(BULLET)).isEqualTo(2);
        for (int i = 0; i < 12 && ents[plane * STRIDE + TYPE] == PLANE; i++) {
            call(GAME, "step", (Object) ents);
            ents[plane * STRIDE + X] = 0;
            ents[plane * STRIDE + Y] = 300;
            call(GAME, "resolveBullets", (Object) ents);
        }
        assertThat(ents[plane * STRIDE + TYPE]).isEqualTo(FALLING);
        assertThat(getInt(GAME, "kills")).isEqualTo(1);
        assertThat(getInt(GAME, "score")).isEqualTo(1000);
    }

    @Test
    void aPlaneOffTheSightIsMissed() {
        int plane = spawn(PLANE, 400, 300, 500);
        call(GAME, "fire", (Object) ents);
        for (int i = 0; i < 14; i++) {
            call(GAME, "resolveBullets", (Object) ents);
            call(GAME, "step", (Object) ents);
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
            call(GAME, "hit", ents, blimp);
            if (shot < 4) {
                assertThat(ents[blimp * STRIDE + TYPE]).isEqualTo(BLIMP);
            }
        }
        assertThat(ents[blimp * STRIDE + TYPE]).as("destroyed").isNotEqualTo(BLIMP);
        assertThat(getInt(GAME, "score")).isEqualTo(5000);
    }

    @Test
    void aTracerThatReachesYouHits() {
        int tracer = spawn(TRACER, 0, 300, 200);
        ents[tracer * STRIDE + VZ] = -40 + 24;
        for (int i = 0; i < 20 && !(boolean) get(GAME, "shotDown"); i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(GAME, "shotDown")).isTrue();
    }

    @Test
    void enemyFireAimsNearYouGiveOrTakeTheScatter() {
        int hits = 0;
        for (int burst = 0; burst < 60; burst++) {
            clearAll();
            set(GAME, "shotDown", false);
            call(GAME, "enemyFires", ents, 0, 300, 600, false);
            for (int i = 0; i < 40 && count(TRACER) > 0; i++) {
                call(GAME, "step", (Object) ents);
            }
            if ((boolean) get(GAME, "shotDown")) {
                hits = hits + 1;
            }
        }
        assertThat(hits).as("most bursts from dead ahead hit, but not all").isBetween(15, 59);
    }

    @Test
    void turningAwayDodgesATracer() {
        call(GAME, "enemyFires", ents, 0, 300, 900, false);
        for (int i = 0; i < 60; i++) {
            stick(100, 0);
            call(GAME, "fly", (Object) ents);
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(GAME, "shotDown")).isFalse();
    }

    @Test
    void flyingLowIntoAPyramidCrashes() {
        set(GAME, "altitude", 80);
        spawn(PYRAMID, 0, 0, 60);
        call(GAME, "step", (Object) ents);
        call(GAME, "step", (Object) ents);
        assertThat((boolean) get(GAME, "shotDown")).isTrue();
    }

    @Test
    void flyingHighClearsAPyramid() {
        set(GAME, "altitude", 200);
        spawn(PYRAMID, 0, 0, 60);
        call(GAME, "step", (Object) ents);
        call(GAME, "step", (Object) ents);
        assertThat((boolean) get(GAME, "shotDown")).isFalse();
    }

    @Test
    void groundTargetsCanOnlyBeHitFromLowDown() {
        int hangar = spawn(HANGAR, 0, 0, 400);
        set(GAME, "altitude", 300);
        assertThat(callInt(GAME, "targetHitBy", ents, 0, 274, 400)).as("bullets pass high over it").isEqualTo(-1);
        assertThat(callInt(GAME, "targetHitBy", ents, 0, 40, 400)).isEqualTo(hangar);
    }

    @Test
    void theLastPlaneLostEndsTheGame() {
        set(GAME, "planes", 1);
        assertThat(callBoolean(GAME, "loseAPlane", (Object) ents)).isFalse();
        assertThat((boolean) get(GAME, "dead")).isTrue();
        set(GAME, "planes", 3);
        assertThat(callBoolean(GAME, "loseAPlane", (Object) ents)).isTrue();
        assertThat(getInt(GAME, "planes")).isEqualTo(2);
    }

    @Test
    void anExtraPlaneEveryTwentyThousandPoints() {
        set(GAME, "score", 19500);
        call(GAME, "addScore", 1000);
        assertThat(getInt(GAME, "planes")).isEqualTo(4);
        call(GAME, "addScore", 1000);
        assertThat(getInt(GAME, "planes")).isEqualTo(4);
    }

    @Test
    void everyLineIsClippedToTheView() {
        set(GAME, "bank", 30f);
        call(GAME, "fly", (Object) ents);
        spawn(PLANE, -200, 250, 80);
        spawn(PYRAMID, 100, 0, 200);
        call(GAME, "render", lines, ents);
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

    /** An autopilot that aims at the nearest target and fires when it is in the sight. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void anAutopilotFliesAWholeWave(int wave) {
        set(GAME, "wave", wave);
        assertThat(flyRound(DOGFIGHT)).as("dogfight, wave %d", wave).isTrue();
        assertThat(getInt(GAME, "kills")).isGreaterThanOrEqualTo(getInt(GAME, "killTarget"));
        assertThat(flyRound(GROUND_ATTACK)).as("ground attack, wave %d", wave).isTrue();
        assertThat(getInt(GAME, "targetsHit")).as("ground targets destroyed").isPositive();
        assertThat(getInt(GAME, "score")).isPositive();
    }

    /** Plays a round with the game's own per-frame steps; false if it does not finish in time. */
    private boolean flyRound(int round) {
        call(GAME, "startRound", round, ents);
        for (int frame = 0; frame < 6000; frame++) {
            set(GAME, "frame", frame);
            autopilot(frame);
            call(GAME, "fly", (Object) ents);
            call(GAME, "spawn", (Object) ents);
            call(GAME, "step", (Object) ents);
            call(GAME, "resolveBullets", (Object) ents);
            call(GAME, "render", lines, ents);
            if ((boolean) get(GAME, "shotDown")) {
                set(GAME, "planes", 3);
                call(GAME, "loseAPlane", (Object) ents);
            }
            if (callBoolean(GAME, "roundOver", (Object) ents)) {
                return true;
            }
        }
        return false;
    }

    private void autopilot(int frame) {
        int altitude = getInt(GAME, "altitude");
        int best = -1;
        int bestZ = Integer.MAX_VALUE;
        boolean ground = getInt(GAME, "round") == GROUND_ATTACK;
        for (int slot = 0; slot < 26; slot++) {
            int type = ents[slot * STRIDE + TYPE];
            int z = ents[slot * STRIDE + Z];
            boolean target = ground ? type == HANGAR || type == FLAK : type == PLANE || type == BLIMP;
            if (target && z > 60 && z < bestZ) {
                best = slot;
                bestZ = z;
            }
        }
        int stickX = 0;
        int stickY = 0;
        if (best >= 0) {
            int x = ents[best * STRIDE + X];
            int y = ground ? 35 + 26 : ents[best * STRIDE + Y] + 26;
            stickX = Math.max(-100, Math.min(100, x * 400 / Math.max(bestZ, 1)));
            stickY = Math.max(-100, Math.min(100, (y - altitude) * 4));
            if (Math.abs(x) < 40 + bestZ / 12 && Math.abs(y - altitude) < 50 && bestZ < 900 && frame % 3 == 0) {
                call(GAME, "fire", (Object) ents);
            }
        }
        // Climb over pyramids in the way.
        for (int slot = 0; slot < 26; slot++) {
            int b = slot * STRIDE;
            if (ents[b + TYPE] == PYRAMID && ents[b + Z] < 500 && Math.abs(ents[b + X]) < 120 && altitude < 150) {
                stickY = 100;
            }
        }
        stick(stickX, stickY);
    }

    private void stick(int x, int y) {
        set(GAME, "stickX", x);
        set(GAME, "stickY", y);
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = callInt(GAME, "freeSlot", (Object) ents);
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
        return callInt(GAME, "count", ents, type);
    }

    private void clearAll() {
        for (int i = 0; i < ents.length; i++) {
            ents[i] = 0;
        }
    }
}
