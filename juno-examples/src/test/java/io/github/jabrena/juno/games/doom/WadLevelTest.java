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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Discovering the WAD's episodes at startup, then loading the selected maps with {@link RandomAccessFile}, the same
 * code the UNO Q runs against the SD card, plus the built-in fallback. WAD checks run only when one is found:
 * {@code -Djuno.doom.wad=/path/DOOM1.WAD}, or a local copy beside the game's sources.
 */
class WadLevelTest {
    private static final Path LOCAL_WAD = Path.of("src/main/java/io/github/jabrena/juno/games/doom/DOOM1.WAD");

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void useUltraViolencePlacements() {
        World.skill = 4;
    }

    @AfterEach
    void removeTheCard() {
        SdCard.present = false;
        World.fromWad = false;
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

        assertThat(WadLevel.load(1, 1)).isFalse();
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

        assertThat(World.vertices).isBetween(1, World.MAX_VERTICES);
        assertThat(World.lines).isBetween(1, World.MAX_LINES);
        assertThat(World.segs).isBetween(1, World.MAX_SEGS);
        assertThat(World.subsectors).isBetween(1, World.MAX_SUBSECTORS);
        assertThat(World.nodes).isEqualTo(World.subsectors - 1).isLessThanOrEqualTo(World.MAX_NODES);
        assertThat(World.sectors).isBetween(1, World.MAX_SECTORS);
        assertThat(World.doors).isBetween(1, World.MAX_DOORS);
        assertThat(World.monsters).isBetween(1, World.MAX_MONSTERS);
        assertThat(World.items).isBetween(1, World.MAX_ITEMS);
        assertThat(World.secrets).as("E1M1 secret sectors").isEqualTo(3);
        assertThat(World.exitX).isNotEqualTo(-30000);
        assertThat(World.routeLength).as("only the start until the route is planned").isOne();
        assertThat(World.startX).isEqualTo(1056);
        assertThat(World.startY).isEqualTo(-3616);
    }

