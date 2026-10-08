package io.github.jabrena.juno.games.doom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts one map of a DOOM WAD file into the {@code Level*} flash tables the wireframe engine reads.
 *
 * <p>The geometry, BSP tree and sector heights come straight from the WAD's {@code VERTEXES},
 * {@code LINEDEFS}, {@code SIDEDEFS}, {@code SEGS}, {@code SSECTORS}, {@code NODES} and {@code SECTORS}
 * lumps; door sectors and their open heights are derived the way DOOM derives them (lowest neighboring
 * ceiling minus 4). Monsters are the zombiemen, sergeants, imps and demons the map places on Ultra-Violence
 * skill, with that skill's health and armor pickups. The autopilot route is not in any WAD: it is a walk through the map chosen for the
 * demo, listed in {@link #ROUTES} by map name.
 *
 * <p>The output contains id Software's level data, so it must never be committed: run it locally on a
 * WAD you have, build or record the game, then restore the original-map {@code Level.java} with
 * {@code git checkout}.
 */
public final class LevelGenerator {
    /** Line specials that open the sector behind them as a door (manual doors, DOOM's types 1/26-28/31-34/117/118). */
    private static final Set<Integer> DOOR_SPECIALS = Set.of(1, 26, 27, 28, 31, 32, 33, 34, 117, 118);
    private static final int VALUES_PER_LINE = 16;
    /** The switch line special that ends the level (DOOM's S1 exit). */
    private static final int EXIT_SPECIAL = 11;

    /** Thing types the engine animates, by its monster kind: zombieman, shotgun sergeant, imp, demon. */
    private static final Map<Integer, Integer> MONSTER_KINDS = Map.of(3004, 0, 9, 1, 3001, 2, 3002, 3);
    /** Pickups by item kind: health bonus, stimpack, medikit, armor bonus, green armor, blue armor. */
    private static final Map<Integer, Integer> ITEM_KINDS = Map.of(2014, 0, 2011, 1, 2012, 2, 2015, 3, 2018, 4, 2019, 5);
    /** THINGS flag bits: placed on Ultra-Violence ("hard") skill, and only in multiplayer. */
    private static final int SKILL_HARD = 4;
    private static final int MULTIPLAYER_ONLY = 16;

    /** A route: its waypoints as x,y pairs, and the waypoint the loop returns to after the last one. */
    record Route(int[] points, int loopStart) {
    }

    /**
     * Demo walks, checked against each map's geometry: no solid line crossed, no step up higher than
     * 24 units, enough headroom everywhere except at doors, which the engine opens. E1M1's ends at its exit switch.
     */
    static final Map<String, Route> ROUTES = Map.of("E1M1", new Route(new int[] {
        1056, -3616, 1056, -3000, 1280, -2930, 1300, -2660, 1440, -2500, 1600, -2500, 1720, -2500,
        2000, -2690, 2400, -2690, 2600, -2620, 2840, -2680, 2960, -2800, 2980, -3000, 3000, -3300,
        3000, -3600, 3000, -3860, 2971, -4061, 2947, -4541, 2923, -4565, 2851, -4733, 2870, -4768}, 13));

    private LevelGenerator() {
    }

    /** {@code LevelGenerator <wad> <map> <output Level.java>}. */
    public static void main(String[] args) throws IOException {
        generate(Path.of(args[0]), args[1], Path.of(args[2]));
    }

    static void generate(Path wad, String map, Path output) throws IOException {
        Route route = ROUTES.get(map);
        if (route == null) {
            throw new IllegalArgumentException("No demo route for map " + map + "; known: " + ROUTES.keySet());
        }
        Wad lumps = new Wad(Files.readAllBytes(wad), map);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, source(lumps, map, route), StandardCharsets.UTF_8);
    }

    private static String source(Wad wad, String map, Route route) {
        ByteBuffer vertexes = wad.lump("VERTEXES");
        ByteBuffer linedefs = wad.lump("LINEDEFS");
        ByteBuffer sidedefs = wad.lump("SIDEDEFS");
        ByteBuffer segs = wad.lump("SEGS");
        ByteBuffer subsectors = wad.lump("SSECTORS");
        ByteBuffer nodes = wad.lump("NODES");
        ByteBuffer sectors = wad.lump("SECTORS");
        ByteBuffer things = wad.lump("THINGS");

        int vertexCount = vertexes.limit() / 4;
        int lineCount = linedefs.limit() / 14;
        int segCount = segs.limit() / 12;
        int subsectorCount = subsectors.limit() / 4;
        int nodeCount = nodes.limit() / 28;
        int sectorCount = sectors.limit() / 26;

        int[] lineFront = new int[lineCount];
        int[] lineBack = new int[lineCount];
        for (int line = 0; line < lineCount; line++) {
            int right = linedefs.getShort(line * 14 + 10);
            int left = linedefs.getShort(line * 14 + 12);
            lineFront[line] = sidedefs.getShort(right * 30 + 28);
            lineBack[line] = left < 0 ? -1 : sidedefs.getShort(left * 30 + 28);
        }
        int[] floor = new int[sectorCount];
        int[] ceiling = new int[sectorCount];
        for (int sector = 0; sector < sectorCount; sector++) {
            floor[sector] = sectors.getShort(sector * 26);
            ceiling[sector] = sectors.getShort(sector * 26 + 2);
        }
        int[] subsectorSector = new int[subsectorCount];
        for (int subsector = 0; subsector < subsectorCount; subsector++) {
            int seg = subsectors.getShort(subsector * 4 + 2);
            int line = segs.getShort(seg * 12 + 6);
            int side = segs.getShort(seg * 12 + 8);
            subsectorSector[subsector] = side == 0 ? lineFront[line] : lineBack[line];
        }

        List<Integer> doorSectors = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (int line = 0; line < lineCount; line++) {
            int special = linedefs.getShort(line * 14 + 6);
            if (DOOR_SPECIALS.contains(special) && lineBack[line] >= 0 && seen.add(lineBack[line])) {
                doorSectors.add(lineBack[line]);
            }
        }
        int exitX = -30000;
        int exitY = -30000;
        for (int line = 0; line < lineCount; line++) {
            int special = linedefs.getShort(line * 14 + 6);
            if (special == EXIT_SPECIAL) {
                int v1 = linedefs.getShort(line * 14) & 0xFFFF;
                int v2 = linedefs.getShort(line * 14 + 2) & 0xFFFF;
                exitX = (vertexes.getShort(v1 * 4) + vertexes.getShort(v2 * 4)) / 2;
                exitY = (vertexes.getShort(v1 * 4 + 2) + vertexes.getShort(v2 * 4 + 2)) / 2;
                break;
            }
        }
        int doors = doorSectors.size();
        int[] doorTop = new int[doors];
        int[] doorX = new int[doors];
        int[] doorY = new int[doors];
        for (int door = 0; door < doors; door++) {
            int sector = doorSectors.get(door);
            int lowest = Integer.MAX_VALUE;
            long sumX = 0;
            long sumY = 0;
            int touching = 0;
            for (int line = 0; line < lineCount; line++) {
                int other = lineFront[line] == sector ? lineBack[line] : lineBack[line] == sector ? lineFront[line] : -2;
                if (other == -2) {
                    continue;
                }
                if (other >= 0) {
                    lowest = Math.min(lowest, ceiling[other]);
                }
                int v1 = linedefs.getShort(line * 14) & 0xFFFF;
                int v2 = linedefs.getShort(line * 14 + 2) & 0xFFFF;
                sumX += vertexes.getShort(v1 * 4) + vertexes.getShort(v2 * 4);
                sumY += vertexes.getShort(v1 * 4 + 2) + vertexes.getShort(v2 * 4 + 2);
                touching += 2;
            }
            doorTop[door] = lowest - 4;
            doorX[door] = (int) (sumX / touching);
            doorY[door] = (int) (sumY / touching);
        }

        int startX = 0;
        int startY = 0;
        int startAngle = 0;
        List<int[]> monsters = new ArrayList<>();
        List<int[]> items = new ArrayList<>();
        for (int thing = 0; thing < things.limit() / 10; thing++) {
            int type = things.getShort(thing * 10 + 6);
            int flags = things.getShort(thing * 10 + 8);
            if (type == 1) {
                startX = things.getShort(thing * 10);
                startY = things.getShort(thing * 10 + 2);
                startAngle = things.getShort(thing * 10 + 4);
            } else if (MONSTER_KINDS.containsKey(type) && (flags & SKILL_HARD) != 0 && (flags & MULTIPLAYER_ONLY) == 0) {
                monsters.add(new int[] {things.getShort(thing * 10), things.getShort(thing * 10 + 2), MONSTER_KINDS.get(type)});
            } else if (ITEM_KINDS.containsKey(type) && (flags & SKILL_HARD) != 0 && (flags & MULTIPLAYER_ONLY) == 0) {
                items.add(new int[] {things.getShort(thing * 10), things.getShort(thing * 10 + 2), ITEM_KINDS.get(type)});
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("""
                package io.github.jabrena.juno.games.doom;

                // GENERATED by LevelGenerator from %s of a DOOM WAD. This file contains id Software's level data:
                // DO NOT COMMIT IT. Restore the original map with `git checkout -- Level.java`.

                /** Map %s: player start, sector heights, doors, monsters, pickups and the autopilot route. */
                final class Level {
                    static final String NAME = "%s";
                    static final int START_X = %d;
                    static final int START_Y = %d;
                    static final int START_ANGLE = %d;
                    static final int LOOP_START = %d;
                    static final int EXIT_X = %d;
                    static final int EXIT_Y = %d;
                    static final int MONSTERS = %d;
                    static final int ITEMS = %d;

                """.formatted(map, map, map, startX, startY, startAngle, route.loopStart(), exitX, exitY, monsters.size(), items.size()));
        int[] routeX = new int[route.points().length / 2];
        int[] routeY = new int[routeX.length];
        for (int i = 0; i < routeX.length; i++) {
            routeX[i] = route.points()[2 * i];
            routeY[i] = route.points()[2 * i + 1];
        }
        table(out, "ROUTE_X", routeX);
        table(out, "ROUTE_Y", routeY);
        table(out, "SECTOR_FLOOR", floor);
        table(out, "SECTOR_CEILING", ceiling);
        table(out, "DOOR_SECTOR", doorSectors.stream().mapToInt(Integer::intValue).toArray());
        table(out, "DOOR_TOP", doorTop);
        table(out, "DOOR_X", doorX);
        table(out, "DOOR_Y", doorY);
        table(out, "MONSTER_X", monsters.stream().mapToInt(m -> m[0]).toArray());
        table(out, "MONSTER_Y", monsters.stream().mapToInt(m -> m[1]).toArray());
        table(out, "MONSTER_KIND", monsters.stream().mapToInt(m -> m[2]).toArray());
        table(out, "ITEM_X", items.stream().mapToInt(m -> m[0]).toArray());
        table(out, "ITEM_Y", items.stream().mapToInt(m -> m[1]).toArray());
        table(out, "ITEM_KIND", items.stream().mapToInt(m -> m[2]).toArray());
        out.append("""

                    private Level() {
                    }
                }

                /** Map vertices. */
                final class LevelVertices {
                """);
        table(out, "X", shorts(vertexes, vertexCount, 4, 0));
        table(out, "Y", shorts(vertexes, vertexCount, 4, 2));
        close(out, "LevelVertices");
        out.append("\n/** Linedefs: end vertices and the sectors on their front and back sides (-1 when one-sided). */\n"
                + "final class LevelLines {\n");
        table(out, "V1", shorts(linedefs, lineCount, 14, 0));
        table(out, "V2", shorts(linedefs, lineCount, 14, 2));
        table(out, "FRONT", lineFront);
        table(out, "BACK", lineBack);
        close(out, "LevelLines");
        out.append("\n/** BSP segs: end vertices, their linedef, and which side of it they run along (0 front, 1 back). */\n"
                + "final class LevelSegs {\n");
        table(out, "V1", shorts(segs, segCount, 12, 0));
        table(out, "V2", shorts(segs, segCount, 12, 2));
        table(out, "LINE", shorts(segs, segCount, 12, 6));
        table(out, "SIDE", shorts(segs, segCount, 12, 8));
        close(out, "LevelSegs");
        out.append("\n/** BSP nodes (children with bit 15 set are subsectors) and subsectors (first seg, seg count, sector). */\n"
                + "final class LevelNodes {\n");
        table(out, "X", shorts(nodes, nodeCount, 28, 0));
        table(out, "Y", shorts(nodes, nodeCount, 28, 2));
        table(out, "DX", shorts(nodes, nodeCount, 28, 4));
        table(out, "DY", shorts(nodes, nodeCount, 28, 6));
        table(out, "RIGHT", shorts(nodes, nodeCount, 28, 24));
        table(out, "LEFT", shorts(nodes, nodeCount, 28, 26));
        table(out, "SUBSECTOR_FIRST", shorts(subsectors, subsectorCount, 4, 2));
        table(out, "SUBSECTOR_COUNT", shorts(subsectors, subsectorCount, 4, 0));
        table(out, "SUBSECTOR_SECTOR", subsectorSector);
        close(out, "LevelNodes");
        return out.toString();
    }

    private static int[] shorts(ByteBuffer lump, int count, int stride, int offset) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = lump.getShort(i * stride + offset);
        }
        return values;
    }

    private static void table(StringBuilder out, String name, int[] values) {
        out.append("    static final short[] ").append(name).append(" = {");
        for (int i = 0; i < values.length; i++) {
            out.append(i % VALUES_PER_LINE == 0 ? "\n        " : " ").append(values[i]).append(',');
        }
        out.append(values.length == 0 ? "};\n" : "\n    };\n");
    }

    private static void close(StringBuilder out, String className) {
        out.append("\n    private ").append(className).append("() {\n    }\n}\n");
    }

    /** The lumps of one map, found after its marker lump in the WAD directory. */
    private static final class Wad {
        private final ByteBuffer data;
        private final int directory;
        private final int lumps;
        private final int marker;

        Wad(byte[] bytes, String map) {
            data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            lumps = data.getInt(4);
            directory = data.getInt(8);
            int found = -1;
            for (int i = 0; i < lumps && found < 0; i++) {
                if (name(i).equals(map)) {
                    found = i;
                }
            }
            if (found < 0) {
                throw new IllegalArgumentException("Map " + map + " is not in this WAD");
            }
            marker = found;
        }

        ByteBuffer lump(String name) {
            for (int i = marker + 1; i < Math.min(lumps, marker + 12); i++) {
                if (name(i).equals(name)) {
                    int offset = data.getInt(directory + i * 16);
                    int size = data.getInt(directory + i * 16 + 4);
                    return data.slice(offset, size).order(ByteOrder.LITTLE_ENDIAN);
                }
            }
            throw new IllegalArgumentException("Lump " + name + " missing after the map marker");
        }

        private String name(int index) {
            byte[] raw = new byte[8];
            data.get(directory + index * 16 + 8, raw);
            int length = 0;
            while (length < 8 && raw[length] != 0) {
                length++;
            }
            return new String(raw, 0, length, StandardCharsets.US_ASCII);
        }
    }
}
