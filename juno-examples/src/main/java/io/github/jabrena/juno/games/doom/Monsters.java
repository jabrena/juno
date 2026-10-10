package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * The map's monsters and the imps' fireballs, after DOOM's thinkers. A monster sleeps until it sees
 * the marine (or hears a shot nearby), then chases, stops to aim and attacks: zombiemen fire one
 * hitscan bullet, sergeants three pellets, imps throw fireballs and demons bite. Hits may stagger it
 * in pain; a dead monster lies where it fell and rises again, out of sight, after a while, so the
 * walk-through never runs out of enemies.
 *
 * <p>State lives in two short arrays: {@code STRIDE} values per monster and {@code SHOT_STRIDE} per
 * fireball.
 */
final class Monsters {
    static final int STRIDE = 8;
    static final int X = 0;
    static final int Y = 1;
    static final int KIND = 2;
    static final int HEALTH = 3;
    static final int STATE = 4;
    static final int TIMER = 5;
    static final int FLOOR = 6;
    static final int FLASH = 7;

    static final int IDLE = 0;
    static final int CHASE = 1;
    static final int AIM = 2;
    static final int PAIN = 3;
    static final int DEAD = 4;

    static final int SHOTS = 6;
    static final int SHOT_STRIDE = 8;
    static final int LIFE = 0;
    static final int SHOT_X = 1;
    static final int SHOT_Y = 2;
    static final int SHOT_Z = 3;
    static final int SHOT_DX = 4;
    static final int SHOT_DY = 5;
    static final int SHOT_DZ = 6;

    static final int EYE = 40;
    static final int CENTER = 28;
    private static final int WAKE_DISTANCE = 2000;
    private static final int HEAR_DISTANCE = 1200;
    private static final int FIRE_DISTANCE = 1600;
    private static final int MELEE = 90;
    private static final int RESPAWN_FRAMES = 750;
    private static final int FIREBALL_SPEED = 12;

    static int kills;

    private Monsters() {
    }

    static void reset(short[] monsters, short[] shots) {
        for (int i = 0; i < World.monsters; i++) {
            spawn(monsters, i);
        }
        for (int i = 0; i < SHOTS * SHOT_STRIDE; i++) {
            shots[i] = 0;
        }
        kills = 0;
    }

    private static void spawn(short[] monsters, int i) {
        int at = i * STRIDE;
        monsters[at + X] = World.monsterX[i];
        monsters[at + Y] = World.monsterY[i];
        int kind = World.monsterKind[i];
        monsters[at + KIND] = (short) kind;
        monsters[at + HEALTH] = (short) fullHealth(kind);
        monsters[at + STATE] = IDLE;
        monsters[at + TIMER] = 0;
        monsters[at + FLOOR] = World.sectorFloor[Player.sectorAt(World.monsterX[i], World.monsterY[i])];
        monsters[at + FLASH] = 0;
    }

    static int fullHealth(int kind) {
        return switch (kind) {
            case Sprites.ZOMBIEMAN -> 20;
            case Sprites.SERGEANT -> 30;
            case Sprites.IMP -> 60;
            default -> 150;
        };
    }

    private static int speed(int kind) {
        return kind == Sprites.DEMON ? 7 : 4;
    }

    static void think(short[] monsters, short[] shots, short[] ceilings, int frame) {
        for (int i = 0; i < World.monsters; i++) {
            int at = i * STRIDE;
            if (monsters[at + FLASH] > 0) {
                monsters[at + FLASH] = (short) (monsters[at + FLASH] - 1);
            }
            int state = monsters[at + STATE];
            float dx = Player.x - monsters[at + X];
            float dy = Player.y - monsters[at + Y];
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (state == DEAD) {
                monsters[at + TIMER] = (short) Math.min(Short.MAX_VALUE, monsters[at + TIMER] + 1);
                if (monsters[at + TIMER] > RESPAWN_FRAMES && distance > 900 && !sees(monsters, at, ceilings)) {
                    spawn(monsters, i);
                }
            } else if (state == IDLE) {
                if (((frame + i) & 7) == 0 && distance < WAKE_DISTANCE && sees(monsters, at, ceilings)) {
                    wake(monsters, at);
                }
            } else if (state == PAIN || state == AIM) {
                monsters[at + TIMER] = (short) (monsters[at + TIMER] - 1);
                if (monsters[at + TIMER] <= 0) {
                    if (state == AIM) {
                        attack(monsters, shots, at, distance, ceilings);
                    }
                    monsters[at + STATE] = CHASE;
                    monsters[at + TIMER] = (short) Random.nextInt(25, 70);
                }
            } else {
                chase(monsters, at, dx, dy, distance, ceilings);
            }
        }
        moveShots(shots, ceilings);
    }

    private static void chase(short[] monsters, int at, float dx, float dy, float distance, short[] ceilings) {
        int kind = monsters[at + KIND];
        if (distance > 72) {
            float step = speed(kind) / distance;
            float fromX = monsters[at + X];
            float fromY = monsters[at + Y];
            float toX = fromX + dx * step;
            float toY = fromY + dy * step;
            if (!Player.blocked(fromX, fromY, toX, toY, ceilings)) {
                place(monsters, at, toX, toY);
            } else if (!Player.blocked(fromX, fromY, toX, fromY, ceilings)) {
                place(monsters, at, toX, fromY);
            } else if (!Player.blocked(fromX, fromY, fromX, toY, ceilings)) {
                place(monsters, at, fromX, toY);
            }
        }
        monsters[at + TIMER] = (short) (monsters[at + TIMER] - 1);
        boolean close = kind == Sprites.DEMON ? distance < MELEE : distance < FIRE_DISTANCE;
        if (monsters[at + TIMER] <= 0) {
            if (close && sees(monsters, at, ceilings)) {
                monsters[at + STATE] = AIM;
                monsters[at + TIMER] = 10;
            } else {
                monsters[at + TIMER] = 15;
            }
        }
    }