    @Test
    void discoversAndLoadsEveryRegularMapFromAllFourEpisodes() throws IOException {
        Path wad = wad();
        World.allocateForWad();

        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.episodes(file)).isEqualTo(0b1_1110);
            for (int episode = 1; episode <= 4; episode++) {
                for (int map = 1; map <= World.LAST_MAP; map++) {
                    file.seek(0);
                    assertThat(WadLevel.read(file, episode, map)).as("E%dM%d", episode, map).isTrue();
                }
            }
        }
    }

    @Test
    void selectedSkillControlsWhichWadThingsArePlaced() throws IOException {
        Path wad = wad();
        World.allocateForWad();

        int easyMonsters;
        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            World.skill = 1;
            assertThat(WadLevel.read(file, 1, 1)).isTrue();
            easyMonsters = World.monsters;
            file.seek(0);
            World.skill = 4;
            assertThat(WadLevel.read(file, 1, 1)).isTrue();
        }

        assertThat(World.monsters).as("Ultra-Violence monsters").isGreaterThan(easyMonsters);
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

    @Test
    void plansAWalkFromTheStartToTheExitSwitchOfE1M2() throws IOException {
        loadMap(1, 2);

        assertThat(RoutePlanner.plan()).isTrue();

        int last = World.routeLength - 1;
        float dx = World.routeX[last] - World.exitX;
        float dy = World.routeY[last] - World.exitY;
        assertThat(dx * dx + dy * dy).as("the route ends within reach of the exit switch").isLessThan(56f * 56f);
    }

    /**
     * Monsters chasing the CPU along E1M2's walls never step through one: positions are stored rounded, and a move
     * checked unrounded used to slip a monster through a wall the CPU then saw it behind and fought forever.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void monstersNeverWalkThroughWalls(int seed) throws IOException {
        loadMap(1, 2);
        short[] monsters = World.monsterStates;
        short[] shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        Random.seed(seed);
        Player.spawn(World.ceilings);
        RoutePlanner.plan();
        Monsters.reset(monsters, shots);
        Items.reset(World.taken);
        Weapon.reset();
        Autopilot.restart();
        short[] before = new short[monsters.length];
        for (int frame = 0; frame < 1500 && Player.health > 0; frame++) {
            System.arraycopy(monsters, 0, before, 0, monsters.length);
            Weapon.tick();
            Autopilot.step(World.ceilings, monsters, World.taken);
            Doors.operate(World.ceilings);
            Items.pickUp(World.taken);
            Monsters.think(monsters, shots, World.ceilings, frame);
            for (int at = 0; at < World.monsters * Monsters.STRIDE; at += Monsters.STRIDE) {
                boolean moved = monsters[at + Monsters.X] != before[at + Monsters.X]
                        || monsters[at + Monsters.Y] != before[at + Monsters.Y];
                if (moved && before[at + Monsters.STATE] != Monsters.DEAD
                        && monsters[at + Monsters.STATE] != Monsters.DEAD) {
                    assertThat(Player.blockedForMonster(before[at + Monsters.X], before[at + Monsters.Y],
                            monsters[at + Monsters.X], monsters[at + Monsters.Y], World.ceilings))
                            .as("monster %d's step on frame %d", at / Monsters.STRIDE, frame).isFalse();
                }
            }
        }
    }

    /** E1M3's exit lies past floors the engine does not move yet: the route still leads the CPU closer to it. */
    @Test
    void routesTowardAnExitNoWalkReaches() throws IOException {
        loadMap(1, 3);

        assertThat(RoutePlanner.plan()).isFalse();

        assertThat(RoutePlanner.shortOfExit).isTrue();
        assertThat(World.routeLength).isGreaterThan(1);
        int last = World.routeLength - 1;
        assertThat(Math.hypot(World.routeX[last] - World.exitX, World.routeY[last] - World.exitY))
                .as("the route ends nearer the exit than the start")
                .isLessThan(Math.hypot(World.startX - World.exitX, World.startY - World.exitY));
        short[] open = openCeilings();
        for (int i = 0; i < last; i++) {
            assertThat(Player.blocked(World.routeX[i], World.routeY[i], World.routeX[i + 1], World.routeY[i + 1], open))
                    .as("leg %d of the route", i).isFalse();
        }
    }

    /**
     * Fighting its way through E1M1, the CPU never stands stuck for long: these runs used to trap it at a corner and
     * in a channel beside a railing, where its routes grazed walls its body could not get past and it re-planned
     * over and over, which freezes the board.
     */
    @ParameterizedTest
    @CsvSource({"1,4", "1,11", "3,3", "3,4", "3,7"})
    void theCpuNeverStaysStuckFightingThroughE1M1(int skill, int seed) throws IOException {
        World.skill = skill;
        loadE1M1();
        World.fromWad = true;
        short[] monsters = World.monsterStates;
        short[] shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        Random.seed(seed);
        Lifts.reset();
        Player.spawn(World.ceilings);
        RoutePlanner.plan();
        Monsters.reset(monsters, shots);
        Items.reset(World.taken);
        Weapon.reset();
        Autopilot.restart();
        float stillX = Player.x;
        float stillY = Player.y;
        int still = 0;
        for (int frame = 0; frame < 8000 && Player.health > 0 && !Player.atExit(); frame++) {
            Weapon.tick();
            Autopilot.step(World.ceilings, monsters, World.taken);
            Doors.operate(World.ceilings);
            Lifts.operate(Player.x, Player.y);
            Player.settle();
            Items.pickUp(World.taken);
            Monsters.think(monsters, shots, World.ceilings, frame);
            if (Math.hypot(Player.x - stillX, Player.y - stillY) < 4) {
                still = still + 1;
            } else {
                still = 0;
                stillX = Player.x;
                stillY = Player.y;
            }
            assertThat(still).as("frames standing near %s,%s on frame %d", stillX, stillY, frame).isLessThan(400);
        }
    }

    /**
     * The CPU marine, with the monsters out of the way, walks each map whose exit the engine can reach from its start
     * to the exit switch, whichever way its random sway takes it: through doors, round corners with its body radius,
     * and on E1M2 riding its lifts.
     */
    @ParameterizedTest
    @CsvSource({"1,1", "1,42", "2,1", "2,7", "5,1", "6,1", "7,1", "7,3"})
    void theCpuWalksThePlannedRouteToTheExit(int map, int seed) throws IOException {
        loadMap(1, map);
        World.fromWad = true;
        assertThat(RoutePlanner.plan()).isTrue();
        short[] none = new short[World.MAX_MONSTERS * Monsters.STRIDE];
        for (int i = 0; i < World.MAX_MONSTERS; i++) {
            none[i * Monsters.STRIDE + Monsters.STATE] = Monsters.DEAD;
        }
        for (int i = 0; i < World.items; i++) {
            World.taken[i] = 1;
        }
        Random.seed(seed);
        Lifts.reset();
        Player.spawn(World.ceilings);
        Autopilot.restart();

        int frame = 0;
        while (frame < 20000 && !Player.atExit()) {
            Autopilot.step(World.ceilings, none, World.taken);
            Doors.operate(World.ceilings);
            Lifts.operate(Player.x, Player.y);
            Player.settle();
            frame = frame + 1;
        }

        assertThat(Player.atExit()).as("at the exit switch of E1M%d after %d frames, standing at %s,%s", map, frame,
                Player.x, Player.y).isTrue();
    }

    @Test
    void theRecordedMediumCpuRunCommitsToTheE1M1ExitBeforeItDies() throws IOException {
        World.skill = 3;
        loadE1M1();
        World.fromWad = true;
        assertThat(RoutePlanner.plan()).isTrue();
        short[] monsters = World.monsterStates;
        short[] shots = new short[Monsters.SHOTS * Monsters.SHOT_STRIDE];
        Random.seed(5_014_000);
        Player.spawn(World.ceilings);
        Monsters.reset(monsters, shots);
        Items.reset(World.taken);
        Weapon.reset();
        Autopilot.restart();

        int frame = 0;
        while (frame < 10_000 && !Player.atExit() && Player.health > 0) {
            frame = frame + 1;
            Weapon.tick();
            if (Player.hurt > 0) {
                Player.hurt = Player.hurt - 1;
            }
            Autopilot.step(World.ceilings, monsters, World.taken);
            Doors.operate(World.ceilings);
            Player.settle();
            Items.pickUp(World.taken);
            Monsters.think(monsters, shots, World.ceilings, frame);
        }

        assertThat(Player.atExit())
                .as("exit after %d frames with %d health and %d kills at %s,%s; route target %d/%d", frame,
                        Player.health, Monsters.kills, Player.x, Player.y, Autopilot.target, World.routeLength)
                .isTrue();
        assertThat(Player.health).isPositive();
        assertThat(Monsters.kills).as("combat before the final exit approach").isPositive();
    }

    /**
     * DOOM marks most windows impassable (linedef flag ML_BLOCKING): two-sided lines whose heights alone would let the
     * marine step or drop through. Every such line must stop him, and monsters too, from either side.
     */
    @Test
    void theLinesE1M1MarksImpassableStopTheMarineAndTheMonsters() throws IOException {
        loadE1M1();
        short[] open = openCeilings();
        int impassable = 0;
        for (int line = 0; line < World.lines; line++) {
            if (World.lineBack[line] < 0 || !World.isImpassable(line, false)) {
                continue;
            }
            impassable++;
            float ax = World.vertexX[World.lineV1[line]];
            float ay = World.vertexY[World.lineV1[line]];
            float bx = World.vertexX[World.lineV2[line]];
            float by = World.vertexY[World.lineV2[line]];
            float length = (float) Math.hypot(bx - ax, by - ay);
            float normalX = -(by - ay) / length * 8;
            float normalY = (bx - ax) / length * 8;
            float midX = (ax + bx) / 2;
            float midY = (ay + by) / 2;
            assertThat(Player.blocked(midX + normalX, midY + normalY, midX - normalX, midY - normalY, open))
                    .as("line %d from the front", line).isTrue();
            assertThat(Player.blocked(midX - normalX, midY - normalY, midX + normalX, midY + normalY, open))
                    .as("line %d from the back", line).isTrue();
            assertThat(Player.blockedForMonster(midX + normalX, midY + normalY, midX - normalX, midY - normalY, open))
                    .as("line %d for a monster", line).isTrue();
        }
        assertThat(impassable).as("E1M1 has windows marked impassable").isPositive();
    }

    private void loadE1M1() throws IOException {
        loadMap(1, 1);
    }

    private void loadMap(int episode, int map) throws IOException {
        Path wad = wad();
        World.allocateForWad();
        try (RandomAccessFile file = new RandomAccessFile(wad.toFile(), "r")) {
            assertThat(WadLevel.read(file, episode, map)).isTrue();
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
