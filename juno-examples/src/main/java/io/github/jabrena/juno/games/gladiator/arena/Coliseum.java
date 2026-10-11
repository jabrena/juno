package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.api.Random;

/**
 * The arena's map: an elliptical wall around a sand floor with {@link #PILLAR_COUNT} round pillars standing at random,
 * seen from above like a Roman amphitheatre. Units are about a centimetre: the floor is 32 m by 20.2 m, in the 87 by
 * 55 proportions of the Colosseum's arena in Rome, and a gladiator's eye is 1.6 m above it. The ARENA board, or the
 * Solo duel, builds it ({@link #build}) and sends it once to every gladiator ({@link GladiatorProtocol#writeMap}),
 * whose board keeps only that copy ({@link GladiatorProtocol#readMap}). Its {@link Braziers} smoke over it.
 */
public final class Coliseum {
    public static final int MAX_PILLARS = 25;
    /** Corners of the polygon the elliptical wall is drawn as, in the first-person view. */
    public static final int WALL_SEGMENTS = 32;
    /** A gladiator's radius. */
    public static final int BODY = 22;
    /** Radius of every pillar: a pillar is two gladiators across. */
    public static final int PILLAR_RADIUS = 2 * BODY;
    /** How many pillars {@link #build} stands on the sand. */
    public static final int PILLAR_COUNT = 5;
    /** The least centre-to-centre distance of two pillars: the gap between their edges is five gladiators wide. */
    public static final int SPACING = 2 * PILLAR_RADIUS + 10 * BODY;
    /** Pillars stand within this far of the middle, so they come between the gates. */
    public static final int PLACE_X = 1000;
    public static final int PLACE_Y = 630;
    /** How far a pillar keeps from a gate and from the lion's gate. */
    public static final int CLEAR = 300;
    private static final int ATTEMPTS = 500;
    public static final int EYE = 160;
    /**
     * Where the left gladiator ({@code slot} 0) starts, facing right; the right one starts mirrored. Both stand near
     * the oval's ends, well clear of the pillars, which stand within {@link #PLACE_X} of the middle.
     */
    public static final int SPAWN_X = 1440;
    public static final int UNIT = 16384;
    /** Unit circle at the wall's corners, times {@link #UNIT}, so a ring of any size is two multiplications. */
    public static final short[] CORNER_COS = unitCircle(true);
    public static final short[] CORNER_SIN = unitCircle(false);

    private int radiusX;
    private int radiusY;
    private int wallHeight;
    private int pillarRadius;
    private int pillarHeight;
    private int pillars;
    private final short[] pillarX = new short[MAX_PILLARS];
    private final short[] pillarY = new short[MAX_PILLARS];
    private final Braziers braziers = new Braziers();

    private static short[] unitCircle(boolean cosine) {
        short[] corners = new short[WALL_SEGMENTS];
        for (int i = 0; i < WALL_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / WALL_SEGMENTS;
            corners[i] = (short) Math.round((cosine ? Math.cos(angle) : Math.sin(angle)) * UNIT);
        }
        return corners;
    }

    /**
     * A new coliseum: an oval about one and a half times as wide as it is deep, with {@link #PILLAR_COUNT} pillars at
     * random places ({@link #fits}), so every bout is fought round a different arena.
     */
    public void build() {
        define(1600, 1012, 300, PILLAR_RADIUS, 420);
        for (int attempt = 0; attempt < ATTEMPTS && pillars < PILLAR_COUNT; attempt++) {
            int x = Random.nextInt(2 * PLACE_X + 1) - PLACE_X;
            int y = Random.nextInt(2 * PLACE_Y + 1) - PLACE_Y;
            if (fits(x, y)) {
                addPillar(x, y);
            }
        }
    }

    /** An empty arena of these sizes, with no pillars yet and no smoke. */
    public void define(int radiusX, int radiusY, int wallHeight, int pillarRadius, int pillarHeight) {
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.wallHeight = wallHeight;
        this.pillarRadius = pillarRadius;
        this.pillarHeight = pillarHeight;
        pillars = 0;
        braziers.setMask(0);
    }

    /**
     * Whether a pillar may stand at ({@code x}, {@code y}): far enough inside the wall for two gladiators to pass it,
     * {@link #SPACING} from every other pillar, and {@link #CLEAR} of both gates and the lion's gate.
     */
    public boolean fits(int x, int y) {
        float ex = x / (float) (radiusX - PILLAR_RADIUS - 4 * BODY);
        float ey = y / (float) (radiusY - PILLAR_RADIUS - 4 * BODY);
        if (ex * ex + ey * ey > 1f || pillarAt(x, y, SPACING - PILLAR_RADIUS) >= 0) {
            return false;
        }
        boolean clear = Math.hypot(x - Lion.GATE_X, y - Lion.gateY(this)) >= CLEAR;
        for (int side = 0; side < 2; side++) {
            clear = clear && Math.hypot(x - spawnX(side), y) >= CLEAR;
        }
        return clear;
    }

    public void addPillar(int x, int y) {
        if (pillars < MAX_PILLARS) {
            pillarX[pillars] = (short) x;
            pillarY[pillars] = (short) y;
            pillars = pillars + 1;
        }
    }

    public int radiusX() {
        return radiusX;
    }

    public int radiusY() {
        return radiusY;
    }

    public int wallHeight() {
        return wallHeight;
    }

    public int pillarRadius() {
        return pillarRadius;
    }

    public int pillarHeight() {
        return pillarHeight;
    }

    public int pillars() {
        return pillars;
    }

    public int pillarX(int p) {
        return pillarX[p];
    }

    public int pillarY(int p) {
        return pillarY[p];
    }

    public Braziers braziers() {
        return braziers;
    }

    public static int spawnX(int slot) {
        return slot == 0 ? -SPAWN_X : SPAWN_X;
    }

    public static float spawnAngle(int slot) {
        return slot == 0 ? 0f : (float) Math.PI;
    }

    /** Whether a gladiator standing at ({@code x}, {@code y}) is inside the wall and clear of every pillar. */
    public boolean walkable(float x, float y) {
        float ex = x / (radiusX - BODY);
        float ey = y / (radiusY - BODY);
        if (ex * ex + ey * ey > 1f) {
            return false;
        }
        return pillarAt(x, y, BODY) < 0;
    }

    /** Whether the point is inside the wall, for a javelin. */
    public boolean inside(float x, float y) {
        float ex = x / radiusX;
        float ey = y / radiusY;
        return ex * ex + ey * ey < 1f;
    }

    /** The pillar that a circle of {@code radius} at ({@code x}, {@code y}) overlaps, or -1. */
    public int pillarAt(float x, float y, int radius) {
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

    /**
     * Whether no pillar and no cloud of smoke ({@link Braziers}) stands between the two points; the wall never does,
     * since the floor is convex.
     */
    public boolean clearSight(float x0, float y0, float x1, float y1) {
        return blocking(x0, y0, x1, y1, 0) < 0 && !braziers.blocks(x0, y0, x1, y1);
    }

    /**
     * The nearest pillar, going from the first point to the second, that a circle of {@code radius} sliding along
     * that line would touch, or -1 when the way is clear.
     */
    public int blocking(float x0, float y0, float x1, float y1, int radius) {
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
