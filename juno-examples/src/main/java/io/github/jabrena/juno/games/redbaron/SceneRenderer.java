package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.redbaron.Entities.ENTITIES;
import static io.github.jabrena.juno.games.redbaron.Entities.E_HEADING;
import static io.github.jabrena.juno.games.redbaron.Entities.E_MODE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TIMER;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VX;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VY;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VZ;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Y;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BLIMP;
import static io.github.jabrena.juno.games.redbaron.Entities.T_BULLET;
import static io.github.jabrena.juno.games.redbaron.Entities.T_DEBRIS;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FALLING;
import static io.github.jabrena.juno.games.redbaron.Entities.T_FLAK;
import static io.github.jabrena.juno.games.redbaron.Entities.T_HANGAR;
import static io.github.jabrena.juno.games.redbaron.Entities.T_MARKER;
import static io.github.jabrena.juno.games.redbaron.Entities.T_NONE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PLANE;
import static io.github.jabrena.juno.games.redbaron.Entities.T_PYRAMID;
import static io.github.jabrena.juno.games.redbaron.Entities.T_TRACER;

/** Draws the landscape, entities and fixed cockpit into the display list. */
final class SceneRenderer {
    static final int LINE = TftTouchShield.WHITE;
    static final int HORIZON = 0x8410;
    private static final int MOUNTAIN = 0x5AEB;
    private static final int GROUND_MARK = 0x4208;
    private static final int ENEMY = TftTouchShield.WHITE;
    private static final int BLIMP = TftTouchShield.CYAN;
    static final int TARGET = TftTouchShield.YELLOW;
    private static final int OBSTACLE = 0x8C51;
    private static final int BULLET = TftTouchShield.YELLOW;
    private static final int TRACER = TftTouchShield.RED;
    static final int COCKPIT = 0xAD55;
    private static final int SIGHT = TftTouchShield.GREEN;
    private static final int FIRE = TftTouchShield.ORANGE;

    private SceneRenderer() {
    }

