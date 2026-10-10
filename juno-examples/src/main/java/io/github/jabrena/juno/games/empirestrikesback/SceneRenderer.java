package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.ENTITIES;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_AUX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_FLAG;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SR;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TIMER;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.GROUND;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ASTEROID;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATAT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATST;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_NONE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_PROBE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_SHOT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_TIE;

/**
 * Draws a frame in vector style: Hoth or the stars, then the entities farthest first, the lasers and
 * the crosshair, into the display list. Each target's screen position and hit radius are recorded as
 * it is drawn, for the lasers to aim at.
 */
final class SceneRenderer {
    // Colors.
    static final int SNOW = 0x6DDF;
    private static final int SNOW_GRID = 0x3252;
    private static final int MOUNTAIN = 0xBDF7;
    private static final int PROBE = TftTouchShield.WHITE;
    private static final int PROBE_EYE = TftTouchShield.RED;
    static final int WALKER_HEAD = TftTouchShield.YELLOW;
    private static final int ROCK = 0xA4C8;
    static final int STAR = 0x8410;
    private static final int TIE = TftTouchShield.GREEN;
    private static final int CROSSHAIR = TftTouchShield.WHITE;
    private static final int LASER = TftTouchShield.RED;
    static final int FIRE_A = TftTouchShield.ORANGE;
    static final int FIRE_B = TftTouchShield.RED;
    static final int HUD = 0x3A7F;

    private static final int GRID = 320;

    private SceneRenderer() {
    }

