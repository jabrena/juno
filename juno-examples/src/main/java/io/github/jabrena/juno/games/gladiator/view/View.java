package io.github.jabrena.juno.games.gladiator.view;

import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;

/**
 * The gladiator's eyes on the screen: its {@link Coliseum} in wireframe, the way DOOM draws its walls, built into a
 * {@link ViewList} from what its {@link Report} says. The pillars are drawn as their outlines, nearest first; then the
 * opponent ({@link Billboard}), only when the arena says this gladiator's eyes see it; then the wall and the stands
 * round it ({@link Rings}) and what stands on the sand ({@link Props}).
 *
 * <p>Each column remembers the nearest pillar standing in it and that pillar's top row: anything farther is hidden
 * below that row, which is how a pillar hides the wall, the far pillars and an opponent behind it.
 */
public final class View {
    public static final float FOCAL = 160f;
    public static final int CX = ViewList.WIDTH / 2;
    public static final int CY = (ViewList.VIEW_TOP + ViewList.VIEW_BOTTOM) / 2;
    public static final float NEAR = 4f;
    public static final int TOP = ViewList.VIEW_TOP;
    public static final int BOTTOM = ViewList.VIEW_BOTTOM - 1;
    public static final int FAR = Short.MAX_VALUE;

    // One repeated byte each, so a pixel's two bus bytes are the same (see DOOM's Renderer).
    public static final int WALL = 0xE6E6;
    public static final int PILLAR = 0xE8E8;
    public static final int PILLAR_FAR = 0xA0A0;
    public static final int WOUND = 0xE0E0;
    public static final int BRONZE = 0xC3C3;
    /** Pillars farther than this are drawn without their mouldings. */
    private static final float SIMPLE_PILLAR = 700f;

    private final ViewList list;
    private final Coliseum coliseum;
    private final Report report;
    private final Billboard billboard = new Billboard(this);
    private final Rings rings = new Rings(this);
    /**
     * Per column: the depth of the nearest pillar standing in it, the row of its top, and the stands' horizon. Rows
     * are unsigned bytes, 0 to 200.
     */
    private final short[] coverDepth = new short[ViewList.WIDTH];
    private final byte[] coverTop = new byte[ViewList.WIDTH];
    private final byte[] horizon = new byte[ViewList.WIDTH];
    private final byte[] order = new byte[Coliseum.MAX_PILLARS];
    private final short[] orderDepth = new short[Coliseum.MAX_PILLARS];
    /** The eye this frame is seen from. */
    private float viewX;
    private float viewY;
    private float cos;
    private float sin;

    public View(ViewList list, Coliseum coliseum, Report report) {
        this.list = list;
        this.coliseum = coliseum;
        this.report = report;
    }

    public ViewList list() {
        return list;
    }

    public Coliseum coliseum() {
        return coliseum;
    }

    public Report report() {
        return report;
    }

    public Billboard billboard() {
        return billboard;
    }

    public float viewX() {
        return viewX;
    }

    public float viewY() {
        return viewY;
    }

    public float cos() {
        return cos;
    }

    public float sin() {
        return sin;
    }

    /**
     * Builds the frame seen by {@code body} into the display list. The opponent, a {@code kind} gladiator at
     * ({@code ox}, {@code oy}) facing {@code oAngle}, is drawn only when {@code visible}; {@code thrusting} when it is
     * striking. {@code swing} and {@code hurt} are frames left of this gladiator's own swing and wound.
     */
    public void build(Body body, boolean visible, float ox, float oy, float oAngle, int kind, boolean thrusting, int swing,
               int hurt) {
        build(body, visible, ox, oy, oAngle, kind, thrusting, swing, hurt, -1);
    }

    /** The Solo variant also selects the equipment visible in this gladiator's own hands. */
    public void build(Body body, boolean visible, float ox, float oy, float oAngle, int kind, boolean thrusting, int swing,
               int hurt, int ownKind) {
        viewX = body.x();
        viewY = body.y();
        cos = (float) Math.cos(body.angle());
        sin = (float) Math.sin(body.angle());
        for (int x = 0; x < ViewList.WIDTH; x++) {
            coverDepth[x] = (short) FAR;
            coverTop[x] = (byte) (BOTTOM + 1);
            horizon[x] = (byte) (BOTTOM + 1);
        }
        list.begin();
        hands(swing, ownKind);
        if (report.holding()) {
            heldJavelin();
        }
        list.pin();
        pillars();
        // What matters most goes first: past ViewList.MAX_LINES, the last lines of a crowded view are left out.
        if (visible) {
            billboard.draw(ox, oy, oAngle, kind, thrusting);
        }
        if (report.lion()) {
            Props.lion(this);
        }
        if (hurt > 0) {
            frame(0);
            if (hurt > 3) {
                frame(3);
            }
        }
        if (report.rooted()) {
            caught();
        }
        rings.draw();
        Props.draw(this);
    }