    static void render(short[] lines, int[] ents) {
        DisplayList.begin();
        drawLandscape(lines);
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < ENTITIES; slot++) {
                int b = slot * E_STRIDE;
                int type = ents[b + E_TYPE];
                boolean far = ents[b + E_Z] > Camera.FAR / 3;
                if (type != T_NONE && far == (pass == 0)) {
                    drawEntity(lines, ents, slot);
                }
            }
        }
        drawCockpit(lines);
        DisplayList.present(lines);
    }

    static void drawLandscape(short[] lines) {
        Camera.worldLine(lines, Camera.CENTER_X - 400, Camera.CENTER_Y,
                Camera.CENTER_X + 400, Camera.CENTER_Y, HORIZON);
        int previousX = 0;
        int previousY = 0;
        boolean previousVisible = false;
        for (int i = 0; i <= 24; i++) {
            float relative = i * 15 - Camera.heading;
            while (relative > 180) {
                relative = relative - 360;
            }
            while (relative < -180) {
                relative = relative + 360;
            }
            boolean visible = relative > -70 && relative < 70;
            int x = 0;
            int y = 0;
            if (visible) {
                x = Camera.CENTER_X + Math.round(Camera.FOCAL * (float) Math.tan(Math.toRadians(relative)));
                y = Camera.CENTER_Y - mountainHeight(i % 24);
                if (previousVisible) {
                    Camera.worldLine(lines, previousX, previousY, x, y, MOUNTAIN);
                }
            }
            previousX = x;
            previousY = y;
            previousVisible = visible;
        }
    }

    private static int mountainHeight(int i) {
        return "0H4R<,8NZ@2D+>T6.LX:2F/*".charAt(i) - '&';
    }

    private static void drawEntity(short[] lines, int[] ents, int slot) {
        int b = slot * E_STRIDE;
        int type = ents[b + E_TYPE];
        int x = ents[b + E_X];
        int y = ents[b + E_Y];
        int z = ents[b + E_Z];
        if (z < Camera.NEAR || z > Camera.FAR) {
            return;
        }
        if (type == T_PLANE || type == T_FALLING) {
            drawBiplane(lines, x, y, z, ents[b + E_HEADING], type == T_FALLING ? FIRE : ENEMY);
        } else if (type == T_BLIMP) {
            int color = BLIMP;
            if (ents[b + E_TIMER] > 0) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                color = FIRE;
            }
            drawBlimp(lines, x, y, z, ents[b + E_HEADING], color);
        } else if (type == T_BULLET) {
            Camera.line3(lines, x, y, z, x - ents[b + E_VX], y - ents[b + E_VY],
                    z - ents[b + E_VZ] / 2, BULLET);
        } else if (type == T_TRACER) {
            Camera.line3(lines, x, y, z, x + ents[b + E_VX], y + ents[b + E_VY],
                    z + ents[b + E_VZ] / 2, TRACER);
        } else if (type == T_HANGAR) {
            drawBox(lines, x, z, 55, GroundAttackRound.HANGAR_HEIGHT, TARGET);
            Camera.line3(lines, x - 55, GroundAttackRound.HANGAR_HEIGHT, z,
                    x, GroundAttackRound.HANGAR_HEIGHT + 30, z, TARGET);
            Camera.line3(lines, x, GroundAttackRound.HANGAR_HEIGHT + 30, z,
                    x + 55, GroundAttackRound.HANGAR_HEIGHT, z, TARGET);
        } else if (type == T_FLAK) {
            drawBox(lines, x, z, 30, GroundAttackRound.FLAK_HEIGHT, TARGET);
            Camera.line3(lines, x, GroundAttackRound.FLAK_HEIGHT, z, x, 70, z + 30, TARGET);
        } else if (type == T_PYRAMID) {
            drawPyramid(lines, x, z);
        } else if (type == T_MARKER) {
            Camera.line3(lines, x - 30, 0, z, x + 30, 0, z, GROUND_MARK);
            Camera.line3(lines, x, 0, z - 30, x, 0, z + 30, GROUND_MARK);
        } else if (type == T_DEBRIS) {
            int size = ents[b + E_MODE] * (12 - ents[b + E_TIMER]) / 8;
            for (int k = 0; k < 6; k++) {
                float angle = (float) (k * Math.PI / 3 + ents[b + E_TIMER] * 0.3);
                int ex = Math.round((float) Math.cos(angle) * size);
                int ey = Math.round((float) Math.sin(angle) * size);
                Camera.line3(lines, x + ex / 2, y + ey / 2, z, x + ex, y + ey, z,
                        k % 2 == 0 ? FIRE : LINE);
            }
        }
    }

    static void drawBiplane(short[] lines, int x, int y, int z, int heading, int color) {
        float radians = (float) Math.toRadians(heading);
        int c = Math.round((float) Math.cos(radians) * 1024);
        int s = Math.round((float) Math.sin(radians) * 1024);
        modelLine(lines, x, y, z, c, s, 0, 0, 42, 0, 0, -52, color);
        modelLine(lines, x, y, z, c, s, -62, 16, 12, 62, 16, 12, color);
        modelLine(lines, x, y, z, c, s, -62, 16, -8, 62, 16, -8, color);
        modelLine(lines, x, y, z, c, s, -62, 16, 12, -62, 16, -8, color);
        modelLine(lines, x, y, z, c, s, 62, 16, 12, 62, 16, -8, color);
        modelLine(lines, x, y, z, c, s, -54, -6, 10, 54, -6, 10, color);
        modelLine(lines, x, y, z, c, s, -54, -6, -6, 54, -6, -6, color);
        modelLine(lines, x, y, z, c, s, -40, -6, 2, -40, 16, 2, color);
        modelLine(lines, x, y, z, c, s, 40, -6, 2, 40, 16, 2, color);
        modelLine(lines, x, y, z, c, s, -20, 0, -48, 20, 0, -48, color);
        modelLine(lines, x, y, z, c, s, 0, 0, -44, 0, 18, -52, color);
        modelLine(lines, x, y, z, c, s, -12, 0, 44, 12, 0, 44, color);
        modelLine(lines, x, y, z, c, s, 0, -12, 44, 0, 12, 44, color);
    }

    private static void drawBlimp(short[] lines, int x, int y, int z, int heading, int color) {
        float radians = (float) Math.toRadians(heading);
        int c = Math.round((float) Math.cos(radians) * 1024);
        int s = Math.round((float) Math.sin(radians) * 1024);
        int previousLength = 0;
        int previousHeight = 0;
        for (int k = 0; k <= 12; k++) {
            float angle = (float) (k * Math.PI / 6);
            int length = Math.round((float) Math.cos(angle) * 170);
            int height = Math.round((float) Math.sin(angle) * 60);
            if (k > 0) {
                modelLine(lines, x, y, z, c, s, 0, previousHeight, previousLength,
                        0, height, length, color);
            }
            previousLength = length;
            previousHeight = height;
        }
        modelLine(lines, x, y, z, c, s, 0, 30, -140, 0, 80, -175, color);
        modelLine(lines, x, y, z, c, s, 0, 80, -175, 0, 40, -175, color);
        modelLine(lines, x, y, z, c, s, 0, -60, 30, 0, -78, 30, color);
        modelLine(lines, x, y, z, c, s, 0, -78, 30, 0, -78, -30, color);
        modelLine(lines, x, y, z, c, s, 0, -78, -30, 0, -60, -30, color);
    }

    private static void drawBox(short[] lines, int x, int z, int half, int height, int color) {
        Camera.line3(lines, x - half, 0, z - half, x + half, 0, z - half, color);
        Camera.line3(lines, x - half, height, z - half, x + half, height, z - half, color);
        Camera.line3(lines, x - half, 0, z - half, x - half, height, z - half, color);
        Camera.line3(lines, x + half, 0, z - half, x + half, height, z - half, color);
        Camera.line3(lines, x - half, height, z - half, x - half, height, z + half, color);
        Camera.line3(lines, x + half, height, z - half, x + half, height, z + half, color);
        Camera.line3(lines, x - half, height, z + half, x + half, height, z + half, color);
    }

    private static void drawPyramid(short[] lines, int x, int z) {
        int half = 80;
        int height = GroundAttackRound.PYRAMID_HEIGHT;
        Camera.line3(lines, x - half, 0, z - half, x + half, 0, z - half, OBSTACLE);
        Camera.line3(lines, x - half, 0, z - half, x, height, z, OBSTACLE);
        Camera.line3(lines, x + half, 0, z - half, x, height, z, OBSTACLE);
        Camera.line3(lines, x - half, 0, z + half, x, height, z, OBSTACLE);
        Camera.line3(lines, x + half, 0, z + half, x, height, z, OBSTACLE);
        Camera.line3(lines, x - half, 0, z - half, x - half, 0, z + half, OBSTACLE);
        Camera.line3(lines, x + half, 0, z - half, x + half, 0, z + half, OBSTACLE);
    }

    static void drawCockpit(short[] lines) {
        DisplayList.addLine(lines, 128, DisplayList.HEIGHT - 1, 146, 196, COCKPIT);
        DisplayList.addLine(lines, 140, DisplayList.HEIGHT - 1, 152, 196, COCKPIT);
        DisplayList.addLine(lines, 146, 196, 152, 196, COCKPIT);
        DisplayList.addLine(lines, 192, DisplayList.HEIGHT - 1, 174, 196, COCKPIT);
        DisplayList.addLine(lines, 180, DisplayList.HEIGHT - 1, 168, 196, COCKPIT);
        DisplayList.addLine(lines, 174, 196, 168, 196, COCKPIT);
        DisplayList.addLine(lines, 70, DisplayList.HEIGHT - 1, 110, 222, COCKPIT);
        DisplayList.addLine(lines, 110, 222, 210, 222, COCKPIT);
        DisplayList.addLine(lines, 210, 222, 250, DisplayList.HEIGHT - 1, COCKPIT);
        for (int k = 0; k < 8; k++) {
            DisplayList.addLine(lines, Camera.CENTER_X + sightX(k), Camera.CENTER_Y + sightY(k),
                    Camera.CENTER_X + sightX(k + 1), Camera.CENTER_Y + sightY(k + 1), SIGHT);
        }
        DisplayList.addLine(lines, Camera.CENTER_X, Camera.CENTER_Y, Camera.CENTER_X, Camera.CENTER_Y, SIGHT);
    }

    static int sightX(int k) {
        int step = k % 8;
        if (step == 0) {
            return 12;
        }
        if (step == 1 || step == 7) {
            return 8;
        }
        if (step == 3 || step == 5) {
            return -8;
        }
        if (step == 4) {
            return -12;
        }
        return 0;
    }

    static int sightY(int k) {
        return sightX(k + 6);
    }

    private static void modelLine(short[] lines, int x, int y, int z, int c, int s,
                                  int ax, int ay, int az, int bx, int by, int bz, int color) {
        Camera.line3(lines, x + (ax * c + az * s) / 1024, y + ay, z + (az * c - ax * s) / 1024,
                x + (bx * c + bz * s) / 1024, y + by, z + (bz * c - bx * s) / 1024, color);
    }
}
