package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.Random;

/** Packed entity records and the shared per-frame movement loop. */
final class Entities {
    static final int ENTITIES = 26;
    static final int E_TYPE = 0;
    static final int E_X = 1;
    static final int E_Y = 2;
    static final int E_Z = 3;
    static final int E_VX = 4;
    static final int E_VY = 5;
    static final int E_VZ = 6;
    static final int E_TIMER = 7;
    static final int E_HEADING = 8;
    static final int E_HP = 9;
    static final int E_MODE = 10;
    static final int E_AUX = 11;
    static final int E_STRIDE = 12;

    static final int T_NONE = 0;
    static final int T_PLANE = 1;
    static final int T_BLIMP = 2;
    static final int T_FALLING = 3;
    static final int T_BULLET = 4;
    static final int T_TRACER = 5;
    static final int T_HANGAR = 6;
    static final int T_FLAK = 7;
    static final int T_PYRAMID = 8;
    static final int T_MARKER = 9;
    static final int T_DEBRIS = 10;

    static final int FLIGHT_SPEED = 24;
    private static final int TRACER_SPEED = 36;
    private static final int TRACER_REACH = 40;
    private static final int AIM_SCATTER = 70;
    private static final int MAX_TRACERS = 3;
    private static final int MARKERS = 8;

    private Entities() {
    }

    static void startRound(int[] ents) {
        clear(ents);
        for (int i = 0; i < MARKERS; i++) {
            int slot = freeSlot(ents);
            int b = slot * E_STRIDE;
            ents[b + E_TYPE] = T_MARKER;
            ents[b + E_X] = Random.nextInt(-1600, 1601);
            ents[b + E_Z] = Camera.NEAR + 200 + i * (Camera.FAR - 400) / MARKERS;
        }
    }

    static void move(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if (type == T_NONE) {
                continue;
            }
            ents[b + E_X] = ents[b + E_X] + ents[b + E_VX];
            ents[b + E_Y] = ents[b + E_Y] + ents[b + E_VY];
            ents[b + E_Z] = ents[b + E_Z] + ents[b + E_VZ] - (type == T_BULLET ? 0 : FLIGHT_SPEED);
            int z = ents[b + E_Z];
            if (type == T_PLANE || type == T_FALLING || type == T_BLIMP) {
                moveAircraft(ents, slot, b, type, z);
            } else if (type == T_BULLET || type == T_TRACER) {
                moveProjectile(ents, b, type, z);
            } else if (type == T_HANGAR || type == T_FLAK || type == T_PYRAMID) {
                moveGroundObject(ents, b, type, z);
            } else {
                moveScenery(ents, b, type, z);
            }
        }
    }

    private static void moveAircraft(int[] ents, int slot, int b, int type, int z) {
        if (type == T_PLANE) {
            DogfightRound.flyEnemy(ents, b);
        } else if (type == T_FALLING) {
            ents[b + E_HEADING] = ents[b + E_HEADING] + 17;
            if (ents[b + E_Y] <= 0 || z < Camera.NEAR) {
                debris(ents, slot, 60);
            }
        } else if (z < Camera.NEAR || Math.abs(ents[b + E_X]) > 2600) {
            ents[b + E_TYPE] = T_NONE;
        }
    }

    private static void moveProjectile(int[] ents, int b, int type, int z) {
        if (type == T_BULLET) {
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] <= 0) {
                ents[b + E_TYPE] = T_NONE;
            }
        } else if (z <= Camera.NEAR) {
            ents[b + E_TYPE] = T_NONE;
            if (Math.abs(ents[b + E_X]) < TRACER_REACH
                    && Math.abs(ents[b + E_Y] - Camera.altitude) < TRACER_REACH) {
                Session.shotDown = true;
            }
        }
    }

    private static void moveGroundObject(int[] ents, int b, int type, int z) {
        if (z < Camera.NEAR) {
            ents[b + E_TYPE] = T_NONE;
            int height = type == T_PYRAMID ? GroundAttackRound.PYRAMID_HEIGHT
                    : type == T_FLAK ? GroundAttackRound.FLAK_HEIGHT : GroundAttackRound.HANGAR_HEIGHT;
            if (Math.abs(ents[b + E_X]) < GroundAttackRound.GROUND_RADIUS
                    && Camera.altitude < height + 20) {
                Session.shotDown = true;
            }
        } else if (type == T_FLAK) {
            GroundAttackRound.flakFires(ents, b);
        }
    }

    private static void moveScenery(int[] ents, int b, int type, int z) {
        if (type == T_MARKER) {
            if (z < Camera.NEAR || z > Camera.FAR || Math.abs(ents[b + E_X]) > 2000) {
                ents[b + E_X] = Random.nextInt(-1600, 1601);
                ents[b + E_Z] = Camera.FAR - Random.nextInt(300);
            }
        } else if (type == T_DEBRIS) {
            ents[b + E_TIMER] = ents[b + E_TIMER] - 1;
            if (ents[b + E_TIMER] <= 0 || z < Camera.NEAR) {
                ents[b + E_TYPE] = T_NONE;
            }
        }
    }

    /** Fires an enemy tracer from (x, y, z) towards the plane. */
    static void enemyFires(int[] ents, int x, int y, int z, boolean fromGround) {
        if (count(ents, T_TRACER) >= MAX_TRACERS) {
            return;
        }
        int slot = freeSlot(ents);
        if (slot < 0) {
            return;
        }
        int b = slot * E_STRIDE;
        int dx = Random.nextInt(-AIM_SCATTER, AIM_SCATTER + 1) - x;
        int dy = Camera.altitude + Random.nextInt(-AIM_SCATTER, AIM_SCATTER + 1) - y;
        int dz = Camera.NEAR - z;
        float length = (float) Math.sqrt((float) dx * dx + (float) dy * dy + (float) dz * dz);
        ents[b + E_TYPE] = T_TRACER;
        ents[b + E_X] = x;
        ents[b + E_Y] = y;
        ents[b + E_Z] = z;
        float speed = TRACER_SPEED;
        ents[b + E_VX] = Math.round(dx * speed / length);
        ents[b + E_VY] = Math.round(dy * speed / length);
        ents[b + E_VZ] = Math.round(dz * speed / length) + FLIGHT_SPEED;
        ents[b + E_AUX] = fromGround ? 1 : 0;
    }

    static void debris(int[] ents, int slot, int size) {
        int b = slot * E_STRIDE;
        ents[b + E_TYPE] = T_DEBRIS;
        ents[b + E_VX] = 0;
        ents[b + E_VY] = 0;
        ents[b + E_VZ] = 0;
        ents[b + E_TIMER] = 10;
        ents[b + E_MODE] = size;
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
