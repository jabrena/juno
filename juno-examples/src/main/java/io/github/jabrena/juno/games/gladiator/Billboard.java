package io.github.jabrena.juno.games.gladiator;

/**
 * The opponent in {@link View}: a {@link Figure} standing where the arena last saw it, always turned to the viewer
 * like DOOM's sprites, and kept only in the columns where no nearer pillar hides it. Facing the viewer it shows its
 * visor and holds its shield on the viewer's right; seen from behind, the other way round.
 */
final class Billboard {
    private static final float NEAREST = 16f;

    // A figure line after clipping to the view.
    private static float clipX0;
    private static float clipY0;
    private static float clipX1;
    private static float clipY1;

    private Billboard() {
    }

    /** A {@code kind} gladiator at ({@code ox}, {@code oy}) facing {@code oAngle}, {@code thrusting} its gladius. */
    static void draw(short[] lines, float ox, float oy, float oAngle, int kind, boolean thrusting) {
        float x = ox - View.viewX;
        float y = oy - View.viewY;
        float depth = x * View.cos + y * View.sin;
        if (depth < NEAREST) {
            return;
        }
        float scale = View.FOCAL / depth;
        float centerX = View.CX + (x * View.sin - y * View.cos) * scale;
        float baseY = View.CY + Coliseum.EYE * scale;
        boolean facing = (float) Math.cos(oAngle) * -x + (float) Math.sin(oAngle) * -y > 0f;
        float across = facing ? scale : -scale;
        int color = Figure.color(kind);
        strokes(lines, Figure.BODY, centerX, baseY, across, scale, depth, color);
        if (facing) {
            strokes(lines, Figure.VISOR, centerX, baseY, across, scale, depth, color);
        }
        strokes(lines, Figure.SHIELD, centerX, baseY, across, scale, depth, Figure.SHIELD_COLOR);
        strokes(lines, thrusting ? Figure.THRUST : Figure.SWORD, centerX, baseY, across, scale, depth, Figure.BLADE);
    }

    private static void strokes(short[] lines, int shape, float centerX, float baseY, float across, float up,
                                float depth, int color) {
        for (int i = Figure.START[shape]; i < Figure.START[shape + 1]; i += 4) {
            line(lines, centerX + Figure.SEGMENTS[i] * across, baseY - Figure.SEGMENTS[i + 1] * up,
                    centerX + Figure.SEGMENTS[i + 2] * across, baseY - Figure.SEGMENTS[i + 3] * up, depth, color);
        }
    }

    /** A figure line in the columns where no nearer pillar hides it. */
    private static void line(short[] lines, float x0, float y0, float x1, float y1, float depth, int color) {
        if (!clip(x0, y0, x1, y1)) {
            return;
        }
        if (clipX0 > clipX1) {
            float swapX = clipX0;
            float swapY = clipY0;
            clipX0 = clipX1;
            clipY0 = clipY1;
            clipX1 = swapX;
            clipY1 = swapY;
        }
        int first = Math.round(clipX0);
        int last = Math.round(clipX1);
        if (first == last) {
            View.vertical(lines, first, Math.round(Math.min(clipY0, clipY1)), Math.round(Math.max(clipY0, clipY1)),
                    depth, color);
            return;
        }
        float slopeY = (clipY1 - clipY0) / (clipX1 - clipX0);
        int runStart = -1;
        for (int x = first; x <= last + 1; x++) {
            boolean visible = x <= last && View.shows(x, Math.round(clipY0 + (x - clipX0) * slopeY), depth);
            if (visible && runStart < 0) {
                runStart = x;
            } else if (!visible && runStart >= 0) {
                ViewList.add(lines, runStart, Math.round(clipY0 + (runStart - clipX0) * slopeY),
                        x - 1, Math.round(clipY0 + (x - 1 - clipX0) * slopeY), color);
                runStart = -1;
            }
        }
    }

    /** Liang-Barsky clipping of a line to the view; the result is left in clipX0..clipY1. */
    private static boolean clip(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float enter = 0f;
        float leave = 1f;
        for (int edge = 0; edge < 4; edge++) {
            float p = edge == 0 ? -dx : edge == 1 ? dx : edge == 2 ? -dy : dy;
            float q = edge == 0 ? x0 : edge == 1 ? ViewList.WIDTH - 1 - x0
                    : edge == 2 ? y0 - View.TOP : View.BOTTOM - y0;
            if (p == 0) {
                if (q < 0) {
                    return false;
                }
            } else {
                float t = q / p;
                if (p < 0) {
                    enter = Math.max(enter, t);
                } else {
                    leave = Math.min(leave, t);
                }
            }
        }
        if (enter > leave) {
            return false;
        }
        clipX0 = x0 + dx * enter;
        clipY0 = y0 + dy * enter;
        clipX1 = x0 + dx * leave;
        clipY1 = y0 + dy * leave;
        return true;
    }
}
