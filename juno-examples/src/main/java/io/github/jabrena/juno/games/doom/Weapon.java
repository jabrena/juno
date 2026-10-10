package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Random;

/**
 * The marine's arms, after DOOM's: fist and chainsaw (slot 1), pistol, shotgun, chaingun, rocket launcher, plasma rifle
 * and BFG 9000 (slots 2-7). Bullets feed the pistol and the chaingun, shells the shotgun, rockets the launcher and
 * cells the plasma rifle and the BFG; the fist and the chainsaw need none. The marine starts with the fist, the pistol
 * and 50 bullets, keeps what he carries from map to map, and loses it all when he dies.
 *
 * <p>Bullets and pellets are hitscan with DOOM's vertical auto-aim: each hits the nearest visible monster within a few
 * degrees of its line. Rockets, plasma and the BFG's ball fly as projectiles ({@link Monsters#shots}). Changing weapon
 * lowers the one in hand and raises the next, so for a moment nothing fires. Every shot wakes everything in earshot.
 * Timings are DOOM's tics converted to this game's 40 ms frames.
 */
final class Weapon {
    static final int FIST = 0;
    static final int CHAINSAW = 1;
    static final int PISTOL = 2;
    static final int SHOTGUN = 3;
    static final int CHAINGUN = 4;
    static final int LAUNCHER = 5;
    static final int PLASMA = 6;
    static final int BFG = 7;
    static final int WEAPONS = 8;

    static final int BULLETS = 0;
    static final int SHELLS = 1;
    static final int ROCKETS = 2;
    static final int CELLS = 3;
    private static final int NO_AMMO = -1;

    /** Frames from one shot to the next, per weapon. */
    private static final int[] CYCLE = {10, 3, 10, 26, 3, 14, 2, 42};
    /** The ammo each weapon uses and how much per shot. */
    private static final int[] AMMO_TYPE = {NO_AMMO, NO_AMMO, BULLETS, SHELLS, BULLETS, ROCKETS, CELLS, CELLS};
    private static final int[] AMMO_USE = {0, 0, 1, 1, 1, 1, 1, 40};
    /** How much of each ammo the marine can carry, doubled by a backpack. */
    private static final int[] AMMO_MAX = {200, 50, 50, 300};
    /** Which weapon to fall back on when the one in hand runs dry, best first, as DOOM's P_CheckAmmo. */
    private static final int[] FALLBACK = {PLASMA, CHAINGUN, SHOTGUN, PISTOL, CHAINSAW, LAUNCHER, BFG, FIST};

    /** Frames the weapon in hand takes to go down, and then the next one to come up. */
    static final int SWAP = 5;
    private static final float SLACK = 0.03f;
    /** How far the fist and the chainsaw reach: DOOM's melee range plus the monster's body. */
    static final float MELEE = 80f;
    /** Rocket, plasma and BFG ball speeds in units per frame, and rocket splash radius. */
    private static final int ROCKET_SPEED = 28;
    private static final int PLASMA_SPEED = 35;
    static final int SPLASH = 128;

    private static int ready;
    /** Frames left of the muzzle flash. */
    static int flash;
    /** The weapon in hand, and the one being raised while {@link #swapping} counts down. */
    static int current;
    private static int next;
    static int swapping;
    /** Bit {@code w} set when weapon {@code w} is carried. */
    static int owned;
    static boolean backpack;
    static final int[] ammo = new int[4];

    private Weapon() {
    }

    /** The marine's arms as a new game or a death leaves them: fist, pistol and 50 bullets. */
    static void reset() {
        owned = 1 << FIST | 1 << PISTOL;
        backpack = false;
        for (int type = 0; type < 4; type++) {
            ammo[type] = 0;
        }
        ammo[BULLETS] = 50;
        current = PISTOL;
        next = PISTOL;
        enterMap();
    }

    /** Ready to fire as a map starts, keeping what the marine carries. */
    static void enterMap() {
        ready = 0;
        flash = 0;
        swapping = 0;
        next = current;
    }

    /** Counts down the reload, the muzzle flash and a weapon change; call once per frame. */
    static void tick() {
        if (ready > 0) {
            ready = ready - 1;
        }
        if (flash > 0) {
            flash = flash - 1;
        }
        if (swapping > 0) {
            swapping = swapping - 1;
            if (swapping == SWAP) {
                current = next;
            }
        }
    }

