package io.github.jabrena.juno.games.spaceparanoids;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.games.spaceparanoids.Entities.ENTITIES;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_TX;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_TZ;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_X;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.F_Z;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_AUX;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_STRIDE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_TIMER;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.I_TYPE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.ONE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.TO_CELLS;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_BLAST;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_ENEMY_SHOT;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_HUNTER;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_NONE;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_POOL;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_SHOT;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TANK;
import static io.github.jabrena.juno.games.spaceparanoids.Entities.T_TURRET;

/** Ray-cast maze walls and the 3D wireframe entities within them. */
final class SceneRenderer {
    private static final int SAMPLE = 4;
    static final int SAMPLES = DisplayList.WIDTH / SAMPLE + 1;

    static final int WALL_TOP = TftTouchShield.CYAN;
    private static final int WALL_BOTTOM = 0x0410;
    static final int WALL_SEAM = 0x0292;
    static final int HUNTER = TftTouchShield.ORANGE;
    static final int TANK = TftTouchShield.RED;
    static final int TURRET = TftTouchShield.YELLOW;
    static final int POOL = TftTouchShield.GREEN;
    private static final int SHOT = TftTouchShield.WHITE;
    private static final int ENEMY_SHOT = TftTouchShield.MAGENTA;

    private SceneRenderer() {
    }

