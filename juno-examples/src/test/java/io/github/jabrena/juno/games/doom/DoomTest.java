package io.github.jabrena.juno.games.doom;

import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rules of the DOOM walk-through on the committed test map: two rooms joined by a doorway, the far
 * one 24 units higher. BSP lookups, collision, occlusion and the autopilot run through the game's
 * own code, the same code the UNO Q runs.
 */
class DoomTest {
    private short[] lines;
    private byte[] clips;
    private short[] stack;
    private short[] ceilings;
    private short[] depths;
    private short[] monsters;
    private short[] shots;
    private byte[] taken;
    private byte[] changes;

    @BeforeEach
    void spawn() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        lines = new short[2 * DisplayList.LIST_SIZE];
        clips = new byte[2 * DisplayList.WIDTH];
        changes = new byte[DisplayList.MAX_LINES];
        stack = new short[64];
        World.loadBuiltIn();
        ceilings = World.ceilings;
        depths = new short[DisplayList.WIDTH];
        monsters = World.monsterStates;
        shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        taken = World.taken;
        Random.seed(7);
        Player.spawn(ceilings);
        Monsters.reset(monsters, shots);
        Items.reset(taken);
        Weapon.reset();
        Autopilot.restart();
        Controls.autopilot = true;
    }

    @Test
    void thePilotBoxesChooseHumanOrCpu() {
        assertThat(Controls.choiceAt(85, 140)).as("HUMAN").isZero();
        assertThat(Controls.choiceAt(235, 140)).as("CPU").isEqualTo(1);
        assertThat(Controls.choiceAt(160, 140)).as("the gap between them").isEqualTo(-1);
        assertThat(Controls.choiceAt(85, 40)).as("above the boxes").isEqualTo(-1);
    }

    @Test
    void tappingThePilotLabelSwitchesBetweenCpuAndHuman() {
        assertThat(Controls.switchPilotAt(300, 219, ceilings)).isTrue();
        assertThat(Controls.autopilot).as("CPU switches to HUMAN").isFalse();

        assertThat(Controls.switchPilotAt(300, 219, ceilings)).isTrue();
        assertThat(Controls.autopilot).as("HUMAN switches back to CPU").isTrue();
    }

    @Test
    void tappingOutsideThePilotLabelKeepsTheCurrentPilot() {
        assertThat(Controls.switchPilotAt(300, 205, ceilings)).as("map row").isFalse();
        assertThat(Controls.switchPilotAt(300, 232, ceilings)).as("FPS row").isFalse();
        assertThat(Controls.switchPilotAt(250, 219, ceilings)).as("armor panel").isFalse();
        assertThat(Controls.autopilot).isTrue();
    }

    @Test
    void eightFortyMillisecondFrameIntervalsReportTwentyFiveFps() {
        FrameStats.reset();
        for (int frame = 0; frame <= 8; frame++) {
            int finished = 1_000 + frame * 40_000;
            FrameStats.record(finished - 3_000, finished - 2_000, finished - 1_000, finished);
        }

        assertThat(FrameStats.fps()).isEqualTo(25);
    }

    @Test
    void theEpisodeAndSkillRowsTakeOnlyTapsInsideThem() {
        assertThat(Menu.rowAt(160, Episodes.TOP, Episodes.TOP, Episodes.HEIGHT, 4)).as("first episode").isZero();
        assertThat(Menu.rowAt(160, Episodes.TOP + Episodes.HEIGHT + 4,
                Episodes.TOP, Episodes.HEIGHT, 4)).as("second episode").isEqualTo(1);
        assertThat(Menu.rowAt(160, Episodes.TOP + Episodes.HEIGHT,
                Episodes.TOP, Episodes.HEIGHT, 4)).as("gap between episodes").isEqualTo(-1);
        assertThat(Menu.rowAt(160, Skills.TOP + 4 * (Skills.HEIGHT + 4),
                Skills.TOP, Skills.HEIGHT, 5)).as("fifth skill").isEqualTo(4);
        assertThat(Menu.rowAt(10, Episodes.TOP, Episodes.TOP, Episodes.HEIGHT, 4)).as("left of menu").isEqualTo(-1);
        assertThat(Menu.rowAt(160, 40, Episodes.TOP, Episodes.HEIGHT, 4)).as("above menu").isEqualTo(-1);
    }

    @Test
    void aBossMapWithoutAnExitEndsOnlyAfterEveryMonsterIsDead() {
        World.fromWad = true;
        World.exitLine = -1;
        for (int i = 0; i < World.monsters; i++) {
            monsters[i * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
        }

        monsters[Monsters.STATE] = Monsters.CHASE;
        assertThat(Campaign.mapWon(monsters)).isFalse();

        monsters[Monsters.STATE] = Monsters.DEAD;
        assertThat(Campaign.mapWon(monsters)).isTrue();
    }

    @Test
    void theEighthWadMapFinishesTheEpisodeInsteadOfLoadingAnotherMap() {
        World.fromWad = true;
        World.map = World.LAST_MAP - 1;
        assertThat(Campaign.episodeFinished()).isFalse();

        World.map = World.LAST_MAP;
        assertThat(Campaign.episodeFinished()).isTrue();

        World.fromWad = false;
        assertThat(Campaign.episodeFinished()).as("the built-in map loops").isFalse();
    }

    @Test
    void intermissionPercentagesAreBoundedAndHandleEmptyTotals() {
        assertThat(Campaign.percent(3, 4)).isEqualTo(75);
        assertThat(Campaign.percent(5, 4)).isEqualTo(100);
        assertThat(Campaign.percent(0, 0)).isZero();
    }

    @Test
    void intermissionCountsOnlyTheItemsActuallyTaken() {
        byte[] pickedUp = {1, 0, 1, 1, 0};
        World.itemKind = new short[] {Items.MEDIKIT, Items.STIMPACK, Items.CLIP, Items.HEALTH_BONUS, Items.GREEN_ARMOR};

        assertThat(Campaign.itemsFound(pickedUp, 4)).as("the clip does not count").isEqualTo(2);
        assertThat(Campaign.itemsCounted(5)).isEqualTo(4);
    }

    @Test
    void enteringASecretSectorCountsItOnlyOnce() {
        World.clearSectorSecrets();
        World.markSecretSector(0);
        World.markSecretSector(1);
        World.resetFoundSecrets();

        World.visitSector(0);
        World.visitSector(0);
        assertThat(World.secretsFound).isEqualTo(1);

        World.visitSector(1);
        assertThat(World.secrets).isEqualTo(2);
        assertThat(World.secretsFound).isEqualTo(2);
    }

    @Test
    void theBspTreeFindsTheSectorUnderAnyPoint() {
        assertThat(Player.sectorAt(100, 100)).isZero();
        assertThat(Player.sectorAt(511, 500)).isZero();
        assertThat(Player.sectorAt(513, 10)).isEqualTo(1);
        assertThat(Player.sectorAt(1000, 256)).isEqualTo(1);
    }

    @Test
    void everySubsectorsSegsLieInsideItsOwnSector() {
        for (int subsector = 0; subsector < LevelNodes.SUBSECTOR_FIRST.length; subsector++) {
            int first = LevelNodes.SUBSECTOR_FIRST[subsector];
            for (int seg = first; seg < first + LevelNodes.SUBSECTOR_COUNT[subsector]; seg++) {
                int line = LevelSegs.LINE[seg];
                int sector = LevelSegs.SIDE[seg] == 0 ? LevelLines.FRONT[line] : LevelLines.BACK[line];
                assertThat(sector).as("seg %d of subsector %d", seg, subsector)
                        .isEqualTo(LevelNodes.SUBSECTOR_SECTOR[subsector]);
            }
        }
    }

    @Test
    void wallsBlockButTheDoorwayAndItsStepLetTheMarineThrough() {
        Player.x = 480;
        Player.y = 100;
        Player.angle = 0;
        assertThat(Player.walk(60, ceilings)).as("solid wall at x=512").isFalse();
        assertThat(Player.x).isEqualTo(480f);

        Player.y = 256;
        assertThat(Player.walk(60, ceilings)).as("doorway, a 24-unit step up").isTrue();
        assertThat(Player.sector).isEqualTo(1);
        for (int i = 0; i < 10; i++) {
            Player.settle();
        }
        assertThat(Player.eye).isEqualTo(24 + Player.EYE_HEIGHT);
    }

    @Test
    void twoWallsSharingACornerLeaveNoSeamForTheCameraToCross() {
        Player.x = 10;
        Player.y = 10;
        Player.angle = (float) (-3 * Math.PI / 4);

        assertThat(Player.walk(30, ceilings)).as("the shared endpoint at 0,0 is still a solid corner").isFalse();
        assertThat(Player.x).isEqualTo(10f);
        assertThat(Player.y).isEqualTo(10f);
    }

    @Test
    void twoWallsSharingACornerAlsoBlockSightThroughTheirSeam() {
        assertThat(Player.canSee(10, 10, Player.EYE_HEIGHT, -10, -10, Player.EYE_HEIGHT, ceilings))
                .as("the sight line touches the solid corner at 0,0")
                .isFalse();
    }

    @Test
    void aStepHigherThan24UnitsOrTooLittleHeadroomBlocks() {
        Player.x = 480;
        Player.y = 256;
        Player.angle = 0;
        ceilings[1] = 70;
        assertThat(Player.walk(60, ceilings)).as("46 units of headroom").isFalse();
    }

    @Test
    void aSurroundingRoomClosesEveryColumnAndItsEdgesAreDrawn() {
        Player.x = 100;
        Player.y = 256;
        Player.angle = (float) Math.PI;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        for (int x = 0; x < DisplayList.WIDTH; x++) {
            assertThat((clips[x] & 0xFF) + 1).as("column %d closed", x)
                    .isGreaterThanOrEqualTo(clips[DisplayList.WIDTH + x] & 0xFF);
        }
        assertThat(colors()).contains(Renderer.WALL).doesNotContain(Renderer.STEP);
    }

    @Test
    void lookingThroughTheDoorwayShowsTheStepAndTheFarRoom() {
        Player.x = 300;
        Player.y = 256;
        Player.angle = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        assertThat(colors()).contains(Renderer.STEP, Renderer.LINTEL);
        int middle = DisplayList.WIDTH / 2;
        assertThat((clips[middle] & 0xFF) + 1)
                .as("the far room's wall finally closes the middle column")
                .isGreaterThanOrEqualTo(clips[DisplayList.WIDTH + middle] & 0xFF);
    }

    @Test
    void aVerticalLineIsOneFillAndASteepOneOnePerColumn() {
        DisplayList.windows = 0;
        DisplayList.pixels = 0;
        DisplayList.drawLine(10, 10, 10, 100, Renderer.WALL);
        assertThat(DisplayList.windows).as("a vertical line is a single address window").isEqualTo(1);
        assertThat(DisplayList.pixels).isEqualTo(91);
        DisplayList.windows = 0;
        DisplayList.pixels = 0;
        DisplayList.drawLine(20, 100, 30, 10, Renderer.WALL);
        assertThat(DisplayList.windows).as("one run per column it spans").isEqualTo(11);
        assertThat(DisplayList.pixels).as("one pixel per row").isEqualTo(91);
    }

    @Test
    void theGunKeepsItsSlotsWhileTheWallsChange() {
        Player.x = 300;
        Player.y = 256;
        Player.angle = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        short[] gun = gunLines();
        int walls = getInt(DisplayList.class, "shown");
        Player.angle = (float) Math.PI;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        assertThat(getInt(DisplayList.class, "shown")).as("the view changed").isNotEqualTo(walls);
        assertThat(gunLines()).isEqualTo(gun);
    }

    @Test
    void anUnchangedFrameSendsNothingToTheScreen() {
        Player.x = 300;
        Player.y = 256;
        Player.angle = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        DisplayList.windows = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        assertThat(DisplayList.windows).isZero();
    }

    @Test
    void afterAWalkTheScreenShowsExactlyWhatAFreshDrawOfTheLastFrameWould() {
        DisplayList.clearView();
        for (int frame = 0; frame < 240; frame++) {
            Weapon.tick();
            Autopilot.step(ceilings, monsters, taken);
            Player.operateDoors(ceilings);
            Player.settle();
            Monsters.think(monsters, shots, ceilings, frame);
            Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        }
        boolean[] walked = litView();
        DisplayList.clearView();
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        boolean[] fresh = litView();
        int wrong = 0;
        for (int i = 0; i < fresh.length; i++) {
            wrong = wrong + (fresh[i] != walked[i] ? 1 : 0);
        }
        assertThat(wrong).as("pixels left lit or erased by the frame-to-frame diff").isZero();
    }

    @Test
    void everyColorTheViewDrawsRepeatsOneByteSoItCostsNoMoreThanBlack() {
        Set<Integer> drawn = new HashSet<>();
        Player.hurt = 6;
        for (int frame = 0; frame < 240; frame++) {
            Weapon.tick();
            Autopilot.step(ceilings, monsters, taken);
            Player.operateDoors(ceilings);
            Player.settle();
            Monsters.think(monsters, shots, ceilings, frame);
            Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
            drawn.addAll(colors());
        }
        assertThat(drawn).contains(Renderer.WALL, Sprites.FLASH_COLOR);
        for (int color : drawn) {
            assertThat(color >> 8).as("color 0x%04X", color).isEqualTo(color & 0xFF);
        }
    }

    @Test
    void walkingIntoAWallAtAnAngleSlidesAlongItLikeDoom() {
        Player.x = 10;
        Player.y = 256;
        Player.angle = (float) (3 * Math.PI / 4);

        assertThat(Player.walk(20, ceilings)).as("the move is not simply refused").isTrue();

        assertThat(Player.x).as("the wall at x = 0 stops the westward part").isEqualTo(10f);
        assertThat(Player.y).as("the northward part slides along it").isGreaterThan(256f);
    }

    @Test
    void walkingStraightIntoAWallStopsTheBodyRadiusShortOfIt() {
        Player.x = 40;
        Player.y = 256;
        Player.angle = (float) Math.PI;

        for (int step = 0; step < 10; step++) {
            Player.walk(8, ceilings);
        }

        assertThat(Player.x).as("the camera never presses into the wall at x = 0").isGreaterThanOrEqualTo(Player.RADIUS);
    }

    @Test
    void theAutopilotWalksTheWholeRouteAndLoops() {
        int visited = 0;
        int previous = Autopilot.target;
        short[] none = new short[Level.MONSTERS * Monsters.STRIDE];
        for (int i = 0; i < Level.MONSTERS; i++) {
            none[i * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
        }
        for (int i = 0; i < Level.ITEMS; i++) {
            taken[i] = 1;
        }
        for (int frame = 0; frame < 3000 && visited < Level.ROUTE_X.length + 1; frame++) {
            Autopilot.step(ceilings, none, taken);
            Player.settle();
            if (Autopilot.target != previous) {
                visited = visited + 1;
                previous = Autopilot.target;
            }
        }
        assertThat(visited).as("waypoints reached, wrapping back to the loop start")
                .isGreaterThan(Level.ROUTE_X.length);
    }

    @Test
    void aPistolShotDownsTheZombiemanInFrontOfTheMarine() {
        int zombie = 0;
        Player.x = 600;
        Player.y = 320;
        Player.angle = 0;
        Player.sector = Player.sectorAt(Player.x, Player.y);
        Player.eye = Level.SECTOR_FLOOR[Player.sector] + Player.EYE_HEIGHT;
        assertThat(Weapon.aimed(monsters, ceilings)).as("the zombieman at (860, 320) is in the sights").isEqualTo(zombie);
        for (int shot = 0; shot < 10 && monsters[zombie + Monsters.STATE] != Monsters.DEAD; shot++) {
            Weapon.reset();
            Weapon.fire(monsters, ceilings);
        }
        assertThat(monsters[zombie + Monsters.STATE]).isEqualTo((short) Monsters.DEAD);
        assertThat(Monsters.kills).isEqualTo(1);
    }

    @Test
    void wallsBlockSightAndShots() {
        Player.x = 100;
        Player.y = 450;
        Player.angle = (float) Math.atan2(320 - 450, 860 - 100);
        assertThat(Player.canSee(100, 450, 41, 860, 320, 64, ceilings)).as("through the wall at x=512").isFalse();
        assertThat(Player.canSee(400, 256, 41, 860, 320, 64, ceilings)).as("through the doorway").isTrue();
        assertThat(Weapon.aimed(monsters, ceilings)).isEqualTo(-1);
    }

    @Test
    void monstersWakeChaseAndHurtTheMarine() {
        Player.x = 600;
        Player.y = 256;
        Player.sector = Player.sectorAt(Player.x, Player.y);
        Player.eye = Level.SECTOR_FLOOR[Player.sector] + Player.EYE_HEIGHT;
        for (int frame = 0; frame < 400 && Player.health == Player.FULL_HEALTH; frame++) {
            Monsters.think(monsters, shots, ceilings, frame);
        }
        assertThat(monsters[Monsters.STATE]).as("the zombieman saw the marine").isNotEqualTo((short) Monsters.IDLE);
        assertThat(Player.health).as("and shot or burned them").isLessThan(Player.FULL_HEALTH);
    }

    @Test
    void aWallHidesTheMonsterBehindIt() {
        Player.x = 100;
        Player.y = 450;
        Player.angle = (float) Math.atan2(320 - 450, 860 - 100);
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        assertThat(colors()).as("the wall at x=512 stands between them").doesNotContain(Sprites.ZOMBIEMAN_COLOR);

        Player.x = 600;
        Player.y = 320;
        Player.angle = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken, changes);
        assertThat(colors()).as("in the same room").contains(Sprites.ZOMBIEMAN_COLOR);
    }

    @Test
    void theCpuClearsTheMapWhileWalkingItsRoute() {
        for (int frame = 0; frame < 4000 && Monsters.kills < Level.MONSTERS && Player.health > 0; frame++) {
            Weapon.tick();
            Autopilot.step(ceilings, monsters, taken);
            Player.settle();
            Monsters.think(monsters, shots, ceilings, frame);
        }
        assertThat(Monsters.kills).isEqualTo(Level.MONSTERS);
        assertThat(Player.health).isPositive();
    }

    @Test
    void armorAbsorbsPartOfEachHitAndPickupsHeal() {
        Player.armor = 100;
        Player.armorClass = 1;
        Player.damage(30);
        assertThat(Player.health).as("15 after halving, 5 of it absorbed").isEqualTo(90);
        assertThat(Player.armor).isEqualTo(95);

        Player.x = 800;
        Player.y = 200;
        Items.pickUp(taken);
        assertThat(Player.health).as("the medikit tops up to 100%").isEqualTo(100);
        assertThat(taken[0]).isEqualTo((byte) 1);

        Player.x = 150;
        Player.y = 150;
        Items.pickUp(taken);
        assertThat(taken[1]).as("green armor below 100 points is taken").isEqualTo((byte) 1);
        assertThat(Player.armor).isEqualTo(100);
    }

    @Test
    void theCpuStepsOffTheRouteForTheMedikitItNeeds() {
        Player.x = 650;
        Player.y = 256;
        Player.health = 40;
        short[] none = new short[Level.MONSTERS * Monsters.STRIDE];
        for (int i = 0; i < Level.MONSTERS; i++) {
            none[i * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
        }
        for (int frame = 0; frame < 200 && taken[0] == 0; frame++) {
            Autopilot.step(ceilings, none, taken);
            Items.pickUp(taken);
        }
        assertThat(Player.health).isEqualTo(65);
    }

    @Test
    void theCpuFightsLikeAPersonReactingLateAndMissingSometimes() {
        int shots = 0;
        int hits = 0;
        int kills = 0;
        for (int seed = 1; seed <= 6; seed++) {
            Random.seed(seed);
            Player.spawn(ceilings);
            Monsters.reset(monsters, shots());
            Weapon.reset();
            Autopilot.restart();
            Player.x = 600;
            Player.y = 256;
            Player.angle = (float) Math.PI / 2;
            Player.sector = Player.sectorAt(Player.x, Player.y);
            Player.eye = Level.SECTOR_FLOOR[Player.sector] + Player.EYE_HEIGHT;
            for (int other = 1; other < Level.MONSTERS; other++) {
                monsters[other * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
            }
            monsters[Monsters.STATE] = Monsters.CHASE;
            for (int frame = 0; frame < 600 && monsters[Monsters.STATE] != Monsters.DEAD; frame++) {
                Weapon.tick();
                int health = monsters[Monsters.HEALTH];
                Autopilot.step(ceilings, monsters, taken);
                if (Weapon.flash == 3) {
                    assertThat(frame).as("no shot before the marine has reacted").isGreaterThanOrEqualTo(5);
                    shots = shots + 1;
                    if (monsters[Monsters.HEALTH] < health) {
                        hits = hits + 1;
                    }
                }
            }
            if (monsters[Monsters.STATE] == Monsters.DEAD) {
                kills = kills + 1;
            }
        }
        assertThat(kills).as("it still wins its fights").isEqualTo(6);
        assertThat(hits).as("but not every shot lands").isLessThan(shots);
    }

    private short[] shots() {
        return shots;
    }

    /** Which pixels of the 3D view are lit on the emulated screen. */
    private static boolean[] litView() {
        boolean[] lit = new boolean[DisplayList.WIDTH * DisplayList.VIEW_BOTTOM];
        for (int y = DisplayList.VIEW_TOP; y < DisplayList.VIEW_BOTTOM; y++) {
            for (int x = 0; x < DisplayList.WIDTH; x++) {
                lit[y * DisplayList.WIDTH + x] = Gpio.FRAMEBUFFER[y * Gpio.STRIDE + x] != DisplayList.BACKGROUND;
            }
        }
        return lit;
    }

    /** The shown frame's first ten slots: the crosshair and the pistol, pinned at the head of the list. */
    private short[] gunLines() {
        int base = getInt(DisplayList.class, "front") * DisplayList.LIST_SIZE;
        short[] gun = new short[10 * 5];
        System.arraycopy(lines, base, gun, 0, gun.length);
        return gun;
    }

    /** Colors of the line fragments the last render built: after present() that half is the front one. */
    private Set<Integer> colors() {
        Set<Integer> colors = new HashSet<>();
        int base = getInt(DisplayList.class, "front") * DisplayList.LIST_SIZE;
        for (int i = 0; i < getInt(DisplayList.class, "shown"); i++) {
            colors.add(lines[base + i * 5 + 4] & 0xFFFF);
        }
        return colors;
    }
}
