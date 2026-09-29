package io.github.jabrena.juno.games.pacman;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The procedural pixel shapes of Pac-Man and the ghosts (body, eyes and frightened face), plus the
 * small decorative icons used for the footer's spare lives and the title screen's chase demo. Split
 * out of {@link SceneRenderer} to keep that class's own cyclomatic complexity in check.
 */
final class Sprites {
    /** {@link #ghostPixel} sentinels: fully transparent, or "not the eyes, keep looking at the body". */
    private static final int TRANSPARENT = -1;
    private static final int NOT_EYES = -2;

    private Sprites() {
    }

    // ---- Pac-Man ----

    static int pacLook() {
        return mouth() * 4 + Session.pacDir + Session.pacDeath * 64 + (Session.pacVisible ? 0 : 1024);
    }

    /** Mouth opening 0 (closed) to 2 (wide), cycling with the distance Pac-Man has moved. */
    private static int mouth() {
        int cycle = (Session.pacSteps / 2) & 3;
        return cycle == 3 ? 1 : cycle;
    }

    static boolean pacPixel(int dx, int dy) {
        if (dx * dx + dy * dy > Session.HALF * Session.HALF + Session.HALF) {
            return false;
        }
        int forward;
        int side;
        if (Session.pacDir == Session.RIGHT) {
            forward = dx;
            side = Math.abs(dy);
        } else if (Session.pacDir == Session.LEFT) {
            forward = -dx;
            side = Math.abs(dy);
        } else if (Session.pacDir == Session.UP) {
            forward = -dy;
            side = Math.abs(dx);
        } else {
            forward = dy;
            side = Math.abs(dx);
        }
        if (Session.pacDeath > 0) {
            // Dying, the mouth opens from 45 degrees until nothing is left.
            if (dx == 0 && dy == 0) {
                return Session.pacDeath < 12;
            }
            double angle = Math.toDegrees(Math.atan2((double) side, (double) forward));
            return angle > 45.0 + Session.pacDeath * 135.0 / 12.0;
        }
        // The mouth is a wedge around the direction of travel, 0, 1 or 2 units open.
        return forward <= 0 || side * 2 > forward * mouth();
    }

    // ---- Ghosts ----

    static int ghostLook(int[] ghosts, int g) {
        int base = g * Session.G_STRIDE;
        int state = ghosts[base + Session.G_STATE];
        int kind = 0;
        if (state == Session.EYES || state == Session.ENTERING) {
            kind = 3;
        } else if (ghosts[base + Session.G_FRIGHTENED] != 0) {
            kind = flashing() ? 2 : 1;
        }
        int feet = ((ghosts[base + Session.G_X] + ghosts[base + Session.G_Y]) >> 2) & 1;
        return ghosts[base + Session.G_DIR] + kind * 4 + feet * 16 + (Session.ghostsVisible ? 0 : 32);
    }

    private static boolean flashing() {
        return Session.frightTimer < 2000 / Session.FRAME_MILLIS && ((Session.frightTimer / 10) & 1) != 0;
    }

    /** The ghost's color at an offset from its center, or -1 where it is transparent. */
    static int ghostPixel(int[] ghosts, int g, int dx, int dy) {
        int base = g * Session.G_STRIDE;
        int state = ghosts[base + Session.G_STATE];
        boolean eyesOnly = state == Session.EYES || state == Session.ENTERING;
        boolean frightened = !eyesOnly && ghosts[base + Session.G_FRIGHTENED] != 0;

        if (!frightened) {
            int eyes = eyesPixel(ghosts, base, dx, dy);
            if (eyes != NOT_EYES) {
                return eyes;
            }
            if (eyesOnly) {
                return TRANSPARENT;
            }
        }
        if (!bodyPixel(ghosts, base, dx, dy)) {
            return TRANSPARENT;
        }
        return frightened ? frightenedFacePixel(dx, dy) : ghostColor(g);
    }

