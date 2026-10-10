package io.github.jabrena.juno.games.doom;

/**
 * Pickups lying in the map, picked up DOOM's way by walking over them. Health bonuses add 1% (up to 200%), stimpacks
 * 10% and medikits 25% (up to 100%), armor bonuses 1 armor point (up to 200), green armor sets 100 points that absorb a
 * third of each hit, blue armor 200 that absorb half. Weapons are carried from then on, with the ammo they come with;
 * clips, shells, rockets and cells, single or boxed, add DOOM's amounts up to what the marine can carry, and a backpack
 * doubles that. Zombiemen drop a clip and sergeants their shotgun, each with half the ammo, in slots after the map's
 * own items that the oldest drop gives up when they run out.
 */
final class Items {
    static final int HEALTH_BONUS = 0;
    static final int STIMPACK = 1;
    static final int MEDIKIT = 2;
    static final int ARMOR_BONUS = 3;
    static final int GREEN_ARMOR = 4;
    static final int BLUE_ARMOR = 5;
    /** Weapons: the chainsaw, then from the shotgun on in {@link Weapon}'s order (kind {@code w + 4} is weapon w). */
    static final int CHAINSAW_PICKUP = 6;
    static final int BFG_PICKUP = 11;
    static final int CLIP = 12;
    static final int BULLET_BOX = 13;
    static final int SHELLS = 14;
    static final int SHELL_BOX = 15;
    static final int ROCKET = 16;
    static final int ROCKET_BOX = 17;
    static final int CELL = 18;
    static final int CELL_PACK = 19;
    static final int BACKPACK = 20;
    static final int DROPPED_CLIP = 21;
    static final int DROPPED_SHOTGUN = 22;
    /** What each ammo pickup from {@link #CLIP} holds, and of which ammo. */
    private static final int[] AMMO_AMOUNT = {10, 50, 4, 20, 1, 5, 20, 100};
    /** The ammo each weapon comes with when picked up. */
    private static final int[] WEAPON_AMMO = {0, 0, 0, 8, 20, 2, 40, 40};
    private static final float REACH = 40f;

    /** The next slot a drop takes once every free slot after the map's items is used. */
    private static int oldestDrop;

    private Items() {
    }

    /** Puts every item of the map back and clears its drops. */
    static void reset(byte[] taken) {
        World.items = World.placedItems;
        oldestDrop = World.placedItems;
        for (int i = 0; i < World.items; i++) {
            taken[i] = 0;
        }
    }

    /** Picks up whatever useful item the marine is standing on. */
    static void pickUp(byte[] taken) {
        for (int i = 0; i < World.items; i++) {
            float dx = World.itemX[i] - Player.x;
            float dy = World.itemY[i] - Player.y;
            if (taken[i] == 0 && dx * dx + dy * dy < REACH * REACH && useful(World.itemKind[i])) {
                apply(World.itemKind[i]);
                taken[i] = 1;
            }
        }
    }

    /** Leaves what a monster of {@code monsterKind} drops as it dies at ({@code x}, {@code y}). */
    static void drop(int monsterKind, int x, int y) {
        int kind = monsterKind == Sprites.ZOMBIEMAN ? DROPPED_CLIP : monsterKind == Sprites.SERGEANT
                ? DROPPED_SHOTGUN : -1;
        // The built-in map's tables hold just its own items, so it has no room for drops.
        int room = World.itemRoom;
        if (kind < 0 || World.placedItems >= room) {
            return;
        }
        int slot = World.items;
        if (slot >= room) {
            slot = oldestDrop;
            oldestDrop = oldestDrop + 1 >= room ? World.placedItems : oldestDrop + 1;
        } else {
            World.items = World.items + 1;
        }
        World.itemX[slot] = (short) x;
        World.itemY[slot] = (short) y;
        World.itemKind[slot] = (short) kind;
        World.taken[slot] = 0;
    }

    /** Whether an item counts toward the intermission's ITEMS: the map's own health and armor, as in DOOM. */
    static boolean countable(int kind) {
        return kind <= BLUE_ARMOR;
    }

    /** The weapon a pickup of this kind gives, or -1. */
    static int weaponOf(int kind) {
        return kind == DROPPED_SHOTGUN ? Weapon.SHOTGUN : kind == CHAINSAW_PICKUP ? Weapon.CHAINSAW
                : kind > CHAINSAW_PICKUP && kind <= BFG_PICKUP ? kind - 4 : -1;
    }

    /** Whether the marine would gain anything from an item of this kind right now. */
    static boolean useful(int kind) {
        int weapon = weaponOf(kind);
        if (weapon >= 0) {
            return !Weapon.carries(weapon) || weapon != Weapon.CHAINSAW && needs(ammoOf(weapon));
        }
        return switch (kind) {
            case HEALTH_BONUS -> Player.health < 200;
            case STIMPACK, MEDIKIT -> Player.health < Player.FULL_HEALTH;
            case ARMOR_BONUS -> Player.armor < 200;
            case GREEN_ARMOR -> Player.armor < 100;
            case BLUE_ARMOR -> Player.armor < 200;
            case BACKPACK -> !Weapon.backpack || needs(Weapon.BULLETS) || needs(Weapon.SHELLS)
                    || needs(Weapon.ROCKETS) || needs(Weapon.CELLS);
            case DROPPED_CLIP -> needs(Weapon.BULLETS);
            default -> needs((kind - CLIP) / 2);
        };
    }

    private static boolean needs(int ammoType) {
        return Weapon.ammo[ammoType] < Weapon.maxAmmo(ammoType);
    }

    /** The ammo a weapon from the shotgun on fires. */
    private static int ammoOf(int weapon) {
        return switch (weapon) {
            case Weapon.SHOTGUN -> Weapon.SHELLS;
            case Weapon.LAUNCHER -> Weapon.ROCKETS;
            case Weapon.PLASMA, Weapon.BFG -> Weapon.CELLS;
            default -> Weapon.BULLETS;
        };
    }

    private static void apply(int kind) {
        int weapon = weaponOf(kind);
        if (weapon >= 0) {
            int amount = WEAPON_AMMO[weapon];
            if (amount > 0) {
                Weapon.addAmmo(ammoOf(weapon), kind == DROPPED_SHOTGUN ? amount / 2 : amount);
            }
            Weapon.give(weapon);
            return;
        }
        switch (kind) {
            case HEALTH_BONUS -> Player.health = Math.min(200, Player.health + 1);
            case STIMPACK -> Player.health = Math.min(Player.FULL_HEALTH, Player.health + 10);
            case MEDIKIT -> Player.health = Math.min(Player.FULL_HEALTH, Player.health + 25);
            case ARMOR_BONUS -> {
                Player.armor = Math.min(200, Player.armor + 1);
                Player.armorClass = Math.max(1, Player.armorClass);
            }
            case GREEN_ARMOR -> {
                Player.armor = 100;
                Player.armorClass = 1;
            }
            case BLUE_ARMOR -> {
                Player.armor = 200;
                Player.armorClass = 2;
            }
            case BACKPACK -> {
                Weapon.backpack = true;
                for (int type = 0; type < 4; type++) {
                    Weapon.addAmmo(type, AMMO_AMOUNT[2 * type]);
                }
            }
            case DROPPED_CLIP -> Weapon.addAmmo(Weapon.BULLETS, 5);
            default -> Weapon.addAmmo((kind - CLIP) / 2, AMMO_AMOUNT[kind - CLIP]);
        }
    }
}
