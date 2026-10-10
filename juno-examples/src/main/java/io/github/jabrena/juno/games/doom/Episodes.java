package io.github.jabrena.juno.games.doom;

/**
 * The episode menu, shown after the pilot is chosen when the maps come from {@code DOOM1.WAD}: DOOM's four episodes,
 * each played from its first map on. An episode the WAD does not hold (the shareware WAD has only the first) is
 * shown greyed out.
 */
final class Episodes {
    static final int TOP = 66;
    static final int HEIGHT = 38;

    private Episodes() {
    }

    /** Shows the menu and sets {@link World#episode} to the one tapped. */
    static void choose() {
        Menu.open("CHOOSE EPISODE", "Which episode do you play?");
        int enabled = World.episodesInWad >> 1;
        for (int episode = 1; episode <= 4; episode++) {
            draw(episode, enabled, false);
        }
        int row = Menu.choose(TOP, HEIGHT, 4, enabled);
        World.episode = row + 1;
        draw(World.episode, enabled, true);
        Controls.waitForRelease();
    }

    private static void draw(int episode, int enabled, boolean chosen) {
        boolean present = (enabled >> (episode - 1) & 1) != 0;
        Menu.row(episode - 1, TOP, HEIGHT, name(episode), present ? detail(episode) : "Not in this DOOM1.WAD",
                present, chosen);
    }

    static String name(int episode) {
        return switch (episode) {
            case 1 -> "KNEE-DEEP IN THE DEAD";
            case 2 -> "THE SHORES OF HELL";
            case 3 -> "INFERNO";
            default -> "THY FLESH CONSUMED";
        };
    }

    private static String detail(int episode) {
        return switch (episode) {
            case 1 -> "Episode 1, starts E1M1 Hangar";
            case 2 -> "Episode 2, starts E2M1 Deimos Anomaly";
            case 3 -> "Episode 3, starts E3M1 Hell Keep";
            default -> "Episode 4, starts E4M1 Hell Beneath";
        };
    }
}
