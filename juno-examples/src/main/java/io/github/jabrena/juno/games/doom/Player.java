package io.github.jabrena.juno.games.doom;

/**
 * Where the marine stands and looks, how they walk, and the doors they open. Movement follows DOOM's
 * rules closely enough for a walk-through: a step up of at most 24 units, at least 56 units of
 * headroom, and no crossing a one-sided wall.
 */
final class Player {
    static final float EYE_HEIGHT = 41f;
    static final int MAX_STEP = 24;
    static final int HEADROOM = 56;
    private static final int EXIT_REACH = 56;
    private static final int DOOR_REACH = 200;
    private static final int DOOR_FORGET = 520;
    private static final int DOOR_SPEED = 6;

    static final int FULL_HEALTH = 100;

    static float x;
    static float y;
    static float angle;
    static float eye;
    static int sector;
    static int health;
    static int armor;
    /** 1 for green armor (absorbs a third of each hit), 2 for blue (absorbs half), 0 for none. */
    static int armorClass;
    /** Frames left of the red flash that shows the marine was just hit. */
    static int hurt;

    private Player() {
    }

    /** Starts at the map's player 1 start with every door closed, as the WAD defines them. */
    static void spawn(short[] ceilings) {
        x = World.startX;
        y = World.startY;
        angle = (float) Math.toRadians(World.startAngle);
        for (int s = 0; s < World.sectors; s++) {
            ceilings[s] = World.sectorCeiling[s];
        }
        sector = sectorAt(x, y);
        eye = World.sectorFloor[sector] + EYE_HEIGHT;
        health = FULL_HEALTH;
        armor = 0;
        armorClass = 0;
        hurt = 0;
    }

    /**
     * Takes a hit at "I'm too young to die" strength (DOOM halves every hit on its easiest skill), with
     * the share the armor absorbs taken from the armor instead.
     */
    static void damage(int amount) {
        int taken = (amount + 1) / 2;
        int absorbed = armorClass == 2 ? taken / 2 : armorClass == 1 ? taken / 3 : 0;
        absorbed = Math.min(absorbed, armor);
        armor = armor - absorbed;
        if (armor == 0) {
            armorClass = 0;
        }
        health = Math.max(0, health - taken + absorbed);
        hurt = 6;
    }

    static void turn(float radians) {
        angle = angle + radians;
        if (angle > (float) Math.PI) {
            angle = angle - 2 * (float) Math.PI;
        } else if (angle < (float) -Math.PI) {
            angle = angle + 2 * (float) Math.PI;
        }
    }

    /** Walks {@code distance} units along the view direction, unless a wall or a step is in the way. */
    static boolean walk(float distance, short[] ceilings) {
        float toX = x + distance * (float) Math.cos(angle);
        float toY = y + distance * (float) Math.sin(angle);
        if (blocked(x, y, toX, toY, ceilings)) {
            return false;
        }
        x = toX;
        y = toY;
        sector = sectorAt(x, y);
        return true;
    }

    /** Sidesteps {@code distance} units to the right of the view (left when negative), unless blocked. */
    static boolean strafe(float distance, short[] ceilings) {
        float toX = x + distance * (float) Math.sin(angle);
        float toY = y - distance * (float) Math.cos(angle);
        if (blocked(x, y, toX, toY, ceilings)) {
            return false;
        }
        x = toX;
        y = toY;
        sector = sectorAt(x, y);
        return true;
    }

    /** Eases the eye toward the floor below: quick up a step, a little slower falling off a ledge. */
    static void settle() {
        float target = World.sectorFloor[sector] + EYE_HEIGHT;
        float delta = target - eye;
        if (delta > 0) {
            eye = eye + Math.min(delta, 8f);
        } else {
            eye = eye + Math.max(delta, -12f);
        }
    }

    /** Whether the marine stands at the map's exit switch (a map without one parks it far away). */
    static boolean atExit() {
        float dx = World.exitX - x;
        float dy = World.exitY - y;
        return dx * dx + dy * dy < EXIT_REACH * EXIT_REACH;
    }

