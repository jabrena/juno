package io.github.jabrena.juno.games.starwars;

import static io.github.jabrena.juno.games.starwars.Entities.CAP_HEIGHT;
import static io.github.jabrena.juno.games.starwars.Entities.ENTITIES;
import static io.github.jabrena.juno.games.starwars.Entities.E_AUX;
import static io.github.jabrena.juno.games.starwars.Entities.E_FLAG;
import static io.github.jabrena.juno.games.starwars.Entities.E_SR;
import static io.github.jabrena.juno.games.starwars.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.starwars.Entities.E_SX;
import static io.github.jabrena.juno.games.starwars.Entities.E_SY;
import static io.github.jabrena.juno.games.starwars.Entities.E_TIMER;
import static io.github.jabrena.juno.games.starwars.Entities.E_TYPE;
import static io.github.jabrena.juno.games.starwars.Entities.E_X;
import static io.github.jabrena.juno.games.starwars.Entities.E_Y;
import static io.github.jabrena.juno.games.starwars.Entities.E_Z;
import static io.github.jabrena.juno.games.starwars.Entities.TRENCH_HALF_WIDTH;
import static io.github.jabrena.juno.games.starwars.Entities.T_CATWALK;
import static io.github.jabrena.juno.games.starwars.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.starwars.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.starwars.Entities.T_NONE;
import static io.github.jabrena.juno.games.starwars.Entities.T_PORT;
import static io.github.jabrena.juno.games.starwars.Entities.T_SHOT;
import static io.github.jabrena.juno.games.starwars.Entities.T_TIE;
import static io.github.jabrena.juno.games.starwars.Entities.T_TOWER;
import static io.github.jabrena.juno.games.starwars.Entities.T_TURRET;
import static io.github.jabrena.juno.games.starwars.Entities.T_VADER;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Draws a frame in vector style: the phase's backdrop, then the entities farthest first, the lasers
 * and the crosshair, into the display list. Each target's screen position and hit radius are
 * recorded as it is drawn, for the lasers to aim at.
 */
final class SceneRenderer {
    // Colors.
    static final int STAR = 0x8410;
    private static final int TIE = TftTouchShield.GREEN;
    private static final int VADER = TftTouchShield.WHITE;
    private static final int CROSSHAIR = TftTouchShield.WHITE;
    static final int LASER = TftTouchShield.RED;
    private static final int FIRE_A = TftTouchShield.ORANGE;
    private static final int FIRE_B = TftTouchShield.RED;
    static final int STRUCTURE = 0x3A7F;
    static final int GRID_LINE = 0x2112;
    static final int TOWER_TOP = TftTouchShield.YELLOW;
    private static final int TURRET = TftTouchShield.MAGENTA;
    private static final int CATWALK = 0xFBE0;
    private static final int PORT_OUTER = TftTouchShield.RED;
    private static final int PORT_INNER = TftTouchShield.YELLOW;

    // Shapes.
    private static final int TIE_WING = 55;
    private static final int TOWER_HALF_WIDTH = 26;
    private static final int CAP_HALF_WIDTH = 16;
    private static final int CATWALK_HALF_HEIGHT = 12;

    private SceneRenderer() {
    }