    private static void place(short[] monsters, int at, float x, float y) {
        monsters[at + X] = (short) Math.round(x);
        monsters[at + Y] = (short) Math.round(y);
        monsters[at + FLOOR] = World.sectorFloor[Player.sectorAt(x, y)];
    }

    private static void attack(short[] monsters, short[] shots, int at, float distance, short[] ceilings) {
        int kind = monsters[at + KIND];
        if (kind == Sprites.DEMON) {
            if (distance < MELEE) {
                Player.damage(Random.nextInt(4, 41));
            }
            return;
        }
        if (kind == Sprites.IMP) {
            launch(monsters, shots, at, distance);
            return;
        }
        monsters[at + FLASH] = 3;
        if (!sees(monsters, at, ceilings)) {
            return;
        }
        int chance = Math.max(15, Math.min(75, 75 - Math.round(distance) / 20));
        int pellets = kind == Sprites.SERGEANT ? 3 : 1;
        for (int p = 0; p < pellets; p++) {
            if (Random.nextInt(100) < chance) {
                Player.damage(Random.nextInt(3, 16));
            }
        }
    }

    private static void launch(short[] monsters, short[] shots, int at, float distance) {
        for (int s = 0; s < SHOTS; s++) {
            int shot = s * SHOT_STRIDE;
            if (shots[shot + LIFE] == 0) {
                float frames = Math.max(1f, distance / FIREBALL_SPEED);
                float z = monsters[at + FLOOR] + CENTER + 4;
                shots[shot + LIFE] = 120;
                shots[shot + SHOT_X] = monsters[at + X];
                shots[shot + SHOT_Y] = monsters[at + Y];
                shots[shot + SHOT_Z] = (short) Math.round(z);
                shots[shot + SHOT_DX] = (short) Math.round((Player.x - monsters[at + X]) / frames);
                shots[shot + SHOT_DY] = (short) Math.round((Player.y - monsters[at + Y]) / frames);
                shots[shot + SHOT_DZ] = (short) Math.round((Player.eye - 14 - z) / frames);
                return;
            }
        }
    }

    private static void moveShots(short[] shots, short[] ceilings) {
        for (int s = 0; s < SHOTS; s++) {
            int shot = s * SHOT_STRIDE;
            if (shots[shot + LIFE] == 0) {
                continue;
            }
            float x = shots[shot + SHOT_X];
            float y = shots[shot + SHOT_Y];
            float z = shots[shot + SHOT_Z];
            float toX = x + shots[shot + SHOT_DX];
            float toY = y + shots[shot + SHOT_DY];
            float toZ = z + shots[shot + SHOT_DZ];
            float dx = Player.x - toX;
            float dy = Player.y - toY;
            if (dx * dx + dy * dy < 26 * 26 && Math.abs(toZ - (Player.eye - 20)) < 40) {
                Player.damage(Random.nextInt(3, 25));
                shots[shot + LIFE] = 0;
            } else if (!Player.canSee(x, y, z, toX, toY, toZ, ceilings)) {
                shots[shot + LIFE] = 0;
            } else {
                shots[shot + SHOT_X] = (short) Math.round(toX);
                shots[shot + SHOT_Y] = (short) Math.round(toY);
                shots[shot + SHOT_Z] = (short) Math.round(toZ);
                shots[shot + LIFE] = (short) (shots[shot + LIFE] - 1);
            }
        }
    }

    /** Whether the monster at {@code at} has a line of sight to the marine's eye. */
    static boolean sees(short[] monsters, int at, short[] ceilings) {
        return Player.canSee(monsters[at + X], monsters[at + Y], monsters[at + FLOOR] + EYE,
                Player.x, Player.y, Player.eye, ceilings);
    }

    private static void wake(short[] monsters, int at) {
        monsters[at + STATE] = CHASE;
        monsters[at + TIMER] = (short) Random.nextInt(8, 30);
    }

    /** A gunshot wakes every sleeping monster within earshot. */
    static void hearShot(short[] monsters) {
        for (int i = 0; i < World.monsters; i++) {
            int at = i * STRIDE;
            float dx = Player.x - monsters[at + X];
            float dy = Player.y - monsters[at + Y];
            if (monsters[at + STATE] == IDLE && dx * dx + dy * dy < (float) HEAR_DISTANCE * HEAR_DISTANCE) {
                wake(monsters, at);
            }
        }
    }

    /** Deals {@code amount} to the monster at {@code at}: it dies, flinches, or at least turns on the marine. */
    static void hit(short[] monsters, int at, int amount) {
        int health = monsters[at + HEALTH] - amount;
        monsters[at + HEALTH] = (short) Math.max(0, health);
        if (health <= 0) {
            monsters[at + STATE] = DEAD;
            monsters[at + TIMER] = 0;
            monsters[at + FLASH] = 0;
            kills = kills + 1;
        } else if (Random.nextInt(100) < 40) {
            monsters[at + STATE] = PAIN;
            monsters[at + TIMER] = 6;
        } else if (monsters[at + STATE] == IDLE) {
            wake(monsters, at);
        }
    }
}
