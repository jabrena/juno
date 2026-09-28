package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Random;

/**
 * Everything in the world, packed as {@value #ENTITIES} records of {@value #E_STRIDE} ints: fighters,
 * fireballs, towers, turrets, catwalks, the exhaust port, debris and the lasers in flight. Each frame
 * they move towards the camera, and those that reach it hit the X-wing or leave the view.
 */
final class Entities {
    static final int ENTITIES = 24;
    static final int E_TYPE = 0;
    static final int E_X = 1;
    static final int E_Y = 2;
    static final int E_Z = 3;
    static final int E_VX = 4;
    static final int E_VY = 5;
    static final int E_VZ = 6;
    static final int E_TIMER = 7;
    /** Where the entity was last drawn and how close a shot must land to hit it (0: not a target). */
    static final int E_SX = 8;
    static final int E_SY = 9;
    static final int E_SR = 10;
    /** TIE: 1 once it breaks off its attack. Tower: its height. Debris: its size. Shot: its target. */
    static final int E_AUX = 11;
    /** Tower: 1 while its top stands. Shot: its target's type. */
    static final int E_FLAG = 12;
    static final int E_STRIDE = 13;

    static final int T_NONE = 0;
    static final int T_TIE = 1;
    static final int T_VADER = 2;
    static final int T_FIREBALL = 3;
    static final int T_TOWER = 4;
    static final int T_TURRET = 5;
    static final int T_CATWALK = 6;
    static final int T_PORT = 7;
    static final int T_DEBRIS = 8;
    static final int T_SHOT = 9;

    // World geometry shared by the phases, the lasers and the renderer.
    static final int GROUND = -140;
    static final int TRENCH_HALF_WIDTH = 170;
    static final int CAP_HEIGHT = 26;
    /** How far above or below a catwalk's middle the X-wing must pass to clear it. */
    private static final int CATWALK_CLEARANCE = 44;
    /** How close to the camera a fireball must arrive to hit. */
    private static final int FIREBALL_REACH = 50;

    private Entities() {
    }

    /** Moves every entity {@code speed} units nearer, and lets each act. */
    static void move(int[] ents, int speed) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE || type == T_SHOT) {
                continue;
            }
            ents[b + E_X] = ents[b + E_X] + ents[b + E_VX];
            ents[b + E_Y] = ents[b + E_Y] + ents[b + E_VY];
            ents[b + E_Z] = ents[b + E_Z] + ents[b + E_VZ] - speed;
            int z = ents[b + E_Z];
            if (type == T_TIE || type == T_VADER) {
                SpacePhase.flyTie(ents, b);
            } else if (type == T_FIREBALL) {
                if (z <= Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    if (Math.abs(ents[b + E_X] - Camera.camX) < FIREBALL_REACH
                            && Math.abs(ents[b + E_Y] - Camera.camY) < FIREBALL_REACH) {
                        Session.shieldHit();
                    }
                }
            } else if (type == T_TOWER || type == T_TURRET) {
                if (z < Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                } else {
                    gunnerFires(ents, b);
                }
            } else if (type == T_CATWALK) {
                if (z <= Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    if (Math.abs(ents[b + E_Y] - Camera.camY) < CATWALK_CLEARANCE) {
                        Session.shieldHit();
                    }
                }
            } else if (type == T_PORT) {
                if (z < Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    TrenchPhase.portMissed = true;
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0 || z < Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /** Towers (from their tops, while they stand) and trench turrets fire now and then. */
    private static void gunnerFires(int[] ents, int b) {
        int z = ents[b + E_Z];
        if (z < 500 || z > 1900) {
            return;
        }
        int y = ents[b + E_Y];
        if (ents[b + E_TYPE] == T_TOWER) {
            if (ents[b + E_FLAG] == 0) {
                return;
            }
            y = GROUND + ents[b + E_AUX] + CAP_HEIGHT / 2;
        }
        ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
        if (ents[b + E_TIMER] <= 0) {
            // Turrets line the whole trench, so each fires less often than a tower.
            int low = ents[b + E_TYPE] == T_TURRET ? 70 : 40;
            ents[b + E_TIMER] = Math.max(24, Random.nextInt(low, low + 60) - 4 * Session.wave);
            fireball(ents, ents[b + E_X], y, z);
        }
    }

    /** Launches a fireball from (x, y, z) aimed at where the camera is now. */
    static void fireball(int[] ents, int x, int y, int z) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int speed = Math.min(20 + 2 * Session.wave, 36);
        int frames = Math.max(1, (z - Camera.NEAR) / (speed + Session.flightSpeed()));
        ents[b + E_TYPE] = T_FIREBALL;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        ents[b + E_VX] = (Camera.camX - x) / frames;
        ents[b + E_VY] = (Camera.camY - y) / frames;
        ents[b + E_VZ] = -speed;
    }

    static void debris(int[] ents, int x, int y, int z, int size) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_DEBRIS;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        ents[b + E_TIMER] = 10;
        ents[b + E_AUX] = size;
    }

    static int freeSlot(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            if (ents[slot * E_STRIDE + E_TYPE] == T_NONE) {
                clearSlot(ents, slot);
                return slot;
            }
        }
        return -1;
    }

    private static void clearSlot(int[] ents, int slot) {
        for (int k = 0; k < E_STRIDE; k++) {
            ents[slot * E_STRIDE + k] = 0;
        }
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

    static void clear(int[] ents) {
        for (int i = 0; i < ENTITIES * E_STRIDE; i++) {
            ents[i] = 0;
        }
    }
}
