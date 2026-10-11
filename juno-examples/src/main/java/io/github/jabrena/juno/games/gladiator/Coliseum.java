package io.github.jabrena.juno.games.gladiator;

/**
 * The arena's map: an elliptical wall around a sand floor with a five-by-three grid of round pillars, seen from
 * above like a Roman amphitheatre. Units are about a centimetre: the floor is 12.3 m by 5.5 m and a gladiator's eye
 * is 1.6 m above it. The ARENA board builds it ({@link #build}) and sends it once to every gladiator
 * ({@link GladiatorProtocol#writeMap}), whose board keeps only that copy ({@link GladiatorProtocol#readMap}).
 */
final class Coliseum {
    static final int MAX_PILLARS = 16;
    /** Corners of the polygon the elliptical wall is drawn as, in the first-person view. */
    static final int WALL_SEGMENTS = 32;
    /** A gladiator's radius. */
    static final int BODY = 22;
    static final int EYE = 160;
    /** Where the left gladiator ({@code slot} 0) starts, facing right; the right one starts mirrored. */
    static final int SPAWN_X = 470;

    static int radiusX;
    static int radiusY;
    static int wallHeight;
    static int pillarRadius;
    static int pillarHeight;
    static int pillars;
    static final short[] pillarX = new short[MAX_PILLARS];
    static final short[] pillarY = new short[MAX_PILLARS];
    /** Unit circle at the wall's corners, times {@link #UNIT}, so a ring of any size is two multiplications. */
    static final short[] cornerCos = new short[WALL_SEGMENTS];
    static final short[] cornerSin = new short[WALL_SEGMENTS];
    static final int UNIT = 16384;

    private Coliseum() {
    }

    /** The coliseum of the aerial picture: three rows of five pillars in an oval twice as wide as it is deep. */
    static void build() {
        radiusX = 615;
        radiusY = 275;
        wallHeight = 300;
        pillarRadius = 32;
        pillarHeight = 420;
        pillars = 0;
        for (int row = -1; row <= 1; row++) {
            for (int column = -2; column <= 2; column++) {
                addPillar(column * 145, row * 125);
            }
        }
        corners();
    }

    static void addPillar(int x, int y) {
        if (pillars < MAX_PILLARS) {
            pillarX[pillars] = (short) x;
            pillarY[pillars] = (short) y;
            pillars = pillars + 1;
        }
    }

    /** Fills the wall's corner table; called once the radii are known. */
    static void corners() {
        for (int i = 0; i < WALL_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / WALL_SEGMENTS;
            cornerCos[i] = (short) Math.round(Math.cos(angle) * UNIT);
            cornerSin[i] = (short) Math.round(Math.sin(angle) * UNIT);
        }
    }

    static int spawnX(int slot) {
        return slot == 0 ? -SPAWN_X : SPAWN_X;
    }

    static float spawnAngle(int slot) {
        return slot == 0 ? 0f : (float) Math.PI;
    }

    /** Whether a gladiator standing at ({@code x}, {@code y}) is inside the wall and clear of every pillar. */
    static boolean walkable(float x, float y) {
        float ex = x / (radiusX - BODY);
        float ey = y / (radiusY - BODY);
        if (ex * ex + ey * ey > 1f) {
            return false;
        }
        return pillarAt(x, y, BODY) < 0;
    }

    /** The pillar that a circle of {@code radius} at ({@code x}, {@code y}) overlaps, or -1. */
    static int pillarAt(float x, float y, int radius) {
        float reach = pillarRadius + radius;
        for (int p = 0; p < pillars; p++) {
            float dx = x - pillarX[p];
            float dy = y - pillarY[p];
            if (dx * dx + dy * dy < reach * reach) {
                return p;
            }
        }
        return -1;
    }

    /** Whether no pillar stands between the two points; the wall never does, since the floor is convex. */
    static boolean clearSight(float x0, float y0, float x1, float y1) {
        return blocking(x0, y0, x1, y1, 0) < 0;
    }

    /**
     * The nearest pillar, going from the first point to the second, that a circle of {@code radius} sliding along
     * that line would touch, or -1 when the way is clear.
     */
    static int blocking(float x0, float y0, float x1, float y1, int radius) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = dx * dx + dy * dy;
        float reach = pillarRadius + radius;
        int nearest = -1;
        float nearestT = 2f;
        for (int p = 0; p < pillars; p++) {
            float px = pillarX[p] - x0;
            float py = pillarY[p] - y0;
            float t = length == 0f ? 0f : Math.clamp((px * dx + py * dy) / length, 0f, 1f);
            float cx = px - dx * t;
            float cy = py - dy * t;
            if (cx * cx + cy * cy < reach * reach && t < nearestT) {
                nearest = p;
                nearestT = t;
            }
        }
        return nearest;
    }
}