    static boolean loaded() {
        return ready == 0 && swapping == 0;
    }

    static boolean carries(int weapon) {
        return (owned & 1 << weapon) != 0;
    }

    /** Whether {@code weapon} is carried with ammo for a shot. */
    static boolean usable(int weapon) {
        return carries(weapon) && (AMMO_TYPE[weapon] == NO_AMMO || ammo[AMMO_TYPE[weapon]] >= AMMO_USE[weapon]);
    }

    /** Ammo left for the weapon in hand, or -1 for the fist and the chainsaw. */
    static int ammoInHand() {
        int type = AMMO_TYPE[current];
        return type == NO_AMMO ? -1 : ammo[type];
    }

    static int maxAmmo(int type) {
        return backpack ? 2 * AMMO_MAX[type] : AMMO_MAX[type];
    }

    /** Adds {@code amount} of ammo, up to what the marine can carry; returns whether any was taken. */
    static boolean addAmmo(int type, int amount) {
        int before = ammo[type];
        ammo[type] = Math.min(maxAmmo(type), before + (World.skill == 1 || World.skill == 5 ? 2 * amount : amount));
        return ammo[type] > before;
    }

    /** Carries {@code weapon} from now on and, as DOOM does with a new one, raises it. */
    static void give(int weapon) {
        boolean fresh = !carries(weapon);
        owned = owned | 1 << weapon;
        if (fresh) {
            select(weapon);
        }
    }

    /** Lowers the weapon in hand and raises {@code weapon}, if it is carried with ammo. */
    static void select(int weapon) {
        if (weapon == next || !usable(weapon)) {
            return;
        }
        next = weapon;
        swapping = 2 * SWAP;
    }

    /** Slot 1 holds both the fist and the chainsaw: a tap there swaps between them, the chainsaw first. */
    static void selectSlot(int slot) {
        if (slot == 1) {
            select(carries(CHAINSAW) && next != CHAINSAW ? CHAINSAW : FIST);
        } else {
            select(slot);
        }
    }

    /** The weapon being raised, or the one in hand. */
    static int wanted() {
        return next;
    }

    /** Fires the weapon in hand if it is ready; returns whether a monster was hit at once. */
    static boolean fire(short[] monsters, short[] ceilings) {
        if (!loaded()) {
            return false;
        }
        if (!usable(current)) {
            fallBack();
            return false;
        }
        int weapon = current;
        ready = CYCLE[weapon];
        if (AMMO_TYPE[weapon] != NO_AMMO) {
            ammo[AMMO_TYPE[weapon]] = ammo[AMMO_TYPE[weapon]] - AMMO_USE[weapon];
        }
        flash = weapon <= CHAINSAW ? 0 : 3;
        Monsters.hearShot(monsters);
        boolean hit = switch (weapon) {
            case FIST, CHAINSAW -> melee(monsters, ceilings);
            case SHOTGUN -> pellets(monsters, ceilings, 7, 0.1f);
            case CHAINGUN -> pellets(monsters, ceilings, 1, 0.05f);
            case LAUNCHER -> launch(Monsters.ROCKET, ROCKET_SPEED);
            case PLASMA -> launch(Monsters.PLASMA, PLASMA_SPEED);
            case BFG -> launch(Monsters.BFG_BALL, PLASMA_SPEED);
            default -> bullet(monsters, ceilings, 0f, Random.nextInt(5, 16));
        };
        if (!usable(weapon)) {
            fallBack();
        }
        return hit;
    }

    /** Out of ammo: raises the best weapon that still has some. */
    private static void fallBack() {
        for (int i = 0; i < FALLBACK.length; i++) {
            if (usable(FALLBACK[i])) {
                select(FALLBACK[i]);
                return;
            }
        }
    }

    private static boolean melee(short[] monsters, short[] ceilings) {
        int target = aimed(monsters, ceilings, 0f);
        if (target < 0 || distanceTo(monsters, target) > MELEE) {
            return false;
        }
        Monsters.hit(monsters, target, 2 * Random.nextInt(1, 11));
        return true;
    }

    /** Fires {@code count} pellets of 5-15 damage, each up to {@code spread} radians off the view. */
    private static boolean pellets(short[] monsters, short[] ceilings, int count, float spread) {
        boolean hit = false;
        for (int p = 0; p < count; p++) {
            float off = spread * (Random.nextInt(2001) - 1000) / 1000f;
            hit = bullet(monsters, ceilings, off, Random.nextInt(5, 16)) || hit;
        }
        return hit;
    }

