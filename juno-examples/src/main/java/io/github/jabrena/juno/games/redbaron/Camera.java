package io.github.jabrena.juno.games.redbaron;

import static io.github.jabrena.juno.games.redbaron.Entities.ENTITIES;
import static io.github.jabrena.juno.games.redbaron.Entities.E_HEADING;
import static io.github.jabrena.juno.games.redbaron.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_TYPE;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VX;
import static io.github.jabrena.juno.games.redbaron.Entities.E_VZ;
import static io.github.jabrena.juno.games.redbaron.Entities.E_X;
import static io.github.jabrena.juno.games.redbaron.Entities.E_Z;
import static io.github.jabrena.juno.games.redbaron.Entities.T_NONE;

/** The biplane's altitude, heading and banked perspective view. */
final class Camera {
    static final int CENTER_X = 160;
    static final int CENTER_Y = 132;
    static final int FOCAL = 160;
    static final int NEAR = 30;
    static final int FAR = 3000;

    private static final int MIN_ALTITUDE = 40;
    private static final int MAX_ALTITUDE = 520;
    private static final int CLIMB = 6;
    private static final int MAX_BANK = 35;
    private static final int BANK_STEP = 3;
    private static final float TURN_RATE = 0.06f;

    static int altitude;
    static float bank;
    static float heading;
    static int bankCos = 1024;
    static int bankSin;

    private Camera() {
    }

    static void startRound(Round round) {
        altitude = round == Round.DOGFIGHT ? 300 : 200;
        level();
    }

    static void level() {
        bank = 0;
        bankCos = 1024;
        bankSin = 0;
    }

    /** Banks, turns, and climbs or dives according to the virtual joystick. */
    static void steer(int[] ents) {
        float target = Controls.stickX * MAX_BANK / 100.0f;
        bank = bank + Math.max(-BANK_STEP, Math.min(BANK_STEP, target - bank));
        float radians = (float) Math.toRadians(bank);
        bankCos = Math.round((float) Math.cos(radians) * 1024);
        bankSin = Math.round((float) Math.sin(radians) * 1024);
        altitude = clamp(altitude + Controls.stickY * CLIMB / 100, MIN_ALTITUDE, MAX_ALTITUDE);

        float turn = bank * TURN_RATE;
        heading = heading + turn;
        if (heading >= 360) {
            heading = heading - 360;
        } else if (heading < 0) {
            heading = heading + 360;
        }
        if (turn != 0) {
            turnWorld(ents, turn);
        }
    }

    private static void turnWorld(int[] ents, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        int c = Math.round((float) Math.cos(radians) * 4096);
        int s = Math.round((float) Math.sin(radians) * 4096);
        int turn = Math.round(degrees);
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_NONE) {
                continue;
            }
            int x = ents[b + E_X];
            int z = ents[b + E_Z];
            ents[b + E_X] = (x * c - z * s) / 4096;
            ents[b + E_Z] = (x * s + z * c) / 4096;
            int vx = ents[b + E_VX];
            int vz = ents[b + E_VZ];
            ents[b + E_VX] = (vx * c - vz * s) / 4096;
            ents[b + E_VZ] = (vx * s + vz * c) / 4096;
            ents[b + E_HEADING] = ents[b + E_HEADING] - turn;
        }
    }

    static int projectX(int x, int z) {
        return clamp(CENTER_X + x * FOCAL / z, -20000, 20000);
    }

    static int projectY(int y, int z) {
        return clamp(CENTER_Y - (y - altitude) * FOCAL / z, -20000, 20000);
    }

    static void line3(short[] lines, int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        if (z0 < NEAR && z1 < NEAR) {
            return;
        }
        if (z0 < NEAR) {
            x0 = x0 + (x1 - x0) * (NEAR - z0) / (z1 - z0);
            y0 = y0 + (y1 - y0) * (NEAR - z0) / (z1 - z0);
            z0 = NEAR;
        } else if (z1 < NEAR) {
            x1 = x1 + (x0 - x1) * (NEAR - z1) / (z0 - z1);
            y1 = y1 + (y0 - y1) * (NEAR - z1) / (z0 - z1);
            z1 = NEAR;
        }
        worldLine(lines, projectX(x0, z0), projectY(y0, z0), projectX(x1, z1), projectY(y1, z1), color);
    }

    static void worldLine(short[] lines, int x0, int y0, int x1, int y1, int color) {
        DisplayList.addLine(lines, rollX(x0, y0), rollY(x0, y0), rollX(x1, y1), rollY(x1, y1), color);
    }

    static int rollX(int x, int y) {
        return CENTER_X + ((x - CENTER_X) * bankCos + (y - CENTER_Y) * bankSin) / 1024;
    }

    static int rollY(int x, int y) {
        return CENTER_Y + ((y - CENTER_Y) * bankCos - (x - CENTER_X) * bankSin) / 1024;
    }

    static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
