package io.github.jabrena.juno.games.tempest;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** All the drawing for the web, claw, flippers and shots; game rules never touch the display directly. */
final class SceneRenderer {
    static final int SPACE = TftTouchShield.BLACK;
    static final int WEB = 0x3A7F;
    static final int LANE_HIGHLIGHT = TftTouchShield.YELLOW;
    static final int CLAW = TftTouchShield.YELLOW;
    static final int FLIPPER = 0xF800;
    static final int FLIPPER_TIPS = 0xF81F;
    static final int SHOT = TftTouchShield.WHITE;
    static final int BULLET = 0xFD20;
    static final int WARP = TftTouchShield.CYAN;

    private static final int SWEEP_STEPS = 8;
    private static final int SWEEP_MILLIS = 30;

    private SceneRenderer() {
    }

    // ---- Web and claw ----

    static void drawWeb(int[] tube, int highlighted) {
        for (int i = 0; i < Tube.LANES; i++) {
            int j = (i + 1) % Tube.LANES;
            Tube.drawLine(tube[Tube.OUTER_X + i], tube[Tube.OUTER_Y + i], tube[Tube.OUTER_X + j],
                    tube[Tube.OUTER_Y + j], WEB);
            Tube.drawLine(tube[Tube.INNER_X + i], tube[Tube.INNER_Y + i], tube[Tube.INNER_X + j],
                    tube[Tube.INNER_Y + j], WEB);
        }
        for (int i = 0; i < Tube.LANES; i++) {
            boolean lit = highlighted >= 0 && (i == highlighted || i == (highlighted + 1) % Tube.LANES);
            drawSpoke(tube, i, lit ? LANE_HIGHLIGHT : WEB);
        }
    }

    static void drawSpoke(int[] tube, int spoke, int color) {
        int s = spoke % Tube.LANES;
        Tube.drawLine(tube[Tube.INNER_X + s], tube[Tube.INNER_Y + s], tube[Tube.OUTER_X + s],
                tube[Tube.OUTER_Y + s], color);
    }

    static void moveClaw(int[] tube, int previous, int lane) {
        drawClaw(tube, previous, SPACE);
        drawSpoke(tube, previous, WEB);
        drawSpoke(tube, previous + 1, WEB);
        drawSpoke(tube, lane, LANE_HIGHLIGHT);
        drawSpoke(tube, lane + 1, LANE_HIGHLIGHT);
        drawClaw(tube, lane, CLAW);
    }

    /** The claw sits just outside the rim, so drawing and erasing it never touches the web. */
    static void drawClaw(int[] tube, int lane, int color) {
        int next = (lane + 1) % Tube.LANES;
        int ax = tube[Tube.OUTER_X + lane];
        int ay = tube[Tube.OUTER_Y + lane];
        int bx = tube[Tube.OUTER_X + next];
        int by = tube[Tube.OUTER_Y + next];
        int mx = (ax + bx) / 2;
        int my = (ay + by) / 2;
        int length = Math.max(1, (int) Math.sqrt((double) ((mx - Tube.CENTER_X) * (mx - Tube.CENTER_X)
                + (my - Tube.CENTER_Y) * (my - Tube.CENTER_Y))));
        int ox = (mx - Tube.CENTER_X) * 12 / length;
        int oy = (my - Tube.CENTER_Y) * 12 / length;
        int leftX = ax + ox / 4;
        int leftY = ay + oy / 4;
        int rightX = bx + ox / 4;
        int rightY = by + oy / 4;
        Tube.drawLine(leftX, leftY, mx + ox, my + oy, color);
        Tube.drawLine(mx + ox, my + oy, rightX, rightY, color);
        Tube.drawLine(leftX, leftY, mx + ox / 2, my + oy / 2, color);
        Tube.drawLine(mx + ox / 2, my + oy / 2, rightX, rightY, color);
    }

    // ---- Enemies ----