    private static boolean bullet(short[] monsters, short[] ceilings, float off, int damage) {
        int target = aimed(monsters, ceilings, off);
        if (target < 0) {
            return false;
        }
        Monsters.hit(monsters, target, damage);
        return true;
    }

    /** Sends a projectile of {@code kind} along the view, from the marine's chest. */
    private static boolean launch(int kind, int speed) {
        float cos = (float) Math.cos(Player.angle);
        float sin = (float) Math.sin(Player.angle);
        Monsters.launch(kind, Player.x + 16 * cos, Player.y + 16 * sin, Player.eye - 10, speed * cos, speed * sin, 0);
        return false;
    }

    private static float distanceTo(short[] monsters, int at) {
        float dx = monsters[at + Monsters.X] - Player.x;
        float dy = monsters[at + Monsters.Y] - Player.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** The nearest live monster in line with the view and in sight, or -1. */
    static int aimed(short[] monsters, short[] ceilings) {
        return aimed(monsters, ceilings, 0f);
    }

    /** The nearest live monster in line with the view turned by {@code off} radians, and in sight; or -1. */
    private static int aimed(short[] monsters, short[] ceilings, float off) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < World.monsters; i++) {
            int at = i * Monsters.STRIDE;
            if (monsters[at + Monsters.STATE] == Monsters.DEAD) {
                continue;
            }
            float dx = monsters[at + Monsters.X] - Player.x;
            float dy = monsters[at + Monsters.Y] - Player.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= bestDistance || distance < 1) {
                continue;
            }
            float angle = angleTo(dx, dy) - off;
            if (angle > (float) Math.PI) {
                angle = angle - 2 * (float) Math.PI;
            } else if (angle < (float) -Math.PI) {
                angle = angle + 2 * (float) Math.PI;
            }
            if (Math.abs(angle) < (float) Math.atan(18f / distance) + SLACK
                    && Monsters.seenByMarine(monsters, at, ceilings)) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The damage per frame {@code weapon} can be expected to deal to a monster {@code distance} away, for the CPU to
     * pick the hardest-hitting weapon it can use; 0 when it is not carried, has no ammo, or must not be used there:
     * the fist and the chainsaw out of reach, a rocket or the BFG's ball so close its blast would hurt the marine.
     */
    static float damageRate(int weapon, float distance) {
        if (!usable(weapon)) {
            return 0f;
        }
        // The share of pellets that hit narrows with distance: a monster ~40 units wide against the spread.
        float width = (float) Math.atan(20f / Math.max(distance, 1f));
        return switch (weapon) {
            case FIST -> distance < MELEE ? 1.1f : 0.05f;
            case CHAINSAW -> distance < 2 * MELEE ? 7f : 0.05f;
            case SHOTGUN -> 70f / CYCLE[SHOTGUN] * Math.min(1f, width / 0.1f);
            case CHAINGUN -> 10f / CYCLE[CHAINGUN] * Math.min(1f, width / 0.05f);
            case LAUNCHER -> distance < 2 * SPLASH ? 0f : 90f / CYCLE[LAUNCHER];
            case PLASMA -> 22.5f / CYCLE[PLASMA];
            case BFG -> distance < 2 * SPLASH ? 0f : 600f / CYCLE[BFG];
            default -> 10f / CYCLE[PISTOL];
        };
    }

    /** The carried weapon that deals the most damage to a monster {@code distance} away. */
    static int best(float distance) {
        int best = FIST;
        float bestRate = -1f;
        for (int weapon = 0; weapon < WEAPONS; weapon++) {
            float rate = damageRate(weapon, distance);
            if (rate > bestRate) {
                best = weapon;
                bestRate = rate;
            }
        }
        return best;
    }

    /** Signed angle from the view direction to the direction ({@code dx}, {@code dy}), in -&#960;..&#960;. */
    static float angleTo(float dx, float dy) {
        float heading = (float) Math.atan2(dy, dx) - Player.angle;
        if (heading > (float) Math.PI) {
            heading = heading - 2 * (float) Math.PI;
        } else if (heading < (float) -Math.PI) {
            heading = heading + 2 * (float) Math.PI;
        }
        return heading;
    }
}
