package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.startrek.DisplayList.HEADER;
import static io.github.jabrena.juno.games.startrek.DisplayList.HEIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.TACTICAL_RIGHT;
import static io.github.jabrena.juno.games.startrek.DisplayList.TACTICAL_X;
import static io.github.jabrena.juno.games.startrek.DisplayList.TACTICAL_Y;
import static io.github.jabrena.juno.games.startrek.DisplayList.addLine;
import static io.github.jabrena.juno.games.startrek.Entities.ENTITIES;
import static io.github.jabrena.juno.games.startrek.Entities.E_AUX;
import static io.github.jabrena.juno.games.startrek.Entities.E_HEADING;
import static io.github.jabrena.juno.games.startrek.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.startrek.Entities.E_TIMER;
import static io.github.jabrena.juno.games.startrek.Entities.E_TYPE;
import static io.github.jabrena.juno.games.startrek.Entities.E_X;
import static io.github.jabrena.juno.games.startrek.Entities.E_Y;
import static io.github.jabrena.juno.games.startrek.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.startrek.Entities.T_KLINGON;
import static io.github.jabrena.juno.games.startrek.Entities.T_MINE;
import static io.github.jabrena.juno.games.startrek.Entities.T_NOMAD;
import static io.github.jabrena.juno.games.startrek.Entities.T_NONE;
import static io.github.jabrena.juno.games.startrek.Entities.T_PHOTON;
import static io.github.jabrena.juno.games.startrek.Entities.T_SAUCER;
import static io.github.jabrena.juno.games.startrek.Entities.T_STARBASE;
import static io.github.jabrena.juno.games.startrek.Entities.T_TORPEDO;
import static io.github.jabrena.juno.games.startrek.Geometry.FIX;
import static io.github.jabrena.juno.games.startrek.Geometry.SECTOR;
import static io.github.jabrena.juno.games.startrek.Geometry.dirX;
import static io.github.jabrena.juno.games.startrek.Geometry.dirY;

/**
 * The colors, one frame of both views, and the tactical view: the sector from above with the
 * Enterprise in the middle.
 */
final class SceneRenderer {
    static final int STAR = 0x7BEF;
    static final int ENTERPRISE = TftTouchShield.WHITE;
    static final int KLINGON = TftTouchShield.GREEN;
    static final int TORPEDO = TftTouchShield.RED;
    static final int PHOTON = TftTouchShield.ORANGE;
    static final int PHASER = TftTouchShield.YELLOW;
    static final int STARBASE = TftTouchShield.CYAN;
    static final int SAUCER = TftTouchShield.MAGENTA;
    static final int NOMAD = 0xFD20;
    static final int MINE = TftTouchShield.RED;
    static final int FRAME = 0x3A7F;
    static final int BUTTON = 0x2124;
    static final int BUTTON_EMPTY = 0x18C3;

    /** World units per pixel in the tactical view. */
    private static final int ZOOM = 2;

    private SceneRenderer() {
    }

    static void render(short[] lines, int[] ents) {
        DisplayList.begin();
        DisplayList.setClip(0, HEADER, TACTICAL_RIGHT, HEIGHT - 1);
        drawTacticalStars(lines);
        for (int slot = 0; slot < ENTITIES; slot++) {
            drawTactical(lines, ents, slot);
        }
        drawEnterprise(lines);
        if (Combat.phaserFrames > 0) {
            float radians = (float) Math.toRadians(Enterprise.heading);
            int length = Combat.phaserLength / ZOOM;
            addLine(lines, TACTICAL_X, TACTICAL_Y, TACTICAL_X + Math.round((float) Math.sin(radians) * length),
                    TACTICAL_Y - Math.round((float) Math.cos(radians) * length), PHASER);
        }
        DisplayList.setClip(DisplayList.PANEL_X + 1, DisplayList.BRIDGE_TOP + 1, DisplayList.WIDTH - 2,
                DisplayList.BRIDGE_BOTTOM - 1);
        BridgeView.draw(lines, ents);
        DisplayList.present(lines);
    }

    /** Stars fixed in the sector, so the tactical view shows the Enterprise's motion. */
    private static void drawTacticalStars(short[] lines) {
        for (int i = 0; i < 26; i++) {
            int sx = (i * 797 + 131) % SECTOR * FIX;
            int sy = (i * 523 + 37 * i * i) % SECTOR * FIX;
            int px = TACTICAL_X + Geometry.wrap(sx - Enterprise.x) / FIX / ZOOM;
            int py = TACTICAL_Y + Geometry.wrap(sy - Enterprise.y) / FIX / ZOOM;
            addLine(lines, px, py, px, py, STAR);
        }
    }

