package io.github.jabrena.juno.games.doom;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Reads one map (E1M1 to E4M9) out of {@code DOOM1.WAD} on the SD card, when it is chosen or reached, into {@link World} with {@link RandomAccessFile}, the JDK's
 * own random-access API: the header points at the lump directory at the end of the file, the directory points at
 * the map's lumps, and each lump is a packed array of little-endian records read one at a time. Nothing is
 * converted ahead of time; derived data (door heights, which monsters and pickups the map places) is worked out
 * exactly as {@code LevelGenerator} does for the flash tables, and the CPU's route is planned afterwards by
 * {@link RoutePlanner}.
 *
 * <p>The WAD is id Software's data: copy your own {@code DOOM1.WAD} to the card's root, never to the repository.
 */
final class WadLevel {
    private static final int HEADER_BYTES = 12;
    private static final int ENTRY_BYTES = 16;
    /** The lumps that follow a map's marker, in the order every DOOM map stores them. */
    private static final int THINGS = 0;
    private static final int LINEDEFS = 1;
    private static final int SIDEDEFS = 2;
    private static final int VERTEXES = 3;
    private static final int SEGS = 4;
    private static final int SSECTORS = 5;
    private static final int NODES = 6;
    private static final int SECTORS = 7;
    private static final int LUMPS = 8;

    private static final int LINEDEF_BYTES = 14;
    private static final int SIDEDEF_BYTES = 30;
    private static final int VERTEX_BYTES = 4;
    private static final int SEG_BYTES = 12;
    private static final int SUBSECTOR_BYTES = 4;
    private static final int NODE_BYTES = 28;
    private static final int SECTOR_BYTES = 26;

    /** The line specials that end the level: DOOM's S1 exit switch and W1 walk-over exit. */
    private static final int EXIT_SWITCH = 11;
    private static final int EXIT_WALK_OVER = 52;
    private static final int NO_EXIT = -30000;

    private WadLevel() {
    }