    /** The screen row of {@code height} above the floor, at the depth whose {@code FOCAL / depth} is {@code scale}. */
    public static int row(float height, float scale) {
        return Math.round(CY - (height - Coliseum.EYE) * scale);
    }

    /** Whether a point at {@code depth} drawn at ({@code column}, {@code y}) shows past the pillars. */
    public boolean shows(int column, int y, float depth) {
        return depth <= coverDepth[column] || y < (coverTop[column] & 0xFF);
    }

    /** The stands' horizon in {@code column}: what is drawn above the wall shows only above it. */
    public int horizon(int column) {
        return horizon[column] & 0xFF;
    }

    /** Raises the horizon in {@code column} to {@code y}. */
    public void raiseHorizon(int column, int y) {
        horizon[column] = (byte) y;
    }

    /** An upright line in {@code column}, cut off where a nearer pillar hides it. */
    public void vertical(int column, int y0, int y1, float depth, int color) {
        if (column < 0 || column >= ViewList.WIDTH) {
            return;
        }
        int top = Math.max(y0, TOP);
        int bottom = Math.min(y1, BOTTOM);
        if (depth > coverDepth[column]) {
            bottom = Math.min(bottom, (coverTop[column] & 0xFF) - 1);
        }
        if (top <= bottom) {
            list.add(column, top, column, bottom, color);
        }
    }

    /** The gladius, at rest or mid-swing, and the shield's rim. */
    private void hands(int swing, int kind) {
        if (kind == GladiatorClass.RETIARIUS) {
            retiariusHands(swing);
            return;
        }
        if (kind == GladiatorClass.THRAEX) {
            thraexHands(swing);
            return;
        }
        int sword = swing > 0 ? 1 : 0;
        for (int i = Figure.HANDS_START[sword]; i < Figure.HANDS_START[sword + 1]; i += 4) {
            list.add(Figure.HANDS[i], Figure.HANDS[i + 1], Figure.HANDS[i + 2], Figure.HANDS[i + 3], Figure.BLADE);
        }
        for (int i = Figure.HANDS_START[2]; i < Figure.HANDS_START[3]; i += 4) {
            list.add(Figure.HANDS[i], Figure.HANDS[i + 1], Figure.HANDS[i + 2], Figure.HANDS[i + 3],
                    Figure.SHIELD_COLOR);
        }
    }

    /** Trident on the right and the near edge of a cast net on the left, while the net is still in hand. */
    private void retiariusHands(int swing) {
        if (swing > 0) {
            list.add(266, 199, 178, 116, Figure.BLADE);
            list.add(178, 116, 166, 110, Figure.BLADE);
            list.add(178, 116, 174, 102, Figure.BLADE);
            list.add(178, 116, 184, 104, Figure.BLADE);
        } else {
            list.add(255, 199, 228, 94, Figure.BLADE);
            list.add(228, 94, 219, 80, Figure.BLADE);
            list.add(228, 94, 228, 77, Figure.BLADE);
            list.add(228, 94, 237, 80, Figure.BLADE);
        }
        if (!report.netReady()) {
            return;
        }
        int net = TftTouchShield.CYAN;
        list.add(0, 129, 66, 141, net);
        list.add(66, 141, 61, 199, net);
        list.add(0, 183, 61, 199, net);
        list.add(8, 131, 55, 197, net);
        list.add(31, 135, 28, 191, net);
        list.add(0, 157, 63, 166, net);
    }

