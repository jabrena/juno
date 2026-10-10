package io.github.jabrena.juno.games.doom;

/**
 * Whether the route planner's marine can reach a crossing, in one pass over the map's lines: on the board every pass
 * is the cost of planning, and a crossing needs four checks. Walks from the entry to the crossing and to {@code side}
 * either side of it, across the walk, must be clear, and the body must fit at the crossing, {@code radius} from any
 * wall. Each line is read once and tried only against the checks its bounding box can touch.
 */
final class Crossing {
    private Crossing() {
    }

    static boolean reachable(float ax, float ay, float bx, float by, float side, float radius, short[] ceilings) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float sideX = length < 1f ? 0 : -dy / length * side;
        float sideY = length < 1f ? 0 : dx / length * side;
        float lowX = Math.min(ax, Math.min(bx - Math.abs(sideX), bx - radius));
        float highX = Math.max(ax, Math.max(bx + Math.abs(sideX), bx + radius));
        float lowY = Math.min(ay, Math.min(by - Math.abs(sideY), by - radius));
        float highY = Math.max(ay, Math.max(by + Math.abs(sideY), by + radius));
        for (int line = 0; line < World.lines; line++) {
            float x1 = World.vertexX[World.lineV1[line]];
            float y1 = World.vertexY[World.lineV1[line]];
            float x2 = World.vertexX[World.lineV2[line]];
            float y2 = World.vertexY[World.lineV2[line]];
            if (Math.max(x1, x2) < lowX || Math.min(x1, x2) > highX || Math.max(y1, y2) < lowY
                    || Math.min(y1, y2) > highY) {
                continue;
            }
            if (Player.stops(line, ax, ay, bx, by, ceilings, false)
                    || Player.stops(line, ax, ay, bx + sideX, by + sideY, ceilings, false)
                    || Player.stops(line, ax, ay, bx - sideX, by - sideY, ceilings, false)
                    || Clearance.isWall(line, ceilings) && Clearance.distanceToLine(bx, by, line) < radius) {
                return false;
            }
        }
        return true;
    }
}
