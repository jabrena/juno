package io.github.jabrena.juno.games.startrek;

/** Fixed point, the wrapping sector, compass bearings and the eight directions of a star. */
final class Geometry {
    static final int FIX = 16;
    /** The sector wraps around: its size in world units. */
    static final int SECTOR = 2048;
    static final int SECTOR_FIX = SECTOR * FIX;

    private Geometry() {
    }

    /** The shortest signed difference across the wrapping sector, in world units x FIX. */
    static int wrap(int delta) {
        int d = delta % SECTOR_FIX;
        if (d > SECTOR_FIX / 2) {
            d = d - SECTOR_FIX;
        } else if (d < -SECTOR_FIX / 2) {
            d = d + SECTOR_FIX;
        }
        return d;
    }

    static int wrapPosition(int position) {
        return ((position % SECTOR_FIX) + SECTOR_FIX) % SECTOR_FIX;
    }

    /** Compass bearing of (dx, dy) in degrees, clockwise from up (screen y points down). */
    static int bearing(int dx, int dy) {
        return normalize(Math.round((float) Math.toDegrees(Math.atan2(dx, -dy))));
    }

    /** The signed turn from one bearing to another, -180..180. */
    static int angleBetween(int from, int to) {
        int d = normalize(to - from);
        return d > 180 ? d - 360 : d;
    }

    static int normalize(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    static int dirX(int k) {
        int step = (k % 8 + 8) % 8;
        if (step == 0) {
            return 10;
        }
        if (step == 1 || step == 7) {
            return 7;
        }
        if (step == 3 || step == 5) {
            return -7;
        }
        if (step == 4) {
            return -10;
        }
        return 0;
    }

    static int dirY(int k) {
        return dirX(k + 6);
    }
}