    static void drawEnemy(int[] tube, int[] enemies, int slot) {
        int base = slot * Session.E_STRIDE;
        int lane = enemies[base + Session.E_LANE];
        int depth = enemies[base + Session.E_DEPTH];
        if (lane == enemies[base + Session.E_SHOWN_LANE]
                && Math.abs(depth - enemies[base + Session.E_SHOWN_DEPTH]) < 12) {
            return;
        }
        eraseEnemy(tube, enemies, slot);
        bowTie(tube, lane, depth, FLIPPER, FLIPPER_TIPS);
        enemies[base + Session.E_SHOWN_LANE] = lane;
        enemies[base + Session.E_SHOWN_DEPTH] = depth;
    }

    static void eraseEnemy(int[] tube, int[] enemies, int slot) {
        int base = slot * Session.E_STRIDE;
        int lane = enemies[base + Session.E_SHOWN_LANE];
        if (lane < 0) {
            return;
        }
        int depth = enemies[base + Session.E_SHOWN_DEPTH];
        bowTie(tube, lane, depth, SPACE, SPACE);
        enemies[base + Session.E_SHOWN_LANE] = -1;
        repairWeb(tube, lane, depth);
    }

    /** Repairs the stretches of the lane's two spokes a bow-tie may have overlapped. */
    private static void repairWeb(int[] tube, int lane, int depth) {
        int from = Math.max(0, depth - Session.ENEMY_HALF_DEPTH - 24);
        int to = Math.min(Tube.DEPTH, depth + Session.ENEMY_HALF_DEPTH + 24);
        for (int side = 0; side <= 1; side++) {
            int spoke = (lane + side) % Tube.LANES;
            boolean lit = spoke == Session.playerLane || spoke == (Session.playerLane + 1) % Tube.LANES;
            Tube.drawLine(Tube.spokeX(tube, spoke, from), Tube.spokeY(tube, spoke, from),
                    Tube.spokeX(tube, spoke, to), Tube.spokeY(tube, spoke, to), lit ? LANE_HIGHLIGHT : WEB);
        }
        if (depth - Session.ENEMY_HALF_DEPTH <= 8) {
            int next = (lane + 1) % Tube.LANES;
            Tube.drawLine(tube[Tube.INNER_X + lane], tube[Tube.INNER_Y + lane], tube[Tube.INNER_X + next],
                    tube[Tube.INNER_Y + next], WEB);
        }
    }

    /** A flipper: a bow-tie spanning the middle of the lane, pinched at its crossing. */
    private static void bowTie(int[] tube, int lane, int depth, int color, int tips) {
        int near = Math.min(Tube.DEPTH, depth + Session.ENEMY_HALF_DEPTH);
        int far = Math.max(0, depth - Session.ENEMY_HALF_DEPTH);
        int ax0 = Tube.spokeX(tube, lane, far);
        int ay0 = Tube.spokeY(tube, lane, far);
        int bx0 = Tube.spokeX(tube, lane + 1, far);
        int by0 = Tube.spokeY(tube, lane + 1, far);
        int ax1 = Tube.spokeX(tube, lane, near);
        int ay1 = Tube.spokeY(tube, lane, near);
        int bx1 = Tube.spokeX(tube, lane + 1, near);
        int by1 = Tube.spokeY(tube, lane + 1, near);
        // Inset a fifth of the lane width from each spoke.
        int lx0 = ax0 + (bx0 - ax0) / 5;
        int ly0 = ay0 + (by0 - ay0) / 5;
        int rx0 = bx0 - (bx0 - ax0) / 5;
        int ry0 = by0 - (by0 - ay0) / 5;
        int lx1 = ax1 + (bx1 - ax1) / 5;
        int ly1 = ay1 + (by1 - ay1) / 5;
        int rx1 = bx1 - (bx1 - ax1) / 5;
        int ry1 = by1 - (by1 - ay1) / 5;
        Tube.drawLine(lx0, ly0, rx1, ry1, color);
        Tube.drawLine(rx0, ry0, lx1, ly1, color);
        Tube.drawLine(lx0, ly0, lx1, ly1, tips);
        Tube.drawLine(rx0, ry0, rx1, ry1, tips);
    }

    // ---- Shots and bullets ----