    /** Curved sica on the right and a smaller upright parmula on the left. */
    private void thraexHands(int swing) {
        if (swing > 0) {
            list.add(254, 185, 181, 129, Figure.BLADE);
            list.add(181, 129, 169, 112, Figure.BLADE);
            list.add(169, 112, 181, 116, Figure.BLADE);
        } else {
            list.add(246, 194, 229, 126, Figure.BLADE);
            list.add(229, 126, 238, 101, Figure.BLADE);
            list.add(238, 101, 228, 108, Figure.BLADE);
        }
        list.add(229, 174, 259, 178, Figure.BLADE);
        int shield = TftTouchShield.YELLOW;
        list.add(10, 151, 55, 143, shield);
        list.add(55, 143, 61, 199, shield);
        list.add(10, 151, 9, 193, shield);
        list.add(9, 193, 61, 199, shield);
        list.add(13, 171, 58, 169, shield);
    }

    /** Every pillar in front, nearest first, each hiding what stands behind it. */
    private void pillars() {
        int count = 0;
        for (int p = 0; p < coliseum.pillars(); p++) {
            float x = coliseum.pillarX(p) - viewX;
            float y = coliseum.pillarY(p) - viewY;
            float depth = x * cos + y * sin;
            if (depth < coliseum.pillarRadius() + NEAR) {
                continue;
            }
            float scale = FOCAL / depth;
            float center = CX + (x * sin - y * cos) * scale;
            float half = coliseum.pillarRadius() * scale;
            if (center + half < 0 || center - half > ViewList.WIDTH - 1) {
                continue;
            }
            // Insertion sort by depth: there are never more than Coliseum.MAX_PILLARS.
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
            pillar(order[i]);
        }
    }

    private void pillar(int p) {
        float x = coliseum.pillarX(p) - viewX;
        float y = coliseum.pillarY(p) - viewY;
        float depth = x * cos + y * sin;
        float scale = FOCAL / depth;
        float center = CX + (x * sin - y * cos) * scale;
        float half = coliseum.pillarRadius() * scale;
        int color = depth < 500f ? PILLAR : PILLAR_FAR;
        int top = row(coliseum.pillarHeight(), scale);
        int base = row(0, scale);
        vertical(Math.round(center - half), top, base, depth, color);
        vertical(Math.round(center + half), top, base, depth, color);
        horizontal(center - half * 1.15f, center + half * 1.15f, top, depth, color);
        // A far pillar is only a few pixels wide: its capital and base mouldings would not show.
        if (depth < SIMPLE_PILLAR) {
            horizontal(center - half, center + half, row(coliseum.pillarHeight() - 30, scale), depth, color);
            horizontal(center - half * 1.1f, center + half * 1.1f, row(26, scale), depth, color);
            horizontal(center - half * 1.1f, center + half * 1.1f, base, depth, color);
        }
        int first = Math.max(0, (int) Math.ceil(center - half));
        int last = Math.min(ViewList.WIDTH - 1, (int) Math.floor(center + half));
        for (int column = first; column <= last; column++) {
            if (coverDepth[column] == FAR) {
                coverDepth[column] = (short) Math.min(FAR, Math.round(depth));
                coverTop[column] = (byte) Math.clamp(top, TOP - 1, BOTTOM + 1);
            }
        }
    }

    private void horizontal(float x0, float x1, int y, float depth, int color) {
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
                list.add(runStart, y, x - 1, y, color);
                runStart = -1;
            }
        }
    }

    /** A javelin carried in the right hand, ready to throw. */
    private void heldJavelin() {
        list.add(316, 170, 222, 70, BRONZE);
        list.add(222, 70, 212, 56, Figure.BLADE);
        list.add(212, 56, 228, 64, Figure.BLADE);
        list.add(228, 64, 222, 70, Figure.BLADE);
    }

    /** The opponent's net over this gladiator's eyes, while it holds it. */
    private void caught() {
        int net = TftTouchShield.CYAN;
        for (int x = 20; x <= 180; x += 80) {
            list.add(x, TOP, x + 60, BOTTOM, net);
            list.add(x + 120, TOP, x + 60, BOTTOM, net);
        }
        list.add(0, 60, ViewList.WIDTH - 1, 64, net);
        list.add(0, 140, ViewList.WIDTH - 1, 136, net);
    }

    /** A red frame round the view, {@code inset} pixels in, for a fresh wound. */
    private void frame(int inset) {
        int left = inset;
        int top = TOP + inset;
        int right = ViewList.WIDTH - 1 - inset;
        int bottom = BOTTOM - inset;
        list.add(left, top, right, top, WOUND);
        list.add(right, top, right, bottom, WOUND);
        list.add(right, bottom, left, bottom, WOUND);
        list.add(left, bottom, left, top, WOUND);
    }
}
