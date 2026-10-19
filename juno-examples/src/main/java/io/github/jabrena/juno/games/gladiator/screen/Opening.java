package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;
import io.github.jabrena.juno.api.util.Random;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.ViewList;

/** The laurel title and rotating wireframe class gallery shown before the game connects. */
public final class Opening {
    private static final Clock CLOCK = MCU.getClock();

    public static final int INTRO_MILLIS = 5000;
    /** The quotes the title picks one of at random, each time it is shown ({@link #quoteLine}). */
    public static final int QUOTES = 2;
    private static final int CLASS_MILLIS = 2500;
    public static final int GALLERY_MILLIS = CLASS_MILLIS * GladiatorClass.CLASSES;
    private static final int FRAME_MILLIS = 50;
    private static final int PLAY_X = 20;
    private static final int PLAY_Y = 206;
    private static final int PLAY_WIDTH = 280;
    private static final int PLAY_HEIGHT = 28;
    private static final int PANEL_X = 190;
    private static final int PANEL_WIDTH = 126;
    private static final int OLIVE = 0x4D42;
    private static final int GOLD = 0xE6C0;

    private static final int[] LEAF_X = {128, 110, 92, 77, 66, 59, 59, 65, 77, 94};
    private static final int[] LEAF_Y = {169, 163, 153, 139, 121, 101, 81, 61, 43, 31};

    private Opening() {
    }

    /** Plays the title, drawing the class gallery's models with {@code wireframe} into {@code list}, until PLAY. */
    public static void play(ViewList list, ClassWireframe wireframe) {
        while (true) {
            int started = CLOCK.millis();
            laurel();
            if (waitForPlay(started, INTRO_MILLIS)) {
                return;
            }
            wipe();
            if (gallery(list, wireframe)) {
                return;
            }
            wipe();
        }
    }

    public static int classAt(int millis) {
        return millis / CLASS_MILLIS % GladiatorClass.CLASSES;
    }

    public static boolean onPlay(int x, int y) {
        return x >= PLAY_X && x < PLAY_X + PLAY_WIDTH && y >= PLAY_Y && y < PLAY_Y + PLAY_HEIGHT;
    }

    /** Line {@code line}, 0 or 1, of quote {@code quote}: the fear of death, or fortune helping the brave. */
    public static String quoteLine(int quote, int line) {
        if (quote == 1) {
            return line == 0 ? "FORTES FORTUNA" : "ADIUVAT";
        }
        return line == 0 ? "TIMOR MORTIS" : "MORTE PEIOR";
    }

    public static String className(int kind) {
        if (kind == GladiatorClass.THRAEX) {
            return "THRAEX";
        }
        if (kind == GladiatorClass.RETIARIUS) {
            return "RETIARIUS";
        }
        if (kind == GladiatorClass.SECUTOR) {
            return "SECUTOR";
        }
        return "MURMILLO";
    }

    private static void laurel() {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        for (int i = 0; i < LEAF_X.length; i++) {
            int x = LEAF_X[i];
            int y = LEAF_Y[i];
            if (i > 0) {
                ViewList.drawLine(LEAF_X[i - 1], LEAF_Y[i - 1], x, y, OLIVE);
                ViewList.drawLine(320 - LEAF_X[i - 1], LEAF_Y[i - 1], 320 - x, y, OLIVE);
            }
            leaf(x, y, -14, -7);
            leaf(x + 3, y - 2, 11, -11);
            leaf(320 - x, y, 14, -7);
            leaf(317 - x, y - 2, -11, -11);
            Delay.millis(85);
        }
        Hud.centered("GLADIATORS", 58, 3, GOLD, ViewList.BACKGROUND);
        Hud.centered("SLAVES OF ROME", 90, 2, GOLD, ViewList.BACKGROUND);
        int quote = Random.nextInt(QUOTES);
        Hud.centered(quoteLine(quote, 0), 132, 2, TftTouchShield.WHITE, ViewList.BACKGROUND);
        Hud.centered(quoteLine(quote, 1), 155, 2, TftTouchShield.WHITE, ViewList.BACKGROUND);
        drawPlayButton();
    }

    /** A five-pixel-wide diagonal stroke tapered to suggest one olive leaf. */
    private static void leaf(int x, int y, int dx, int dy) {
        int px = dy < 0 ? 1 : -1;
        int py = dx < 0 ? -1 : 1;
        for (int width = -2; width <= 2; width++) {
            int shorten = Math.abs(width) * 2;
            ViewList.drawLine(x + px * width, y + py * width,
                    x + dx - dx * shorten / 16 + px * width,
                    y + dy - dy * shorten / 16 + py * width, OLIVE);
        }
    }

