package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The screens between the game's moments. The cover opens on a dark-red sky, then {@link Lava} floods up
 * from the bottom and keeps flowing while the {@link Logo} is drawn letter by letter and a blinking
 * prompt appears; it plays until a tap. The marine and demons of {@link Figures} and the mountains are
 * switched off behind flags.
 */
final class Interludes {
    static final int PROMPT_X = 80;
    static final int PROMPT_Y = 194;
    static final int PROMPT_WIDTH = 160;
    static final int PROMPT_HEIGHT = 28;

    private static final int ART_HEIGHT = DisplayList.HEIGHT;
    private static final int FRAME_MILLIS = 60;
    private static final int FLOOD_RISE = 10;
    /** Switches the mountains on the cover back on (the sky is always painted first). */
    private static final boolean MOUNTAINS = false;
    /** Switches the horned demons on the cover back on. */
    private static final boolean DEMONS = false;
    /** Switches the marine, his plasma and muzzle flash on the cover back on. */
    private static final boolean MARINE = false;

    private static final int ROCK = TftTouchShield.color(130, 28, 12);
    private static final int ROCK_DARK = TftTouchShield.color(80, 14, 8);
    private static final int[] LEFT_PEAK = {64, 46, 36, 48, 72, 100, 128, 156, 184};
    private static final int[] RIGHT_PEAK = {8, 0, 14, 38, 70, 104, 140};

    private Interludes() {
    }

    /** Plays the cover and returns once the screen has been tapped (and released). */
    static void title() {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        Lava.reset();
        sky();
        if (MOUNTAINS) {
            mountains();
        }
        Delay.millis(500);
        for (int top = DisplayList.HEIGHT - Lava.CELL; top > -Lava.CELL; top -= FLOOD_RISE) {
            Lava.flood(Math.max(top, 0));
            Lava.tick = Lava.tick + 1;
            Delay.millis(FRAME_MILLIS);
        }
        pause(150);
        for (int letter = 0; letter < 4; letter++) {
            Logo.draw(letter);
            Lava.lettersShown = letter + 1;
            pause(260);
        }
        if (DEMONS) {
            Figures.scenery();
        }
        if (MARINE) {
            Figures.marine();
            Lava.marineShown = true;
        }
        prompt();
        Lava.promptShown = true;
        boolean tapped = false;
        int frame = 0;
        while (!tapped) {
            flicker();
            blink(frame % 14 < 9);
            frame = frame + 1;
            tapped = TftTouchShield.readTouch();
            Delay.millis(FRAME_MILLIS);
        }
        Controls.waitForRelease();
    }

    /** One animated frame: the lava, and the cast when it is switched on. */
    private static void flicker() {
        Lava.fx();
        if (DEMONS) {
            Figures.demonsInLava();
        }
        if (MARINE) {
            Figures.flash(Lava.tick);
        }
        Lava.tick = Lava.tick + 1;
    }

    /** The plaque under the logo that holds the blinking prompt. */
    private static void prompt() {
        TftTouchShield.fillRect(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT, DisplayList.BACKGROUND);
        TftTouchShield.drawRect(PROMPT_X, PROMPT_Y, PROMPT_WIDTH, PROMPT_HEIGHT, Logo.EDGE);
        TftTouchShield.drawRect(PROMPT_X + 2, PROMPT_Y + 2, PROMPT_WIDTH - 4, PROMPT_HEIGHT - 4, Logo.EDGE_DARK);
    }

    private static void blink(boolean on) {
        Hud.showCentered(on ? "TAP TO PLAY" : "           ", PROMPT_Y + 8, 2, TftTouchShield.WHITE);
    }

    /** Waits while the lava keeps flowing behind whatever is being painted. */
    private static void pause(int millis) {
        for (int waited = 0; waited < millis; waited += FRAME_MILLIS) {
            flicker();
            Delay.millis(FRAME_MILLIS);
        }
    }

    /** The end-of-level card shown when the marine reaches the exit switch. */
    static void complete() {
        Hud.drawStatus();
        Hud.showCentered(Level.NAME + " COMPLETE", 100, 3, TftTouchShield.GREEN);
        Delay.millis(4000);
    }

    /** The card shown when the marine dies. */
    static void died() {
        Hud.drawStatus();
        Hud.showCentered("YOU DIED", 110, 4, TftTouchShield.RED);
        Delay.millis(2500);
    }

    /** The sky, painted at once: dark red at the top, burning red mid-way, lava orange at the horizon. */
    private static void sky() {
        for (int y = 0; y < ART_HEIGHT; y += 4) {
            TftTouchShield.fillRect(0, y, DisplayList.WIDTH, 4, skyColor(y));
        }
    }

    private static int skyColor(int y) {
        int t = y / 4 * 4 * 100 / ART_HEIGHT;
        if (t < 50) {
            return TftTouchShield.color(85 + (190 - 85) * t / 50, 8 + (35 - 8) * t / 50, 5 + 5 * t / 50);
        }
        return TftTouchShield.color(190 + (245 - 190) * (t - 50) / 50, 35 + (115 - 35) * (t - 50) / 50,
                10 + 10 * (t - 50) / 50);
    }

    private static void mountains() {
        for (int i = 0; i < LEFT_PEAK.length; i++) {
            int top = LEFT_PEAK[i];
            TftTouchShield.fillRect(i * 6, top, 6, ART_HEIGHT - top, ROCK);
            TftTouchShield.fillRect(i * 6, top, 6, 3, ROCK_DARK);
        }
        for (int i = 0; i < RIGHT_PEAK.length; i++) {
            int top = RIGHT_PEAK[i];
            int x = DisplayList.WIDTH - (RIGHT_PEAK.length - i) * 7;
            TftTouchShield.fillRect(x, top, 7, ART_HEIGHT - top, ROCK);
            TftTouchShield.fillRect(x, top, 7, 3, ROCK_DARK);
        }
    }
}
