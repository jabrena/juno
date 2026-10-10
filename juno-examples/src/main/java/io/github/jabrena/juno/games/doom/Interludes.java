package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The screens between the game's moments. The cover is still {@link Lava} with a large DOOM title and a blinking
 * prompt on dark plaques; it waits for a tap. At startup the cover shows while the map loads, and the prompt only
 * once it has. The title is text rather than the drawn logo so the UNO Q's sketch heap
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

    /** The end-of-level card shown when the marine reaches the exit switch. */
    static void complete() {
        Hud.drawStatus();
        if (World.fromWad) {
            Hud.showCentered("E1M1 COMPLETE", 100, 3, TftTouchShield.GREEN);
        } else {
            Hud.showCentered(Level.NAME + " COMPLETE", 100, 3, TftTouchShield.GREEN);
        }
        Delay.millis(4000);
    }

    /** The card shown when the marine dies, before the game returns to its title. */
    static void died() {
        Hud.drawStatus();
        Hud.showCentered("YOU DIED", 110, 4, TftTouchShield.RED);
        Delay.millis(2500);
    }
}
