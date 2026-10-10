package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * The map the engine plays, held in RAM: E1M1 read from {@code DOOM1.WAD} on the SD card by {@link WadLevel}, or,
 * as the fallback, a copy of the built-in map in flash ({@link Level}, {@link LevelVertices}, {@link LevelLines},
 * {@link LevelSegs}, {@link LevelNodes}). The tables mean exactly what the flash ones do; each count says how many
 * entries are in use, since arrays are allocated once with a fixed capacity.
 *
 * <p>A map read from the WAD gets its CPU route from {@link RoutePlanner}; the built-in map, which has no exit,
 * keeps its own patrol route. The WAD's capacities fit E1M1 and take about {@link #WAD_ARENA_BYTES} of arena, more than the runtime's
 * default 8 KB, so build with {@code -Djuno.Xmx=26k}. With a smaller arena, no card or no WAD, the built-in map is
 * played instead, sized by its own counts.
 */
final class World {
    static final int MAX_VERTICES = 480;
    static final int MAX_LINES = 480;
    static final int MAX_SEGS = 736;
    static final int MAX_NODES = 240;
    static final int MAX_SUBSECTORS = 240;
    static final int MAX_SECTORS = 88;
    static final int MAX_DOORS = 8;
    static final int MAX_MONSTERS = 32;
    static final int MAX_ITEMS = 48;
    static final int MAX_ROUTE = 32;

    /** The WAD-sized tables plus the game state sized by them, with room for each block's header. */
    static final int WAD_ARENA_BYTES = 2 * (2 * MAX_VERTICES + 4 * MAX_LINES + 4 * MAX_SEGS + 6 * MAX_NODES
            + 3 * MAX_SUBSECTORS + 3 * MAX_SECTORS + 4 * MAX_DOORS + (3 + Monsters.STRIDE) * MAX_MONSTERS
            + 3 * MAX_ITEMS + 2 * MAX_ROUTE) + MAX_ITEMS + 40 * 16;

    /**
     * What {@link RoutePlanner} borrows while it plans: open ceilings, per subsector a distance, parent, entry point,
     * flag and BSP parent, each node's parent, and four polygon buffers, plus each block's header.
     */
    static final int PLAN_ARENA_BYTES = 2 * MAX_SECTORS + 13 * MAX_SUBSECTORS + 2 * MAX_NODES
            + 16 * LeafPolygon.MAX_CORNERS + 12 * 16;

    /** Whether the map came from the WAD (E1M1) rather than the built-in fallback. */
    static boolean fromWad;

    static int vertices;
    static int lines;
    static int segs;
    static int nodes;
    static int subsectors;
    static int sectors;
    static int doors;
    static int monsters;
    static int items;
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

    /** Live ceiling heights (doors move them), each monster's state, and which items were picked up. */
    static short[] ceilings;
    static short[] monsterStates;
    static byte[] taken;

    private World() {
    }

    /**
     * Loads E1M1 from the SD card when the card, the WAD and the arena room are all there, otherwise the built-in
     * map. Called once, before the first level starts.
     */
    static void load() {
        fromWad = false;
        boolean card = SdCard.begin();
        boolean room = card && Memory.arenaCapacityBytes() - Memory.arenaUsedBytes() >= WAD_ARENA_BYTES;
        if (room) {
            allocateForWad();
            fromWad = WadLevel.load();
        }
        boolean planned = fromWad && Memory.arenaCapacityBytes() - Memory.arenaUsedBytes() >= PLAN_ARENA_BYTES
                && RoutePlanner.plan();
        if (!fromWad) {
            // A WAD that failed half way leaves the WAD-sized tables, which hold the built-in map just as well.
            if (!room) {
                allocateForBuiltIn();
            }
            copyBuiltIn();
        }
        report(card, room, planned);
    }

    /** Says over the serial port which map was loaded and why, for {@code juno:monitor}. */
    private static void report(boolean card, boolean room, boolean planned) {
        if (fromWad) {
            Serial.println("DOOM: E1M1 loaded from DOOM1.WAD");
        } else if (!card) {
            Serial.println("DOOM: no SD card, playing the built-in map");
        } else if (!room) {
            Serial.println("DOOM: arena too small for the WAD (build with -Djuno.Xmx=26k), playing the built-in map");
        } else {
            Serial.println("DOOM: no readable DOOM1.WAD on the card, playing the built-in map");
        }
        Serial.print(planned ? "DOOM: planned route=" : "DOOM: route=");
        Serial.print(routeLength);
        Serial.print(" arena ");
        Serial.print(Memory.arenaUsedBytes());
        Serial.print("/");
        Serial.println(Memory.arenaCapacityBytes());
    }

    /** The built-in map alone, without touching the card: what the JVM tests play. */
    static void loadBuiltIn() {
        fromWad = false;
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
        short[] ry = new short[MAX_ROUTE];
        short[] live = new short[MAX_SECTORS];
        short[] states = new short[MAX_MONSTERS * Monsters.STRIDE];
        byte[] picked = new byte[MAX_ITEMS];
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
        publish(vx, vy, l1, l2, lf, lb, s1, s2, sl, ss);
        publishTree(nx, ny, ndx, ndy, nr, nl, bf, bc, bs);
        publishThings(fl, ce, ds, dt, dx, dy, mx, my, mk);
        publishRest(ix, iy, ik, rx, ry, live, states, picked);
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
