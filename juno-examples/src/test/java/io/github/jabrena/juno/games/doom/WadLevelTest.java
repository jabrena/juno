package io.github.jabrena.juno.games.doom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.SdCard;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Loading the map at startup: E1M1 from a WAD read with {@link RandomAccessFile}, the same code the UNO Q runs
 * against the SD card, and the built-in map as the fallback. The E1M1 checks need a WAD you own, so they run only
 * when one is found: {@code -Djuno.doom.wad=/path/DOOM1.WAD}, or a local copy beside the game's sources.
 */
class WadLevelTest {
    private static final Path LOCAL_WAD = Path.of("src/main/java/io/github/jabrena/juno/games/doom/DOOM1.WAD");

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void removeTheCard() {
        SdCard.present = false;
    }

    @Test
    void withoutACardTheBuiltInMapIsPlayed() {
        SdCard.present = false;

        World.load();

        assertThat(World.fromWad).isFalse();
        assertThat(World.vertices).isEqualTo(Level.VERTICES).isEqualTo(LevelVertices.X.length);
        assertThat(World.lines).isEqualTo(Level.LINES);
        assertThat(World.nodes).isEqualTo(Level.NODES);
        assertThat(World.monsters).isEqualTo(Level.MONSTERS);
        assertThat(World.routeLength).isEqualTo(Level.ROUTE_POINTS);
        assertThat(World.startX).isEqualTo(Level.START_X);
        assertThat(World.sectorFloor[1]).isEqualTo(Level.SECTOR_FLOOR[1]);
    }

    @Test
    void aMissingWadIsNoMapRatherThanAnError() {
        assumeTrue(!Files.exists(Path.of("DOOM1.WAD")), "a DOOM1.WAD sits in the working directory");

        assertThat(WadLevel.load()).isFalse();
    }

