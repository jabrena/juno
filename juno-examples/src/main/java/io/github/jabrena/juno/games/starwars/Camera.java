package io.github.jabrena.juno.games.starwars;

/**
 * The cockpit's view: it looks along +z, world z is the distance ahead of it, and points are
 * perspective-projected onto the screen ({@code x' = cx + x·f/z}), with 3D lines clipped at the near
 * plane. In the trench the X-wing drifts towards where the crosshair points.
 */
final class Camera {
    static final int CENTER_X = 160;
    static final int CENTER_Y = 130;
    static final int FOCAL = 160;
    static final int NEAR = 40;
    static final int FAR = 2400;
    private static final int CAMERA_LIMIT = 110;
    private static final int CAMERA_STEP = 10;

    static int camX;
    static int camY;

    private Camera() {
    }

    static void center() {
        camX = 0;
        camY = 0;
    }

    /** In the trench the X-wing drifts towards where the crosshair points. */
    static void steer() {
        int targetX = clamp((Controls.crossX - CENTER_X) * CAMERA_LIMIT / 150, -CAMERA_LIMIT, CAMERA_LIMIT);
        int targetY = clamp((CENTER_Y - Controls.crossY) * CAMERA_LIMIT / 100, -CAMERA_LIMIT, CAMERA_LIMIT);
        camX = camX + clamp(targetX - camX, -CAMERA_STEP, CAMERA_STEP);
        camY = camY + clamp(targetY - camY, -CAMERA_STEP, CAMERA_STEP);
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

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
