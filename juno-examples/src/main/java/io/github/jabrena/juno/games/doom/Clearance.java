package io.github.jabrena.juno.games.doom;

/** How much room the marine has around a point: the distance to the nearest wall they could not walk through. */
final class Clearance {
    /** The nearest wall point the last {@link #room} found: what {@link Player} slides along. */
    static float wallX;
    static float wallY;

    private Clearance() {
    }

    /**
     * Distance from ({@code px}, {@code py}) to the nearest wall: a one-sided line, a line the map marks impassable,
     * or an opening too low to stand in (a closed door). A ledge is not a wall here: as in DOOM, the marine's body
     * may hang over its edge, so it can step down off it and walk on. Lines farther than {@code reach} are ignored,
     * and {@code reach} is returned when none is closer.
     */
    static float room(float px, float py, float reach, short[] ceilings) {
        float nearest = reach;
        wallX = px;
        wallY = py;
        for (int line = 0; line < World.lines; line++) {
            float ax = World.vertexX[World.lineV1[line]];
            float ay = World.vertexY[World.lineV1[line]];
            float bx = World.vertexX[World.lineV2[line]];
            float by = World.vertexY[World.lineV2[line]];
            boolean near = Math.max(ax, bx) >= px - reach && Math.min(ax, bx) <= px + reach
                    && Math.max(ay, by) >= py - reach && Math.min(ay, by) <= py + reach;
            if (near && isWall(line, ceilings)) {
                float distance = distanceToSegment(px, py, ax, ay, bx, by);
                if (distance < nearest) {
                    nearest = distance;
                    wallX = nearX + px;
                    wallY = nearY + py;
                }
            }
        }
        return nearest;
    }

    /** Whether {@code line} is a wall: one-sided, marked impassable, or with no room to stand in its opening. */
    private static boolean isWall(int line, short[] ceilings) {
        int back = World.lineBack[line];
        if (back < 0 || World.isImpassable(line, false)) {
            return true;
        }
        int front = World.lineFront[line];
        int floor = Math.max(World.sectorFloor[front], World.sectorFloor[back]);
        int ceiling = Math.min(ceilings[front], ceilings[back]);
        return ceiling - floor < Player.HEADROOM;
    }

    /** Where {@link #pushOut} moved its point. */
    static float pushedX;
    static float pushedY;

    /**
     * Moves ({@code px}, {@code py}) straight away from the wall nearest it until {@code radius} from it, into
     * {@link #pushedX}/{@link #pushedY}; {@code false} when it already has that room (or sits on the wall itself).
     */
    static boolean pushOut(float px, float py, float radius, short[] ceilings) {
        float room = room(px, py, radius, ceilings);
        if (room >= radius || room < 0.01f) {
            return false;
        }
        pushedX = wallX + (px - wallX) * radius / room;
        pushedY = wallY + (py - wallY) * radius / room;
        return true;
    }

    /**
     * Where a body of {@code radius} at ({@code x}, {@code y}) ends up trying to move to ({@code toX}, {@code toY})
     * past the wall in the way, into {@link #pushedX}/{@link #pushedY}: pushed out from that wall (a little more
     * than {@code radius}, so rounding does not leave it short), or, when that would send it back the way it came
     * (walking head-on at a corner), a step to the side of the move with more room, to go round the corner.
     */
    static boolean slide(float x, float y, float toX, float toY, float radius, short[] ceilings) {
        if (!pushOut(toX, toY, radius + 0.5f, ceilings)) {
            return false;
        }
        float moveX = toX - x;
        float moveY = toY - y;
        if ((pushedX - x) * moveX + (pushedY - y) * moveY > 0) {
            return true;
        }
        float leftRoom = room(x - moveY, y + moveX, 2 * radius, ceilings);
        float rightRoom = room(x + moveY, y - moveX, 2 * radius, ceilings);
        float side = leftRoom >= rightRoom ? 1 : -1;
        pushedX = x - side * moveY;
        pushedY = y + side * moveX;
        return true;
    }

    /** Distance from ({@code px}, {@code py}) to linedef {@code line}. */
    static float distanceToLine(float px, float py, int line) {
        return distanceToSegment(px, py, World.vertexX[World.lineV1[line]], World.vertexY[World.lineV1[line]],
                World.vertexX[World.lineV2[line]], World.vertexY[World.lineV2[line]]);
    }

    private static float nearX;
    private static float nearY;

    /** Distance to the segment a&#8594;b; leaves the offset to its nearest point in {@link #nearX}/{@link #nearY}. */
    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        nearX = ax + t * dx - px;
        nearY = ay + t * dy - py;
        return (float) Math.sqrt(nearX * nearX + nearY * nearY);
    }
}
