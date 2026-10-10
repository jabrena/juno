package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * The map the engine plays, held in RAM: one map at a time (E1M1 to E4M9) read from {@code DOOM1.WAD} on the SD
 * card by {@link WadLevel} when it is chosen or reached, or,
 * as the fallback, a copy of the built-in map in flash ({@link Level}, {@link LevelVertices}, {@link LevelLines},
 * {@link LevelSegs}, {@link LevelNodes}). The tables mean exactly what the flash ones do; each count says how many
 * entries are in use, since arrays are allocated once with a fixed capacity.
 *
 * <p>A map read from the WAD gets its CPU route from {@link RoutePlanner}; the built-in map, which has no exit,
 * keeps its own patrol route. Arrays have a fixed size, so the tables are sized once for the largest map of DOOM's
 * four episodes (E2M7 and E4M9) and reused by every map: about {@link #WAD_ARENA_BYTES} of arena, plus the route
 * planner's tables and the renderer's buffers, so build with {@code -Djuno.Xmx=92k}. With a smaller arena, no card
 * or no WAD, the built-in map is played instead, sized by its own counts.
 */
final class World {
    // The largest figure among E1M1-E4M9 for each table, rounded up; monsters and pickups as Ultra-Violence places them.
    static final int MAX_VERTICES = 1632;
    static final int MAX_LINES = 1776;
    static final int MAX_SEGS = 2688;
    static final int MAX_NODES = 960;
    static final int MAX_SUBSECTORS = 960;
    static final int MAX_SECTORS = 328;
    static final int MAX_DOORS = 48;
    static final int MAX_MONSTERS = 180;
    static final int MAX_ITEMS = 152;
    static final int MAX_ROUTE = 64;
    /** Words of one per-sector bitsets: 16 sectors to a {@code short}. */
    static final int SECTOR_WORDS = (MAX_SECTORS + 15) / 16;
    /** The renderer's buffers, which {@link Doom} allocates after the tables (with each block's header). */
    static final int RENDER_ARENA_BYTES = 4 * DisplayList.LIST_SIZE + 3 * DisplayList.WIDTH + DisplayList.MAX_LINES
            + 128 + 2 * Monsters.SHOTS * Monsters.SHOT_STRIDE + 6 * 16;
    /** The last map number of an episode played in order; map 9 is the secret level, reached only by a secret exit. */
    static final int LAST_MAP = 8;
    /** Words of one per-line bitset: 16 lines to a {@code short}. */
    static final int LINE_WORDS = (MAX_LINES + 15) / 16;
    /** DOOM's linedef flags: impassable to everyone, and impassable to monsters only. */
    static final int ML_BLOCKING = 1;
    static final int ML_BLOCKMONSTERS = 2;

    /** The WAD-sized tables plus the game state sized by them, with room for each block's header. */
    static final int WAD_ARENA_BYTES = 2 * (2 * MAX_VERTICES + 4 * MAX_LINES + 4 * MAX_SEGS + 6 * MAX_NODES
            + 3 * MAX_SUBSECTORS + 3 * MAX_SECTORS + 4 * MAX_DOORS + (3 + Monsters.STRIDE) * MAX_MONSTERS
            + 3 * MAX_ITEMS + 2 * MAX_ROUTE + 2 * LINE_WORDS + 2 * SECTOR_WORDS) + MAX_ITEMS + 44 * 16
            + Lifts.ARENA_BYTES + 4 * MAX_ROUTE + 2 * 16;

    /**
     * What {@link RoutePlanner} keeps for planning: open ceilings, per subsector a distance, parent, entry point,
     * flag and BSP parent, each node's parent, and four polygon buffers, plus each block's header.
     */
    static final int PLAN_ARENA_BYTES = 2 * MAX_SECTORS + 13 * MAX_SUBSECTORS + 2 * MAX_NODES
            + 16 * LeafPolygon.MAX_CORNERS + 4 * MAX_ROUTE + 14 * 16;

    /** Whether the maps come from the WAD rather than the built-in fallback. */
    static boolean fromWad;
    /** The WAD's episodes: bit {@code e} set when EeM1 is there. */
    static int episodesInWad;
    /** The map being played (EeMm) and DOOM's skill, 1 (I'm Too Young to Die) to 5 (Nightmare!). */
    static int episode = 1;
    static int map = 1;
    static int skill = 1;

    static int vertices;
    static int lines;
    static int segs;
    static int nodes;
    static int subsectors;
    static int sectors;
    static int doors;
    static int monsters;
    static int items;
    static int secrets;
    static int secretsFound;
    static int routeLength;

    static int startX;
    static int startY;
    static int startAngle;
    static int loopStart;
    static int exitX;
    static int exitY;
    /** The exit switch's line, or -1 when the map has none (the built-in map): what {@link RoutePlanner} aims for. */
    static int exitLine;

    static short[] vertexX;
    static short[] vertexY;
    static short[] lineV1;
    static short[] lineV2;
    static short[] lineFront;
    static short[] lineBack;
    static short[] segV1;
    static short[] segV2;
    static short[] segLine;
    static short[] segSide;
    static short[] nodeX;
    static short[] nodeY;
    static short[] nodeDx;
    static short[] nodeDy;
    static short[] nodeRight;
    static short[] nodeLeft;
    static short[] subsectorFirst;
    static short[] subsectorCount;
    static short[] subsectorSector;
    static short[] sectorFloor;
    static short[] sectorCeiling;
    static short[] doorSector;
    static short[] doorTop;
    static short[] doorX;
    static short[] doorY;
    static short[] monsterX;
    static short[] monsterY;
    static short[] monsterKind;
    static short[] itemX;
    static short[] itemY;
    static short[] itemKind;
    static short[] routeX;
    static short[] routeY;
    /** The route as planned when the map started: a restart after a death walks it again without re-planning. */
    private static short[] plannedX;
    private static short[] plannedY;
    private static int plannedLength;
    private static int plannedLoop;
    /** One bit per line: set when the WAD marks it impassable ({@link #ML_BLOCKING}), like most windows. */
    static short[] lineBlocking;
    /** One bit per line: set when it is impassable to monsters only ({@link #ML_BLOCKMONSTERS}). */
    static short[] lineBlocksMonsters;
    /** One bit per sector for DOOM secret sectors, and one for secrets entered during this attempt. */
    static short[] sectorSecrets;
    static short[] foundSecrets;

    /** Live ceiling heights (doors move them), each monster's state, and which items were picked up. */
    static short[] ceilings;
    static short[] monsterStates;
    static byte[] taken;

    private World() {
    }

    /**
     * Prepares the maps, once at startup: with a card, a WAD holding at least one episode and room in the arena for
     * the largest map, its route planning and the renderer, the WAD-sized tables are allocated and the maps are then
     * read one at a time by {@link #loadMap}; otherwise the built-in map is copied in and played.
     */
    static void load() {
        fromWad = false;
        boolean card = SdCard.begin();
        boolean room = card && Memory.arenaCapacityBytes() - Memory.arenaUsedBytes()
                >= WAD_ARENA_BYTES + PLAN_ARENA_BYTES + RENDER_ARENA_BYTES;
        episodesInWad = room ? WadLevel.episodes() : 0;
        if (episodesInWad != 0) {
            allocateForWad();
            RoutePlanner.reserve();
            fromWad = true;
        } else {
            allocateForBuiltIn();
            copyBuiltIn();
        }
        report(card, room);
    }

    /**
     * Reads map {@link #episode}/{@link #map} from the WAD at {@link #skill}; {@code false} when the map is missing
     * or does not fit (the tables then hold nothing playable). Its route is planned by {@link #planRoute} each time
     * the map starts.
     */
    static boolean loadMap() {
        if (!WadLevel.load(episode, map)) {
            Serial.print("DOOM: cannot load map ");
            printMapName();
            Serial.println("");
            return false;
        }
        Serial.print("DOOM: ");
        printMapName();
        Serial.print(" loaded, arena ");
        printArena();
        return true;
    }

    /**
     * Plans the CPU's route from the map's start to its exit afresh, so neither the previous map's route nor one
     * re-planned mid-map before a death carries over. When no walk reaches the exit, the route leads as close to it
     * as the marine can walk; with no route at all it is just the start, where the CPU stands and fights. The built-in map keeps its own patrol.
     */
    static void planRoute() {
        if (!fromWad) {
            return;
        }
        Loading.beginPlan();
        boolean planned = RoutePlanner.plan();
        Loading.endPlan();
        if (!planned && !RoutePlanner.shortOfExit) {
            routeX[0] = (short) startX;
            routeY[0] = (short) startY;
            routeLength = 1;
            loopStart = 0;
        }
        Serial.print("DOOM: ");
        printMapName();
        Serial.print(planned ? " route planned, waypoints="
                : RoutePlanner.shortOfExit ? " exit unreachable, route to nearest spot, waypoints="
                : " no route to the exit, waypoints=");
        Serial.println(routeLength);
        for (int i = 0; i < routeLength; i++) {
            plannedX[i] = routeX[i];
            plannedY[i] = routeY[i];
        }
        plannedLength = routeLength;
        plannedLoop = loopStart;
    }

    /**
     * Puts back the route planned when the map started, for a restart after a death: the CPU may have re-planned
     * part of it since, and planning the whole map again would hold the restart for seconds on the board.
     */
    static void restoreRoute() {
        if (!fromWad) {
            return;
        }
        for (int i = 0; i < plannedLength; i++) {
            routeX[i] = plannedX[i];
            routeY[i] = plannedY[i];
        }
        routeLength = plannedLength;
        loopStart = plannedLoop;
    }

    private static void printArena() {
        Serial.print(Memory.arenaUsedBytes());
        Serial.print("/");
        Serial.println(Memory.arenaCapacityBytes());
    }

    private static void printMapName() {
        Serial.print("E");
        Serial.print(episode);
        Serial.print("M");
        Serial.print(map);
    }

    /** Says over the serial port where the maps come from and why, for {@code juno:monitor}. */
    private static void report(boolean card, boolean room) {
        if (fromWad) {
            Serial.println("DOOM: maps from DOOM1.WAD, loaded one at a time");
        } else if (!card) {
            Serial.println("DOOM: no SD card, playing the built-in map");
        } else if (!room) {
            Serial.println("DOOM: arena too small for the WAD (build with -Djuno.Xmx=92k), playing the built-in map");
        } else {
            Serial.println("DOOM: no readable DOOM1.WAD on the card, playing the built-in map");
        }
        Serial.print("DOOM: arena ");
        printArena();
    }

    /** The built-in map alone, without touching the card: what the JVM tests play. */
    static void loadBuiltIn() {
        fromWad = false;
        episodesInWad = 0;
        episode = 1;
        map = 1;
        skill = 1;
        allocateForBuiltIn();
        copyBuiltIn();
    }

    static void allocateForWad() {
        short[] vx = new short[MAX_VERTICES];
        short[] vy = new short[MAX_VERTICES];
        short[] l1 = new short[MAX_LINES];
        short[] l2 = new short[MAX_LINES];
        short[] lf = new short[MAX_LINES];
        short[] lb = new short[MAX_LINES];
        short[] s1 = new short[MAX_SEGS];
        short[] s2 = new short[MAX_SEGS];
        short[] sl = new short[MAX_SEGS];
        short[] ss = new short[MAX_SEGS];
        short[] nx = new short[MAX_NODES];
        short[] ny = new short[MAX_NODES];
        short[] ndx = new short[MAX_NODES];
        short[] ndy = new short[MAX_NODES];
        short[] nr = new short[MAX_NODES];
        short[] nl = new short[MAX_NODES];
        short[] bf = new short[MAX_SUBSECTORS];
        short[] bc = new short[MAX_SUBSECTORS];
        short[] bs = new short[MAX_SUBSECTORS];
        short[] fl = new short[MAX_SECTORS];
        short[] ce = new short[MAX_SECTORS];
        short[] ds = new short[MAX_DOORS];
        short[] dt = new short[MAX_DOORS];
        short[] dx = new short[MAX_DOORS];
        short[] dy = new short[MAX_DOORS];
        short[] mx = new short[MAX_MONSTERS];
        short[] my = new short[MAX_MONSTERS];
        short[] mk = new short[MAX_MONSTERS];
        short[] ix = new short[MAX_ITEMS];
        short[] iy = new short[MAX_ITEMS];
        short[] ik = new short[MAX_ITEMS];
        short[] rx = new short[MAX_ROUTE];
        short[] keptX = new short[MAX_ROUTE];
        short[] keptY = new short[MAX_ROUTE];
        plannedX = keptX;
        plannedY = keptY;
        short[] ry = new short[MAX_ROUTE];
        short[] live = new short[MAX_SECTORS];
        short[] states = new short[MAX_MONSTERS * Monsters.STRIDE];
        byte[] picked = new byte[MAX_ITEMS];
        lineBlocking = new short[LINE_WORDS];
        lineBlocksMonsters = new short[LINE_WORDS];
        sectorSecrets = new short[SECTOR_WORDS];
        foundSecrets = new short[SECTOR_WORDS];
        Lifts.allocateForWad();
        publish(vx, vy, l1, l2, lf, lb, s1, s2, sl, ss);
        publishTree(nx, ny, ndx, ndy, nr, nl, bf, bc, bs);
        publishThings(fl, ce, ds, dt, dx, dy, mx, my, mk);
        publishRest(ix, iy, ik, rx, ry, live, states, picked);
    }

    static void allocateForBuiltIn() {
        short[] vx = new short[Level.VERTICES];
        short[] vy = new short[Level.VERTICES];
        short[] l1 = new short[Level.LINES];
        short[] l2 = new short[Level.LINES];
        short[] lf = new short[Level.LINES];
        short[] lb = new short[Level.LINES];
        short[] s1 = new short[Level.SEGS];
        short[] s2 = new short[Level.SEGS];
        short[] sl = new short[Level.SEGS];
        short[] ss = new short[Level.SEGS];
        short[] nx = new short[Level.NODES];
        short[] ny = new short[Level.NODES];
        short[] ndx = new short[Level.NODES];
        short[] ndy = new short[Level.NODES];
        short[] nr = new short[Level.NODES];
        short[] nl = new short[Level.NODES];
        short[] bf = new short[Level.SUBSECTORS];
        short[] bc = new short[Level.SUBSECTORS];
        short[] bs = new short[Level.SUBSECTORS];
        short[] fl = new short[Level.SECTORS];
        short[] ce = new short[Level.SECTORS];
        short[] ds = new short[Level.DOORS];
        short[] dt = new short[Level.DOORS];
        short[] dx = new short[Level.DOORS];
        short[] dy = new short[Level.DOORS];
        short[] mx = new short[Level.MONSTERS];
        short[] my = new short[Level.MONSTERS];
        short[] mk = new short[Level.MONSTERS];
        short[] ix = new short[Level.ITEMS];
        short[] iy = new short[Level.ITEMS];
        short[] ik = new short[Level.ITEMS];
        short[] rx = new short[Level.ROUTE_POINTS];
        short[] ry = new short[Level.ROUTE_POINTS];
        short[] live = new short[Level.SECTORS];
        short[] states = new short[Level.MONSTERS * Monsters.STRIDE];
        byte[] picked = new byte[Level.ITEMS];
        // The built-in map marks no line impassable: its bitsets stay clear.
        lineBlocking = new short[(Level.LINES + 15) / 16];
        lineBlocksMonsters = new short[(Level.LINES + 15) / 16];
        sectorSecrets = new short[(Level.SECTORS + 15) / 16];
        foundSecrets = new short[(Level.SECTORS + 15) / 16];
        Lifts.allocateForBuiltIn();
        publish(vx, vy, l1, l2, lf, lb, s1, s2, sl, ss);
        publishTree(nx, ny, ndx, ndy, nr, nl, bf, bc, bs);
        publishThings(fl, ce, ds, dt, dx, dy, mx, my, mk);
        publishRest(ix, iy, ik, rx, ry, live, states, picked);
    }

    /** Records a WAD linedef's flags in the line bitsets. */
    static void setLineFlags(int line, int flags) {
        int bit = 1 << (line & 15);
        if ((flags & ML_BLOCKING) != 0) {
            lineBlocking[line >> 4] = (short) (lineBlocking[line >> 4] | bit);
        }
        if ((flags & ML_BLOCKMONSTERS) != 0) {
            lineBlocksMonsters[line >> 4] = (short) (lineBlocksMonsters[line >> 4] | bit);
        }
    }

    /** Whether nothing may walk across {@code line}, or, for a monster, whether it is closed to monsters too. */
    static boolean isImpassable(int line, boolean monster) {
        int bit = 1 << (line & 15);
        return (lineBlocking[line >> 4] & bit) != 0 || monster && (lineBlocksMonsters[line >> 4] & bit) != 0;
    }

    /** Clears map-authored secrets before a WAD map is read, or for the built-in map which has none. */
    static void clearSectorSecrets() {
        secrets = 0;
        int words = (sectors + 15) / 16;
        for (int word = 0; word < words; word++) {
            sectorSecrets[word] = 0;
        }
    }

    /** Marks a WAD sector special 9 as one of the map's secrets. */
    static void markSecretSector(int sector) {
        int word = sector >> 4;
        int bit = 1 << (sector & 15);
        if ((sectorSecrets[word] & bit) == 0) {
            sectorSecrets[word] = (short) (sectorSecrets[word] | bit);
            secrets = secrets + 1;
        }
    }

    /** Starts a fresh map attempt with no secrets credited yet. */
    static void resetFoundSecrets() {
        secretsFound = 0;
        int words = (sectors + 15) / 16;
        for (int word = 0; word < words; word++) {
            foundSecrets[word] = 0;
        }
    }

    /** Credits a secret sector the first time the marine enters it during this attempt. */
    static void visitSector(int sector) {
        int word = sector >> 4;
        int bit = 1 << (sector & 15);
        if ((sectorSecrets[word] & bit) != 0 && (foundSecrets[word] & bit) == 0) {
            foundSecrets[word] = (short) (foundSecrets[word] | bit);
            secretsFound = secretsFound + 1;
        }
    }

    private static void publish(short[] vx, short[] vy, short[] l1, short[] l2, short[] lf, short[] lb,
                                short[] s1, short[] s2, short[] sl, short[] ss) {
        vertexX = vx;
        vertexY = vy;
        lineV1 = l1;
        lineV2 = l2;
        lineFront = lf;
        lineBack = lb;
        segV1 = s1;
        segV2 = s2;
        segLine = sl;
        segSide = ss;
    }

    private static void publishTree(short[] nx, short[] ny, short[] ndx, short[] ndy, short[] nr, short[] nl,
                                    short[] bf, short[] bc, short[] bs) {
        nodeX = nx;
        nodeY = ny;
        nodeDx = ndx;
        nodeDy = ndy;
        nodeRight = nr;
        nodeLeft = nl;
        subsectorFirst = bf;
        subsectorCount = bc;
        subsectorSector = bs;
    }

    private static void publishThings(short[] fl, short[] ce, short[] ds, short[] dt, short[] dx, short[] dy,
                                      short[] mx, short[] my, short[] mk) {
        sectorFloor = fl;
        sectorCeiling = ce;
        doorSector = ds;
        doorTop = dt;
        doorX = dx;
        doorY = dy;
        monsterX = mx;
        monsterY = my;
        monsterKind = mk;
    }

    private static void publishRest(short[] ix, short[] iy, short[] ik, short[] rx, short[] ry, short[] live,
                                    short[] states, byte[] picked) {
        itemX = ix;
        itemY = iy;
        itemKind = ik;
        routeX = rx;
        routeY = ry;
        ceilings = live;
        monsterStates = states;
        taken = picked;
    }

    /** Copies the built-in map out of flash into the RAM tables. */
    private static void copyBuiltIn() {
        copyGeometry();
        copyTree();
        copyThings();
        startX = Level.START_X;
        startY = Level.START_Y;
        startAngle = Level.START_ANGLE;
        loopStart = Level.LOOP_START;
        exitX = Level.EXIT_X;
        exitY = Level.EXIT_Y;
        exitLine = -1;
    }

    private static void copyGeometry() {
        vertices = LevelVertices.X.length;
        for (int i = 0; i < vertices; i++) {
            vertexX[i] = LevelVertices.X[i];
            vertexY[i] = LevelVertices.Y[i];
        }
        lines = LevelLines.V1.length;
        for (int i = 0; i < lines; i++) {
            lineV1[i] = LevelLines.V1[i];
            lineV2[i] = LevelLines.V2[i];
            lineFront[i] = LevelLines.FRONT[i];
            lineBack[i] = LevelLines.BACK[i];
        }
        segs = LevelSegs.V1.length;
        for (int i = 0; i < segs; i++) {
            segV1[i] = LevelSegs.V1[i];
            segV2[i] = LevelSegs.V2[i];
            segLine[i] = LevelSegs.LINE[i];
            segSide[i] = LevelSegs.SIDE[i];
        }
    }

    private static void copyTree() {
        nodes = LevelNodes.X.length;
        for (int i = 0; i < nodes; i++) {
            nodeX[i] = LevelNodes.X[i];
            nodeY[i] = LevelNodes.Y[i];
            nodeDx[i] = LevelNodes.DX[i];
            nodeDy[i] = LevelNodes.DY[i];
            nodeRight[i] = LevelNodes.RIGHT[i];
            nodeLeft[i] = LevelNodes.LEFT[i];
        }
        subsectors = LevelNodes.SUBSECTOR_FIRST.length;
        for (int i = 0; i < subsectors; i++) {
            subsectorFirst[i] = LevelNodes.SUBSECTOR_FIRST[i];
            subsectorCount[i] = LevelNodes.SUBSECTOR_COUNT[i];
            subsectorSector[i] = LevelNodes.SUBSECTOR_SECTOR[i];
        }
    }

    private static void copyThings() {
        sectors = Level.SECTOR_FLOOR.length;
        clearSectorSecrets();
        for (int i = 0; i < sectors; i++) {
            sectorFloor[i] = Level.SECTOR_FLOOR[i];
            sectorCeiling[i] = Level.SECTOR_CEILING[i];
        }
        doors = Level.DOOR_SECTOR.length;
        for (int i = 0; i < doors; i++) {
            doorSector[i] = Level.DOOR_SECTOR[i];
            doorTop[i] = Level.DOOR_TOP[i];
            doorX[i] = Level.DOOR_X[i];
            doorY[i] = Level.DOOR_Y[i];
        }
        monsters = Level.MONSTER_X.length;
        for (int i = 0; i < monsters; i++) {
            monsterX[i] = Level.MONSTER_X[i];
            monsterY[i] = Level.MONSTER_Y[i];
            monsterKind[i] = Level.MONSTER_KIND[i];
        }
        items = Level.ITEM_X.length;
        for (int i = 0; i < items; i++) {
            itemX[i] = Level.ITEM_X[i];
            itemY[i] = Level.ITEM_Y[i];
            itemKind[i] = Level.ITEM_KIND[i];
        }
        routeLength = Level.ROUTE_X.length;
        for (int i = 0; i < routeLength; i++) {
            routeX[i] = Level.ROUTE_X[i];
            routeY[i] = Level.ROUTE_Y[i];
        }
    }
}
