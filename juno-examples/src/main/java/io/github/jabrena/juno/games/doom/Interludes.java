package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The screens between the game's moments. The cover is still {@link Lava} with a large DOOM title and a blinking
 * prompt on dark plaques; it waits for a tap. At startup the cover shows while the WAD is inspected, and the prompt
 * appears once episode selection is available. The title is text rather than the drawn logo so the UNO Q's sketch heap
 * keeps room for the map and its route. The marine and demons of {@link Figures} are switched off behind flags.
 */
final class Interludes {
    static final int PROMPT_X = 80;
    static final int PROMPT_Y = 194;
    static final int PROMPT_WIDTH = 160;
    static final int PROMPT_HEIGHT = 28;

    private static final int TITLE_SIZE = 8;
    private static final int TITLE_Y = 56;
    private static final int PLAQUE_MARGIN = 12;
    private static final int FRAME_MILLIS = 60;
    private static final int DEATH_TOP = 84;
    private static final int DEATH_HEIGHT = 48;
    /** Switches the horned demons on the cover back on. */
    private static final boolean DEMONS = false;
    /** Switches the marine, his plasma and muzzle flash on the cover back on. */
    private static final boolean MARINE = false;

    private static final int EDGE = TftTouchShield.color(215, 70, 30);
    private static final int EDGE_DARK = TftTouchShield.color(120, 25, 12);

    private Interludes() {
    }

    /** Plays the cover and returns once the screen has been tapped (and released). */
    static void title() {
        cover();
        waitForTap();
    }

    /** The cover without its prompt: the lava, the DOOM title, and the prompt's empty plaque. */
    static void cover() {
        Lava.paint();
        int titleWidth = 4 * 6 * TITLE_SIZE;
        plaque((DisplayList.WIDTH - titleWidth) / 2 - PLAQUE_MARGIN, TITLE_Y - PLAQUE_MARGIN,
                titleWidth + 2 * PLAQUE_MARGIN, 7 * TITLE_SIZE + 2 * PLAQUE_MARGIN);
        Hud.showCentered("DOOM", TITLE_Y, TITLE_SIZE, TftTouchShield.RED);
        if (DEMONS) {
            Figures.scenery();
            Figures.demonsInLava();
        }
        if (MARINE) {
            Figures.marine();
        }
        plaque(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT);
    }

    /** Says on the cover's plaque that the map is still loading, so no tap is asked for yet. */
    static void loading() {
        Hud.showCentered("LOADING", PROMPT_Y + 8, 2, TftTouchShield.YELLOW);
    }

    /** Blinks the prompt on the cover until the screen has been tapped (and released). */
    static void waitForTap() {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            blink(frame % 14 < 9);
            frame = frame + 1;
            tapped = TftTouchShield.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease();
    }

    /** Covers whatever is on the screen with lava, at once: the cut between the game's moments. */
    static void flood() {
        Lava.paint();
    }

    /** Shows which WAD map is being read after the menus or between two completed maps. */
    static void loadingMap() {
        flood();
        plaque(52, 88, 216, 64);
        showMapMessage("LOADING ", 108, 3, TftTouchShield.YELLOW);
        Loading.show(72, 136, 160);
    }

    /** A dark plaque with a double red edge, holding the title or the blinking prompt over the lava. */
    private static void plaque(int x, int y, int width, int height) {
        TftTouchShield.fillRect(x, y, width, height, DisplayList.BACKGROUND);
        TftTouchShield.drawRect(x, y, width, height, EDGE);
        TftTouchShield.drawRect(x + 2, y + 2, width - 4, height - 4, EDGE_DARK);
    }

    private static void blink(boolean on) {
        // As wide as "TAP TO PLAY", so it also wipes out "LOADING".
        Hud.showCentered(on ? "TAP TO PLAY" : "           ", PROMPT_Y + 8, 2, TftTouchShield.WHITE);
    }

    /** The DOOM-style tally shown after the exit; the next map is read while it shows. */
    static void complete(int elapsedMillis, byte[] taken) {
        flood();
        plaque(14, 8, 292, 224);
        if (World.fromWad) {
            showMapMessage("", 20, 3, TftTouchShield.YELLOW);
        } else {
            Hud.showCentered(Level.NAME, 20, 3, TftTouchShield.YELLOW);
        }
        Hud.showCentered("FINISHED", 49, 3, TftTouchShield.RED);
        statistic("KILLS", Monsters.kills, World.monsters, 91);
        statistic("ITEMS", Campaign.itemsFound(taken, World.items), World.items, 121);
        statistic("SECRETS", World.secretsFound, World.secrets, 151);
        time(elapsedMillis, 183);
    }

    /** Says under the tally which map is being read, while it loads and the CPU's route is planned. */
    static void tallyLoading() {
        Hud.showCentered("                                  ", 215, 1, TftTouchShield.YELLOW);
        showMapMessage("LOADING ", 215, 1, TftTouchShield.YELLOW);
        Loading.show(70, 203, 160);
    }

