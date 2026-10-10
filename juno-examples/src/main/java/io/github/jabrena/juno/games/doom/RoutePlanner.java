package io.github.jabrena.juno.games.doom;

/**
 * Plans the CPU marine's route through any map with an exit switch, so {@link Autopilot} needs no hand-made walk.
 *
 * <p>The BSP tree already cuts the map into convex subsectors, and a marine can walk in a straight line between any
 * two points of one. So the planner runs Dijkstra over the subsectors, from the one holding the player start to the
 * one just in front of the exit switch. Each subsector is entered at one point; from there the marine may leave
 * through any stretch of its border ({@link LeafPolygon}) that it can walk to and across in a straight line, as
 * {@link Player#blocked} judges it with the marine's own rules: no one-sided wall, a step up of at most 24 units,
 * enough headroom. Every door counts as
 * open, since the marine opens them on the way. The entry points along the shortest walk are then straightened
 * wherever a straight walk is clear, and become {@link World}'s route.
 */
final class RoutePlanner {
    private static final int UNSEEN = Integer.MAX_VALUE;
    private static final short START = -1;
    private static final short UNREACHED = -2;
    /** How far in front of the exit switch the route ends: well within the marine's reach of it. */
    private static final float EXIT_STANDOFF = 32f;
    /** How far to either side of a border its crossing is tested. */
    private static final float NUDGE = 2f;
    /** Crossing points are tried along each border at about this spacing: less than the narrowest doorway. */
    private static final float SPACING = 16f;
    /** How far the route keeps from wall ends and corners, which the swaying marine would otherwise catch. */
    private static final float CLEARANCE = 12f;
    /** How many points ahead straightening looks for a clear straight walk. */
    private static final int LOOKAHEAD = 16;

    private static short[] open;
    private static int[] distance;
    private static short[] parent;
    private static short[] entryX;
    private static short[] entryY;
    private static byte[] settled;
    private static short[] nodeParent;
    private static short[] leafParent;
    private static float[] cornerX;
    private static float[] cornerY;
    private static float[] spareX;
    private static float[] spareY;

    private RoutePlanner() {
    }

    /** Replaces the route with a walk from the start to the exit; {@code false} when the map has none. */
    static boolean plan() {
        if (World.exitLine < 0) {
            return false;
        }
        borrow();
        int line = World.exitLine;
        float dx = World.vertexX[World.lineV2[line]] - World.vertexX[World.lineV1[line]];
        float dy = World.vertexY[World.lineV2[line]] - World.vertexY[World.lineV1[line]];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        // A line's front side, where its switch is used from, lies to the right of its direction.
        float goalX = World.exitX + dy / length * EXIT_STANDOFF;
        float goalY = World.exitY - dx / length * EXIT_STANDOFF;
        int goalLeaf = search(goalX, goalY);
        boolean planned = goalLeaf >= 0 && straighten(goalLeaf, goalX, goalY);
        giveBack();
        return planned;
    }

    /** The planner's working tables, held only while it plans so the collector can reclaim them afterwards. */
    private static void borrow() {
        short[] ceilings = new short[World.MAX_SECTORS];
        int[] distances = new int[World.MAX_SUBSECTORS];
        short[] parents = new short[World.MAX_SUBSECTORS];
        short[] xs = new short[World.MAX_SUBSECTORS];
        short[] ys = new short[World.MAX_SUBSECTORS];
        byte[] flags = new byte[World.MAX_SUBSECTORS];
        short[] nodeParents = new short[World.MAX_NODES];
        short[] leafParents = new short[World.MAX_SUBSECTORS];
        float[] polygonX = new float[LeafPolygon.MAX_CORNERS];
        float[] polygonY = new float[LeafPolygon.MAX_CORNERS];
        float[] scratchX = new float[LeafPolygon.MAX_CORNERS];
        float[] scratchY = new float[LeafPolygon.MAX_CORNERS];
        open = ceilings;
        distance = distances;
        parent = parents;
        entryX = xs;
        entryY = ys;
        settled = flags;
        nodeParent = nodeParents;
        leafParent = leafParents;
        cornerX = polygonX;
        cornerY = polygonY;
        spareX = scratchX;
        spareY = scratchY;
        for (int sector = 0; sector < World.sectors; sector++) {
            open[sector] = World.sectorCeiling[sector];
        }
        for (int door = 0; door < World.doors; door++) {
            open[World.doorSector[door]] = World.doorTop[door];
        }
        LeafPolygon.link(nodeParent, leafParent);
    }

    private static void giveBack() {
        open = null;
        distance = null;
        parent = null;
        entryX = null;
        entryY = null;
        settled = null;
        nodeParent = null;
        leafParent = null;
        cornerX = null;
        cornerY = null;
        spareX = null;
        spareY = null;
    }

    /** Dijkstra from the start's subsector; returns the goal's subsector once reached, or -1. */
    private static int search(float goalX, float goalY) {
        for (int leaf = 0; leaf < World.subsectors; leaf++) {
            distance[leaf] = UNSEEN;
            parent[leaf] = UNREACHED;
            settled[leaf] = 0;
        }
        int goalLeaf = Player.subsectorAt(goalX, goalY);
        int start = Player.subsectorAt(World.startX, World.startY);
        distance[start] = 0;
        parent[start] = START;
        entryX[start] = (short) World.startX;
        entryY[start] = (short) World.startY;
        int leaf = closest();
        while (leaf >= 0 && !(leaf == goalLeaf
                && !Player.blocked(entryX[leaf], entryY[leaf], goalX, goalY, open))) {
            settled[leaf] = 1;
            leaveThrough(leaf);
            leaf = closest();
        }
        return leaf;
    }

