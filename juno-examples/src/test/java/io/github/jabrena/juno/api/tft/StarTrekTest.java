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
 * Rules of Star Trek: the wrapping sector, phasers, photon torpedoes, Klingon torpedoes, docking, the
 * starbase, saucers, Nomad's mines, and an autopilot that clears whole sectors.
 */
class StarTrekTest {
    private static final Class<?> GAME = StarTrek.class;
    private static final int FIX = 16;
    private static final int STRIDE = 10;
    private static final int TYPE = 0;
    private static final int X = 1;
    private static final int Y = 2;
    private static final int HEADING = 5;
    private static final int HP = 7;
    private static final int COOL = 9;
    private static final int KLINGON = 1;
    private static final int TORPEDO = 2;
    private static final int PHOTON = 3;
    private static final int STARBASE = 4;
    private static final int SAUCER = 5;
    private static final int NOMAD = 6;
    private static final int MINE = 7;
    private static final int CENTER = 1024 * FIX;

    private short[] lines;
    private int[] ents;

    @BeforeEach
    void newGame() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 200 * 5];
        ents = new int[24 * STRIDE];
        set(GAME, "score", 0);
        set(GAME, "sector", 1);
        set(GAME, "shields", 100);
        set(GAME, "photons", 5);
        set(GAME, "warps", 3);
        set(GAME, "shown", 0);
        set(GAME, "built", 0);
        call(GAME, "startSector", (Object) ents);
        clearAll();
    }

    @Test
    void theSectorWrapsAround() {
        assertThat(callInt(GAME, "wrap", 2000 * FIX)).isEqualTo(-48 * FIX);
        assertThat(callInt(GAME, "wrap", -2000 * FIX)).isEqualTo(48 * FIX);
        assertThat(callInt(GAME, "wrapPosition", -16)).isEqualTo(2048 * FIX - 16);
    }

    @Test
    void phasersHitTheFirstKlingonStraightAheadTwiceToDestroyIt() {
        int near = spawn(KLINGON, CENTER, CENTER - 150 * FIX, 2);
        int far = spawn(KLINGON, CENTER, CENTER - 250 * FIX, 2);
        fireAndRecharge();
        assertThat(ents[near * STRIDE + HP]).isEqualTo(1);
        assertThat(ents[far * STRIDE + HP]).as("the beam stops at the first ship").isEqualTo(2);
        fireAndRecharge();
        assertThat(ents[near * STRIDE + TYPE]).isNotEqualTo(KLINGON);
        assertThat(getInt(GAME, "score")).isEqualTo(1000);
    }

    @Test
    void phasersMissWhatIsBesideBehindOrOutOfRange() {
        int beside = spawn(KLINGON, CENTER + 120 * FIX, CENTER - 100 * FIX, 2);
        int behind = spawn(KLINGON, CENTER, CENTER + 100 * FIX, 2);
        int far = spawn(KLINGON, CENTER, CENTER - 400 * FIX, 2);
        fireAndRecharge();
        assertThat(ents[beside * STRIDE + HP]).isEqualTo(2);
        assertThat(ents[behind * STRIDE + HP]).isEqualTo(2);
        assertThat(ents[far * STRIDE + HP]).isEqualTo(2);
    }

    @Test
    void phasersNeedToRecharge() {
        int klingon = spawn(KLINGON, CENTER, CENTER - 150 * FIX, 2);
        call(GAME, "firePhasers", (Object) ents);
        call(GAME, "firePhasers", (Object) ents);
        assertThat(ents[klingon * STRIDE + HP]).isEqualTo(1);
    }

    @Test
    void aPhotonTorpedoBlastDestroysEverythingNearby() {
        int a = spawn(KLINGON, CENTER, CENTER - 200 * FIX, 2);
        int b = spawn(KLINGON, CENTER + 40 * FIX, CENTER - 220 * FIX, 2);
        int c = spawn(SAUCER, CENTER - 30 * FIX, CENTER - 190 * FIX, 1);
        int spared = spawn(KLINGON, CENTER + 300 * FIX, CENTER - 200 * FIX, 2);
        call(GAME, "firePhoton", (Object) ents);
        assertThat(getInt(GAME, "photons")).isEqualTo(4);
        for (int i = 0; i < 30; i++) {
            freeze(a, b, c, spared);
            call(GAME, "step", (Object) ents);
        }
        assertThat(ents[a * STRIDE + TYPE]).isNotEqualTo(KLINGON);
        assertThat(ents[b * STRIDE + TYPE]).isNotEqualTo(KLINGON);
        assertThat(ents[c * STRIDE + TYPE]).isNotEqualTo(SAUCER);
        assertThat(ents[spared * STRIDE + TYPE]).isEqualTo(KLINGON);
    }

    @Test
    void photonTorpedoesRunOut() {
        set(GAME, "photons", 0);
        call(GAME, "firePhoton", (Object) ents);
        assertThat(callInt(GAME, "count", ents, PHOTON)).isZero();
    }

    @Test
    void klingonTorpedoesDrainTheShieldsAndTheLastHitDestroysTheEnterprise() {
        call(GAME, "fireTorpedo", ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(GAME, "shields")).isEqualTo(90);
        set(GAME, "shields", 10);
        call(GAME, "fireTorpedo", ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(GAME, "dead")).isTrue();
    }

    @Test
    void dockingRestoresShieldsTorpedoesAndWarps() {
        set(GAME, "shields", 30);
        set(GAME, "photons", 1);
        set(GAME, "warps", 0);
        spawn(STARBASE, CENTER, CENTER - 20 * FIX, 6);
        call(GAME, "step", (Object) ents);
        assertThat(getInt(GAME, "shields")).isEqualTo(100);
        assertThat(getInt(GAME, "photons")).isEqualTo(5);
        assertThat(getInt(GAME, "warps")).isEqualTo(3);
    }

    @Test
    void theStarbaseFallsAfterSixTorpedoHits() {
        int base = spawn(STARBASE, CENTER + 600 * FIX, CENTER, 6);
        for (int shot = 0; shot < 6; shot++) {
            call(GAME, "fireTorpedo", ents, CENTER + 500 * FIX, CENTER, 90);
            for (int i = 0; i < 40; i++) {
                call(GAME, "step", (Object) ents);
            }
        }
        assertThat(ents[base * STRIDE + TYPE]).isNotEqualTo(STARBASE);
        assertThat((boolean) get(GAME, "baseLost")).isTrue();
    }

    @Test
    void saucersHomeInAndDrainTheShieldsOnContact() {
        spawn(SAUCER, CENTER + 200 * FIX, CENTER + 150 * FIX, 1);
        for (int i = 0; i < 300 && getInt(GAME, "shields") == 100; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(GAME, "shields")).isEqualTo(92);
        assertThat(callInt(GAME, "count", ents, SAUCER)).isZero();
    }

    @Test
    void nomadLaysMinesThatDamageTheEnterprise() {
        int nomad = spawn(NOMAD, CENTER + 500 * FIX, CENTER, 4);
        ents[nomad * STRIDE + COOL] = 1;
        call(GAME, "step", (Object) ents);
        assertThat(callInt(GAME, "count", ents, MINE)).isEqualTo(1);
        spawn(MINE, CENTER, CENTER - 5 * FIX, 1);
        call(GAME, "step", (Object) ents);
        assertThat(getInt(GAME, "shields")).isEqualTo(88);
    }

    @Test
    void everyFourthSectorBringsNomad() {
        for (int sector = 1; sector <= 8; sector++) {
            set(GAME, "sector", sector);
            call(GAME, "startSector", (Object) ents);
            assertThat(callInt(GAME, "count", ents, NOMAD)).as("sector %d", sector).isEqualTo(sector % 4 == 0 ? 1 : 0);
            assertThat(callInt(GAME, "count", ents, STARBASE)).isEqualTo(1);
            assertThat(callInt(GAME, "count", ents, KLINGON)).isPositive();
        }
    }

    @Test
    void linesStayInsideTheirViews() {
        call(GAME, "startSector", (Object) ents);
        set(GAME, "heading", 45);
        spawn(KLINGON, CENTER + 60 * FIX, CENTER - 80 * FIX, 2);
        call(GAME, "render", lines, ents);
        int built = getInt(GAME, "shown");
        assertThat(built).isPositive();
        int base = getInt(GAME, "front") * 200 * 5;
        for (int i = 0; i < built; i++) {
            int x0 = lines[base + i * 5];
            int y0 = lines[base + i * 5 + 1];
            int x1 = lines[base + i * 5 + 2];
            int y1 = lines[base + i * 5 + 3];
            boolean tactical = x0 <= 219 && x1 <= 219 && y0 >= 20 && y1 >= 20 && y0 <= 239 && y1 <= 239 && x0 >= 0
                    && x1 >= 0;
            boolean bridge = x0 >= 223 && x1 >= 223 && x0 <= 318 && x1 <= 318 && y0 >= 22 && y1 >= 22 && y0 <= 97
                    && y1 <= 97;
            assertThat(tactical || bridge).as("line %d (%d,%d)-(%d,%d)", i, x0, y0, x1, y1).isTrue();
        }
    }

    /** An autopilot that hunts the nearest enemy, docks when the shields run low, and uses its weapons. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 6, 8})
    void anAutopilotClearsWholeSectors(int sector) {
        set(GAME, "sector", sector);
        call(GAME, "startSector", (Object) ents);
        for (int frame = 0; frame < 9000; frame++) {
            set(GAME, "frame", frame);
            autopilot(frame);
            call(GAME, "step", (Object) ents);
            call(GAME, "render", lines, ents);
            if ((boolean) get(GAME, "dead")) {
                set(GAME, "dead", false);
                set(GAME, "shields", 100);
            }
            if (callBoolean(GAME, "sectorCleared", (Object) ents)) {
                return;
            }
        }
        throw new AssertionError("sector " + sector + " not cleared");
    }

    private void autopilot(int frame) {
        int shipX = getInt(GAME, "shipX");
        int shipY = getInt(GAME, "shipY");
        int heading = getInt(GAME, "heading");
        int base = callInt(GAME, "find", ents, STARBASE);
        boolean needDock = getInt(GAME, "shields") < 50 && base >= 0 && !(boolean) get(GAME, "docked");
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int slot = 0; slot < 24; slot++) {
            int type = ents[slot * STRIDE + TYPE];
            boolean wanted = needDock ? slot == base : type == KLINGON || type == NOMAD || type == SAUCER;
            if (wanted) {
                int d = distance(slot, shipX, shipY);
                if (d < bestDistance) {
                    best = slot;
                    bestDistance = d;
                }
            }
        }
        if (best < 0) {
            set(GAME, "steering", false);
            return;
        }
        int dx = callInt(GAME, "wrap", ents[best * STRIDE + X] - shipX) / FIX;
        int dy = callInt(GAME, "wrap", ents[best * STRIDE + Y] - shipY) / FIX;
        int bearing = callInt(GAME, "bearing", dx, dy);
        int off = Math.abs(callInt(GAME, "angleBetween", heading, bearing));
        // Steer at it, but hold off at phaser range instead of ramming it.
        set(GAME, "steering", needDock || bestDistance > 200 || off > 20);
        set(GAME, "steerX", 110 + dx / 3);
        set(GAME, "steerY", 130 + dy / 3);
        if (!needDock && off < 12 && bestDistance < 300) {
            call(GAME, "firePhasers", (Object) ents);
            if (bestDistance > 120 && frame % 90 == 0) {
                call(GAME, "firePhoton", (Object) ents);
            }
        }
    }

    private int distance(int slot, int shipX, int shipY) {
        int dx = callInt(GAME, "wrap", ents[slot * STRIDE + X] - shipX) / FIX;
        int dy = callInt(GAME, "wrap", ents[slot * STRIDE + Y] - shipY) / FIX;
        return (int) Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    private void fireAndRecharge() {
        call(GAME, "firePhasers", (Object) ents);
        set(GAME, "phaserCooldown", 0);
    }

    /** Keeps the given ships where they are while a test steps the simulation. */
    private void freeze(int... slots) {
        for (int slot : slots) {
            if (ents[slot * STRIDE + TYPE] != 0) {
                ents[slot * STRIDE + 3] = 0;
                ents[slot * STRIDE + 4] = 0;
                ents[slot * STRIDE + COOL] = 1000;
            }
        }
    }

    private int spawn(int type, int x, int y, int hp) {
        int slot = callInt(GAME, "spawn", ents, type, x, y, hp);
        ents[slot * STRIDE + HEADING] = 0;
        ents[slot * STRIDE + COOL] = 1000;
        return slot;
    }

    private void clearAll() {
        for (int i = 0; i < ents.length; i++) {
            ents[i] = 0;
        }
    }
}