    private static void wipe() {
        for (int edge = 0; edge < ViewList.WIDTH / 2; edge += 12) {
            TftTouchShield.fillRect(edge, 0, 12, ViewList.HEIGHT, ViewList.BACKGROUND);
            TftTouchShield.fillRect(ViewList.WIDTH - edge - 12, 0, 12, ViewList.HEIGHT, ViewList.BACKGROUND);
            Delay.millis(18);
        }
    }

    /** Shows every class once, returning whether PLAY was tapped. */
    private static boolean gallery(ViewList list, ClassWireframe wireframe) {
        int started = CLOCK.millis();
        int shownClass = -1;
        boolean touching = true;
        while (CLOCK.millis() - started < GALLERY_MILLIS) {
            int elapsed = CLOCK.millis() - started;
            int kind = classAt(elapsed);
            if (kind != shownClass) {
                shownClass = kind;
                drawGallery(list, kind);
            }
            wireframe.build(kind, elapsed * 0.0025f);
            list.present();
            if (TftTouchShield.readTouch()) {
                if (!touching && onPlay(TftTouchShield.touchX(), TftTouchShield.touchY())) {
                    waitForRelease();
                    return true;
                }
                touching = true;
            } else {
                touching = false;
            }
            Delay.millis(FRAME_MILLIS);
        }
        return false;
    }

    private static void drawGallery(ViewList list, int kind) {
        TftTouchShield.fillScreen(ViewList.BACKGROUND);
        list.clearView();
        TftTouchShield.fillRect(PANEL_X, 8, PANEL_WIDTH, 188, Hud.PANEL);
        TftTouchShield.fillRect(PANEL_X, 8, 4, 188, classColor(kind));
        text(PANEL_X + 12, 18, 2, classColor(kind), className(kind));
        text(PANEL_X + 12, 51, 1, TftTouchShield.GRAY, description(kind));
        text(PANEL_X + 12, 78, 1, TftTouchShield.WHITE, "LIFE  100");
        text(PANEL_X + 12, 104, 1, GOLD, "ARMS");
        text(PANEL_X + 12, 121, 1, TftTouchShield.WHITE, firstArm(kind));
        text(PANEL_X + 12, 137, 1, TftTouchShield.WHITE, secondArm(kind));
        drawPlayButton();
    }

    private static void drawPlayButton() {
        TftTouchShield.fillRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, Hud.PANEL);
        TftTouchShield.drawRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, GOLD);
        Hud.centered("PLAY", PLAY_Y + 6, 2, GOLD, Hud.PANEL);
    }

    private static boolean waitForPlay(int started, int duration) {
        boolean touching = true;
        while (CLOCK.millis() - started < duration) {
            if (TftTouchShield.readTouch()) {
                if (!touching && onPlay(TftTouchShield.touchX(), TftTouchShield.touchY())) {
                    waitForRelease();
                    return true;
                }
                touching = true;
            } else {
                touching = false;
            }
            Delay.millis(10);
        }
        return false;
    }

    public static int classColor(int kind) {
        if (kind == GladiatorClass.THRAEX) {
            return TftTouchShield.YELLOW;
        }
        if (kind == GladiatorClass.RETIARIUS) {
            return TftTouchShield.CYAN;
        }
        if (kind == GladiatorClass.SECUTOR) {
            return TftTouchShield.RED;
        }
        return TftTouchShield.ORANGE;
    }

    private static String description(int kind) {
        if (kind == GladiatorClass.THRAEX) {
            return "AGILE DUELIST";
        }
        if (kind == GladiatorClass.RETIARIUS) {
            return "NET FIGHTER";
        }
        if (kind == GladiatorClass.SECUTOR) {
            return "RELENTLESS";
        }
        return "HEAVY DEFENDER";
    }

    public static String firstArm(int kind) {
        if (kind == GladiatorClass.THRAEX) {
            return "SICA";
        }
        if (kind == GladiatorClass.RETIARIUS) {
            return "TRIDENT";
        }
        return "GLADIUS";
    }

    public static String secondArm(int kind) {
        if (kind == GladiatorClass.THRAEX) {
            return "PARMULA";
        }
        if (kind == GladiatorClass.RETIARIUS) {
            return "NET";
        }
        return "SCUTUM";
    }

    private static void text(int x, int y, int size, int color, String value) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, Hud.PANEL);
        TftTouchShield.setCursor(x, y);
        TftTouchShield.print(value);
    }

    private static void waitForRelease() {
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
