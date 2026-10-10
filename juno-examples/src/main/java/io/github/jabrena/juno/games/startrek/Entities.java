package io.github.jabrena.juno.games.startrek;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.startrek.Geometry.FIX;

/**
 * Everything in the sector besides the Enterprise, as packed records in one {@code int[]}:
 * positions and velocities in world units x FIX, heading in degrees clockwise from up. Also the
 * slot helpers, distances, and the loop that moves everything one frame.
 */
final class Entities {
    static final int ENTITIES = 24;
    static final int E_TYPE = 0;
    static final int E_X = 1;
    static final int E_Y = 2;
    static final int E_VX = 3;
    static final int E_VY = 4;
    static final int E_HEADING = 5;
    static final int E_TIMER = 6;
    static final int E_HP = 7;
    /** Klingon: 1 when attacking the starbase. Debris: its size. */
    static final int E_AUX = 8;
    /** Klingon and Nomad: frames until the next torpedo or mine. */
    static final int E_COOL = 9;
    static final int E_STRIDE = 10;

    static final int T_NONE = 0;
    static final int T_KLINGON = 1;
    static final int T_TORPEDO = 2;
    static final int T_PHOTON = 3;
    static final int T_STARBASE = 4;
    static final int T_SAUCER = 5;
    static final int T_NOMAD = 6;
    static final int T_MINE = 7;
    static final int T_DEBRIS = 8;

    static final int KLINGON_HP = 2;
    static final int STARBASE_HP = 6;
    static final int NOMAD_HP = 4;
    static final int HIT_RADIUS = 14;

    private Entities() {
    }

    /** Moves everything one frame, then lets each object act. */
    static void move(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE) {
                continue;
            }
            ents[b + E_X] = Geometry.wrapPosition(ents[b + E_X] + ents[b + E_VX]);
            ents[b + E_Y] = Geometry.wrapPosition(ents[b + E_Y] + ents[b + E_VY]);
            if (type == T_KLINGON) {
                Enemies.flyKlingon(ents, b);
            } else if (type == T_SAUCER) {
                Enemies.flySaucer(ents, b);
            } else if (type == T_NOMAD) {
                Enemies.flyNomad(ents, b);
            } else if (type == T_TORPEDO) {
                Combat.flyTorpedo(ents, b);
            } else if (type == T_PHOTON) {
                Combat.flyPhoton(ents, slot);
            } else if (type == T_MINE) {
                Enemies.touchMine(ents, b);
            } else if (type == T_STARBASE) {
                Enterprise.dockIfClose(ents, b);
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    static void debris(int[] ents, int x, int y, int size) {
        int slot = spawn(ents, T_DEBRIS, x, y, 1);
        if (slot >= 0) {
            ents[slot * E_STRIDE + E_TIMER] = 8;
            ents[slot * E_STRIDE + E_AUX] = size;
        }
    }

    static boolean isHostile(int type) {
        return type == T_KLINGON || type == T_TORPEDO || type == T_SAUCER || type == T_NOMAD || type == T_MINE;
    }

    /** A rough radius of each kind of object, in world units. */
    static int radius(int type) {
        if (type == T_KLINGON) {
            return 14;
        }
        if (type == T_STARBASE) {
            return 26;
        }
        if (type == T_NOMAD) {
            return 12;
        }
        if (type == T_SAUCER) {
            return 9;
        }
        return 4;
    }

    static int distance(int[] ents, int a, int b) {
        int dx = Geometry.wrap(ents[a + E_X] - ents[b + E_X]) / FIX;
        int dy = Geometry.wrap(ents[a + E_Y] - ents[b + E_Y]) / FIX;
        return (int) Math.sqrt((float) dx * dx + (float) dy * dy);
    }

    static int distanceToShip(int[] ents, int b) {
        int dx = Geometry.wrap(ents[b + E_X] - Enterprise.x) / FIX;
        int dy = Geometry.wrap(ents[b + E_Y] - Enterprise.y) / FIX;
        return (int) Math.sqrt((float) dx * dx + (float) dy * dy);
    }

    static int spawn(int[] ents, int type, int x, int y, int hp) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            if (ents[b + E_TYPE] == T_NONE) {
                for (int k = 0; k < E_STRIDE; k++) {
                    ents[b + k] = 0;
                }
                ents[b + E_TYPE] = type;
                ents[b + E_X] = Geometry.wrapPosition(x);
                ents[b + E_Y] = Geometry.wrapPosition(y);
                ents[b + E_HP] = hp;
                return slot;
            }
        }
        return -1;
    }

    /** Spawns an enemy somewhere in the sector, at least 400 units from the Enterprise. */
    static int spawnAway(int[] ents, int type, int hp) {
        int angle = Random.nextInt(360);
        int distance = Random.nextInt(400, 900);
        float radians = (float) Math.toRadians(angle);
        int x = Enterprise.x + Math.round((float) Math.sin(radians) * distance) * FIX;
        int y = Enterprise.y - Math.round((float) Math.cos(radians) * distance) * FIX;
        int slot = spawn(ents, type, x, y, hp);
        if (slot >= 0) {
            ents[slot * E_STRIDE + E_TIMER] = Random.nextInt(20, 80);
            ents[slot * E_STRIDE + E_COOL] = Random.nextInt(30, 80);
        }
        return slot;
    }

    static int find(int[] ents, int type) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                return slot;
            }
        }
        return -1;
    }

    static int count(int[] ents, int type) {
        int n = 0;
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == type) {
                n = n + 1;
            }
        }
        return n;
    }

    static void clear(int[] records, int length) {
        for (int i = 0; i < length; i++) {
            records[i] = 0;
        }
    }
}
