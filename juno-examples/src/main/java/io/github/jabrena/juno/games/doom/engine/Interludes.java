package io.github.jabrena.juno.games.doom.engine;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.Delay;

/**
 * The screens between the game's moments. The cover is still {@link Lava} with a large DOOM title and a blinking
 * prompt on dark plaques; it waits for a tap. At startup the cover shows while the WAD is inspected, and the prompt
 * appears once episode selection is available. The title is text rather than the drawn logo so the UNO Q's sketch heap
 * keeps room for the map and its route.
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
    private static final int PAUSE_TOP = 70;
    private static final int PAUSE_HEIGHT = 48;

    private static final int EDGE = TftTouchShield.color(215, 70, 30);
    private static final int EDGE_DARK = TftTouchShield.color(120, 25, 12);

    private Interludes() {
    }

    /** Plays the cover and returns once the screen has been tapped (and released). */
    static void title(TftTouchShield tft) {
        cover(tft);
        waitForTap(tft);
    }

    /** The cover without its prompt: the lava, the DOOM title, and the prompt's empty plaque. */
    static void cover(TftTouchShield tft) {
        Lava.paint(tft);
        int titleWidth = 4 * 6 * TITLE_SIZE;
        plaque(tft, (DisplayList.WIDTH - titleWidth) / 2 - PLAQUE_MARGIN, TITLE_Y - PLAQUE_MARGIN,
                titleWidth + 2 * PLAQUE_MARGIN, 7 * TITLE_SIZE + 2 * PLAQUE_MARGIN);
        Hud.showCentered(tft, "DOOM", TITLE_Y, TITLE_SIZE, TftTouchShield.RED);
        plaque(tft, PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT);
    }

    /** Says on the cover's plaque that the map is still loading, so no tap is asked for yet. */
    static void loading(TftTouchShield tft) {
        Hud.showCentered(tft, "LOADING", PROMPT_Y + 8, 2, TftTouchShield.YELLOW);
    }

    /** Blinks the prompt on the cover until the screen has been tapped (and released). */
    static void waitForTap(TftTouchShield tft) {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            blink(tft, frame % 14 < 9);
            frame = frame + 1;
            tapped = tft.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease(tft);
    }

    /** Covers whatever is on the screen with lava, at once: the cut between the game's moments. */
    static void flood(TftTouchShield tft) {
        Lava.paint(tft);
    }

    /**
     * The tally's frame for a map about to start, without its statistics: the map's name and ENTERING, as DOOM shows
     * between maps. The first map of an episode is read under it, so every map starts from the same screen.
     */
    static void entering(TftTouchShield tft) {
        flood(tft);
        plaque(tft, 14, 8, 292, 224);
        showMapMessage(tft, "", 20, 3, TftTouchShield.YELLOW);
        Hud.showCentered(tft, "ENTERING", 49, 3, TftTouchShield.RED);
    }

    /** A dark plaque with a double red edge, holding the title or the blinking prompt over the lava. */
    private static void plaque(TftTouchShield tft, int x, int y, int width, int height) {
        tft.fillRect(x, y, width, height, DisplayList.BACKGROUND);
        tft.drawRect(x, y, width, height, EDGE);
        tft.drawRect(x + 2, y + 2, width - 4, height - 4, EDGE_DARK);
    }

    private static void blink(TftTouchShield tft, boolean on) {
        // As wide as "TAP TO PLAY", so it also wipes out "LOADING".
        Hud.showCentered(tft, on ? "TAP TO PLAY" : "           ", PROMPT_Y + 8, 2, TftTouchShield.WHITE);
    }

    /** The DOOM-style tally shown after the exit; the next map is read while it shows. */
    static void complete(TftTouchShield tft, int elapsedMillis, byte[] taken) {
        flood(tft);
        plaque(tft, 14, 8, 292, 224);
        if (World.fromWad) {
            showMapMessage(tft, "", 20, 3, TftTouchShield.YELLOW);
        } else {
            Hud.showCentered(tft, Level.NAME, 20, 3, TftTouchShield.YELLOW);
        }
        Hud.showCentered(tft, "FINISHED", 49, 3, TftTouchShield.RED);
        statistic(tft, "KILLS", Monsters.kills, World.monsters, 91);
        statistic(tft, "ITEMS", Campaign.itemsFound(taken, World.placedItems), Campaign.itemsCounted(World.placedItems), 121);
        statistic(tft, "SECRETS", World.secretsFound, World.secrets, 151);
        time(tft, elapsedMillis, 183);
    }

    /** Says under the tally which map is being read, while it loads and the CPU's route is planned. */
    static void tallyLoading(TftTouchShield tft) {
        Hud.showCentered(tft, "                                  ", 215, 1, TftTouchShield.YELLOW);
        showMapMessage(tft, "LOADING ", 215, 1, TftTouchShield.YELLOW);
        Loading.show(tft, 70, 203, 160);
    }

    /**
     * Blinks what a tap leads to under the tally until the screen is tapped: the map just loaded, the episode's end,
     * or the built-in map once more.
     */
    static void waitToPlay(TftTouchShield tft) {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            boolean on = frame % 14 < 9;
            if (!World.fromWad) {
                Hud.showCentered(tft, on ? "TAP TO PLAY AGAIN" : "                 ", 215, 1, TftTouchShield.YELLOW);
            } else if (Campaign.episodeFinished()) {
                Hud.showCentered(tft, on ? "EPISODE COMPLETE - TAP TO CONTINUE" : "                                  ",
                        215, 1, TftTouchShield.YELLOW);
            } else if (on) {
                showMapMessage(tft, "TAP TO PLAY ", 215, 1, TftTouchShield.YELLOW);
            } else {
                Hud.showCentered(tft, "                ", 215, 1, TftTouchShield.YELLOW);
            }
            frame = frame + 1;
            tapped = tft.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease(tft);
    }

    /** A distinct story card shown after E?M8; a tap returns to the game title. */
    static void episodeComplete(TftTouchShield tft) {
        flood(tft);
        plaque(tft, 8, 8, 304, 224);
        showEpisodeHeading(tft);
        story(tft);
        waitForContinue(tft);
    }

    private static void statistic(TftTouchShield tft, String label, int found, int total, int y) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        tft.setCursor(32, y);
        tft.print(label);
        tft.setCursor(140, y);
        tft.print(found);
        tft.print("/");
        tft.print(total);
        tft.setCursor(248, y);
        tft.print(Campaign.percent(found, total));
        tft.print("%");
    }

    private static void time(TftTouchShield tft, int elapsedMillis, int y) {
        int seconds = Math.max(0, elapsedMillis / 1000);
        int minutes = Math.min(99, seconds / 60);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        tft.setCursor(32, y);
        tft.print("TIME");
        tft.setCursor(196, y);
        twoDigits(tft, minutes);
        tft.print(":");
        twoDigits(tft, seconds % 60);
    }

    private static void twoDigits(TftTouchShield tft, int value) {
        if (value < 10) {
            tft.print("0");
        }
        tft.print(value);
    }

    private static void showEpisodeHeading(TftTouchShield tft) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.RED, DisplayList.BACKGROUND);
        tft.setCursor(46, 24);
        tft.print("EPISODE ");
        tft.print(World.episode);
        tft.print(" COMPLETE");
    }

    private static void story(TftTouchShield tft) {
        switch (World.episode) {
            case 1 -> {
                storyLine(tft, "THE PHOBOS BASE FALLS SILENT.", 70);
                storyLine(tft, "YOU FOUGHT THROUGH EVERY GATE", 86);
                storyLine(tft, "AND DROVE THE INVASION BACK.", 102);
            }
            case 2 -> {
                storyLine(tft, "DEIMOS NO LONGER BELONGS TO HELL.", 70);
                storyLine(tft, "THE LOST MOON IS QUIET AGAIN,", 86);
                storyLine(tft, "BUT EARTH WAITS BEYOND THE VOID.", 102);
            }
            case 3 -> {
                storyLine(tft, "HELL ITSELF COULD NOT HOLD YOU.", 70);
                storyLine(tft, "THE INFERNAL GATES LIE BROKEN", 86);
                storyLine(tft, "BENEATH THE MARINE'S BOOTS.", 102);
            }
            default -> {
                storyLine(tft, "THE FINAL HORDE HAS FALLEN.", 70);
                storyLine(tft, "EARTH HAS ONE MORE DAWN", 86);
                storyLine(tft, "BECAUSE THE MARINE STOOD FAST.", 102);
            }
        }
        storyLine(tft, "THE EPISODE IS COMPLETE.", 136);
    }

    private static void storyLine(TftTouchShield tft, String text, int y) {
        Hud.showCentered(tft, text, y, 1, TftTouchShield.RED);
    }

    private static void waitForContinue(TftTouchShield tft) {
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            Hud.showCentered(tft, frame % 14 < 9 ? "TAP TO RETURN TO TITLE" : "                      ", 207, 1,
                    TftTouchShield.YELLOW);
            frame = frame + 1;
            tapped = tft.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease(tft);
    }

    /** Centers a fixed prefix followed by the current four-character EeMm map name. */
    private static void showMapMessage(TftTouchShield tft, String prefix, int y, int size, int color) {
        int characters = prefix.length() + 4;
        tft.setTextSize(size);
        tft.setTextColor(color, DisplayList.BACKGROUND);
        tft.setCursor((DisplayList.WIDTH - characters * 6 * size) / 2, y);
        tft.print(prefix);
        tft.print("E");
        tft.print(World.episode);
        tft.print("M");
        tft.print(World.map);
    }

    /**
     * The card shown when the marine dies, then a choice: {@code true} to restart the map, {@code false} to quit
     * to the game's title.
     */
    /**
     * The game paused over the view, from a tap on the marine's face in the status bar: carry on where it stopped, or
     * quit to the title. Returns whether the player quits.
     */
    static boolean paused(TftTouchShield tft) {
        Controls.waitForRelease(tft);
        plaque(tft, 14, 24, 292, 152);
        Hud.showCentered(tft, "PAUSED", 34, 3, TftTouchShield.RED);
        Menu.row(tft, 0, PAUSE_TOP, PAUSE_HEIGHT, "CONTINUE", "Back to the game", true, false);
        Menu.row(tft, 1, PAUSE_TOP, PAUSE_HEIGHT, "QUIT TO TITLE", "Leave this game", true, false);
        int row = Menu.choose(tft, PAUSE_TOP, PAUSE_HEIGHT, 2, 3);
        Menu.row(tft, row, PAUSE_TOP, PAUSE_HEIGHT, row == 0 ? "CONTINUE" : "QUIT TO TITLE",
                row == 0 ? "Back to the game" : "Leave this game", true, true);
        Controls.waitForRelease(tft);
        return row == 1;
    }

    static boolean died(TftTouchShield tft) {
        Hud.drawStatus(tft);
        Hud.showCentered(tft, "YOU DIED", 110, 4, TftTouchShield.RED);
        Delay.millis(2000);
        Menu.open(tft, "YOU DIED", "Restart the map, or quit to the title?");
        Menu.row(tft, 0, DEATH_TOP, DEATH_HEIGHT, "RESTART MAP", "Back to the start of this map", true, false);
        Menu.row(tft, 1, DEATH_TOP, DEATH_HEIGHT, "QUIT", "Return to the DOOM title", true, false);
        int row = Menu.choose(tft, DEATH_TOP, DEATH_HEIGHT, 2, 3);
        Menu.row(tft, row, DEATH_TOP, DEATH_HEIGHT, row == 0 ? "RESTART MAP" : "QUIT",
                row == 0 ? "Back to the start of this map" : "Return to the DOOM title", true, true);
        Controls.waitForRelease(tft);
        flood(tft);
        return row == 0;
    }
}