    static void showShot(int[] tube, int[] shots, int slot) {
        drawMover(tube, shots, slot, SHOT);
    }

    static void showBullet(int[] tube, int[] bullets, int slot) {
        drawMover(tube, bullets, slot, BULLET);
    }

    private static void drawMover(int[] tube, int[] records, int slot, int color) {
        int base = slot * Session.S_STRIDE;
        int lane = records[base + Session.S_LANE];
        int depth = records[base + Session.S_DEPTH];
        int x = (Tube.spokeX(tube, lane, depth) + Tube.spokeX(tube, lane + 1, depth)) / 2;
        int y = (Tube.spokeY(tube, lane, depth) + Tube.spokeY(tube, lane + 1, depth)) / 2;
        if (x == records[base + Session.S_SHOWN_X] && y == records[base + Session.S_SHOWN_Y]) {
            return;
        }
        eraseShot(records, slot);
        TftTouchShield.fillRect(x - 1, y - 1, 2, 2, color);
        TftTouchShield.drawPixel(x, y + 1, color);
        TftTouchShield.drawPixel(x + 1, y, color);
        records[base + Session.S_SHOWN_X] = x;
        records[base + Session.S_SHOWN_Y] = y;
    }

    static void eraseShot(int[] records, int slot) {
        int base = slot * Session.S_STRIDE;
        int x = records[base + Session.S_SHOWN_X];
        if (x < 0) {
            return;
        }
        int y = records[base + Session.S_SHOWN_Y];
        TftTouchShield.fillRect(x - 1, y - 1, 2, 2, SPACE);
        TftTouchShield.drawPixel(x, y + 1, SPACE);
        TftTouchShield.drawPixel(x + 1, y, SPACE);
        records[base + Session.S_SHOWN_X] = -1;
    }

    static void clearShots(int[] records, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (records[slot * Session.S_STRIDE + Session.S_ACTIVE] != 0) {
                eraseShot(records, slot);
                records[slot * Session.S_STRIDE + Session.S_ACTIVE] = 0;
            }
        }
    }

    // ---- Rings and sweeps ----

    /** One cross-section of the tube: the ring joining every spoke at {@code depth}. */
    static void drawRing(int[] tube, int depth, int color) {
        for (int lane = 0; lane < Tube.LANES; lane++) {
            Tube.drawLine(Tube.spokeX(tube, lane, depth), Tube.spokeY(tube, lane, depth),
                    Tube.spokeX(tube, lane + 1, depth), Tube.spokeY(tube, lane + 1, depth), color);
        }
    }

    /**
     * A ring travelling from {@code from} to {@code to} in {@link #SWEEP_STEPS} steps, each drawn
     * briefly and erased; the caller redraws the web afterwards, since the erased ring nicks the
     * spokes where it crossed them.
     */
    static void sweepRing(int[] tube, int from, int to, int color) {
        int step = (to - from) / SWEEP_STEPS;
        for (int k = 0; k <= SWEEP_STEPS; k++) {
            int depth = from + step * k;
            drawRing(tube, depth, color);
            Delay.millis(SWEEP_MILLIS);
            drawRing(tube, depth, SPACE);
        }
    }

    // ---- Feedback ----

    /** The Superzapper's blast: a white ring racing from the far end out to the rim. */
    static void zapFlash(int[] tube) {
        sweepRing(tube, 0, Tube.DEPTH, TftTouchShield.WHITE);
    }

    /** A ring bursts outward from the claw's lane to mark the life just lost. */
    static void deathFlash(int[] tube, int lane) {
        int next = (lane + 1) % Tube.LANES;
        int mx = (tube[Tube.OUTER_X + lane] + tube[Tube.OUTER_X + next]) / 2;
        int my = (tube[Tube.OUTER_Y + lane] + tube[Tube.OUTER_Y + next]) / 2;
        for (int r = 2; r < 22; r = r + 2) {
            TftTouchShield.drawCircle(mx, my, r, (r & 2) == 0 ? CLAW : FLIPPER);
            Delay.millis(40);
        }
    }
}
