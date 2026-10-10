package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * How the CPU marine fights, like a person rather than an aimbot: it needs a moment to react to a new monster, swings
 * its view with momentum (so it overshoots and corrects), aims with an error that only slowly settles and is thrown
 * off again by each shot's recoil, sometimes pulls the trigger before it is lined up, keeps strafing, backs off from
 * monsters that come close, retreats when badly hurt, and catches its breath after a kill. So it misses, takes hits,
 * and now and then dies. With several monsters near, it kills the toughest first. It fights with the weapon that deals the most damage at the monster's distance: the chainsaw
 * when one is near enough to close in on, never a rocket that would blast the marine too. {@link Autopilot} walks the
 * route when there is nothing to fight.
 */
final class Combat {
    private static final float ENGAGE = 1600f;
    private static final float FIELD_OF_VIEW = 1.2f;
    private static final float MAX_SPIN = 0.15f;
    private static final float SPIN_ACCELERATION = 0.03f;
    private static final float TOO_CLOSE = 160f;
    private static final int LOW_HEALTH = 30;
    /** Within this distance the toughest monster is fought first; farther ones by distance alone. */
    private static final float CLOSE = 600f;

    private static int enemy = -1;
    private static int reaction;
    private static int calm;
    private static float aimError;
    private static float spin;
    private static int strafeDirection;
    private static int strafeTime;

    private Combat() {
    }

    /** Forgets the fight, as a map starts. */
    static void restart() {
        enemy = -1;
        reaction = 0;
        calm = 0;
        spin = 0;
        aimError = 0;
        strafeDirection = 0;
        strafeTime = 0;
    }

    /** Stops the view's aiming swing, while the marine walks rather than fights. */
    static void steady() {
        spin = 0;
    }

    /**
     * Fights the monster it has noticed, the way a player would; returns whether the marine was busy
     * fighting (or catching its breath after a kill) instead of walking on.
     */
    static boolean fight(short[] ceilings, short[] monsters, float sway) {
        int seen = noticed(ceilings, monsters);
        if (seen < 0) {
            if (enemy >= 0) {
                enemy = -1;
                calm = Random.nextInt(8, 22);
            }
            if (calm > 0) {
                calm = calm - 1;
                Player.turn(sway * 0.5f);
                return true;
            }
            return false;
        }
        if (seen != enemy) {
            enemy = seen;
            reaction = Random.nextInt(6, 18);
            aimError = (Random.nextInt(2) == 0 ? -1 : 1) * Autopilot.random(0.08f, 0.25f);
        }
        if (reaction > 0) {
            reaction = reaction - 1;
            return false;
        }
        float dx = monsters[enemy + Monsters.X] - Player.x;
        float dy = monsters[enemy + Monsters.Y] - Player.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        arm(distance);
        aim(Weapon.angleTo(dx, dy) + aimError);
        aimError = aimError * 0.96f + Autopilot.random(-0.02f, 0.02f);
        float off = Math.abs(Weapon.angleTo(dx, dy) + aimError);
        float tolerance = (float) Math.atan(14f / distance);
        boolean lined = off < tolerance;
        boolean impatient = off < 2.5f * tolerance && Random.nextInt(100) < 10;
        boolean blastsBack = Weapon.current == Weapon.LAUNCHER && distance < Weapon.SPLASH + 32;
        if ((lined || impatient) && Weapon.loaded() && !blastsBack) {
            Weapon.fire(monsters, ceilings);
            aimError = aimError + Autopilot.random(-0.14f, 0.14f);
        }
        dodge(distance, Math.abs(Weapon.angleTo(dx, dy)), ceilings);
        return true;
    }

    /**
     * Raises the weapon that deals the most damage at {@code distance}, when it beats the one in hand by a clear
     * margin, so a monster at the edge of two weapons' ranges does not keep the marine swapping instead of shooting.
     */
    private static void arm(float distance) {
        int best = Weapon.best(distance);
        int wanted = Weapon.wanted();
        if (best != wanted && Weapon.damageRate(best, distance) > 1.25f * Weapon.damageRate(wanted, distance)) {
            Weapon.select(best);
        }
    }

    /**
     * The monster to fight. Of those near ({@link #CLOSE}), the toughest first (DOOM's starting health: a demon before an
     * imp before a sergeant before a zombieman), then the rest; of those farther off, the nearest. The current one is
     * kept while it stays in sight, unless a tougher one comes near or an equal one is much closer.
     */
    private static int noticed(short[] ceilings, short[] monsters) {
        int best = -1;
        int bestStrength = -1;
        float bestDistance = ENGAGE;
        int currentStrength = -1;
        float currentDistance = Float.MAX_VALUE;
        for (int i = 0; i < World.monsters; i++) {
            int at = i * Monsters.STRIDE;
            int state = monsters[at + Monsters.STATE];
            if (state == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= ENGAGE) {
                continue;
            }
            boolean aware = state != Monsters.IDLE || Math.abs(Weapon.angleTo(dx, dy)) < FIELD_OF_VIEW;
            if (aware && Monsters.seenByMarine(monsters, at, ceilings)) {
                int strength = distance < CLOSE ? Monsters.fullHealth(monsters[at + Monsters.KIND]) : 0;
                if (at == enemy) {
                    currentStrength = strength;
                    currentDistance = distance;
                }
                if (strength > bestStrength || strength == bestStrength && distance < bestDistance) {
                    best = at;
                    bestStrength = strength;
                    bestDistance = distance;
                }
            }
        }
        boolean keep = currentDistance < Float.MAX_VALUE && currentStrength == bestStrength
                && bestDistance > 0.6f * currentDistance;
        return keep ? enemy : best;
    }

    /** Swings the view toward {@code heading} with a hand's momentum: it speeds up, overshoots, corrects. */
    private static void aim(float heading) {
        float wanted = Math.max(-MAX_SPIN, Math.min(MAX_SPIN, heading * 0.4f));
        spin = spin + Math.max(-SPIN_ACCELERATION, Math.min(SPIN_ACCELERATION, wanted - spin));
        Player.turn(spin);
    }

    /** Dodges without stepping up to a closed door: opening one mid-fight lets out whatever waits behind it. */
    private static void dodge(float distance, float off, short[] ceilings) {
        Player.keepDoorsShut = true;
        if (Weapon.wanted() <= Weapon.CHAINSAW && Player.health >= LOW_HEALTH) {
            closeIn(distance, off, ceilings);
        } else {
            evade(distance, ceilings);
        }
        Player.keepDoorsShut = false;
    }

    /** With the chainsaw or the fist, walks up to the monster it faces until it is within reach. */
    private static void closeIn(float distance, float off, short[] ceilings) {
        if (distance > Weapon.MELEE - 24 && off < 0.5f) {
            Player.walk(6f, ceilings);
        }
    }

    /** Keeps moving under fire: retreats when hurt, backs off from close monsters, otherwise strafes. */
    private static void evade(float distance, short[] ceilings) {
        if (Player.health < LOW_HEALTH && distance < 500) {
            Player.walk(-6f, ceilings);
            return;
        }
        if (distance < TOO_CLOSE) {
            Player.walk(-4f, ceilings);
            return;
        }
        strafeTime = strafeTime - 1;
        if (strafeTime <= 0) {
            strafeDirection = Random.nextInt(3) - 1;
            strafeTime = Random.nextInt(15, 40);
        }
        if (strafeDirection != 0 && !Player.strafe(4f * strafeDirection, ceilings)) {
            strafeDirection = -strafeDirection;
        }
    }
}
