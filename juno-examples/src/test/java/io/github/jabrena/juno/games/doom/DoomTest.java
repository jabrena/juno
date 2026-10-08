package io.github.jabrena.juno.games.doom;

import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.jabrena.juno.api.Random;
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

    @BeforeEach
    void spawn() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        lines = new short[2 * DisplayList.LIST_SIZE];
        clips = new byte[2 * DisplayList.WIDTH];
        stack = new short[64];
        ceilings = new short[Level.SECTOR_CEILING.length];
        depths = new short[DisplayList.WIDTH];
        monsters = new short[Level.MONSTERS * Monsters.STRIDE];
        shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        taken = new byte[Level.ITEMS];
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
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken);
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
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken);
        assertThat(colors()).contains(Renderer.STEP, Renderer.LINTEL);
        int middle = DisplayList.WIDTH / 2;
        assertThat((clips[middle] & 0xFF) + 1)
                .as("the far room's wall finally closes the middle column")
                .isGreaterThanOrEqualTo(clips[DisplayList.WIDTH + middle] & 0xFF);
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
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken);
        assertThat(colors()).as("the wall at x=512 stands between them").doesNotContain(Sprites.ZOMBIEMAN_COLOR);

        Player.x = 600;
        Player.y = 320;
        Player.angle = 0;
        Renderer.render(lines, clips, depths, stack, ceilings, monsters, shots, taken);
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
