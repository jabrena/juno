package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.jabrena.juno.api.tft.Internals.call;
import static io.github.jabrena.juno.api.tft.Internals.callBoolean;
import static io.github.jabrena.juno.api.tft.Internals.get;
import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static io.github.jabrena.juno.api.tft.Internals.set;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules of Star Wars: clipping, lasers, Vader, shields, catwalks, the pilot screen, and the CPU pilot
 * that flies whole waves through the game's own simulation and rendering.
 */
class StarWarsTest {
    private static final Class<?> GAME = StarWars.class;
    private static final int STRIDE = 13;
    private static final int TYPE = 0;
    private static final int X = 1;
    private static final int Y = 2;
    private static final int Z = 3;
    private static final int SX = 8;
    private static final int SY = 9;
    private static final int TIE = 1;
    private static final int VADER = 2;
    private static final int FIREBALL = 3;
    private static final int TURRET = 5;
    private static final int CATWALK = 6;
    private static final int PORT = 7;

    private short[] lines;
    private int[] ents;
    private int onTarget;
    private int misses;

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
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        set(Controls.class, "crossX", 160);
        set(Controls.class, "crossY", 130);
        call(GAME, "startPhase", Phase.SPACE, ents);
    }

    @Test
    void linesAreClippedToTheView() {
        call(DisplayList.class, "addLine", lines, -5000, -2000, 5000, 2260, 0xFFFF);
        call(DisplayList.class, "addLine", lines, -50, 10, -10, 400, 0xFFFF);
        call(DisplayList.class, "addLine", lines, 100, 100, 100, 100, 0xFFFF);
        assertThat(getInt(DisplayList.class, "built")).as("the second line is wholly off screen").isEqualTo(2);
        int base = (1 - getInt(DisplayList.class, "front")) * 180 * 5;
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < 4; k = k + 2) {
                assertThat((int) lines[base + i * 5 + k]).isBetween(0, 319);
                assertThat((int) lines[base + i * 5 + k + 1]).isBetween(20, 239);
            }
        }
    }

    @Test
    void lasersDestroyTheTieFighterUnderTheCrosshair() {
        int tie = spawn(TIE, 60, -40, 700);
        render();
        aimAt(tie);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[tie * STRIDE + TYPE]).as("destroyed").isNotEqualTo(TIE);
        assertThat(getInt(SpacePhase.class, "kills")).isEqualTo(1);
        assertThat(getInt(Session.class, "score")).isEqualTo(1000);
    }

    @Test
    void lasersFiredAtEmptySpaceMiss() {
        int tie = spawn(TIE, 60, -40, 700);
        render();
        set(Controls.class, "crossX", 20);
        set(Controls.class, "crossY", 30);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[tie * STRIDE + TYPE]).isEqualTo(TIE);
        assertThat(getInt(Session.class, "score")).isZero();
    }

    @Test
    void vaderCannotBeDestroyedOnlyDrivenOff() {
        int vader = spawn(VADER, 0, 0, 600);
        render();
        aimAt(vader);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(ents[vader * STRIDE + TYPE]).isEqualTo(VADER);
        assertThat(ents[vader * STRIDE + 6]).as("flying away").isPositive();
        assertThat(getInt(SpacePhase.class, "kills")).isZero();
        assertThat(getInt(Session.class, "score")).isZero();
    }

    @Test
    void fireballsCostAShieldAndTheLastHitEndsTheGame() {
        call(Entities.class, "fireball", ents, 300, 200, 900);
        for (int frame = 0; frame < 200 && getInt(Session.class, "shields") == 6; frame++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Session.class, "shields")).isEqualTo(5);
        assertThat(callBoolean(GAME, "phaseOver", (Object) ents)).isFalse();
        set(Session.class, "shields", 0);
        call(Session.class, "shieldHit");
        assertThat((boolean) get(Session.class, "dead")).isTrue();
    }

    @Test
    void shootingAFireballDownScores() {
        call(Entities.class, "fireball", ents, 0, 0, 1200);
        int fireball = find(FIREBALL);
        render();
        aimAt(fireball);
        call(Combat.class, "fire", (Object) ents);
        converge();
        assertThat(find(FIREBALL)).isNegative();
        assertThat(getInt(Session.class, "score")).isEqualTo(33);
        assertThat(getInt(Session.class, "shields")).isEqualTo(6);
    }

    @Test
    void catwalksMustBeClearedAboveOrBelow() {
        call(GAME, "startPhase", Phase.TRENCH, ents);
        set(TrenchPhase.class, "trenchLength", 1 << 29);
        set(Session.class, "spawnCountdown", 1 << 29);
        int catwalk = spawn(CATWALK, 0, 0, 400);
        for (int frame = 0; frame < 40 && ents[catwalk * STRIDE + TYPE] == CATWALK; frame++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Session.class, "shields")).as("flying straight into it").isEqualTo(5);

        set(Controls.class, "crossY", 30);
        for (int frame = 0; frame < 20; frame++) {
            call(Camera.class, "steer");
        }
        assertThat(getInt(Camera.class, "camY")).isEqualTo(110);
        catwalk = spawn(CATWALK, 0, 0, 400);
        for (int frame = 0; frame < 40 && ents[catwalk * STRIDE + TYPE] == CATWALK; frame++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Session.class, "shields")).as("passing above").isEqualTo(5);
    }

    @Test
    void missingTheExhaustPortEndsTheRunWithoutDestroyingIt() {
        call(GAME, "startPhase", Phase.TRENCH, ents);
        spawn(PORT, 0, -150, 600);
        set(TrenchPhase.class, "portSpawned", true);
        for (int frame = 0; frame < 100 && !callBoolean(GAME, "phaseOver", (Object) ents); frame++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(TrenchPhase.class, "portMissed")).isTrue();
        assertThat((boolean) get(TrenchPhase.class, "portDestroyed")).isFalse();
    }

    /** The CPU pilot flies three waves without being destroyed, though it is not a perfect shot. */
    @Test
    void theCpuPilotDestroysTheDeathStarWaveAfterWave() {
        set(Controls.class, "autopilot", true);
        for (int wave = 1; wave <= 3; wave++) {
            set(Session.class, "wave", wave);
            for (Phase phase : Phase.values()) {
                boolean done = false;
                for (int attempt = 0; attempt < 4 && !done; attempt++) {
                    call(GAME, "startPhase", phase, ents);
                    fly(phase);
                    done = phase != Phase.TRENCH || (boolean) get(TrenchPhase.class, "portDestroyed");
                }
                assertThat(done).as("wave %d: exhaust port destroyed", wave).isTrue();
            }
            call(Interludes.class, "destroyDeathStar");
        }
        assertThat(getInt(Session.class, "score")).isGreaterThan(100000);
        assertThat(getInt(Session.class, "shields")).isGreaterThan(0);
        assertThat(misses).as("shots fired at nothing").isPositive();
        assertThat(onTarget).as("shots fired at a target").isGreaterThan(misses);
    }

    @Test
    void theCpuPilotSteersPastCatwalksInTheTrench() {
        call(GAME, "startPhase", Phase.TRENCH, ents);
        spawn(CATWALK, 0, 60, 900);
        call(AutopilotSW.class, "fly", (Object) ents);
        assertThat(getInt(Controls.class, "crossY")).as("below a high catwalk").isEqualTo(210);
        ents[0] = 0;
        spawn(CATWALK, 0, -60, 900);
        call(AutopilotSW.class, "fly", (Object) ents);
        assertThat(getInt(Controls.class, "crossY")).as("above a low catwalk").isEqualTo(50);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat((int) call(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat((int) call(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat((int) call(Controls.class, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat((int) call(Controls.class, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void fly(Phase phase) {
        for (int frame = 1; frame < 5000; frame++) {
            set(Session.class, "frame", frame);
            call(AutopilotSW.class, "fly", (Object) ents);
            countNewShots();
            call(GAME, "step", (Object) ents);
            call(Combat.class, "resolveShots", (Object) ents);
            call(SceneRenderer.class, "render", lines, ents);
            assertThat((boolean) get(Session.class, "dead")).as("destroyed in phase %s", phase).isFalse();
            if (callBoolean(GAME, "phaseOver", (Object) ents)) {
                return;
            }
        }
        throw new AssertionError("phase " + phase + " never ended");
    }

    /** Tallies the shots fired this frame: with a target under the crosshair, or at empty space. */
    private void countNewShots() {
        for (int slot = 0; slot < 24; slot++) {
            int b = slot * STRIDE;
            if (ents[b + TYPE] == 9 && ents[b + 7] == 4) {
                if (ents[b + 11] >= 0) {
                    onTarget = onTarget + 1;
                } else {
                    misses = misses + 1;
                }
            }
        }
    }

    private int spawn(int type, int x, int y, int z) {
        int slot = (int) call(Entities.class, "freeSlot", (Object) ents);
        ents[slot * STRIDE + TYPE] = type;
        ents[slot * STRIDE + X] = x;
        ents[slot * STRIDE + Y] = y;
        ents[slot * STRIDE + Z] = z;
        if (type == TURRET) {
            ents[slot * STRIDE + 7] = 1000;
        }
        return slot;
    }

    private int find(int type) {
        for (int slot = 0; slot < 24; slot++) {
            if (ents[slot * STRIDE + TYPE] == type) {
                return slot;
            }
        }
        return -1;
    }

    private void render() {
        call(SceneRenderer.class, "render", lines, ents);
    }

    private void aimAt(int slot) {
        set(Controls.class, "crossX", ents[slot * STRIDE + SX]);
        set(Controls.class, "crossY", ents[slot * STRIDE + SY]);
    }

    /** Lets the lasers reach the crosshair, with nothing else moving. */
    private void converge() {
        for (int frame = 0; frame < 4; frame++) {
            call(Combat.class, "resolveShots", (Object) ents);
        }
    }
}
