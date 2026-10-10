package io.github.jabrena.juno.games.doom;

/**
 * DOOM's skill menu, after the episode: which monsters and pickups the map places (its easy, medium and hard
 * flags), whether hits are halved (only on the easiest), and on Nightmare! faster monsters.
 */
final class Skills {
    static final int TOP = 62;
    static final int HEIGHT = 30;

    private Skills() {
    }

    /** Shows the menu and sets {@link World#skill} to the one tapped. */
    static void choose() {
        Menu.open("CHOOSE SKILL", "How hard do you want it?");
        for (int skill = 1; skill <= 5; skill++) {
            Menu.row(skill - 1, TOP, HEIGHT, name(skill), detail(skill), true, false);
        }
        World.skill = Menu.choose(TOP, HEIGHT, 5, 31) + 1;
        Menu.row(World.skill - 1, TOP, HEIGHT, name(World.skill), detail(World.skill), true, true);
        Controls.waitForRelease();
        Interludes.flood();
    }

    static String name(int skill) {
        return switch (skill) {
            case 1 -> "I'M TOO YOUNG TO DIE";
            case 2 -> "HEY, NOT TOO ROUGH";
            case 3 -> "HURT ME PLENTY";
            case 4 -> "ULTRA-VIOLENCE";
            default -> "NIGHTMARE!";
        };
    }

    private static String detail(int skill) {
        return switch (skill) {
            case 1 -> "Few monsters, every hit halved";
            case 2 -> "Few monsters, full damage";
            case 3 -> "More monsters, full damage";
            case 4 -> "Every monster, full damage";
            default -> "Every monster, and faster";
        };
    }
}
