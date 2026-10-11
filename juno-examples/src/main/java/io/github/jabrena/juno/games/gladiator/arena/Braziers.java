package io.github.jabrena.juno.games.gladiator.arena;

/**
 * The braziers round the pillars: each in turn puffs out a cloud of smoke that hides whoever stands in or behind it,
 * cover the AMBUSH gladiator hides in. The clouds follow the fight's clock, so the arena only has to say which are up
 * ({@link #mask()}); a gladiator's board takes that from its {@code WORLD} packets.
 */
public final class Braziers {
    /** Off for now, braziers and smoke alike, to measure what drawing them costs in frames a second. */
    public static final boolean ENABLED = false;
    public static final int COUNT = 4;
    /** Radius of a cloud of smoke. */
    public static final int SMOKE = 170;
    /** How long a cloud hangs in the air, out of every {@link #PERIOD_MILLIS} for each brazier. */
    public static final int SMOKE_MILLIS = 7000;
    public static final int PERIOD_MILLIS = 20_000;
    /** Two gladiators this close see each other even inside the smoke. */
    public static final int CLOSE = 120;

    private static final int BLOCK = 2 * Coliseum.SPACING + Coliseum.PILLAR_RADIUS;
    /** North, south, west and east of the middle. */
    public static final short[] X = {0, 0, (short) -(BLOCK + 500), (short) (BLOCK + 500)};
    public static final short[] Y = {(short) (BLOCK + 300), (short) -(BLOCK + 300), 0, 0};

    /** Bit {@code b} set while brazier {@code b}'s cloud is up. */
    private int mask;

    public int mask() {
        return mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    /** Which clouds are up {@code fightMillis} into a fight: the braziers take turns, five seconds apart. */
    public static int maskAt(int fightMillis) {
        int up = 0;
        for (int b = 0; b < COUNT; b++) {
            if ((fightMillis + (COUNT - b) * PERIOD_MILLIS / COUNT) % PERIOD_MILLIS < SMOKE_MILLIS) {
                up = up | 1 << b;
            }
        }
        return up;
    }

    public boolean smoky(int b) {
        return (mask & 1 << b) != 0;
    }

    /** Whether a cloud of smoke stands between the two points, unless they are close enough to see through it. */
    public boolean blocks(float x0, float y0, float x1, float y1) {
        if (mask == 0) {
            return false;
        }
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = dx * dx + dy * dy;
        if (length < CLOSE * CLOSE) {
            return false;
        }
        for (int b = 0; b < COUNT; b++) {
            if (smoky(b)) {
                float px = X[b] - x0;
                float py = Y[b] - y0;
                float t = Math.clamp((px * dx + py * dy) / length, 0f, 1f);
                float cx = px - dx * t;
                float cy = py - dy * t;
                if (cx * cx + cy * cy < SMOKE * SMOKE) {
                    return true;
                }
            }
        }
        return false;
    }
}
