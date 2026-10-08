package io.github.jabrena.juno.games.doom;

// An original two-room test map in the format LevelGenerator writes, so the game builds and runs
// without any WAD. Generate a real map over this file locally (see LevelGenerator) and never
// commit the result: it contains id Software's data.

/** Map TEST: player start, sector heights, doors, monsters, pickups and the autopilot route. */
final class Level {
    static final String NAME = "TEST";
    static final int START_X = 256;
    static final int START_Y = 256;
    static final int START_ANGLE = 0;
    static final int LOOP_START = 0;
    static final int MONSTERS = 3;
    static final int ITEMS = 2;

    static final short[] ROUTE_X = {256, 420, 700, 900, 900, 620, 420, 150, 110};
    static final short[] ROUTE_Y = {256, 256, 256, 400, 110, 200, 256, 420, 110};
    static final short[] SECTOR_FLOOR = {0, 24};
    static final short[] SECTOR_CEILING = {128, 160};
    static final short[] DOOR_SECTOR = {};
    static final short[] DOOR_TOP = {};
    static final short[] DOOR_X = {};
    static final short[] DOOR_Y = {};
    static final short[] MONSTER_X = {860, 930, 700};
    static final short[] MONSTER_Y = {320, 120, 440};
    static final short[] MONSTER_KIND = {0, 2, 1};
    static final short[] ITEM_X = {800, 150};
    static final short[] ITEM_Y = {200, 150};
    static final short[] ITEM_KIND = {2, 4};

    private Level() {
    }
}

/** Map vertices. */
final class LevelVertices {
    static final short[] X = {0, 0, 512, 512, 512, 512, 1024, 1024};
    static final short[] Y = {0, 512, 512, 320, 192, 0, 512, 0};

    private LevelVertices() {
    }
}

/** Linedefs: end vertices and the sectors on their front and back sides (-1 when one-sided). */
final class LevelLines {
    static final short[] V1 = {0, 1, 2, 3, 4, 5, 5, 3, 2, 6, 7};
    static final short[] V2 = {1, 2, 3, 4, 5, 0, 4, 2, 6, 7, 5};
    static final short[] FRONT = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1};
    static final short[] BACK = {-1, -1, -1, 1, -1, -1, -1, -1, -1, -1, -1};

    private LevelLines() {
    }
}

/** BSP segs: end vertices, their linedef, and which side of it they run along (0 front, 1 back). */
final class LevelSegs {
    static final short[] V1 = {0, 1, 2, 3, 4, 5, 5, 4, 3, 2, 6, 7};
    static final short[] V2 = {1, 2, 3, 4, 5, 0, 4, 3, 2, 6, 7, 5};
    static final short[] LINE = {0, 1, 2, 3, 4, 5, 6, 3, 7, 8, 9, 10};
    static final short[] SIDE = {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0};

    private LevelSegs() {
    }
}

/** BSP nodes (children with bit 15 set are subsectors) and subsectors (first seg, seg count, sector). */
final class LevelNodes {
    static final short[] X = {512};
    static final short[] Y = {0};
    static final short[] DX = {0};
    static final short[] DY = {512};
    static final short[] RIGHT = {-32767};
    static final short[] LEFT = {-32768};
    static final short[] SUBSECTOR_FIRST = {0, 6};
    static final short[] SUBSECTOR_COUNT = {6, 6};
    static final short[] SUBSECTOR_SECTOR = {0, 1};

    private LevelNodes() {
    }
}