    static void render(short[] lines, int[] ents) {
        DisplayList.begin();
        if (Session.round == Round.ASTEROIDS) {
            drawStars(lines);
        } else {
            drawHoth(lines);
        }
        // Farthest first, so what is near is drawn last.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < ENTITIES; slot++) {
                int b = slot * E_STRIDE;
                int type = ents[b + E_TYPE];
                boolean far = ents[b + E_Z] > Camera.FAR / 2;
                if (type != T_NONE && type != T_SHOT && far == (pass == 0)) {
                    drawEntity(lines, ents, slot);
                }
            }
        }
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == T_SHOT) {
                drawShot(lines, ents, slot);
            }
        }
        drawCrosshair(lines);
        DisplayList.present(lines);
    }

    private static void drawStars(short[] lines) {
        for (int i = 0; i < 28; i++) {
            int x = (i * 97 + 13 - Camera.camX / 8 + 3200) % DisplayList.WIDTH;
            int y = DisplayList.HEADER + 2 + (i * 61 + 7 * i * i + Camera.camY / 8 + 2200)
                    % (DisplayList.HEIGHT - DisplayList.HEADER - 4);
            DisplayList.addLine(lines, x, y, x, y, STAR);
        }
    }

    /** The horizon, a ridge of snowy mountains on it, and snow drifts streaming past on the ground. */
    static void drawHoth(short[] lines) {
        int horizon = Camera.CENTER_Y;
        DisplayList.addLine(lines, 0, horizon, DisplayList.WIDTH - 1, horizon, SNOW);
        int previousX = -1;
        int previousY = horizon;
        for (int k = 0; k <= 16; k++) {
            int x = k * 20 - 1;
            int y = horizon - ridge(k);
            DisplayList.addLine(lines, previousX, previousY, x, y, MOUNTAIN);
            previousX = x;
            previousY = y;
        }
        int offset = Session.travel % GRID;
        for (int k = 0; k < 7; k++) {
            int z = GRID * (k + 1) - offset;
            if (z < Camera.NEAR || z > Camera.FAR) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                int x = -600 + c * 400 + ((k & 1) == 0 ? 0 : 200);
                Camera.line3(lines, x - 60, GROUND, z, x + 60, GROUND, z, SNOW_GRID);
            }
        }
    }

    /** Height in pixels of the mountain ridge at each 20-pixel step along the horizon. */
    private static int ridge(int k) {
        // One character per point, '0' = 0 pixels; a string keeps the table out of the arena.
        return "05<8@6;3?9A57=48;0".charAt(k) - '0';
    }

    private static void drawEntity(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        ents[b + E_SR] = 0;
        if (z < Camera.NEAR) {
            return;
        }
        int px = Camera.projectX(x, z);
        int py = Camera.projectY(y, z);
        ents[b + E_SX] = px;
        ents[b + E_SY] = py;
        if (type == T_PROBE) {
            drawProbe(lines, px, py, z, slot);
            ents[b + E_SR] = Camera.scale(60, z) + 5;
        } else if (type == T_ATAT) {
            WalkersRound.drawAtat(lines, ents, b);
        } else if (type == T_ATST) {
            WalkersRound.drawAtst(lines, ents, b);
        } else if (type == T_ASTEROID) {
            drawAsteroid(lines, px, py, z, ents[b + E_AUX], ents[b + E_FLAG]);
            ents[b + E_SR] = Camera.scale(ents[b + E_AUX], z) + 4;
        } else if (type == T_TIE) {
            drawTie(lines, px, py, z);
            ents[b + E_SR] = Camera.scale(80, z) + 4;
        } else if (type == T_FIREBALL) {
            ents[b + E_SR] = drawFireball(lines, px, py, z, slot);
        } else if (type == T_DEBRIS) {
            drawDebris(lines, px, py, z, 10 - ents[b + E_TIMER], ents[b + E_AUX]);
        }
    }

    /** A spinning fireball; returns its hit radius. */
    private static int drawFireball(short[] lines, int px, int py, int z, int slot) {
        int r = Math.max(2, Math.min(Camera.scale(28, z), 40));
        int spin = (Session.frame + slot) & 1;
        int straight = spin == 0 ? FIRE_A : FIRE_B;
        int diagonal = spin == 0 ? FIRE_B : FIRE_A;
        int d = r * 7 / 10;
        DisplayList.addLine(lines, px - r, py, px + r, py, straight);
        DisplayList.addLine(lines, px, py - r, px, py + r, straight);
        DisplayList.addLine(lines, px - d, py - d, px + d, py + d, diagonal);
        DisplayList.addLine(lines, px - d, py + d, px + d, py - d, diagonal);
        return r + 6;
    }

    /** An explosion {@code age} frames old: eight sparks flying outwards. */
    private static void drawDebris(short[] lines, int px, int py, int z, int age, int size) {
        int r0 = size * age / 10;
        int r1 = r0 + size / 4 + 4;
        for (int k = 0; k < 8; k++) {
            int color = (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE;
            Camera.part(lines, px, py, z, dirX(k) * r0 / 10, dirY(k) * r0 / 10, dirX(k) * r1 / 10,
                    dirY(k) * r1 / 10, color);
        }
    }

    /** A probe droid: an octagonal head with a red eye, and legs dangling (and swaying) below. */
    private static void drawProbe(short[] lines, int px, int py, int z, int slot) {
        for (int k = 0; k < 8; k++) {
            Camera.part(lines, px, py, z, dirX(k) * 4, dirY(k) * 4, dirX(k + 1) * 4, dirY(k + 1) * 4, PROBE);
        }
        Camera.part(lines, px, py, z, -40, 0, 40, 0, PROBE);
        Camera.part(lines, px, py, z, -6, 12, 6, 12, PROBE_EYE);
        int sway = ((Session.frame + slot * 3) / 4) % 3 - 1;
        for (int leg = -2; leg <= 2; leg++) {
            int top = leg * 12;
            Camera.part(lines, px, py, z, top, -40, top + leg * 6 + sway * 6, -95 - Math.abs(leg) * 8, PROBE);
        }
    }

    /** A tumbling asteroid: an irregular heptagon, turning, with a crease across it. */
    private static void drawAsteroid(short[] lines, int px, int py, int z, int radius, int seed) {
        float turn = (Session.frame + seed) * 0.08f * ((seed & 1) == 0 ? 1 : -1);
        int firstX = 0;
        int firstY = 0;
        int previousX = 0;
        int previousY = 0;
        for (int k = 0; k < 7; k++) {
            float angle = turn + k * 0.8976f;
            int r = radius * (7 + (seed >> k) % 4) / 10;
            int vx = Math.round((float) Math.cos(angle) * r);
            int vy = Math.round((float) Math.sin(angle) * r);
            if (k == 0) {
                firstX = vx;
                firstY = vy;
            } else {
                Camera.part(lines, px, py, z, previousX, previousY, vx, vy, ROCK);
            }
            if (k == 3) {
                Camera.part(lines, px, py, z, firstX / 2, firstY / 2, vx / 2, vy / 2, ROCK);
            }
            previousX = vx;
            previousY = vy;
        }
        Camera.part(lines, px, py, z, previousX, previousY, firstX, firstY, ROCK);
    }

    /** A TIE fighter seen head-on: two hexagonal wings joined to a hexagonal cockpit. */
    private static void drawTie(short[] lines, int px, int py, int z) {
        int wing = 55;
        if (Camera.scale(70, z) < 7) {
            Camera.part(lines, px, py, z, -wing, 70, -wing, -70, TIE);
            Camera.part(lines, px, py, z, wing, 70, wing, -70, TIE);
            Camera.part(lines, px, py, z, -wing, 0, wing, 0, TIE);
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int wx = side * wing;
            int o = side * 14;
            Camera.part(lines, px, py, z, wx, 70, wx + o, 35, TIE);
            Camera.part(lines, px, py, z, wx + o, 35, wx + o, -35, TIE);
            Camera.part(lines, px, py, z, wx + o, -35, wx, -70, TIE);
            Camera.part(lines, px, py, z, wx, -70, wx - o, -35, TIE);
            Camera.part(lines, px, py, z, wx - o, -35, wx - o, 35, TIE);
            Camera.part(lines, px, py, z, wx - o, 35, wx, 70, TIE);
            Camera.part(lines, px, py, z, side * 16, 0, wx - o, 0, TIE);
        }
        Camera.part(lines, px, py, z, 16, 0, 8, 14, TIE);
        Camera.part(lines, px, py, z, 8, 14, -8, 14, TIE);
        Camera.part(lines, px, py, z, -8, 14, -16, 0, TIE);
        Camera.part(lines, px, py, z, -16, 0, -8, -14, TIE);
        Camera.part(lines, px, py, z, -8, -14, 8, -14, TIE);
        Camera.part(lines, px, py, z, 8, -14, 16, 0, TIE);
    }

    /** The twin lasers' beams, travelling from the lower corners to the crosshair. */
    private static void drawShot(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int tx = ents[b + E_X];
        int ty = ents[b + E_Y];
        int progress = Combat.SHOT_FRAMES - ents[b + E_TIMER];
        int from = progress * 22;
        int to = from + 34;
        for (int corner = 0; corner < 2; corner++) {
            int cx = corner == 0 ? 4 : DisplayList.WIDTH - 5;
            int cy = DisplayList.HEIGHT - 5;
            DisplayList.addLine(lines, cx + (tx - cx) * from / 100, cy + (ty - cy) * from / 100,
                    cx + (tx - cx) * to / 100, cy + (ty - cy) * to / 100, LASER);
        }
    }

    private static void drawCrosshair(short[] lines) {
        int x = Controls.crossX;
        int y = Controls.crossY;
        DisplayList.addLine(lines, x - 11, y, x - 4, y, CROSSHAIR);
        DisplayList.addLine(lines, x + 4, y, x + 11, y, CROSSHAIR);
        DisplayList.addLine(lines, x, y - 11, x, y - 4, CROSSHAIR);
        DisplayList.addLine(lines, x, y + 4, x, y + 11, CROSSHAIR);
    }

    private static int dirX(int k) {
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

    private static int dirY(int k) {
        return dirX(k + 6);
    }
}
