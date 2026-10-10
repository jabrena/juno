package io.github.jabrena.juno.games.doom;

/**
 * Where the marine stands and looks, and how they walk; {@link Doors} opens the doors they come to.
 * Movement follows DOOM's rules closely enough for a walk-through: a step up of at most 24 units, at least 56 units of
 * headroom, no crossing a one-sided wall or a line the map marks impassable (most windows), and a move
 * into a wall slides along it instead of stopping.
 */
final class Player {
    static final float EYE_HEIGHT = 41f;
    static final int MAX_STEP = 24;
    static final int HEADROOM = 56;
    /**
     * How close the marine's body, and so the camera, comes to a wall: enough that a wall in view always lies beyond
     * the renderer's near plane (4 units, at up to 45 degrees off the view), so the camera never sees past it. DOOM's
     * own 16 made doorways and corners too tight for the CPU's routes.
     */
    static final float RADIUS = 8f;
    private static final float USE_RANGE = 64f;

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
    /**
     * Set while the CPU dodges in a fight: a move that would start a closed door opening is refused, so backing off
     * or strafing never lets out what waits behind it.
     */
    static boolean keepDoorsShut;

    // The box around the path blocked() or canSee() tests: a line outside it cannot cross the path.
    private static float lowX;
    private static float lowY;
    private static float highX;
    private static float highY;

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
        World.resetFoundSecrets();
        World.visitSector(sector);
        eye = World.sectorFloor[sector] + EYE_HEIGHT;
        health = FULL_HEALTH;
        armor = 0;
        armorClass = 0;
        hurt = 0;
    }

    /**
     * Takes a hit, halved on "I'm Too Young to Die" (DOOM's easiest skill), with the share the armor absorbs taken
     * from the armor instead.
     */
    static void damage(int amount) {
        int taken = World.skill == 1 ? (amount + 1) / 2 : amount;
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

    /** Walks {@code distance} units along the view direction, sliding along a wall or step that is in the way. */
    static boolean walk(float distance, short[] ceilings) {
        return move(x + distance * (float) Math.cos(angle), y + distance * (float) Math.sin(angle), ceilings);
    }

    /** Sidesteps {@code distance} units to the right of the view (left when negative), sliding along walls. */
    static boolean strafe(float distance, short[] ceilings) {
        return move(x + distance * (float) Math.sin(angle), y - distance * (float) Math.cos(angle), ceilings);
    }

    /**
     * Moves toward ({@code toX}, {@code toY}). Like DOOM, a move that runs into a wall is not simply refused: the
     * marine slides along it, keeping whichever part of the move (east-west or north-south) is still free, the larger
     * one first. Returns whether the marine moved at all.
     */
    private static boolean move(float toX, float toY, short[] ceilings) {
        if (free(toX, toY, ceilings)) {
            return place(toX, toY);
        }
        boolean xFirst = Math.abs(toX - x) >= Math.abs(toY - y);
        float firstX = xFirst ? toX : x;
        float firstY = xFirst ? y : toY;
        if ((firstX != x || firstY != y) && free(firstX, firstY, ceilings)) {
            return place(firstX, firstY);
        }
        float secondX = xFirst ? x : toX;
        float secondY = xFirst ? toY : y;
        if ((secondX != x || secondY != y) && free(secondX, secondY, ceilings)) {
            return place(secondX, secondY);
        }
        return slideAlongWall(toX, toY, ceilings);
    }

    /**
     * Pushes the target out to {@link #RADIUS} from the wall nearest it: how the body rounds a corner or slides
     * along a wall at an angle, where the move as asked would press it into the wall.
     */
    private static boolean slideAlongWall(float toX, float toY, short[] ceilings) {
        return Clearance.slide(x, y, toX, toY, RADIUS, ceilings) && free(Clearance.pushedX, Clearance.pushedY, ceilings)
                && place(Clearance.pushedX, Clearance.pushedY);
    }

    /**
     * Whether the marine can step to ({@code toX}, {@code toY}): no wall in the way, and the body keeps
     * {@link #RADIUS} from every wall it cannot pass, or at least gets no closer to one. Without the radius the
     * point-sized camera slid along walls and pressed into corners, showing the view past them.
     */
    private static boolean free(float toX, float toY, short[] ceilings) {
        if (blocked(x, y, toX, toY, ceilings)) {
            return false;
        }
        if (keepDoorsShut && Doors.wouldOpen(toX, toY, ceilings) && !Doors.wouldOpen(x, y, ceilings)) {
            return false;
        }
        float room = Clearance.room(toX, toY, RADIUS, ceilings);
        return room >= RADIUS || room >= Clearance.room(x, y, RADIUS, ceilings);
    }

    private static boolean place(float toX, float toY) {
        x = toX;
        y = toY;
        sector = sectorAt(x, y);
        World.visitSector(sector);
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

    /** Whether the marine is within reach of the map's exit switch; never on a map without one (the built-in map). */
    static boolean atExit() {
        // DOOM's use range, measured to the switch's line: the body keeps the marine out of a switch's recess.
        return World.exitLine >= 0 && Clearance.distanceToLine(x, y, World.exitLine) < USE_RANGE;
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

    /**
     * Whether walking from one point to another crosses a wall, a line the map marks impassable (most windows), a
     * step over 24 units or a too-low opening.
     */
    static boolean blocked(float fromX, float fromY, float toX, float toY, short[] ceilings) {
        return blocked(fromX, fromY, toX, toY, ceilings, false);
    }

    /** {@link #blocked} for a monster, which the lines marked impassable to monsters stop as well. */
    static boolean blockedForMonster(float fromX, float fromY, float toX, float toY, short[] ceilings) {
        return blocked(fromX, fromY, toX, toY, ceilings, true);
    }

    private static boolean blocked(float fromX, float fromY, float toX, float toY, short[] ceilings, boolean monster) {
        box(fromX, fromY, toX, toY);
        for (int line = 0; line < World.lines; line++) {
            int v1 = World.lineV1[line];
            int v2 = World.lineV2[line];
            if (!outside(World.vertexX[v1], World.vertexY[v1], World.vertexX[v2], World.vertexY[v2])
                    && stops(line, fromX, fromY, toX, toY, ceilings, monster)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code line} stops a step from ({@code fromX}, {@code fromY}) to ({@code toX}, {@code toY}): the step
     * crosses it, and it is a wall, marked impassable, a step up of more than {@link #MAX_STEP} or too low an opening.
     */
    static boolean stops(int line, float fromX, float fromY, float toX, float toY, short[] ceilings, boolean monster) {
        float ax = World.vertexX[World.lineV1[line]];
        float ay = World.vertexY[World.lineV1[line]];
        float bx = World.vertexX[World.lineV2[line]];
        float by = World.vertexY[World.lineV2[line]];
        float from = side(ax, ay, bx, by, fromX, fromY);
        float to = side(ax, ay, bx, by, toX, toY);
        if (from * to >= 0) {
            return false;
        }
        // A path through a linedef endpoint still touches the wall. Treating zero as separate let the
        // point-sized camera slip exactly between two solid walls that share that endpoint.
        if (side(fromX, fromY, toX, toY, ax, ay) * side(fromX, fromY, toX, toY, bx, by) > 0) {
            return false;
        }
        int back = World.lineBack[line];
        if (back < 0 || World.isImpassable(line, monster)) {
            return true;
        }
        int front = World.lineFront[line];
        int target = from < 0 ? back : front;
        int origin = from < 0 ? front : back;
        int floor = Math.max(World.sectorFloor[front], World.sectorFloor[back]);
        int ceiling = Math.min(ceilings[front], ceilings[back]);
        boolean riding = Lifts.planning && Lifts.isLiftSector(origin);
        return World.sectorFloor[target] - World.sectorFloor[origin] > MAX_STEP && !riding
                || ceiling - floor < HEADROOM;
    }

    /**
     * DOOM-style line of sight between two eyes: blocked by one-sided walls, closed doors and any
     * opening the sight line passes above or below.
     */
    static boolean canSee(float ax, float ay, float az, float bx, float by, float bz, short[] ceilings) {
        box(ax, ay, bx, by);
        for (int line = 0; line < World.lines; line++) {
            int v1 = World.lineV1[line];
            int v2 = World.lineV2[line];
            float lx = World.vertexX[v1];
            float ly = World.vertexY[v1];
            float mx = World.vertexX[v2];
            float my = World.vertexY[v2];
            if (outside(lx, ly, mx, my)) {
                continue;
            }
            int back = World.lineBack[line];
            float from = side(lx, ly, mx, my, ax, ay);
            float to = side(lx, ly, mx, my, bx, by);
            if (from * to >= 0) {
                continue;
            }
            float start = side(ax, ay, bx, by, lx, ly);
            float end = side(ax, ay, bx, by, mx, my);
            if (start * end > 0) {
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

    private static void box(float ax, float ay, float bx, float by) {
        lowX = Math.min(ax, bx);
        lowY = Math.min(ay, by);
        highX = Math.max(ax, bx);
        highY = Math.max(ay, by);
    }

    /** Whether the line a&#8594;b lies wholly to one side of the path's box, so the two cannot cross. */
    private static boolean outside(float ax, float ay, float bx, float by) {
        return Math.max(ax, bx) < lowX || Math.min(ax, bx) > highX || Math.max(ay, by) < lowY
                || Math.min(ay, by) > highY;
    }

    /** Cross product sign: negative when ({@code px}, {@code py}) is right of the line a&#8594;b. */
    private static float side(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
