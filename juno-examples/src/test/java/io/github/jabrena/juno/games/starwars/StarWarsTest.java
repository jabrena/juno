package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Rules of Star Wars: clipping, lasers, Vader, shields, catwalks, the pilot screen, and the CPU pilot
 * that flies whole waves through the game's own simulation and rendering.
 */
class StarWarsTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
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
        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 180 * 5];
        ents = new int[24 * STRIDE];
        Session.score = 0;
        Session.shields = 6;
        Session.wave = 1;
        DisplayList.shown = 0;
        DisplayList.built = 0;
        Controls.crossX = 160;
        Controls.crossY = 130;
        StarWars.startPhase(Phase.SPACE, ents);
    }

    @Test
    void linesAreClippedToTheView() {
        DisplayList.addLine(lines, -5000, -2000, 5000, 2260, 0xFFFF);
        DisplayList.addLine(lines, -50, 10, -10, 400, 0xFFFF);
        DisplayList.addLine(lines, 100, 100, 100, 100, 0xFFFF);
        assertThat(DisplayList.built).as("the second line is wholly off screen").isEqualTo(2);
        int base = (1 - DisplayList.front) * 180 * 5;
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
        Combat.fire(ents);
        converge();
        assertThat(ents[tie * STRIDE + TYPE]).as("destroyed").isNotEqualTo(TIE);
        assertThat(SpacePhase.kills).isEqualTo(1);
        assertThat(Session.score).isEqualTo(1000);
    }

    @Test
    void lasersFiredAtEmptySpaceMiss() {
        int tie = spawn(TIE, 60, -40, 700);
        render();
        Controls.crossX = 20;
        Controls.crossY = 30;
        Combat.fire(ents);
        converge();
        assertThat(ents[tie * STRIDE + TYPE]).isEqualTo(TIE);
        assertThat(Session.score).isZero();
    }

    @Test
    void vaderCannotBeDestroyedOnlyDrivenOff() {
        int vader = spawn(VADER, 0, 0, 600);
        render();
        aimAt(vader);
        Combat.fire(ents);
        converge();
        assertThat(ents[vader * STRIDE + TYPE]).isEqualTo(VADER);
        assertThat(ents[vader * STRIDE + 6]).as("flying away").isPositive();
        assertThat(SpacePhase.kills).isZero();
        assertThat(Session.score).isZero();
    }

    @Test
    void fireballsCostAShieldAndTheLastHitEndsTheGame() {
        Entities.fireball(ents, 300, 200, 900);
        for (int frame = 0; frame < 200 && Session.shields == 6; frame++) {
            StarWars.step(tft, ents);
        }
        assertThat(Session.shields).isEqualTo(5);
        assertThat(StarWars.phaseOver(ents)).isFalse();
        Session.shields = 0;
        Session.shieldHit(tft);
        assertThat((boolean) Session.dead).isTrue();
    }

    @Test
    void shootingAFireballDownScores() {
        Entities.fireball(ents, 0, 0, 1200);
        int fireball = find(FIREBALL);
        render();
        aimAt(fireball);
        Combat.fire(ents);
        converge();
        assertThat(find(FIREBALL)).isNegative();
        assertThat(Session.score).isEqualTo(33);
        assertThat(Session.shields).isEqualTo(6);
    }

    @Test
    void catwalksMustBeClearedAboveOrBelow() {
        StarWars.startPhase(Phase.TRENCH, ents);
        TrenchPhase.trenchLength = 1 << 29;
        Session.spawnCountdown = 1 << 29;
        int catwalk = spawn(CATWALK, 0, 0, 400);
        for (int frame = 0; frame < 40 && ents[catwalk * STRIDE + TYPE] == CATWALK; frame++) {
            StarWars.step(tft, ents);
        }
        assertThat(Session.shields).as("flying straight into it").isEqualTo(5);

        Controls.crossY = 30;
        for (int frame = 0; frame < 20; frame++) {
            Camera.steer();
        }
        assertThat(Camera.camY).isEqualTo(110);
        catwalk = spawn(CATWALK, 0, 0, 400);
        for (int frame = 0; frame < 40 && ents[catwalk * STRIDE + TYPE] == CATWALK; frame++) {
            StarWars.step(tft, ents);
        }
        assertThat(Session.shields).as("passing above").isEqualTo(5);
    }

    @Test
    void missingTheExhaustPortEndsTheRunWithoutDestroyingIt() {
        StarWars.startPhase(Phase.TRENCH, ents);
        spawn(PORT, 0, -150, 600);
        TrenchPhase.portSpawned = true;
        for (int frame = 0; frame < 100 && !StarWars.phaseOver(ents); frame++) {
            StarWars.step(tft, ents);
        }
        assertThat((boolean) TrenchPhase.portMissed).isTrue();
        assertThat((boolean) TrenchPhase.portDestroyed).isFalse();
    }

    /** The CPU pilot flies three waves without being destroyed, though it is not a perfect shot. */
    @Test
    void theCpuPilotDestroysTheDeathStarWaveAfterWave() {
        Controls.autopilot = true;
        for (int wave = 1; wave <= 3; wave++) {
            Session.wave = wave;
            for (Phase phase : Phase.values()) {
                boolean done = false;
                for (int attempt = 0; attempt < 4 && !done; attempt++) {
                    StarWars.startPhase(phase, ents);
                    fly(phase);
                    done = phase != Phase.TRENCH || (boolean) TrenchPhase.portDestroyed;
                }
                assertThat(done).as("wave %d: exhaust port destroyed", wave).isTrue();
            }
            Interludes.destroyDeathStar(tft);
        }
        assertThat(Session.score).isGreaterThan(100000);
        assertThat(Session.shields).isGreaterThan(0);
        assertThat(misses).as("shots fired at nothing").isPositive();
        assertThat(onTarget).as("shots fired at a target").isGreaterThan(misses);
    }

    @Test
    void theCpuPilotSteersPastCatwalksInTheTrench() {
        StarWars.startPhase(Phase.TRENCH, ents);
        spawn(CATWALK, 0, 60, 900);
        AutopilotSW.fly(ents);
        assertThat(Controls.crossY).as("below a high catwalk").isEqualTo(210);
        ents[0] = 0;
        spawn(CATWALK, 0, -60, 900);
        AutopilotSW.fly(ents);
        assertThat(Controls.crossY).as("above a low catwalk").isEqualTo(50);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat((int) Controls.choiceAt(80, 140)).isZero();
        assertThat((int) Controls.choiceAt(230, 140)).isEqualTo(1);
        assertThat((int) Controls.choiceAt(160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat((int) Controls.choiceAt(80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void fly(Phase phase) {
        for (int frame = 1; frame < 5000; frame++) {
            Session.frame = frame;
            AutopilotSW.fly(ents);
            countNewShots();
            StarWars.step(tft, ents);
            Combat.resolveShots(tft, ents);
            SceneRenderer.render(tft, lines, ents);
            assertThat((boolean) Session.dead).as("destroyed in phase %s", phase).isFalse();
            if (StarWars.phaseOver(ents)) {
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
        int slot = (int) Entities.freeSlot(ents);
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
        SceneRenderer.render(tft, lines, ents);
    }

    private void aimAt(int slot) {
        Controls.crossX = ents[slot * STRIDE + SX];
        Controls.crossY = ents[slot * STRIDE + SY];
    }

    /** Lets the lasers reach the crosshair, with nothing else moving. */
    private void converge() {
        for (int frame = 0; frame < 4; frame++) {
            Combat.resolveShots(tft, ents);
        }
    }
}