    static void render(byte[] maze, short[] lines, int[] depth, int[] faces, int[] fs, int[] is) {
        DisplayList.begin();
        drawWalls(maze, lines, depth, faces);
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (is[slot * I_STRIDE + I_TYPE] != T_NONE) {
                drawEntity(lines, depth, fs, is, slot);
            }
        }
        DisplayList.addLine(lines, Camera.CENTER_X - 10, Camera.CENTER_Y,
                Camera.CENTER_X - 4, Camera.CENTER_Y, TftTouchShield.GREEN);
        DisplayList.addLine(lines, Camera.CENTER_X + 4, Camera.CENTER_Y,
                Camera.CENTER_X + 10, Camera.CENTER_Y, TftTouchShield.GREEN);
        DisplayList.addLine(lines, Camera.CENTER_X, Camera.CENTER_Y - 8,
                Camera.CENTER_X, Camera.CENTER_Y - 3, TftTouchShield.GREEN);
        DisplayList.present(lines);
    }

    static void drawWalls(byte[] maze, short[] lines, int[] depth, int[] faces) {
        for (int i = 0; i < SAMPLES; i++) {
            Camera.castRay(maze, sampleColumn(i));
            depth[i] = (int) (Camera.rayDistance * ONE);
            faces[i] = Camera.rayFace;
        }
        int face = faces[0];
        int start = 0;
        float startDistance = depth[0] * TO_CELLS;
        int last = 0;
        float lastDistance = startDistance;
        for (int i = 1; i < SAMPLES; i++) {
            int column = sampleColumn(i);
            while (faces[i] != face) {
                int lo = last;
                float loDistance = lastDistance;
                int hi = column;
                float hiDistance = depth[i] * TO_CELLS;
                int hiFace = faces[i];
                while (hi - lo > 1) {
                    int mid = (lo + hi) / 2;
                    Camera.castRay(maze, mid);
                    if (Camera.rayFace == face) {
                        lo = mid;
                        loDistance = Camera.rayDistance;
                    } else {
                        hi = mid;
                        hiDistance = Camera.rayDistance;
                        hiFace = Camera.rayFace;
                    }
                }
                wallRun(maze, lines, face, start, startDistance, lo, loDistance);
                if (loDistance <= hiDistance) {
                    DisplayList.addLine(lines, lo, wallTop(loDistance), lo, wallBottom(loDistance), WALL_TOP);
                } else {
                    DisplayList.addLine(lines, hi, wallTop(hiDistance), hi, wallBottom(hiDistance), WALL_TOP);
                }
                face = hiFace;
                start = hi;
                startDistance = hiDistance;
                last = hi;
                lastDistance = hiDistance;
            }
            last = column;
            lastDistance = depth[i] * TO_CELLS;
        }
        wallRun(maze, lines, face, start, startDistance, last, lastDistance);
    }

    private static int sampleColumn(int i) {
        return Math.min(i * SAMPLE, DisplayList.WIDTH - 1);
    }

    private static void wallRun(byte[] maze, short[] lines, int face,
                                int c0, float d0, int c1, float d1) {
        DisplayList.addLine(lines, c0, wallTop(d0), c1, wallTop(d1), WALL_TOP);
        DisplayList.addLine(lines, c0, wallBottom(d0), c1, wallBottom(d1), WALL_BOTTOM);
        Camera.castRay(maze, c0);
        float a0 = Camera.rayAlong;
        Camera.castRay(maze, c1);
        float a1 = Camera.rayAlong;
        float plane = (face >> 2);
        int from = (int) Math.ceil(Math.min(a0, a1) + 0.02f);
        int to = (int) Math.floor(Math.max(a0, a1) - 0.02f);
        for (int k = from; k <= to; k++) {
            float sx = (face & 1) == 0 ? plane - Camera.posX : k - Camera.posX;
            float sz = (face & 1) == 0 ? k - Camera.posZ : plane - Camera.posZ;
            float distance = Camera.inverse * (-Camera.planeZ * sx + Camera.planeX * sz);
            if (distance < Camera.NEAR) {
                continue;
            }
            int column = Camera.screenX(Camera.inverse * (Camera.dirZ * sx - Camera.dirX * sz), distance);
            if (column > c0 && column < c1) {
                DisplayList.addLine(lines, column, wallTop(distance), column, wallBottom(distance), WALL_SEAM);
            }
        }
    }

    private static int wallTop(float distance) {
        return Camera.clamp(Camera.CENTER_Y
                - (int) (Camera.FOCAL * (Camera.WALL_HEIGHT - Camera.EYE) / distance), -20000, 20000);
    }

    private static int wallBottom(float distance) {
        return Camera.clamp(Camera.CENTER_Y + (int) (Camera.FOCAL * Camera.EYE / distance), -20000, 20000);
    }

    private static void drawEntity(short[] lines, int[] depth, int[] fs, int[] is, int slot) {
        int f = slot * F_STRIDE;
        int b = slot * I_STRIDE;
        float x = fs[f + F_X] * TO_CELLS;
        float z = fs[f + F_Z] * TO_CELLS;
        float sx = x - Camera.posX;
        float sz = z - Camera.posZ;
        float distance = Camera.inverse * (-Camera.planeZ * sx + Camera.planeX * sz);
        if (distance < Camera.NEAR) {
            return;
        }
        float lateral = Camera.inverse * (Camera.dirZ * sx - Camera.dirX * sz);
        int column = Camera.CENTER_X + (int) (Camera.CENTER_X * lateral / distance);
        if (column < -40 || column > DisplayList.WIDTH + 40) {
            return;
        }
        int sample = Camera.clamp((column + SAMPLE / 2) / SAMPLE, 0, SAMPLES - 1);
        if (distance > depth[sample] * TO_CELLS + 0.3f) {
            return;
        }
        drawType(lines, fs, is, slot, f, b, x, z);
    }

    private static void drawType(short[] lines, int[] fs, int[] is, int slot, int f, int b, float x, float z) {
        int type = is[b + I_TYPE];
        float spin = Session.frame * 0.12f + slot;
        if (type == T_HUNTER) {
            drawHunter(lines, x, z, 0.62f + 0.05f * (float) Math.sin(spin), spin);
        } else if (type == T_TANK) {
            drawTank(lines, x, z, fs[f + F_TX] * TO_CELLS - x, fs[f + F_TZ] * TO_CELLS - z);
        } else if (type == T_TURRET) {
            drawTurret(lines, x, z, spin * 0.5f);
        } else if (type == T_POOL) {
            float r = 0.18f + 0.04f * (float) Math.sin(spin * 2f);
            floorDiamond(lines, x, z, r, POOL);
            floorDiamond(lines, x, z, r * 0.5f, POOL);
        } else if (type == T_SHOT || type == T_ENEMY_SHOT) {
            drawShot(lines, x, z, type);
        } else if (type == T_BLAST) {
            drawBlast(lines, is, b, x, z);
        }
    }

    private static void drawShot(short[] lines, float x, float z, int type) {
        int color = type == T_SHOT ? SHOT : ENEMY_SHOT;
        float y = type == T_SHOT ? 0.22f : 0.35f;
        float size = 0.05f;
        Camera.line3(lines, x - size, y, z, x + size, y, z, color);
        Camera.line3(lines, x, y - size, z, x, y + size, z, color);
        Camera.line3(lines, x, y, z - size, x, y, z + size, color);
    }

    private static void drawBlast(short[] lines, int[] is, int b, float x, float z) {
        int age = is[b + I_TIMER];
        float r0 = 0.04f * age;
        float r1 = r0 + 0.12f;
        float y = is[b + I_AUX] == T_HUNTER ? 0.62f : 0.2f;
        for (int k = 0; k < 8; k++) {
            float c = (float) Math.cos(k * 0.785f);
            float s = (float) Math.sin(k * 0.785f);
            int color = (k & 1) == 0 ? TftTouchShield.YELLOW : TftTouchShield.ORANGE;
            Camera.line3(lines, x + c * r0, y + s * r0, z, x + c * r1, y + s * r1, z, color);
        }
    }

    static void drawHunter(short[] lines, float x, float z, float y, float spin) {
        float r = 0.2f;
        float c = (float) Math.cos(spin) * r;
        float s = (float) Math.sin(spin) * r;
        float ax = x + c;
        float az = z + s;
        float bx = x - s;
        float bz = z + c;
        float cx = x - c;
        float cz = z - s;
        float ex = x + s;
        float ez = z - c;
        float top = y + 0.24f;
        float bottom = y - 0.24f;
        Camera.line3(lines, ax, y, az, bx, y, bz, HUNTER);
        Camera.line3(lines, bx, y, bz, cx, y, cz, HUNTER);
        Camera.line3(lines, cx, y, cz, ex, y, ez, HUNTER);
        Camera.line3(lines, ex, y, ez, ax, y, az, HUNTER);
        Camera.line3(lines, x, top, z, ax, y, az, HUNTER);
        Camera.line3(lines, x, top, z, bx, y, bz, HUNTER);
        Camera.line3(lines, x, top, z, cx, y, cz, HUNTER);
        Camera.line3(lines, x, top, z, ex, y, ez, HUNTER);
        Camera.line3(lines, x, bottom, z, ax, y, az, TftTouchShield.YELLOW);
        Camera.line3(lines, x, bottom, z, cx, y, cz, TftTouchShield.YELLOW);
    }

    private static void drawTank(short[] lines, float x, float z, float hx, float hz) {
        float length = (float) Math.sqrt(hx * hx + hz * hz);
        float fx = 1f;
        float fz = 0f;
        if (length > 0.001f) {
            fx = hx / length;
            fz = hz / length;
        }
        float rx = -fz;
        float rz = fx;
        box(lines, x, z, fx, fz, rx, rz, 0.26f, 0.2f, 0f, 0.12f, TANK);
        box(lines, x, z, fx, fz, rx, rz, 0.12f, 0.1f, 0.12f, 0.22f, TANK);
        Camera.line3(lines, x + fx * 0.12f, 0.17f, z + fz * 0.12f,
                x + fx * 0.36f, 0.17f, z + fz * 0.36f, TANK);
    }

    private static void drawTurret(short[] lines, float x, float z, float spin) {
        float c = (float) Math.cos(spin) * 0.2f;
        float s = (float) Math.sin(spin) * 0.2f;
        float tipY = 0.55f;
        Camera.line3(lines, x + c, 0f, z + s, x - s, 0f, z + c, TURRET);
        Camera.line3(lines, x - s, 0f, z + c, x - c, 0f, z - s, TURRET);
        Camera.line3(lines, x - c, 0f, z - s, x + s, 0f, z - c, TURRET);
        Camera.line3(lines, x + s, 0f, z - c, x + c, 0f, z + s, TURRET);
        Camera.line3(lines, x, tipY, z, x + c, 0f, z + s, TURRET);
        Camera.line3(lines, x, tipY, z, x - s, 0f, z + c, TURRET);
        Camera.line3(lines, x, tipY, z, x - c, 0f, z - s, TURRET);
        Camera.line3(lines, x, tipY, z, x + s, 0f, z - c, TURRET);
    }

    private static void box(short[] lines, float x, float z, float fx, float fz, float rx, float rz,
                            float l, float w, float y0, float y1, int color) {
        float ax = x + fx * l + rx * w;
        float az = z + fz * l + rz * w;
        float bx = x + fx * l - rx * w;
        float bz = z + fz * l - rz * w;
        float cx = x - fx * l - rx * w;
        float cz = z - fz * l - rz * w;
        float dx = x - fx * l + rx * w;
        float dz = z - fz * l + rz * w;
        for (int edge = 0; edge < 2; edge++) {
            float y = edge == 0 ? y0 : y1;
            Camera.line3(lines, ax, y, az, bx, y, bz, color);
            Camera.line3(lines, bx, y, bz, cx, y, cz, color);
            Camera.line3(lines, cx, y, cz, dx, y, dz, color);
            Camera.line3(lines, dx, y, dz, ax, y, az, color);
        }
    }

    private static void floorDiamond(short[] lines, float x, float z, float r, int color) {
        Camera.line3(lines, x + r, 0f, z, x, 0f, z + r, color);
        Camera.line3(lines, x, 0f, z + r, x - r, 0f, z, color);
        Camera.line3(lines, x - r, 0f, z, x, 0f, z - r, color);
        Camera.line3(lines, x, 0f, z - r, x + r, 0f, z, color);
    }
}
