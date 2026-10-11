package io.github.jabrena.juno.games.gladiator;

/**
 * The gladiator's eyes on the screen: the coliseum in wireframe, the way DOOM draws its walls. The pillars are drawn
 * as their outlines, nearest first; then the wall and the stands round it ({@link Rings}); then the opponent
 * ({@link Billboard}), only when the arena says this gladiator's eyes see it.
 *
 * <p>Each column remembers the nearest pillar standing in it and that pillar's top row: anything farther is hidden
 * below that row, which is how a pillar hides the wall, the far pillars and an opponent behind it.
 */
final class View {
    static final float FOCAL = 160f;
    static final int CX = ViewList.WIDTH / 2;
    static final int CY = (ViewList.VIEW_TOP + ViewList.VIEW_BOTTOM) / 2;
    static final float NEAR = 4f;
    static final int TOP = ViewList.VIEW_TOP;
    static final int BOTTOM = ViewList.VIEW_BOTTOM - 1;
    static final int FAR = Short.MAX_VALUE;

    // One repeated byte each, so a pixel's two bus bytes are the same (see DOOM's Renderer).
    static final int WALL = 0xE6E6;
    static final int PILLAR = 0xE8E8;
    static final int PILLAR_FAR = 0xA0A0;
    static final int WOUND = 0xE0E0;

    /**
     * Per column: the depth of the nearest pillar standing in it, the row of its top, and the stands' horizon. Rows
     * are unsigned bytes, 0 to 200.
     */
    static final short[] coverDepth = new short[ViewList.WIDTH];
    static final byte[] coverTop = new byte[ViewList.WIDTH];
    static final byte[] horizon = new byte[ViewList.WIDTH];
    private static final byte[] order = new byte[Coliseum.MAX_PILLARS];
    private static final short[] orderDepth = new short[Coliseum.MAX_PILLARS];

    /** The eye this frame is seen from. */
    static float viewX;
    static float viewY;
    static float cos;
    static float sin;

    private View() {
    }

    /**
     * Builds the frame seen by {@code body} into the display list. The opponent, a {@code kind} gladiator at
     * ({@code ox}, {@code oy}) facing {@code oAngle}, is drawn only when {@code visible}; {@code thrusting} when it is
     * striking. {@code swing} and {@code hurt} are frames left of this gladiator's own swing and wound.
     */
    static void build(short[] lines, Body body, boolean visible, float ox, float oy, float oAngle, int kind,
                      boolean thrusting, int swing, int hurt) {
        viewX = body.x;
        viewY = body.y;
        cos = (float) Math.cos(body.angle);
        sin = (float) Math.sin(body.angle);
        for (int x = 0; x < ViewList.WIDTH; x++) {
            coverDepth[x] = (short) FAR;
            coverTop[x] = (byte) (BOTTOM + 1);
            horizon[x] = (byte) (BOTTOM + 1);
        }
        ViewList.begin();
        hands(lines, swing);
        ViewList.pin();
        pillars(lines);
        Rings.draw(lines);
        if (visible) {
            Billboard.draw(lines, ox, oy, oAngle, kind, thrusting);
        }
        if (hurt > 0) {
            frame(lines, 0);
            if (hurt > 3) {
                frame(lines, 3);
            }
        }
    }

    /** The screen row of {@code height} above the floor, at the depth whose {@code FOCAL / depth} is {@code scale}. */
    static int row(float height, float scale) {
        return Math.round(CY - (height - Coliseum.EYE) * scale);
    }

    /** Whether a point at {@code depth} drawn at ({@code column}, {@code y}) shows past the pillars. */
    static boolean shows(int column, int y, float depth) {
        return depth <= coverDepth[column] || y < (coverTop[column] & 0xFF);
    }

    /** An upright line in {@code column}, cut off where a nearer pillar hides it. */
    static void vertical(short[] lines, int column, int y0, int y1, float depth, int color) {
        if (column < 0 || column >= ViewList.WIDTH) {
            return;
        }
        int top = Math.max(y0, TOP);
        int bottom = Math.min(y1, BOTTOM);
        if (depth > coverDepth[column]) {
            bottom = Math.min(bottom, (coverTop[column] & 0xFF) - 1);
        }
        if (top <= bottom) {
            ViewList.add(lines, column, top, column, bottom, color);
        }
    }