    /** Reads map EeMm from the card's {@code DOOM1.WAD}; {@code false} when the file or map is missing, broken or too large. */
    static boolean load(int episode, int map) {
        try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
            return read(wad, episode, map);
        } catch (IOException missingOrBroken) {
            return false;
        }
    }

    /** Which episodes the card's WAD holds: bit {@code e} set when map EeM1 is there; 0 without a readable WAD. */
    static int episodes() {
        try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
            return episodes(wad);
        } catch (IOException missingOrBroken) {
            return 0;
        }
    }

    static int episodes(RandomAccessFile wad) throws IOException {
        byte[] record = new byte[ENTRY_BYTES];
        wad.readFully(record, 0, HEADER_BYTES);
        if (record[1] != 'W' || record[2] != 'A' || record[3] != 'D') {
            return 0;
        }
        int found = 0;
        for (int episode = 1; episode <= 4; episode++) {
            if (findMap(wad, record, int32(record, 4), int32(record, 8), episode, 1) >= 0) {
                found = found | 1 << episode;
            }
            wad.seek(0);
            wad.readFully(record, 0, HEADER_BYTES);
        }
        return found;
    }

    /** Reads E1M1 from an open WAD into {@link World}'s WAD-sized tables. */
    static boolean read(RandomAccessFile wad) throws IOException {
        return read(wad, 1, 1);
    }

    /** Reads map EeMm from an open WAD into {@link World}'s WAD-sized tables. */
    static boolean read(RandomAccessFile wad, int episode, int map) throws IOException {
        byte[] record = new byte[NODE_BYTES];
        int[] lumps = new int[2 * LUMPS];
        wad.readFully(record, 0, HEADER_BYTES);
        if (record[1] != 'W' || record[2] != 'A' || record[3] != 'D') {
            return false;
        }
        int marker = findMap(wad, record, int32(record, 4), int32(record, 8), episode, map);
        if (marker < 0) {
            return false;
        }
        for (int lump = 0; lump < LUMPS; lump++) {
            wad.readFully(record, 0, ENTRY_BYTES);
            lumps[2 * lump] = int32(record, 0);
            lumps[2 * lump + 1] = int32(record, 4);
        }
        if (!count(lumps)) {
            return false;
        }
        int things = lumps[2 * THINGS + 1] / WadThings.THING_BYTES;
        Loading.beginLoad(World.vertices + 2 * World.sectors + 3 * World.lines + World.segs + World.subsectors
                + World.nodes + things, World.subsectors);
        boolean read = readRecords(wad, record, lumps, things);
        Loading.endLoad();
        return read;
    }

    /** The map's lumps into {@link World}, each record advancing {@link Loading}'s bar. */
    private static boolean readRecords(RandomAccessFile wad, byte[] record, int[] lumps, int things)
            throws IOException {
        readVertices(wad, record, lumps[2 * VERTEXES]);
        readSectors(wad, record, lumps[2 * SECTORS]);
        if (!readLines(wad, record, lumps[2 * LINEDEFS], lumps[2 * SIDEDEFS])
                || !Lifts.resolve(wad, record, lumps[2 * SECTORS], SECTOR_BYTES)) {
            return false;
        }
        placeDoors();
        readSegs(wad, record, lumps[2 * SEGS]);
        readSubsectors(wad, record, lumps[2 * SSECTORS]);
        readNodes(wad, record, lumps[2 * NODES]);
        if (!WadThings.read(wad, record, lumps[2 * THINGS], things)) {
            return false;
        }
        // Until RoutePlanner replaces it, the route is just the start: the CPU stands and fights.
        World.routeX[0] = (short) World.startX;
        World.routeY[0] = (short) World.startY;
        World.routeLength = 1;
        World.loopStart = 0;
        return true;
    }

    /** The directory index of the EeMm marker lump, leaving the file right after its entry; -1 if absent. */
    private static int findMap(RandomAccessFile wad, byte[] entry, int lumps, int directory, int episode, int map)
            throws IOException {
        wad.seek(directory);
        for (int index = 0; index < lumps; index++) {
            wad.readFully(entry, 0, ENTRY_BYTES);
            if (entry[8] == 'E' && entry[9] == '0' + episode && entry[10] == 'M' && entry[11] == '0' + map
                    && entry[12] == 0) {
                return index;
            }
        }
        return -1;
    }

    /** Sets the table counts from the lump sizes; {@code false} when the map outgrows a table's capacity. */
    private static boolean count(int[] lumps) {
        World.vertices = lumps[2 * VERTEXES + 1] / VERTEX_BYTES;
        World.lines = lumps[2 * LINEDEFS + 1] / LINEDEF_BYTES;
        World.segs = lumps[2 * SEGS + 1] / SEG_BYTES;
        World.subsectors = lumps[2 * SSECTORS + 1] / SUBSECTOR_BYTES;
        World.nodes = lumps[2 * NODES + 1] / NODE_BYTES;
        World.sectors = lumps[2 * SECTORS + 1] / SECTOR_BYTES;
        return World.vertices <= World.MAX_VERTICES && World.lines <= World.MAX_LINES
                && World.segs <= World.MAX_SEGS && World.subsectors <= World.MAX_SUBSECTORS
                && World.nodes <= World.MAX_NODES && World.sectors <= World.MAX_SECTORS;
    }

    private static void readVertices(RandomAccessFile wad, byte[] record, int offset) throws IOException {
        wad.seek(offset);
        for (int i = 0; i < World.vertices; i++) {
            wad.readFully(record, 0, VERTEX_BYTES);
            Loading.advance(1);
            World.vertexX[i] = int16(record, 0);
            World.vertexY[i] = int16(record, 2);
        }
    }

    private static void readSectors(RandomAccessFile wad, byte[] record, int offset) throws IOException {
        wad.seek(offset);
        World.clearSectorSecrets();
        for (int i = 0; i < World.sectors; i++) {
            wad.readFully(record, 0, SECTOR_BYTES);
            Loading.advance(1);
            World.sectorFloor[i] = int16(record, 0);
            World.sectorCeiling[i] = int16(record, 2);
            if (int16(record, 22) == 9) {
                World.markSecretSector(i);
            }
        }
    }

    /**
     * Linedefs name their sides by sidedef number, and the sector is the sidedef's last field: each side is one
     * seek into SIDEDEFS. Also collects the door sectors and the exit switch from the line specials, and hands lift and
     * remote door lines to {@link Lifts}.
     */
    private static boolean readLines(RandomAccessFile wad, byte[] record, int offset, int sidedefs)
            throws IOException {
        World.doors = 0;
        Lifts.clear();
        World.exitX = NO_EXIT;
        World.exitY = NO_EXIT;
        World.exitLine = -1;
        // These arrays are reused for every map, so no previous map's blocking flags may leak into the next one.
        int words = (World.lines + 15) / 16;
        for (int word = 0; word < words; word++) {
            World.lineBlocking[word] = 0;
            World.lineBlocksMonsters[word] = 0;
        }
        for (int line = 0; line < World.lines; line++) {
            wad.seek(offset + line * LINEDEF_BYTES);
            wad.readFully(record, 0, LINEDEF_BYTES);
            Loading.advance(3);
            int v1 = int16(record, 0) & 0xFFFF;
            int v2 = int16(record, 2) & 0xFFFF;
            World.setLineFlags(line, int16(record, 4));
            int special = int16(record, 6);
            Lifts.noteLine(line, special, int16(record, 8));
            int right = int16(record, 10);
            int left = int16(record, 12);
            World.lineV1[line] = (short) v1;
            World.lineV2[line] = (short) v2;
            World.lineFront[line] = sideSector(wad, record, sidedefs, right);
            World.lineBack[line] = left < 0 ? -1 : sideSector(wad, record, sidedefs, left);
            if (isDoor(special) && left >= 0 && !addDoor(World.lineBack[line])) {
                return false;
            }
            if ((special == EXIT_SWITCH || special == EXIT_WALK_OVER) && World.exitLine < 0) {
                World.exitLine = line;
                World.exitX = (World.vertexX[v1] + World.vertexX[v2]) / 2;
                World.exitY = (World.vertexY[v1] + World.vertexY[v2]) / 2;
            }
        }
        return true;
    }

    private static short sideSector(RandomAccessFile wad, byte[] record, int sidedefs, int side) throws IOException {
        wad.seek(sidedefs + side * SIDEDEF_BYTES + SIDEDEF_BYTES - 2);
        wad.readFully(record, 0, 2);
        return int16(record, 0);
    }

    /** Line specials that open the sector behind them as a door (manual doors, DOOM's types 1/26-28/31-34/117/118). */
    private static boolean isDoor(int special) {
        return special == 1 || (special >= 26 && special <= 28) || (special >= 31 && special <= 34)
                || special == 117 || special == 118;
    }

    static boolean addDoor(int sector) {
        for (int door = 0; door < World.doors; door++) {
            if (World.doorSector[door] == sector) {
                return true;
            }
        }
        if (World.doors == World.MAX_DOORS) {
            return false;
        }
        World.doorSector[World.doors] = (short) sector;
        World.doors = World.doors + 1;
        return true;
    }

    /** A door opens to its lowest neighboring ceiling minus 4, as in DOOM, and is triggered from its middle. */
    private static void placeDoors() {
        for (int door = 0; door < World.doors; door++) {
            int sector = World.doorSector[door];
            int lowest = Integer.MAX_VALUE;
            int sumX = 0;
            int sumY = 0;
            int touching = 0;
            for (int line = 0; line < World.lines; line++) {
                int front = World.lineFront[line];
                int back = World.lineBack[line];
                int other = front == sector ? back : back == sector ? front : -2;
                if (other == -2) {
                    continue;
                }
                if (other >= 0) {
                    lowest = Math.min(lowest, World.sectorCeiling[other]);
                }
                int v1 = World.lineV1[line];
                int v2 = World.lineV2[line];
                sumX = sumX + World.vertexX[v1] + World.vertexX[v2];
                sumY = sumY + World.vertexY[v1] + World.vertexY[v2];
                touching = touching + 2;
            }
            World.doorTop[door] = (short) (lowest - 4);
            World.doorX[door] = (short) (sumX / touching);
            World.doorY[door] = (short) (sumY / touching);
        }
    }

    private static void readSegs(RandomAccessFile wad, byte[] record, int offset) throws IOException {
        wad.seek(offset);
        for (int i = 0; i < World.segs; i++) {
            wad.readFully(record, 0, SEG_BYTES);
            Loading.advance(1);
            World.segV1[i] = int16(record, 0);
            World.segV2[i] = int16(record, 2);
            World.segLine[i] = int16(record, 6);
            World.segSide[i] = int16(record, 8);
        }
    }

    /** A subsector's sector is the one on the side its first seg runs along. */
    private static void readSubsectors(RandomAccessFile wad, byte[] record, int offset) throws IOException {
        wad.seek(offset);
        for (int i = 0; i < World.subsectors; i++) {
            wad.readFully(record, 0, SUBSECTOR_BYTES);
            Loading.advance(1);
            int first = int16(record, 2);
            World.subsectorCount[i] = int16(record, 0);
            World.subsectorFirst[i] = (short) first;
            int line = World.segLine[first];
            World.subsectorSector[i] = World.segSide[first] == 0 ? World.lineFront[line] : World.lineBack[line];
        }
    }

    private static void readNodes(RandomAccessFile wad, byte[] record, int offset) throws IOException {
        wad.seek(offset);
        for (int i = 0; i < World.nodes; i++) {
            wad.readFully(record, 0, NODE_BYTES);
            Loading.advance(1);
            World.nodeX[i] = int16(record, 0);
            World.nodeY[i] = int16(record, 2);
            World.nodeDx[i] = int16(record, 4);
            World.nodeDy[i] = int16(record, 6);
            World.nodeRight[i] = int16(record, 24);
            World.nodeLeft[i] = int16(record, 26);
        }
    }

    static short int16(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
    }

    static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 | (bytes[offset + 2] & 0xFF) << 16
                | bytes[offset + 3] << 24;
    }
}