    @Test
    void aTruncatedWadFailsWithTheJdksEofException() throws IOException {
        Path wad = temporaryDirectory.resolve("SHORT.WAD");
        Files.write(wad, new byte[] {'I', 'W', 'A', 'D', 1, 0});
        World.allocateForWad();

        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThatThrownBy(() -> WadLevel.read(file)).isInstanceOf(EOFException.class);
        }
    }

    @Test
    void aFileThatIsNotAWadIsNoMap() throws IOException {
        Path notAWad = temporaryDirectory.resolve("NOTES.TXT");
        Files.writeString(notAWad, "just some text, long enough");
        World.allocateForWad();

        try (RandomAccessFile file = new RandomAccessFile(notAWad.toFile(), "r")) {
            assertThat(WadLevel.read(file)).isFalse();
        }
    }

    @Test
    void readsE1M1FromTheWad() throws IOException {
        Path wad = wad();
        World.allocateForWad();

        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.read(file)).isTrue();
        }

        assertThat(World.vertices).isEqualTo(467);
        assertThat(World.lines).isEqualTo(475);
        assertThat(World.segs).isEqualTo(732);
        assertThat(World.subsectors).isEqualTo(237);
        assertThat(World.nodes).isEqualTo(236);
        assertThat(World.sectors).isEqualTo(85);
        assertThat(World.doors).isEqualTo(4);
        assertThat(World.monsters).isEqualTo(29);
        assertThat(World.items).isEqualTo(44);
        assertThat(World.exitX).isNotEqualTo(-30000);
        assertThat(World.routeLength).as("only the start until the route is planned").isOne();
        assertThat(World.startX).isEqualTo(1056);
        assertThat(World.startY).isEqualTo(-3616);
    }

    @Test
    void e1m1sBspTreeAgreesWithItsSegsAndHoldsTheStart() throws IOException {
        Path wad = wad();
        World.allocateForWad();
        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.read(file)).isTrue();
        }

        for (int subsector = 0; subsector < World.subsectors; subsector++) {
            int first = World.subsectorFirst[subsector];
            for (int seg = first; seg < first + World.subsectorCount[subsector]; seg++) {
                int line = World.segLine[seg];
                int sector = World.segSide[seg] == 0 ? World.lineFront[line] : World.lineBack[line];
                assertThat(sector).as("seg %d of subsector %d", seg, subsector)
                        .isEqualTo(World.subsectorSector[subsector]);
            }
        }
        int start = Player.sectorAt(World.startX, World.startY);
        assertThat(start).isBetween(0, World.sectors - 1);
        for (int door = 0; door < World.doors; door++) {
            assertThat(World.doorTop[door]).as("door %d opens above its floor", door)
                    .isGreaterThan(World.sectorFloor[World.doorSector[door]]);
        }
    }

    @Test
    void theBuiltInMapHasNoExitToPlanFor() {
        World.loadBuiltIn();

        assertThat(RoutePlanner.plan()).isFalse();
        assertThat(World.routeLength).as("its own patrol route stays").isEqualTo(Level.ROUTE_POINTS);
    }

    @Test
    void plansAWalkFromTheStartToTheExitSwitchOfE1M1() throws IOException {
        loadE1M1();

        assertThat(RoutePlanner.plan()).isTrue();

        int last = World.routeLength - 1;
        assertThat(World.routeX[0]).isEqualTo((short) World.startX);
        assertThat(World.routeY[0]).isEqualTo((short) World.startY);
        float dx = World.routeX[last] - World.exitX;
        float dy = World.routeY[last] - World.exitY;
        assertThat(dx * dx + dy * dy).as("the route ends within reach of the exit switch").isLessThan(56f * 56f);
        short[] open = openCeilings();
        for (int i = 0; i < last; i++) {
            assertThat(Player.blocked(World.routeX[i], World.routeY[i], World.routeX[i + 1], World.routeY[i + 1], open))
                    .as("leg %d of the planned route", i).isFalse();
        }
    }

    /**
     * The CPU marine, with the monsters out of the way, walks E1M1 from its start to the exit switch, whichever way
     * its random sway takes it.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 7, 42})
    void theCpuWalksThePlannedRouteToTheExitOfE1M1(int seed) throws IOException {
        loadE1M1();
        assertThat(RoutePlanner.plan()).isTrue();
        short[] none = new short[World.MAX_MONSTERS * Monsters.STRIDE];
        for (int i = 0; i < World.MAX_MONSTERS; i++) {
            none[i * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
        }
        for (int i = 0; i < World.items; i++) {
            World.taken[i] = 1;
        }
        Random.seed(seed);
        Player.spawn(World.ceilings);
        Autopilot.restart();

        int frame = 0;
        while (frame < 20000 && !Player.atExit()) {
            Autopilot.step(World.ceilings, none, World.taken);
            Player.operateDoors(World.ceilings);
            Player.settle();
            frame = frame + 1;
        }

        assertThat(Player.atExit()).as("at the exit switch after %d frames, standing at %s,%s", frame, Player.x,
                Player.y).isTrue();
    }

    private void loadE1M1() throws IOException {
        Path wad = wad();
        World.allocateForWad();
        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.read(file)).isTrue();
        }
    }

    private static short[] openCeilings() {
        short[] open = new short[World.MAX_SECTORS];
        for (int sector = 0; sector < World.sectors; sector++) {
            open[sector] = World.sectorCeiling[sector];
        }
        for (int door = 0; door < World.doors; door++) {
            open[World.doorSector[door]] = World.doorTop[door];
        }
        return open;
    }

    /** The runtime loader and the offline generator read the same WAD into the same tables, value for value. */
    @Test
    void matchesTheTablesLevelGeneratorWrites() throws IOException {
        Path wad = wad();
        Path generated = temporaryDirectory.resolve("Level.java");
        LevelGenerator.generate(wad, "E1M1", generated);
        World.allocateForWad();
        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.read(file)).isTrue();
        }
        Map<String, int[]> tables = tables(Files.readString(generated));

        assertTable(tables, "Level.SECTOR_FLOOR", World.sectorFloor, World.sectors);
        assertTable(tables, "Level.SECTOR_CEILING", World.sectorCeiling, World.sectors);
        assertTable(tables, "Level.DOOR_SECTOR", World.doorSector, World.doors);
        assertTable(tables, "Level.DOOR_TOP", World.doorTop, World.doors);
        assertTable(tables, "Level.DOOR_X", World.doorX, World.doors);
        assertTable(tables, "Level.DOOR_Y", World.doorY, World.doors);
        assertTable(tables, "Level.MONSTER_X", World.monsterX, World.monsters);
        assertTable(tables, "Level.MONSTER_Y", World.monsterY, World.monsters);
        assertTable(tables, "Level.MONSTER_KIND", World.monsterKind, World.monsters);
        assertTable(tables, "Level.ITEM_X", World.itemX, World.items);
        assertTable(tables, "Level.ITEM_Y", World.itemY, World.items);
        assertTable(tables, "Level.ITEM_KIND", World.itemKind, World.items);
        assertTable(tables, "LevelVertices.X", World.vertexX, World.vertices);
        assertTable(tables, "LevelVertices.Y", World.vertexY, World.vertices);
        assertTable(tables, "LevelLines.V1", World.lineV1, World.lines);
        assertTable(tables, "LevelLines.V2", World.lineV2, World.lines);
        assertTable(tables, "LevelLines.FRONT", World.lineFront, World.lines);
        assertTable(tables, "LevelLines.BACK", World.lineBack, World.lines);
        assertTable(tables, "LevelSegs.V1", World.segV1, World.segs);
        assertTable(tables, "LevelSegs.V2", World.segV2, World.segs);
        assertTable(tables, "LevelSegs.LINE", World.segLine, World.segs);
        assertTable(tables, "LevelSegs.SIDE", World.segSide, World.segs);
        assertTable(tables, "LevelNodes.X", World.nodeX, World.nodes);
        assertTable(tables, "LevelNodes.Y", World.nodeY, World.nodes);
        assertTable(tables, "LevelNodes.DX", World.nodeDx, World.nodes);
        assertTable(tables, "LevelNodes.DY", World.nodeDy, World.nodes);
        assertTable(tables, "LevelNodes.RIGHT", World.nodeRight, World.nodes);
        assertTable(tables, "LevelNodes.LEFT", World.nodeLeft, World.nodes);
        assertTable(tables, "LevelNodes.SUBSECTOR_FIRST", World.subsectorFirst, World.subsectors);
        assertTable(tables, "LevelNodes.SUBSECTOR_COUNT", World.subsectorCount, World.subsectors);
        assertTable(tables, "LevelNodes.SUBSECTOR_SECTOR", World.subsectorSector, World.subsectors);
        assertThat(Files.readString(generated)).contains(
                "START_X = " + World.startX + ";", "START_Y = " + World.startY + ";",
                "START_ANGLE = " + World.startAngle + ";",
                "EXIT_X = " + World.exitX + ";", "EXIT_Y = " + World.exitY + ";");
    }

    private static void assertTable(Map<String, int[]> tables, String name, short[] loaded, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = loaded[i];
        }
        assertThat(values).as(name).containsExactly(tables.get(name));
    }

    /** Every {@code static final short[]} table of a generated source, keyed {@code Class.NAME}. */
    private static Map<String, int[]> tables(String source) {
        Map<String, int[]> tables = new HashMap<>();
        Matcher classes = Pattern.compile("final class (\\w+) \\{(.*?)\\n}", Pattern.DOTALL).matcher(source);
        while (classes.find()) {
            Matcher table = Pattern.compile("static final short\\[] (\\w+) = \\{([^}]*)}").matcher(classes.group(2));
            while (table.find()) {
                tables.put(classes.group(1) + "." + table.group(1), Arrays.stream(table.group(2).split(","))
                        .map(String::trim).filter(value -> !value.isEmpty()).mapToInt(Integer::parseInt).toArray());
            }
        }
        return tables;
    }

    private static Path wad() {
        Path wad = Path.of(System.getProperty("juno.doom.wad", LOCAL_WAD.toString()));
        assumeTrue(Files.exists(wad), "No DOOM1.WAD to read: pass -Djuno.doom.wad=/path/DOOM1.WAD");
        return wad;
    }
}
