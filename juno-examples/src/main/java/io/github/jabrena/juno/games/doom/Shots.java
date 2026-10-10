package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * What flies, in {@link Monsters#shots}: imps' fireballs, which hurt the marine, and the marine's rockets, plasma and
 * BFG balls, which burst on the first monster or wall they meet. A rocket's blast reaches everything near it, the
 * marine included; the BFG's ball also sprays what the marine faces.
 */
final class Shots {
    private Shots() {
    }

    /** Puts a projectile of {@code kind} in flight, if a slot is free; it lives about five seconds. */
    static void launch(int kind, float x, float y, float z, float dx, float dy, float dz) {
        for (int s = 0; s < Monsters.SHOTS; s++) {
            int shot = s * Monsters.SHOT_STRIDE;
            if (Monsters.shots[shot + Monsters.LIFE] == 0) {
                Monsters.shots[shot + Monsters.LIFE] = 120;
                Monsters.shots[shot + Monsters.SHOT_X] = (short) Math.round(x);
                Monsters.shots[shot + Monsters.SHOT_Y] = (short) Math.round(y);
                Monsters.shots[shot + Monsters.SHOT_Z] = (short) Math.round(z);
                Monsters.shots[shot + Monsters.SHOT_DX] = (short) Math.round(dx);
                Monsters.shots[shot + Monsters.SHOT_DY] = (short) Math.round(dy);
                Monsters.shots[shot + Monsters.SHOT_DZ] = (short) Math.round(dz);
                Monsters.shots[shot + Monsters.SHOT_KIND] = (short) kind;
                return;
            }
        }
    }

    static void move(short[] monsters, short[] shots, short[] ceilings) {
        for (int s = 0; s < Monsters.SHOTS; s++) {
            int shot = s * Monsters.SHOT_STRIDE;
            if (shots[shot + Monsters.LIFE] == 0) {
                continue;
            }
            int kind = shots[shot + Monsters.SHOT_KIND];
            float x = shots[shot + Monsters.SHOT_X];
            float y = shots[shot + Monsters.SHOT_Y];
            float z = shots[shot + Monsters.SHOT_Z];
            float toX = x + shots[shot + Monsters.SHOT_DX];
            float toY = y + shots[shot + Monsters.SHOT_DY];
            float toZ = z + shots[shot + Monsters.SHOT_DZ];
            if (kind == Monsters.FIREBALL) {
                float dx = Player.x - toX;
                float dy = Player.y - toY;
                if (dx * dx + dy * dy < 26 * 26 && Math.abs(toZ - (Player.eye - 20)) < 40) {
                    Player.damage(Random.nextInt(3, 25));
                    shots[shot + Monsters.LIFE] = 0;
                    continue;
                }
            } else {
                int struck = struck(monsters, toX, toY, toZ);
                if (struck >= 0) {
                    explode(monsters, ceilings, kind, struck, toX, toY, toZ);
                    shots[shot + Monsters.LIFE] = 0;
                    continue;
                }
            }
            if (!Player.canSee(x, y, z, toX, toY, toZ, ceilings) || shots[shot + Monsters.LIFE] == 1) {
                if (kind != Monsters.FIREBALL) {
                    explode(monsters, ceilings, kind, -1, x, y, z);
                }
                shots[shot + Monsters.LIFE] = 0;
            } else {
                shots[shot + Monsters.SHOT_X] = (short) Math.round(toX);
                shots[shot + Monsters.SHOT_Y] = (short) Math.round(toY);
                shots[shot + Monsters.SHOT_Z] = (short) Math.round(toZ);
                shots[shot + Monsters.LIFE] = (short) (shots[shot + Monsters.LIFE] - 1);
            }
        }
    }

    /** The live monster a projectile at ({@code x}, {@code y}, {@code z}) runs into, or -1. */
    private static int struck(short[] monsters, float x, float y, float z) {
        for (int i = 0; i < World.monsters; i++) {
            int at = i * Monsters.STRIDE;
            if (monsters[at + Monsters.STATE] == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - x;
            float dy = monsters[at + Monsters.Y] - y;
            float above = z - monsters[at + Monsters.FLOOR];
            if (dx * dx + dy * dy < 24 * 24 && above > -8 && above < 64) {
                return at;
            }
        }
        return -1;
    }

    /**
     * A projectile of the marine's bursts at ({@code x}, {@code y}, {@code z}), on the monster at {@code struck} or
     * against a wall: plasma just burns what it hit; a rocket also blasts everything within {@link Weapon#SPLASH} it
     * can reach, the marine included; the BFG's ball hits hard and then sprays what the marine faces.
     */
    private static void explode(short[] monsters, short[] ceilings, int kind, int struck, float x, float y, float z) {
        if (struck >= 0) {
            int damage = kind == Monsters.PLASMA ? 5 : kind == Monsters.ROCKET ? 20 : 100;
            Monsters.hit(monsters, struck, damage * Random.nextInt(1, 9));
        }
        if (kind == Monsters.ROCKET) {
            for (int i = 0; i < World.monsters; i++) {
                int at = i * Monsters.STRIDE;
                float reach = Weapon.SPLASH - distance(monsters[at + Monsters.X] - x, monsters[at + Monsters.Y] - y);
                if (monsters[at + Monsters.STATE] != Monsters.DEAD && reach > 0 && Player.canSee(x, y, z, monsters[at + Monsters.X],
                        monsters[at + Monsters.Y], monsters[at + Monsters.FLOOR] + Monsters.CENTER, ceilings)) {
                    Monsters.hit(monsters, at, Math.round(reach));
                }
            }
            float reach = Weapon.SPLASH - distance(Player.x - x, Player.y - y);
            if (reach > 0 && Player.canSee(x, y, z, Player.x, Player.y, Player.eye - 20, ceilings)) {
                Player.damage(Math.round(reach));
            }
        } else if (kind == Monsters.BFG_BALL) {
            spray(monsters, ceilings);
        }
    }

    /**
     * The BFG's spray: DOOM's 40 tracers fanned over the quarter circle the marine faces, each hitting the first
     * monster in its line for 15-120. A monster takes as many tracers as its width covers.
     */
    private static void spray(short[] monsters, short[] ceilings) {
        for (int i = 0; i < World.monsters; i++) {
            int at = i * Monsters.STRIDE;
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = distance(dx, dy);
            if (monsters[at + Monsters.STATE] == Monsters.DEAD || distance > 1024 || distance < 1
                    || Math.abs(Weapon.angleTo(dx, dy)) > (float) Math.PI / 4 || !Monsters.seenByMarine(monsters, at, ceilings)) {
                continue;
            }
            int tracers = Math.max(1, Math.min(40, Math.round(2 * (float) Math.atan(20f / distance) / 0.039f)));
            int damage = 0;
            for (int t = 0; t < tracers; t++) {
                damage = damage + Random.nextInt(15, 121);
            }
            Monsters.hit(monsters, at, damage);
        }
    }

    private static float distance(float dx, float dy) {
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
