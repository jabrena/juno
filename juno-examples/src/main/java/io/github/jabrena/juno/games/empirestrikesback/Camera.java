package io.github.jabrena.juno.games.empirestrikesback;

/**
 * Your craft's view: it looks along +z, world z is the distance ahead of it, and points are
 * perspective-projected onto the screen ({@code x' = cx + x·f/z}), with 3D lines clipped at the near
 * plane. The craft drifts towards where the crosshair points: steering and aiming are one.
 */
final class Camera {
    static final int CENTER_X = 160;
    static final int CENTER_Y = 130;
    static final int FOCAL = 160;
    static final int NEAR = 40;
    static final int FAR = 2600;
    private static final int CAMERA_LIMIT_X = 260;
    private static final int CAMERA_LOW = -70;
    private static final int CAMERA_HIGH = 150;
    private static final int CAMERA_STEP = 12;

    static int camX;
    static int camY;

    private Camera() {
    }

    static void center() {
        camX = 0;
        camY = 0;
    }

    /** Your craft drifts towards where the crosshair points: steering and aiming are one. */
    static void steer() {
        int targetX = headingX(Controls.crossX);
        int targetY = headingY(Controls.crossY);
        camX = camX + clamp(targetX - camX, -CAMERA_STEP, CAMERA_STEP);
        camY = camY + clamp(targetY - camY, -CAMERA_STEP, CAMERA_STEP);
    }

    /** Where the craft heads, across, with the crosshair at screen column {@code x}. */
    static int headingX(int x) {
        return clamp((x - CENTER_X) * CAMERA_LIMIT_X / 150, -CAMERA_LIMIT_X, CAMERA_LIMIT_X);
    }

    /** Where the craft heads, in height, with the crosshair at screen row {@code y}. */
    static int headingY(int y) {
        return clamp((CENTER_Y - y) * CAMERA_HIGH / 100, CAMERA_LOW, CAMERA_HIGH);
    }

    static int projectX(int x, int z) {
        return clamp(CENTER_X + (x - camX) * FOCAL / z, -20000, 20000);
    }

    static int projectY(int y, int z) {
        return clamp(CENTER_Y - (y - camY) * FOCAL / z, -20000, 20000);
    }

    /** Pixels spanned by {@code size} world units at depth {@code z}. */
    static int scale(int size, int z) {
        return size * FOCAL / z;
    }

    /** A line of a flat object whose points all lie at depth {@code z}, around its projection (px, py). */
    static void part(short[] lines, int px, int py, int z, int dx0, int dy0, int dx1, int dy1, int color) {
        DisplayList.addLine(lines, px + dx0 * FOCAL / z, py - dy0 * FOCAL / z, px + dx1 * FOCAL / z,
                py - dy1 * FOCAL / z, color);
    }

    /** A 3D line, clipped at the near plane and projected. */
    static void line3(short[] lines, int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        if (z0 < NEAR && z1 < NEAR) {
            return;
        }
        if (z0 < NEAR) {
            x0 = x0 + (x1 - x0) * (NEAR - z0) / (z1 - z0);
            y0 = y0 + (y1 - y0) * (NEAR - z0) / (z1 - z0);
            z0 = NEAR;
        } else if (z1 < NEAR) {
            x1 = x1 + (x0 - x1) * (NEAR - z1) / (z0 - z1);
            y1 = y1 + (y0 - y1) * (NEAR - z1) / (z0 - z1);
            z1 = NEAR;
        }
        DisplayList.addLine(lines, projectX(x0, z0), projectY(y0, z0), projectX(x1, z1), projectY(y1, z1), color);
    }

    /** The visible edges of an axis-aligned box: its front face, back face and the four sides. */
    static void box(short[] lines, int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        line3(lines, x0, y0, z0, x1, y0, z0, color);
        line3(lines, x1, y0, z0, x1, y1, z0, color);
        line3(lines, x1, y1, z0, x0, y1, z0, color);
        line3(lines, x0, y1, z0, x0, y0, z0, color);
        line3(lines, x0, y1, z0, x0, y1, z1, color);
        line3(lines, x1, y1, z0, x1, y1, z1, color);
        line3(lines, x0, y1, z1, x1, y1, z1, color);
        line3(lines, x0, y0, z0, x0, y0, z1, color);
        line3(lines, x1, y0, z0, x1, y0, z1, color);
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