    private static void drawTactical(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        if (type == T_NONE) {
            return;
        }
        int px = TACTICAL_X + Geometry.wrap(ents[b + E_X] - Enterprise.x) / FIX / ZOOM;
        int py = TACTICAL_Y + Geometry.wrap(ents[b + E_Y] - Enterprise.y) / FIX / ZOOM;
        if (px < -40 || px > TACTICAL_RIGHT + 40 || py < -40 || py > HEIGHT + 40) {
            return;
        }
        if (type == T_KLINGON) {
            drawKlingonTop(lines, px, py, ents[b + E_HEADING]);
        } else if (type == T_STARBASE) {
            for (int k = 0; k < 8; k++) {
                addLine(lines, px + dirX(k) * 13 / 10, py + dirY(k) * 13 / 10, px + dirX(k + 1) * 13 / 10,
                        py + dirY(k + 1) * 13 / 10, STARBASE);
            }
            addLine(lines, px - 18, py, px + 18, py, STARBASE);
            addLine(lines, px, py - 18, px, py + 18, STARBASE);
        } else if (type == T_SAUCER) {
            int s = (Session.frame + slot) % 2 == 0 ? 5 : 3;
            addLine(lines, px - 6, py, px, py - s, SAUCER);
            addLine(lines, px, py - s, px + 6, py, SAUCER);
            addLine(lines, px + 6, py, px, py + s, SAUCER);
            addLine(lines, px, py + s, px - 6, py, SAUCER);
        } else if (type == T_NOMAD) {
            addLine(lines, px - 6, py - 6, px + 6, py - 6, NOMAD);
            addLine(lines, px + 6, py - 6, px + 6, py + 6, NOMAD);
            addLine(lines, px + 6, py + 6, px - 6, py + 6, NOMAD);
            addLine(lines, px - 6, py + 6, px - 6, py - 6, NOMAD);
            addLine(lines, px - 3, py, px + 3, py, NOMAD);
        } else if (type == T_MINE) {
            addLine(lines, px - 2, py - 2, px + 2, py + 2, MINE);
            addLine(lines, px - 2, py + 2, px + 2, py - 2, MINE);
        } else if (type == T_TORPEDO || type == T_PHOTON) {
            int color = type == T_TORPEDO ? TORPEDO : PHOTON;
            int s = (Session.frame & 1) == 0 ? 3 : 2;
            addLine(lines, px - s, py, px + s, py, color);
            addLine(lines, px, py - s, px, py + s, color);
        } else if (type == T_DEBRIS) {
            int size = ents[b + E_AUX] * (9 - ents[b + E_TIMER]) / 8 / ZOOM + 2;
            for (int k = 0; k < 8; k++) {
                addLine(lines, px + dirX(k) * size / 20, py + dirY(k) * size / 20, px + dirX(k) * size / 10,
                        py + dirY(k) * size / 10, (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE);
            }
        }
    }

    /** The Enterprise from above, in the middle of the tactical view: saucer, neck, hull, nacelles. */
    static void drawEnterprise(short[] lines) {
        float radians = (float) Math.toRadians(Enterprise.heading);
        int c = Math.round((float) Math.cos(radians) * 64);
        int s = Math.round((float) Math.sin(radians) * 64);
        for (int k = 0; k < 8; k++) {
            shipLine(lines, c, s, dirX(k) * 7 / 10, dirY(k) * 7 / 10 - 5, dirX(k + 1) * 7 / 10, dirY(k + 1) * 7 / 10 - 5,
                    ENTERPRISE);
        }
        shipLine(lines, c, s, 0, 2, 0, 7, ENTERPRISE);
        shipLine(lines, c, s, -2, 7, 2, 7, ENTERPRISE);
        shipLine(lines, c, s, -2, 7, -2, 12, ENTERPRISE);
        shipLine(lines, c, s, 2, 7, 2, 12, ENTERPRISE);
        shipLine(lines, c, s, -7, 5, -7, 15, ENTERPRISE);
        shipLine(lines, c, s, 7, 5, 7, 15, ENTERPRISE);
        shipLine(lines, c, s, -7, 9, 7, 9, ENTERPRISE);
    }

    /** A line of a top-view model given with y pointing forward-down, rotated by (c, s)/64 about the center. */
    private static void shipLine(short[] lines, int c, int s, int x0, int y0, int x1, int y1, int color) {
        addLine(lines, TACTICAL_X + (x0 * c - y0 * s) / 64, TACTICAL_Y + (x0 * s + y0 * c) / 64,
                TACTICAL_X + (x1 * c - y1 * s) / 64, TACTICAL_Y + (x1 * s + y1 * c) / 64, color);
    }

    /** A Klingon battlecruiser from above: a head on a long neck, and swept wings. */
    static void drawKlingonTop(short[] lines, int px, int py, int headingDegrees) {
        float radians = (float) Math.toRadians(headingDegrees);
        int c = Math.round((float) Math.cos(radians) * 64);
        int s = Math.round((float) Math.sin(radians) * 64);
        klingonLine(lines, px, py, c, s, -3, -12, 3, -12);
        klingonLine(lines, px, py, c, s, 0, -10, 0, 2);
        klingonLine(lines, px, py, c, s, -11, 7, 11, 7);
        klingonLine(lines, px, py, c, s, -11, 7, 0, 1);
        klingonLine(lines, px, py, c, s, 11, 7, 0, 1);
        klingonLine(lines, px, py, c, s, -11, 7, -11, 2);
        klingonLine(lines, px, py, c, s, 11, 7, 11, 2);
    }

    private static void klingonLine(short[] lines, int px, int py, int c, int s, int x0, int y0, int x1, int y1) {
        addLine(lines, px + (x0 * c - y0 * s) / 64, py + (x0 * s + y0 * c) / 64, px + (x1 * c - y1 * s) / 64,
                py + (x1 * s + y1 * c) / 64, KLINGON);
    }
}
