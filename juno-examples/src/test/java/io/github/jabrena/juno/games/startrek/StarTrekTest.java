package io.github.jabrena.juno.games.startrek;

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
 * Rules of Star Trek: the wrapping sector, phasers, photon torpedoes, Klingon torpedoes, docking, the
 * starbase, saucers, Nomad's mines, the pilot screen, and the CPU pilot that clears whole sectors.
 */
class StarTrekTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
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
        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(42);
        lines = new short[2 * 200 * 5];
        ents = new int[24 * STRIDE];
        Session.score = 0;
        Session.sector = 1;
        Enterprise.shields = 100;
        Enterprise.photons = 5;
        Enterprise.warps = 3;
        DisplayList.shown = 0;
        DisplayList.built = 0;
        Sector.start(ents);
        clearAll();
    }

    @Test
    void theSectorWrapsAround() {
        assertThat(Geometry.wrap(2000 * FIX)).isEqualTo(-48 * FIX);
        assertThat(Geometry.wrap(-2000 * FIX)).isEqualTo(48 * FIX);
        assertThat(Geometry.wrapPosition(-16)).isEqualTo(2048 * FIX - 16);
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
        assertThat(Session.score).isEqualTo(1000);
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
        Combat.firePhasers(ents);
        Combat.firePhasers(ents);
        assertThat(ents[klingon * STRIDE + HP]).isEqualTo(1);
    }

    @Test
    void aPhotonTorpedoBlastDestroysEverythingNearby() {
        int a = spawn(KLINGON, CENTER, CENTER - 200 * FIX, 2);
        int b = spawn(KLINGON, CENTER + 40 * FIX, CENTER - 220 * FIX, 2);
        int c = spawn(SAUCER, CENTER - 30 * FIX, CENTER - 190 * FIX, 1);
        int spared = spawn(KLINGON, CENTER + 300 * FIX, CENTER - 200 * FIX, 2);
        Combat.firePhoton(tft, ents);
        assertThat(Enterprise.photons).isEqualTo(4);
        for (int i = 0; i < 30; i++) {
            freeze(a, b, c, spared);
            StarTrek.step(tft, ents);
        }
        assertThat(ents[a * STRIDE + TYPE]).isNotEqualTo(KLINGON);
        assertThat(ents[b * STRIDE + TYPE]).isNotEqualTo(KLINGON);
        assertThat(ents[c * STRIDE + TYPE]).isNotEqualTo(SAUCER);
        assertThat(ents[spared * STRIDE + TYPE]).isEqualTo(KLINGON);
    }

    @Test
    void photonTorpedoesRunOut() {
        Enterprise.photons = 0;
        Combat.firePhoton(tft, ents);
        assertThat(Entities.count(ents, PHOTON)).isZero();
    }

    @Test
    void klingonTorpedoesDrainTheShieldsAndTheLastHitDestroysTheEnterprise() {
        Combat.fireTorpedo(ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            StarTrek.step(tft, ents);
        }
        assertThat(Enterprise.shields).isEqualTo(90);
        Enterprise.shields = 10;
        Combat.fireTorpedo(ents, CENTER, CENTER - 100 * FIX, 180);
        for (int i = 0; i < 40; i++) {
            StarTrek.step(tft, ents);
        }
        assertThat((boolean) Session.dead).isTrue();
    }

    @Test
    void dockingRestoresShieldsTorpedoesAndWarps() {
        Enterprise.shields = 30;
        Enterprise.photons = 1;
        Enterprise.warps = 0;
        spawn(STARBASE, CENTER, CENTER - 20 * FIX, 6);
        StarTrek.step(tft, ents);
        assertThat(Enterprise.shields).isEqualTo(100);
        assertThat(Enterprise.photons).isEqualTo(5);
        assertThat(Enterprise.warps).isEqualTo(3);
    }

    @Test
    void theStarbaseFallsAfterSixTorpedoHits() {
        int base = spawn(STARBASE, CENTER + 600 * FIX, CENTER, 6);
        for (int shot = 0; shot < 6; shot++) {
            Combat.fireTorpedo(ents, CENTER + 500 * FIX, CENTER, 90);
            for (int i = 0; i < 40; i++) {
                StarTrek.step(tft, ents);
            }
        }
        assertThat(ents[base * STRIDE + TYPE]).isNotEqualTo(STARBASE);
        assertThat((boolean) Session.baseLost).isTrue();
    }

    @Test
    void saucersHomeInAndDrainTheShieldsOnContact() {
        spawn(SAUCER, CENTER + 200 * FIX, CENTER + 150 * FIX, 1);
        for (int i = 0; i < 300 && Enterprise.shields == 100; i++) {
            StarTrek.step(tft, ents);
        }
        assertThat(Enterprise.shields).isEqualTo(92);
        assertThat(Entities.count(ents, SAUCER)).isZero();
    }

    @Test
    void nomadLaysMinesThatDamageTheEnterprise() {
        int nomad = spawn(NOMAD, CENTER + 500 * FIX, CENTER, 4);
        ents[nomad * STRIDE + COOL] = 1;
        StarTrek.step(tft, ents);
        assertThat(Entities.count(ents, MINE)).isEqualTo(1);
        spawn(MINE, CENTER, CENTER - 5 * FIX, 1);
        StarTrek.step(tft, ents);
        assertThat(Enterprise.shields).isEqualTo(88);
    }

    @Test
    void everyFourthSectorBringsNomad() {
        for (int sector = 1; sector <= 8; sector++) {
            Session.sector = sector;
            Sector.start(ents);
            assertThat(Entities.count(ents, NOMAD)).as("sector %d", sector)
                    .isEqualTo(sector % 4 == 0 ? 1 : 0);
            assertThat(Entities.count(ents, STARBASE)).isEqualTo(1);
            assertThat(Entities.count(ents, KLINGON)).isPositive();
        }
    }

    @Test
    void linesStayInsideTheirViews() {
        Sector.start(ents);
        Enterprise.heading = 45;
        spawn(KLINGON, CENTER + 60 * FIX, CENTER - 80 * FIX, 2);
        SceneRenderer.render(tft, lines, ents);
        int built = DisplayList.shown;
        assertThat(built).isPositive();
        int base = DisplayList.front * 200 * 5;
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
        Session.sector = sector;
        Controls.autopilot = true;
        Sector.start(ents);
        boolean cleared = false;
        for (int frame = 0; frame < 9000; frame++) {
            Session.frame = frame;
            AutopilotST.fly(tft, ents);
            StarTrek.step(tft, ents);
            SceneRenderer.render(tft, lines, ents);
            if ((boolean) Session.dead) {
                Session.dead = false;
                Enterprise.shields = 100;
            }
            if (Sector.isCleared(ents)) {
                cleared = true;
                break;
            }
        }
        assertThat(cleared).as("sector %d should be cleared by the CPU pilot", sector).isTrue();
    }

    @Test
    void theCpuPilotDocksWhenItsShieldsRunLow() {
        Enterprise.shields = 40;
        spawn(STARBASE, CENTER + 300 * FIX, CENTER, 6);
        spawn(KLINGON, CENTER, CENTER - 150 * FIX, 2);
        for (int frame = 0; frame < 600 && !(boolean) Enterprise.docked; frame++) {
            AutopilotST.fly(tft, ents);
            StarTrek.step(tft, ents);
        }
        assertThat((boolean) Enterprise.docked).isTrue();
        assertThat(Enterprise.shields).isEqualTo(100);
    }

    @Test
    void theCpuPilotWarpsAwayFromATorpedoWhenItsShieldsAreLow() {
        Enterprise.shields = 20;
        Combat.fireTorpedo(ents, CENTER, CENTER - 60 * FIX, 180);
        AutopilotST.fly(tft, ents);
        assertThat(Enterprise.warps).isEqualTo(2);
    }

    @Test
    void thePilotScreenHasAHumanAndACpuButton() {
        assertThat(Controls.choiceAt(80, 140)).isZero();
        assertThat(Controls.choiceAt(230, 140)).isEqualTo(1);
        assertThat(Controls.choiceAt(160, 140)).as("between the buttons").isEqualTo(-1);
        assertThat(Controls.choiceAt(80, 40)).as("above the buttons").isEqualTo(-1);
    }

    private void fireAndRecharge() {
        Combat.firePhasers(ents);
        Combat.phaserCooldown = 0;
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
        int slot = Entities.spawn(ents, type, x, y, hp);
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