    /** Opens the doors the marine walks up to and closes the ones left far behind. */
    static void operateDoors(short[] ceilings) {
        for (int d = 0; d < World.doors; d++) {
            int door = World.doorSector[d];
            float dx = World.doorX[d] - x;
            float dy = World.doorY[d] - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            int ceiling = ceilings[door];
            if (distance < DOOR_REACH && ceiling < World.doorTop[d]) {
                ceilings[door] = (short) Math.min(World.doorTop[d], ceiling + DOOR_SPEED);
            } else if (distance > DOOR_FORGET && ceiling > World.sectorFloor[door]) {
                ceilings[door] = (short) Math.max(World.sectorFloor[door], ceiling - DOOR_SPEED);
            }
        }
    }

    /** The sector containing ({@code px}, {@code py}), found by walking the BSP tree to a subsector. */
    static int sectorAt(float px, float py) {
        return World.subsectorSector[subsectorAt(px, py)];
    }

    /** The BSP leaf (a convex subsector) containing ({@code px}, {@code py}). */
    static int subsectorAt(float px, float py) {
        int count = World.nodes;
        int child = count == 0 ? 0x8000 : count - 1;
        while ((child & 0x8000) == 0) {
            child = (onBackSide(child, px, py) ? World.nodeLeft[child] : World.nodeRight[child]) & 0xFFFF;
        }
        return child & 0x7FFF;
    }

    /** DOOM's R_PointOnSide: whether the point lies on the node's back (left) side. */
    static boolean onBackSide(int node, float px, float py) {
        float dx = px - World.nodeX[node];
        float dy = py - World.nodeY[node];
        return dy * World.nodeDx[node] >= dx * World.nodeDy[node];
    }

    /** Whether walking from one point to another crosses a wall, a step over 24 units or a too-low opening. */
    static boolean blocked(float fromX, float fromY, float toX, float toY, short[] ceilings) {
        for (int line = 0; line < World.lines; line++) {
            int v1 = World.lineV1[line];
            int v2 = World.lineV2[line];
            float ax = World.vertexX[v1];
            float ay = World.vertexY[v1];
            float bx = World.vertexX[v2];
            float by = World.vertexY[v2];
            float from = side(ax, ay, bx, by, fromX, fromY);
            float to = side(ax, ay, bx, by, toX, toY);
            if (from * to >= 0) {
                continue;
            }
            float start = side(fromX, fromY, toX, toY, ax, ay);
            float end = side(fromX, fromY, toX, toY, bx, by);
            if (start * end >= 0) {
                continue;
            }
            int back = World.lineBack[line];
            if (back < 0) {
                return true;
            }
            int front = World.lineFront[line];
            int target = from < 0 ? back : front;
            int origin = from < 0 ? front : back;
            int floor = Math.max(World.sectorFloor[front], World.sectorFloor[back]);
            int ceiling = Math.min(ceilings[front], ceilings[back]);
            if (World.sectorFloor[target] - World.sectorFloor[origin] > MAX_STEP || ceiling - floor < HEADROOM) {
                return true;
            }
        }
        return false;
    }

    /**
     * DOOM-style line of sight between two eyes: blocked by one-sided walls, closed doors and any
     * opening the sight line passes above or below.
     */
    static boolean canSee(float ax, float ay, float az, float bx, float by, float bz, short[] ceilings) {
        for (int line = 0; line < World.lines; line++) {
            int back = World.lineBack[line];
            int v1 = World.lineV1[line];
            int v2 = World.lineV2[line];
            float lx = World.vertexX[v1];
            float ly = World.vertexY[v1];
            float mx = World.vertexX[v2];
            float my = World.vertexY[v2];
            float from = side(lx, ly, mx, my, ax, ay);
            float to = side(lx, ly, mx, my, bx, by);
            if (from * to >= 0) {
                continue;
            }
            float start = side(ax, ay, bx, by, lx, ly);
            float end = side(ax, ay, bx, by, mx, my);
            if (start * end >= 0) {
                continue;
            }
            if (back < 0) {
                return false;
            }
            int front = World.lineFront[line];
            float z = az + (bz - az) * (from / (from - to));
            int floor = Math.max(World.sectorFloor[front], World.sectorFloor[back]);
            int ceiling = Math.min(ceilings[front], ceilings[back]);
            if (z <= floor || z >= ceiling) {
                return false;
            }
        }
        return true;
    }

    /** Cross product sign: negative when ({@code px}, {@code py}) is right of the line a&#8594;b. */
    private static float side(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
