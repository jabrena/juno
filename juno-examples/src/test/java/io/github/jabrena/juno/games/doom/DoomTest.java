package io.github.jabrena.juno.games.doom;

import static io.github.jabrena.juno.api.tft.Internals.getInt;
import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void spawn() {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        lines = new short[2 * DisplayList.LIST_SIZE];
        clips = new byte[2 * DisplayList.WIDTH];
        stack = new short[64];
        ceilings = new short[Level.SECTOR_CEILING.length];
        Player.spawn(ceilings);
        Autopilot.restart();
        Controls.autopilot = true;
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
        Renderer.render(lines, clips, stack, ceilings);
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
        Renderer.render(lines, clips, stack, ceilings);
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
        for (int frame = 0; frame < 3000 && visited < Level.ROUTE_X.length + 1; frame++) {
            Autopilot.step(ceilings);
            Player.settle();
            if (Autopilot.target != previous) {
                visited = visited + 1;
                previous = Autopilot.target;
            }
        }
        assertThat(visited).as("waypoints reached, wrapping back to the loop start")
                .isGreaterThan(Level.ROUTE_X.length);
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