    static void render(short[] lines, int[] ents) {
        DisplayList.begin();
        switch (Session.phase) {
            case SPACE -> drawStars(lines);
            case SURFACE -> SurfacePhase.drawSurface(lines);
            default -> TrenchPhase.drawTrench(lines);
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

    static void drawStars(short[] lines) {
        int width = DisplayList.WIDTH;
        int header = DisplayList.HEADER;
        for (int i = 0; i < 28; i++) {
            int x = (i * 97 + 13) % width;
            int y = header + 2 + (i * 61 + 7 * i * i) % (DisplayList.HEIGHT - header - 4);
            DisplayList.addLine(lines, x, y, x, y, STAR);
        }
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
        if (type == T_TIE || type == T_VADER) {
            drawTie(lines, px, py, z, type == T_VADER);
            ents[b + E_SR] = Camera.scale(80, z) + 4;
        } else if (type == T_FIREBALL) {
            ents[b + E_SR] = drawFireball(lines, px, py, z, slot);
        } else if (type == T_TOWER) {
            drawTower(lines, ents, b, px, py, z);
        } else if (type == T_TURRET) {
            Camera.line3(lines, x, y - 16, z - 16, x, y + 16, z - 16, TURRET);
            Camera.line3(lines, x, y + 16, z - 16, x, y + 16, z + 16, TURRET);
            Camera.line3(lines, x, y + 16, z + 16, x, y - 16, z + 16, TURRET);
            Camera.line3(lines, x, y - 16, z + 16, x, y - 16, z - 16, TURRET);
            ents[b + E_SR] = Camera.scale(22, z) + 5;
        } else if (type == T_CATWALK) {
            drawCatwalk(lines, y, z);
        } else if (type == T_PORT) {
            floorSquare(lines, x, y, z, 50, PORT_OUTER);
            floorSquare(lines, x, y, z, 22, PORT_INNER);
            ents[b + E_SR] = Camera.scale(45, z) + 6;
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

    private static void drawCatwalk(short[] lines, int y, int z) {
        int w = TRENCH_HALF_WIDTH;
        int top = y + CATWALK_HALF_HEIGHT;
        int bottom = y - CATWALK_HALF_HEIGHT;
        Camera.line3(lines, -w, top, z, w, top, z, CATWALK);
        Camera.line3(lines, -w, bottom, z, w, bottom, z, CATWALK);
        Camera.line3(lines, -w, top, z, -w, bottom, z, CATWALK);
        Camera.line3(lines, w, top, z, w, bottom, z, CATWALK);
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

    /**
     * A TIE fighter seen head-on: two hexagonal wings joined to a hexagonal cockpit. Vader's has
     * bent wings. Far away, a wing is one stroke.
     */
    static void drawTie(short[] lines, int px, int py, int z, boolean vader) {
        int color = vader ? VADER : TIE;
        if (Camera.scale(70, z) < 7) {
            Camera.part(lines, px, py, z, -TIE_WING, 70, -TIE_WING, -70, color);
            Camera.part(lines, px, py, z, TIE_WING, 70, TIE_WING, -70, color);
            Camera.part(lines, px, py, z, -TIE_WING, 0, TIE_WING, 0, color);
            return;
        }
        for (int side = -1; side <= 1; side = side + 2) {
            int wx = side * TIE_WING;
            int o = side * 14;
            if (vader) {
                Camera.part(lines, px, py, z, wx - side * 20, 74, wx + o, 34, color);
                Camera.part(lines, px, py, z, wx + o, 34, wx + o, -34, color);
                Camera.part(lines, px, py, z, wx + o, -34, wx - side * 20, -74, color);
                Camera.part(lines, px, py, z, wx - side * 20, 74, wx - side * 20, -74, color);
                Camera.part(lines, px, py, z, side * 16, 0, wx - side * 20, 0, color);
            } else {
                Camera.part(lines, px, py, z, wx, 70, wx + o, 35, color);
                Camera.part(lines, px, py, z, wx + o, 35, wx + o, -35, color);
                Camera.part(lines, px, py, z, wx + o, -35, wx, -70, color);
                Camera.part(lines, px, py, z, wx, -70, wx - o, -35, color);
                Camera.part(lines, px, py, z, wx - o, -35, wx - o, 35, color);
                Camera.part(lines, px, py, z, wx - o, 35, wx, 70, color);
                Camera.part(lines, px, py, z, wx, 70, wx, -70, color);
                Camera.part(lines, px, py, z, side * 16, 0, wx - o, 0, color);
            }
        }
        int cockpit = vader ? TftTouchShield.RED : color;
        Camera.part(lines, px, py, z, 16, 0, 8, 14, cockpit);
        Camera.part(lines, px, py, z, 8, 14, -8, 14, cockpit);
        Camera.part(lines, px, py, z, -8, 14, -16, 0, cockpit);
        Camera.part(lines, px, py, z, -16, 0, -8, -14, cockpit);
        Camera.part(lines, px, py, z, -8, -14, 8, -14, cockpit);
        Camera.part(lines, px, py, z, 8, -14, 16, 0, cockpit);
    }

    /** A laser tower on the surface; (px, py) is its foot. Its top is the target. */
    private static void drawTower(short[] lines, int[] ents, int b, int px, int py, int z) {
        int h = ents[b + E_AUX];
        int w = TOWER_HALF_WIDTH;
        Camera.part(lines, px, py, z, -w, 0, -w, h, STRUCTURE);
        Camera.part(lines, px, py, z, w, 0, w, h, STRUCTURE);
        Camera.part(lines, px, py, z, -w, h, w, h, STRUCTURE);
        if (ents[b + E_FLAG] != 0) {
            int c = CAP_HALF_WIDTH;
            Camera.part(lines, px, py, z, -c, h, -c, h + CAP_HEIGHT, TOWER_TOP);
            Camera.part(lines, px, py, z, -c, h + CAP_HEIGHT, c, h + CAP_HEIGHT, TOWER_TOP);
            Camera.part(lines, px, py, z, c, h + CAP_HEIGHT, c, h, TOWER_TOP);
            Camera.part(lines, px, py, z, -c, h + CAP_HEIGHT / 2, c, h + CAP_HEIGHT / 2, TOWER_TOP);
            ents[b + E_SX] = px;
            ents[b + E_SY] = py - Camera.scale(h + CAP_HEIGHT / 2, z);
            ents[b + E_SR] = Camera.scale(CAP_HALF_WIDTH + 6, z) + 5;
        }
    }

    private static void floorSquare(short[] lines, int x, int y, int z, int half, int color) {
        Camera.line3(lines, x - half, y, z - half, x + half, y, z - half, color);
        Camera.line3(lines, x + half, y, z - half, x + half, y, z + half, color);
        Camera.line3(lines, x + half, y, z + half, x - half, y, z + half, color);
        Camera.line3(lines, x - half, y, z + half, x - half, y, z - half, color);
    }

    /** The four wing cannons' beams, travelling from the screen's corners to the crosshair. */
    private static void drawShot(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int tx = ents[b + E_X];
        int ty = ents[b + E_Y];
        int progress = Combat.SHOT_FRAMES - ents[b + E_TIMER];
        int from = progress * 22;
        int to = from + 34;
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? 4 : DisplayList.WIDTH - 5;
            int cy = (corner & 2) == 0 ? DisplayList.HEADER + 4 : DisplayList.HEIGHT - 5;
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
        if (k == 0) {
            return 10;
        }
        if (k == 1 || k == 7) {
            return 7;
        }
        if (k == 3 || k == 5) {
            return -7;
        }
        if (k == 4) {
            return -10;
        }
        return 0;
    }

    private static int dirY(int k) {
        return dirX((k + 6) % 8);
    }
}
