package io.github.jabrena.juno.games.empirestrikesback;

/**
 * Everything in the world, packed as {@value #ENTITIES} records of {@value #E_STRIDE} ints: probe
 * droids, walkers, asteroids, TIE fighters, fireballs, debris and the lasers in flight. Each frame
 * they move towards the camera, and those that reach it hit your craft or leave the view.
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
    /** Where the entity's target was last drawn and how close a shot must land to hit it (0: none). */
    static final int E_SX = 8;
    static final int E_SY = 9;
    static final int E_SR = 10;
    /** Asteroid: radius. Debris: size. Shot: the target slot. TIE: 1 once it breaks off. */
    static final int E_AUX = 11;
    /** Asteroid: shape seed. Shot: the target's type. AT-AT: the direction it walks. */
    static final int E_FLAG = 12;
    static final int E_HP = 13;
    static final int E_STRIDE = 14;

    static final int T_NONE = 0;
    static final int T_PROBE = 1;
    static final int T_ATAT = 2;
    static final int T_ATST = 3;
    static final int T_ASTEROID = 4;
    static final int T_TIE = 5;
    static final int T_FIREBALL = 6;
    static final int T_DEBRIS = 7;
    static final int T_SHOT = 8;

    // World geometry shared by the rounds, the lasers, the renderer and the opening.
    static final int GROUND = -150;
    static final int ATAT_HEAD_Y = GROUND + 190;
    static final int ATST_HEAD_Y = GROUND + 150;
    static final int ATAT_HITS = 3;
    /** How close to the camera a fireball or asteroid must arrive to hit. */
    static final int FIREBALL_REACH = 50;
    /** How much a fireball may correct its course towards you each frame. */
    private static final int HOMING = 2;

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
            if (type == T_PROBE) {
                ProbesRound.flyProbe(ents, b);
            } else if (type == T_ATAT || type == T_ATST) {
                if (z < Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                } else {
                    WalkersRound.walkerFires(ents, b);
                }
            } else if (type == T_TIE) {
                AsteroidsRound.flyTie(ents, b);
            } else if (type == T_FIREBALL || type == T_ASTEROID) {
                if (type == T_FIREBALL && z > Camera.NEAR) {
                    home(ents, b, speed);
                }
                if (z <= Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                    int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
                    if (Math.abs(ents[b + E_X] - Camera.camX) < reach && Math.abs(ents[b + E_Y] - Camera.camY) < reach) {
                        Session.shieldHit();
                    }
                }
            } else if (type == T_DEBRIS) {
                ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
                if (ents[b + E_TIMER] <= 0 || z < Camera.NEAR) {
                    ents[b + E_TYPE] = T_NONE;
                }
            }
        }
    }

    /**
     * Fireballs home in on your craft, but can only correct their course a little each frame, so a
     * quick move still dodges them.
     */
    private static void home(int[] ents, int b, int speed) {
        int frames = Math.max(1, (ents[b + E_Z] - Camera.NEAR) / (speed - ents[b + E_VZ]));
        int wantX = (Camera.camX - ents[b + E_X]) / frames;
        int wantY = (Camera.camY - ents[b + E_Y]) / frames;
        ents[b + E_VX] = ents[b + E_VX] + Camera.clamp(wantX - ents[b + E_VX], -HOMING, HOMING);
        ents[b + E_VY] = ents[b + E_VY] + Camera.clamp(wantY - ents[b + E_VY], -HOMING, HOMING);
    }

    /** Launches a fireball from (x, y, z) aimed at where your craft is now. */
    static void fireball(int[] ents, int x, int y, int z) {
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int speed = Math.min(18 + 2 * Session.wave, 34);
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