    /** Eyes: 4x4 whites with 2x2 pupils looking where the ghost is heading; {@link #NOT_EYES} outside them. */
    private static int eyesPixel(int[] ghosts, int base, int dx, int dy) {
        int dir = ghosts[base + Session.G_DIR];
        int ox = Session.dx(dir);
        int oy = Session.dy(dir);
        int ex = dx - ox;
        int ey = dy - oy;
        boolean inEyeRow = ey >= -4 && ey <= -1;
        boolean inLeftEye = ex >= -5 && ex <= -2;
        boolean inRightEye = ex >= 1 && ex <= 4;
        if (!inEyeRow || (!inLeftEye && !inRightEye)) {
            return NOT_EYES;
        }
        int pupilX = ex >= 1 ? ex - 2 : ex + 1;
        boolean pupil = pupilX + 4 >= 1 + ox && pupilX + 4 <= 2 + ox && ey + 4 >= 1 + oy && ey + 4 <= 2 + oy;
        return pupil ? SceneRenderer.EYE_PUPIL : TftTouchShield.WHITE;
    }

    /** The body silhouette: a round top, straight sides and a wavy, alternating hem. */
    private static boolean bodyPixel(int[] ghosts, int base, int dx, int dy) {
        if (dy <= 0) {
            return dx * dx + dy * dy <= Session.HALF * Session.HALF + Session.HALF;
        }
        if (dy < Session.HALF) {
            return true;
        }
        int feet = ((ghosts[base + Session.G_X] + ghosts[base + Session.G_Y]) >> 2) & 1;
        return ((dx + Session.HALF + feet * 2) & 3) < 2;
    }

    /** Frightened face: two small eyes and a zig-zag mouth, flashing white/red before it wears off. */
    private static int frightenedFacePixel(int dx, int dy) {
        boolean flash = flashing();
        int skin = flash ? TftTouchShield.WHITE : SceneRenderer.FRIGHT_COLOR;
        int face = flash ? TftTouchShield.RED : SceneRenderer.FRIGHT_FACE;
        boolean eyeDot = dy >= -3 && dy <= -2 && (dx == -3 || dx == -2 || dx == 2 || dx == 3);
        boolean mouth = (dy == 2 && (dx & 1) == 0 && Math.abs(dx) <= 4)
                || (dy == 3 && (dx & 1) != 0 && Math.abs(dx) <= 5);
        return eyeDot || mouth ? face : skin;
    }

    static int ghostColor(int g) {
        if (g == 0) {
            return 0xF800;
        }
        if (g == 1) {
            return 0xFDDF;
        }
        if (g == 2) {
            return 0x07FF;
        }
        return 0xFDCA;
    }

    // ---- Small decorative icons: the footer's spare lives and the title screen's chase demo ----

    /** A plain filled circle with a triangular mouth wedge cut from one side, open or closed. */
    static void drawPacIcon(int cx, int cy, int radius, boolean open, boolean facingLeft) {
        TftTouchShield.fillCircle(cx, cy, radius, SceneRenderer.PAC_COLOR);
        if (!open) {
            return;
        }
        for (int i = 0; i < radius; i++) {
            int width = radius - i;
            int x = facingLeft ? cx - radius : cx + i;
            TftTouchShield.fillRect(x, cy - i / 2, width, 1 + i, SceneRenderer.SPACE);
        }
    }

    /** A plain filled circle with two small white eyes, for the title screen's chase demo only. */
    static void drawGhostIcon(int cx, int cy, int radius, int color) {
        TftTouchShield.fillCircle(cx, cy, radius, color);
        TftTouchShield.fillRect(cx - radius / 2 - 1, cy - radius / 2, 2, 3, TftTouchShield.WHITE);
        TftTouchShield.fillRect(cx + radius / 2 - 1, cy - radius / 2, 2, 3, TftTouchShield.WHITE);
    }
}
