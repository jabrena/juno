package io.github.jabrena.juno.games.doom;

/**
 * Health and armor lying in the map, picked up DOOM's way by walking over them: health bonuses add 1%
 * (up to 200%), stimpacks 10% and medikits 25% (up to 100%), armor bonuses 1 armor point (up to 200),
 * green armor sets 100 points that absorb a third of each hit, blue armor 200 that absorb half.
 */
final class Items {
    static final int HEALTH_BONUS = 0;
    static final int STIMPACK = 1;
    static final int MEDIKIT = 2;
    static final int ARMOR_BONUS = 3;
    static final int GREEN_ARMOR = 4;
    static final int BLUE_ARMOR = 5;
    private static final float REACH = 40f;

    private Items() {
    }

    static void reset(byte[] taken) {
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

    /** Whether the marine would gain anything from an item of this kind right now. */
    static boolean useful(int kind) {
        return switch (kind) {
            case HEALTH_BONUS -> Player.health < 200;
            case STIMPACK, MEDIKIT -> Player.health < Player.FULL_HEALTH;
            case ARMOR_BONUS -> Player.armor < 200;
            case GREEN_ARMOR -> Player.armor < 100;
            default -> Player.armor < 200;
        };
    }

    private static void apply(int kind) {
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
            default -> {
                Player.armor = 200;
                Player.armorClass = 2;
            }
        }
    }
}