    /** Offers every subsector reachable across {@code leaf}'s border, entering it where the border is crossed. */
    private static void leaveThrough(int leaf) {
        int corners = LeafPolygon.build(leaf, nodeParent, leafParent, cornerX, cornerY, spareX, spareY);
        float centerX = 0;
        float centerY = 0;
        for (int i = 0; i < corners; i++) {
            centerX = centerX + cornerX[i] / corners;
            centerY = centerY + cornerY[i] / corners;
        }
        for (int i = 0; i < corners; i++) {
            int j = i + 1 == corners ? 0 : i + 1;
            float dx = cornerX[j] - cornerX[i];
            float dy = cornerY[j] - cornerY[i];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1f) {
                continue;
            }
            // Straight out across the border: the edge's normal, turned away from the polygon's middle.
            float outX = dy / length * NUDGE;
            float outY = -dx / length * NUDGE;
            if ((cornerX[i] - centerX) * outX + (cornerY[i] - centerY) * outY < 0) {
                outX = -outX;
                outY = -outY;
            }
            float alongX = dx / length * CLEARANCE;
            float alongY = dy / length * CLEARANCE;
            int crossings = Math.max(1, (int) (length / SPACING));
            for (int k = 0; k < crossings; k++) {
                float t = (k + 0.5f) / crossings;
                tryCrossing(leaf, cornerX[i] + t * dx + outX, cornerY[i] + t * dy + outY, alongX, alongY);
            }
        }
    }

    /**
     * Offers the subsector holding ({@code x}, {@code y}), just across the border, if the marine can walk there with
     * room to spare: the points {@link #CLEARANCE} to either side along the border must be reachable too.
     */
    private static void tryCrossing(int leaf, float x, float y, float alongX, float alongY) {
        int next = Player.subsectorAt(x, y);
        // The whole walk from where the marine entered, not just the border: a subsector's polygon can reach into
        // the void where no seg of its own bounds it, and only the walls tell walkable floor from void.
        if (next == leaf || settled[next] != 0 || Player.blocked(entryX[leaf], entryY[leaf], x, y, open)
                || Player.blocked(entryX[leaf], entryY[leaf], x + alongX, y + alongY, open)
                || Player.blocked(entryX[leaf], entryY[leaf], x - alongX, y - alongY, open)) {
            return;
        }
        int cost = distance[leaf] + length(entryX[leaf], entryY[leaf], x, y);
        if (cost < distance[next]) {
            distance[next] = cost;
            parent[next] = (short) leaf;
            entryX[next] = (short) Math.round(x);
            entryY[next] = (short) Math.round(y);
        }
    }

    private static int closest() {
        int closest = -1;
        for (int leaf = 0; leaf < World.subsectors; leaf++) {
            if (settled[leaf] == 0 && distance[leaf] != UNSEEN
                    && (closest < 0 || distance[leaf] < distance[closest])) {
                closest = leaf;
            }
        }
        return closest;
    }

    /**
     * Turns the chain of entry points, start to goal, into the route, keeping only the points where a straight walk
     * would hit something. Returns {@code false} if the route outgrows its table.
     */
    private static boolean straighten(int goalLeaf, float goalX, float goalY) {
        // Reuse the distance table for the walking order: subsectors from the start to the goal.
        int steps = 0;
        for (int leaf = goalLeaf; leaf != START; leaf = parent[leaf]) {
            steps = steps + 1;
        }
        int index = steps;
        for (int leaf = goalLeaf; leaf != START; leaf = parent[leaf]) {
            index = index - 1;
            distance[index] = leaf;
        }
        int last = steps;
        World.routeX[0] = (short) World.startX;
        World.routeY[0] = (short) World.startY;
        int count = 1;
        int at = 0;
        while (at < last) {
            int next = at + 1;
            for (int ahead = Math.min(last, at + LOOKAHEAD); ahead > at + 1; ahead--) {
                if (clear(pointX(at, last, goalX), pointY(at, last, goalY), pointX(ahead, last, goalX),
                        pointY(ahead, last, goalY))) {
                    next = ahead;
                    break;
                }
            }
            if (count == World.MAX_ROUTE) {
                return false;
            }
            World.routeX[count] = (short) Math.round(pointX(next, last, goalX));
            World.routeY[count] = (short) Math.round(pointY(next, last, goalY));
            count = count + 1;
            at = next;
        }
        World.routeLength = count;
        World.loopStart = count - 1;
        return true;
    }

    /** Whether a straight walk is clear with {@link #CLEARANCE} to spare on both sides. */
    private static boolean clear(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1f) {
            return true;
        }
        float sideX = -dy / length * CLEARANCE;
        float sideY = dx / length * CLEARANCE;
        return !Player.blocked(ax, ay, bx, by, open)
                && !Player.blocked(ax + sideX, ay + sideY, bx + sideX, by + sideY, open)
                && !Player.blocked(ax - sideX, ay - sideY, bx - sideX, by - sideY, open);
    }

    /** Point {@code k} of the walk: the entry point of its {@code k}-th subsector, then the goal at {@code last}. */
    private static float pointX(int k, int last, float goalX) {
        return k == last ? goalX : entryX[distance[k]];
    }

    private static float pointY(int k, int last, float goalY) {
        return k == last ? goalY : entryY[distance[k]];
    }

    private static int length(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }
}