    /** The gladius, at rest or mid-swing, and the shield's rim. */
    private static void hands(short[] lines, int swing) {
        int sword = swing > 0 ? 1 : 0;
        for (int i = Figure.HANDS_START[sword]; i < Figure.HANDS_START[sword + 1]; i += 4) {
            ViewList.add(lines, Figure.HANDS[i], Figure.HANDS[i + 1], Figure.HANDS[i + 2], Figure.HANDS[i + 3],
                    Figure.BLADE);
        }
        for (int i = Figure.HANDS_START[2]; i < Figure.HANDS_START[3]; i += 4) {
            ViewList.add(lines, Figure.HANDS[i], Figure.HANDS[i + 1], Figure.HANDS[i + 2], Figure.HANDS[i + 3],
                    Figure.SHIELD_COLOR);
        }
    }

    /** Every pillar in front, nearest first, each hiding what stands behind it. */
    private static void pillars(short[] lines) {
        int count = 0;
        for (int p = 0; p < Coliseum.pillars; p++) {
            float x = Coliseum.pillarX[p] - viewX;
            float y = Coliseum.pillarY[p] - viewY;
            float depth = x * cos + y * sin;
            if (depth < Coliseum.pillarRadius + NEAR) {
                continue;
            }
            float scale = FOCAL / depth;
            float center = CX + (x * sin - y * cos) * scale;
            float half = Coliseum.pillarRadius * scale;
            if (center + half < 0 || center - half > ViewList.WIDTH - 1) {
                continue;
            }
            // Insertion sort by depth: there are never more than sixteen.
            int at = count;
            while (at > 0 && orderDepth[at - 1] > depth) {
                order[at] = order[at - 1];
                orderDepth[at] = orderDepth[at - 1];
                at = at - 1;
            }
            order[at] = (byte) p;
            orderDepth[at] = (short) Math.min(FAR, Math.round(depth));
            count = count + 1;
        }
        for (int i = 0; i < count; i++) {
            pillar(lines, order[i]);
        }
    }

    private static void pillar(short[] lines, int p) {
        float x = Coliseum.pillarX[p] - viewX;
        float y = Coliseum.pillarY[p] - viewY;
        float depth = x * cos + y * sin;
        float scale = FOCAL / depth;
        float center = CX + (x * sin - y * cos) * scale;
        float half = Coliseum.pillarRadius * scale;
        int color = depth < 500f ? PILLAR : PILLAR_FAR;
        int top = row(Coliseum.pillarHeight, scale);
        int base = row(0, scale);
        vertical(lines, Math.round(center - half), top, base, depth, color);
        vertical(lines, Math.round(center + half), top, base, depth, color);
        horizontal(lines, center - half * 1.15f, center + half * 1.15f, top, depth, color);
        horizontal(lines, center - half, center + half, row(Coliseum.pillarHeight - 30, scale), depth, color);
        horizontal(lines, center - half * 1.1f, center + half * 1.1f, row(26, scale), depth, color);
        horizontal(lines, center - half * 1.1f, center + half * 1.1f, base, depth, color);
        int first = Math.max(0, (int) Math.ceil(center - half));
        int last = Math.min(ViewList.WIDTH - 1, (int) Math.floor(center + half));
        for (int column = first; column <= last; column++) {
            if (coverDepth[column] == FAR) {
                coverDepth[column] = (short) Math.min(FAR, Math.round(depth));
                coverTop[column] = (byte) Math.clamp(top, TOP - 1, BOTTOM + 1);
            }
        }
    }

    private static void horizontal(short[] lines, float x0, float x1, int y, float depth, int color) {
        if (y < TOP || y > BOTTOM) {
            return;
        }
        int first = Math.max(0, (int) Math.ceil(x0));
        int last = Math.min(ViewList.WIDTH - 1, (int) Math.floor(x1));
        int runStart = -1;
        for (int x = first; x <= last + 1; x++) {
            boolean visible = x <= last && shows(x, y, depth);
            if (visible && runStart < 0) {
                runStart = x;
            } else if (!visible && runStart >= 0) {
                ViewList.add(lines, runStart, y, x - 1, y, color);
                runStart = -1;
            }
        }
    }

    /** A red frame round the view, {@code inset} pixels in, for a fresh wound. */
    private static void frame(short[] lines, int inset) {
        int left = inset;
        int top = TOP + inset;
        int right = ViewList.WIDTH - 1 - inset;
        int bottom = BOTTOM - inset;
        ViewList.add(lines, left, top, right, top, WOUND);
        ViewList.add(lines, right, top, right, bottom, WOUND);
        ViewList.add(lines, right, bottom, left, bottom, WOUND);
        ViewList.add(lines, left, bottom, left, top, WOUND);
    }
}