    /**
     * Blinks what a tap leads to under the tally until the screen is tapped: the map just loaded, the episode's end,
     * or the built-in map once more.
     */
    static void waitToPlay() {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            boolean on = frame % 14 < 9;
            if (!World.fromWad) {
                Hud.showCentered(on ? "TAP TO PLAY AGAIN" : "                 ", 215, 1, TftTouchShield.YELLOW);
            } else if (Campaign.episodeFinished()) {
                Hud.showCentered(on ? "EPISODE COMPLETE - TAP TO CONTINUE" : "                                  ",
                        215, 1, TftTouchShield.YELLOW);
            } else if (on) {
                showMapMessage("TAP TO PLAY ", 215, 1, TftTouchShield.YELLOW);
            } else {
                Hud.showCentered("                ", 215, 1, TftTouchShield.YELLOW);
            }
            frame = frame + 1;
            tapped = TftTouchShield.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease();
    }

    /** A distinct story card shown after E?M8; a tap returns to the game title. */
    static void episodeComplete() {
        flood();
        plaque(8, 8, 304, 224);
        showEpisodeHeading();
        story();
        waitForContinue();
    }

    private static void statistic(String label, int found, int total, int y) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(32, y);
        TftTouchShield.print(label);
        TftTouchShield.setCursor(140, y);
        TftTouchShield.print(found);
        TftTouchShield.print("/");
        TftTouchShield.print(total);
        TftTouchShield.setCursor(248, y);
        TftTouchShield.print(Campaign.percent(found, total));
        TftTouchShield.print("%");
    }

    private static void time(int elapsedMillis, int y) {
        int seconds = Math.max(0, elapsedMillis / 1000);
        int minutes = Math.min(99, seconds / 60);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(32, y);
        TftTouchShield.print("TIME");
        TftTouchShield.setCursor(196, y);
        twoDigits(minutes);
        TftTouchShield.print(":");
        twoDigits(seconds % 60);
    }

    private static void twoDigits(int value) {
        if (value < 10) {
            TftTouchShield.print("0");
        }
        TftTouchShield.print(value);
    }

    private static void showEpisodeHeading() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        TftTouchShield.setCursor(46, 24);
        TftTouchShield.print("EPISODE ");
        TftTouchShield.print(World.episode);
        TftTouchShield.print(" COMPLETE");
    }

    private static void story() {
        switch (World.episode) {
            case 1 -> {
                storyLine("THE PHOBOS BASE FALLS SILENT.", 70);
                storyLine("YOU FOUGHT THROUGH EVERY GATE", 86);
                storyLine("AND DROVE THE INVASION BACK.", 102);
            }
            case 2 -> {
                storyLine("DEIMOS NO LONGER BELONGS TO HELL.", 70);
                storyLine("THE LOST MOON IS QUIET AGAIN,", 86);
                storyLine("BUT EARTH WAITS BEYOND THE VOID.", 102);
            }
            case 3 -> {
                storyLine("HELL ITSELF COULD NOT HOLD YOU.", 70);
                storyLine("THE INFERNAL GATES LIE BROKEN", 86);
                storyLine("BENEATH THE MARINE'S BOOTS.", 102);
            }
            default -> {
                storyLine("THE FINAL HORDE HAS FALLEN.", 70);
                storyLine("EARTH HAS ONE MORE DAWN", 86);
                storyLine("BECAUSE THE MARINE STOOD FAST.", 102);
            }
        }
        storyLine("THE EPISODE IS COMPLETE.", 136);
    }

    private static void storyLine(String text, int y) {
        Hud.showCentered(text, y, 1, TftTouchShield.RED);
    }

    private static void waitForContinue() {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            Hud.showCentered(frame % 14 < 9 ? "TAP TO RETURN TO TITLE" : "                      ", 207, 1,
                    TftTouchShield.YELLOW);
            frame = frame + 1;
            tapped = TftTouchShield.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease();
    }

    /** Centers a fixed prefix followed by the current four-character EeMm map name. */
    private static void showMapMessage(String prefix, int y, int size, int color) {
        int characters = prefix.length() + 4;
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, DisplayList.BACKGROUND);
        TftTouchShield.setCursor((DisplayList.WIDTH - characters * 6 * size) / 2, y);
        TftTouchShield.print(prefix);
        TftTouchShield.print("E");
        TftTouchShield.print(World.episode);
        TftTouchShield.print("M");
        TftTouchShield.print(World.map);
    }

    /**
     * The card shown when the marine dies, then a choice: {@code true} to restart the map, {@code false} to quit
     * to the game's title.
     */
    static boolean died() {
        Hud.drawStatus();
        Hud.showCentered("YOU DIED", 110, 4, TftTouchShield.RED);
        Delay.millis(2000);
        Menu.open("YOU DIED", "Restart the map, or quit to the title?");
        Menu.row(0, DEATH_TOP, DEATH_HEIGHT, "RESTART MAP", "Back to the start of this map", true, false);
        Menu.row(1, DEATH_TOP, DEATH_HEIGHT, "QUIT", "Return to the DOOM title", true, false);
        int row = Menu.choose(DEATH_TOP, DEATH_HEIGHT, 2, 3);
        Menu.row(row, DEATH_TOP, DEATH_HEIGHT, row == 0 ? "RESTART MAP" : "QUIT",
                row == 0 ? "Back to the start of this map" : "Return to the DOOM title", true, true);
        Controls.waitForRelease();
        flood();
        return row == 0;
    }
}
