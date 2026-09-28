package io.github.jabrena.juno.games.startrek;

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
 * Rules of Star Trek: the wrapping sector, phasers, photon torpedoes, Klingon torpedoes, docking, the
 * starbase, saucers, Nomad's mines, the pilot screen, and the CPU pilot that clears whole sectors.
 */
class StarTrekTest {
    private static final Class<?> GAME = StarTrek.class;
    private static final int FIX = 16;
    private static final int STRIDE = 10;
    private static final int TYPE = 0;
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
        set(Session.class, "score", 0);
        set(Session.class, "sector", 1);
        set(Enterprise.class, "shields", 100);
        set(Enterprise.class, "photons", 5);
        set(Enterprise.class, "warps", 3);
        set(DisplayList.class, "shown", 0);
        set(DisplayList.class, "built", 0);
        call(Sector.class, "start", (Object) ents);
        clearAll();
    }

    @Test
    void theSectorWrapsAround() {
        assertThat(callInt(Geometry.class, "wrap", 2000 * FIX)).isEqualTo(-48 * FIX);
        assertThat(callInt(Geometry.class, "wrap", -2000 * FIX)).isEqualTo(48 * FIX);
        assertThat(callInt(Geometry.class, "wrapPosition", -16)).isEqualTo(2048 * FIX - 16);
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
        assertThat(getInt(Session.class, "score")).isEqualTo(1000);
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
        call(Combat.class, "firePhasers", (Object) ents);
        call(Combat.class, "firePhasers", (Object) ents);
        assertThat(ents[klingon * STRIDE + HP]).isEqualTo(1);
    }

    @Test
    void aPhotonTorpedoBlastDestroysEverythingNearby() {
        int a = spawn(KLINGON, CENTER, CENTER - 200 * FIX, 2);
        int b = spawn(KLINGON, CENTER + 40 * FIX, CENTER - 220 * FIX, 2);
        int c = spawn(SAUCER, CENTER - 30 * FIX, CENTER - 190 * FIX, 1);
        int spared = spawn(KLINGON, CENTER + 300 * FIX, CENTER - 200 * FIX, 2);
        call(Combat.class, "firePhoton", (Object) ents);
        assertThat(getInt(Enterprise.class, "photons")).isEqualTo(4);
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
        set(Enterprise.class, "photons", 0);
        call(Combat.class, "firePhoton", (Object) ents);
        assertThat(callInt(Entities.class, "count", ents, PHOTON)).isZero();
    }

    @Test
    void klingonTorpedoesDrainTheShieldsAndTheLastHitDestroysTheEnterprise() {
        call(Combat.class, "fireTorpedo", ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Enterprise.class, "shields")).isEqualTo(90);
        set(Enterprise.class, "shields", 10);
        call(Combat.class, "fireTorpedo", ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(Session.class, "dead")).isTrue();
    }

    @Test
    void dockingRestoresShieldsTorpedoesAndWarps() {
        set(Enterprise.class, "shields", 30);
        set(Enterprise.class, "photons", 1);
        set(Enterprise.class, "warps", 0);
        spawn(STARBASE, CENTER, CENTER - 20 * FIX, 6);
        call(GAME, "step", (Object) ents);
        assertThat(getInt(Enterprise.class, "shields")).isEqualTo(100);
        assertThat(getInt(Enterprise.class, "photons")).isEqualTo(5);
        assertThat(getInt(Enterprise.class, "warps")).isEqualTo(3);
    }

    @Test
    void theStarbaseFallsAfterSixTorpedoHits() {
        int base = spawn(STARBASE, CENTER + 600 * FIX, CENTER, 6);
        for (int shot = 0; shot < 6; shot++) {
            call(Combat.class, "fireTorpedo", ents, CENTER + 500 * FIX, CENTER, 90);
            for (int i = 0; i < 40; i++) {
                call(GAME, "step", (Object) ents);
            }
        }
        assertThat(ents[base * STRIDE + TYPE]).isNotEqualTo(STARBASE);
        assertThat((boolean) get(Session.class, "baseLost")).isTrue();
    }

    @Test
    void saucersHomeInAndDrainTheShieldsOnContact() {
        spawn(SAUCER, CENTER + 200 * FIX, CENTER + 150 * FIX, 1);
        for (int i = 0; i < 300 && getInt(Enterprise.class, "shields") == 100; i++) {
            call(GAME, "step", (Object) ents);
        }
        assertThat(getInt(Enterprise.class, "shields")).isEqualTo(92);
        assertThat(callInt(Entities.class, "count", ents, SAUCER)).isZero();
    }

    @Test
    void nomadLaysMinesThatDamageTheEnterprise() {
        int nomad = spawn(NOMAD, CENTER + 500 * FIX, CENTER, 4);
        ents[nomad * STRIDE + COOL] = 1;
        call(GAME, "step", (Object) ents);
        assertThat(callInt(Entities.class, "count", ents, MINE)).isEqualTo(1);
        spawn(MINE, CENTER, CENTER - 5 * FIX, 1);
        call(GAME, "step", (Object) ents);
        assertThat(getInt(Enterprise.class, "shields")).isEqualTo(88);
    }

    @Test
    void everyFourthSectorBringsNomad() {
        for (int sector = 1; sector <= 8; sector++) {
            set(Session.class, "sector", sector);
            call(Sector.class, "start", (Object) ents);
            assertThat(callInt(Entities.class, "count", ents, NOMAD)).as("sector %d", sector)
                    .isEqualTo(sector % 4 == 0 ? 1 : 0);
            assertThat(callInt(Entities.class, "count", ents, STARBASE)).isEqualTo(1);
            assertThat(callInt(Entities.class, "count", ents, KLINGON)).isPositive();
        }
    }

    @Test
    void linesStayInsideTheirViews() {
        call(Sector.class, "start", (Object) ents);
        set(Enterprise.class, "heading", 45);
        spawn(KLINGON, CENTER + 60 * FIX, CENTER - 80 * FIX, 2);
        call(SceneRenderer.class, "render", lines, ents);
        int built = getInt(DisplayList.class, "shown");
        assertThat(built).isPositive();
        int base = getInt(DisplayList.class, "front") * 200 * 5;
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

    /** The CPU pilot hunts the nearest enemy, docks when the shields run low, and uses its weapons. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 6, 8})
    void theCpuPilotClearsWholeSectors(int sector) {
        set(Session.class, "sector", sector);
        set(Controls.class, "autopilot", true);
        call(Sector.class, "start", (Object) ents);
        for (int frame = 0; frame < 9000; frame++) {
            set(Session.class, "frame", frame);
            call(AutopilotST.class, "fly", (Object) ents);
            call(GAME, "step", (Object) ents);
            call(SceneRenderer.class, "render", lines, ents);
            if ((boolean) get(Session.class, "dead")) {
                set(Session.class, "dead", false);
                set(Enterprise.class, "shields", 100);
            }
            if (callBoolean(Sector.class, "isCleared", (Object) ents)) {
                return;
            }
        }
        throw new AssertionError("sector " + sector + " not cleared");
    }

    @Test
    void theCpuPilotDocksWhenItsShieldsRunLow() {
        set(Enterprise.class, "shields", 40);
        spawn(STARBASE, CENTER + 300 * FIX, CENTER, 6);
        spawn(KLINGON, CENTER, CENTER - 150 * FIX, 2);
        for (int frame = 0; frame < 600 && !(boolean) get(Enterprise.class, "docked"); frame++) {
            call(AutopilotST.class, "fly", (Object) ents);
            call(GAME, "step", (Object) ents);
        }
        assertThat((boolean) get(Enterprise.class, "docked")).isTrue();
        assertThat(getInt(Enterprise.class, "shields")).isEqualTo(100);
    }

    @Test
    void theCpuPilotWarpsAwayFromATorpedoWhenItsShieldsAreLow() {
        set(Enterprise.class, "shields", 20);
        call(Combat.class, "fireTorpedo", ents, CENTER, CENTER - 60 * FIX, 180);
        call(AutopilotST.class, "fly", (Object) ents);
        assertThat(getInt(Enterprise.class, "warps")).isEqualTo(2);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(callInt(Controls.class, "choiceAt", 80, 140)).isZero();
        assertThat(callInt(Controls.class, "choiceAt", 230, 140)).isEqualTo(1);
        assertThat(callInt(Controls.class, "choiceAt", 160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(callInt(Controls.class, "choiceAt", 80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void fireAndRecharge() {
        call(Combat.class, "firePhasers", (Object) ents);
        set(Combat.class, "phaserCooldown", 0);
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
        int slot = callInt(Entities.class, "spawn", ents, type, x, y, hp);
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
