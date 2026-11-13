package io.github.jabrena.juno.usecases.gladiator.screen;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.usecases.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.usecases.gladiator.view.Hud;
import io.github.jabrena.juno.usecases.gladiator.view.ViewList;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

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
    public static void play(TftTouchShield tft, ViewList list, ClassWireframe wireframe) {
        while (true) {
            int started = CLOCK.millis();
            laurel(tft);
            if (waitForPlay(tft, started, INTRO_MILLIS)) {
                return;
            }
            wipe(tft);
            if (gallery(tft, list, wireframe)) {
                return;
            }
            wipe(tft);
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

    private static void laurel(TftTouchShield tft) {
        tft.fillScreen(ViewList.BACKGROUND);
        for (int i = 0; i < LEAF_X.length; i++) {
            int x = LEAF_X[i];
            int y = LEAF_Y[i];
            if (i > 0) {
                ViewList.drawLine(tft, LEAF_X[i - 1], LEAF_Y[i - 1], x, y, OLIVE);
                ViewList.drawLine(tft, 320 - LEAF_X[i - 1], LEAF_Y[i - 1], 320 - x, y, OLIVE);
            }
            leaf(tft, x, y, -14, -7);
            leaf(tft, x + 3, y - 2, 11, -11);
            leaf(tft, 320 - x, y, 14, -7);
            leaf(tft, 317 - x, y - 2, -11, -11);
            Delay.millis(85);
        }
        Hud.centered(tft, "GLADIATORS", 58, 3, GOLD, ViewList.BACKGROUND);
        Hud.centered(tft, "SLAVES OF ROME", 90, 2, GOLD, ViewList.BACKGROUND);
        int quote = Random.nextInt(QUOTES);
        Hud.centered(tft, quoteLine(quote, 0), 132, 2, TftTouchShield.WHITE, ViewList.BACKGROUND);
        Hud.centered(tft, quoteLine(quote, 1), 155, 2, TftTouchShield.WHITE, ViewList.BACKGROUND);
        drawPlayButton(tft);
    }

    /** A five-pixel-wide diagonal stroke tapered to suggest one olive leaf. */
    private static void leaf(TftTouchShield tft, int x, int y, int dx, int dy) {
        int px = dy < 0 ? 1 : -1;
        int py = dx < 0 ? -1 : 1;
        for (int width = -2; width <= 2; width++) {
            int shorten = Math.abs(width) * 2;
            ViewList.drawLine(tft, x + px * width, y + py * width,
                    x + dx - dx * shorten / 16 + px * width,
                    y + dy - dy * shorten / 16 + py * width, OLIVE);
        }
    }

    private static void wipe(TftTouchShield tft) {
        for (int edge = 0; edge < ViewList.WIDTH / 2; edge += 12) {
            tft.fillRect(edge, 0, 12, ViewList.HEIGHT, ViewList.BACKGROUND);
            tft.fillRect(ViewList.WIDTH - edge - 12, 0, 12, ViewList.HEIGHT, ViewList.BACKGROUND);
            Delay.millis(18);
        }
    }

    /** Shows every class once, returning whether PLAY was tapped. */
    private static boolean gallery(TftTouchShield tft, ViewList list, ClassWireframe wireframe) {
        int started = CLOCK.millis();
        int shownClass = -1;
        boolean touching = true;
        while (CLOCK.millis() - started < GALLERY_MILLIS) {
            int elapsed = CLOCK.millis() - started;
            int kind = classAt(elapsed);
            if (kind != shownClass) {
                shownClass = kind;
                drawGallery(tft, list, kind);
            }
            wireframe.build(kind, elapsed * 0.0025f);
            list.present(tft);
            if (tft.readTouch()) {
                if (!touching && onPlay(tft.touchX(), tft.touchY())) {
                    waitForRelease(tft);
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

    private static void drawGallery(TftTouchShield tft, ViewList list, int kind) {
        tft.fillScreen(ViewList.BACKGROUND);
        list.clearView(tft);
        tft.fillRect(PANEL_X, 8, PANEL_WIDTH, 188, Hud.PANEL);
        tft.fillRect(PANEL_X, 8, 4, 188, classColor(kind));
        text(tft, PANEL_X + 12, 18, 2, classColor(kind), className(kind));
        text(tft, PANEL_X + 12, 51, 1, TftTouchShield.GRAY, description(kind));
        text(tft, PANEL_X + 12, 78, 1, TftTouchShield.WHITE, "LIFE  100");
        text(tft, PANEL_X + 12, 104, 1, GOLD, "ARMS");
        text(tft, PANEL_X + 12, 121, 1, TftTouchShield.WHITE, firstArm(kind));
        text(tft, PANEL_X + 12, 137, 1, TftTouchShield.WHITE, secondArm(kind));
        drawPlayButton(tft);
    }

    private static void drawPlayButton(TftTouchShield tft) {
        tft.fillRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, Hud.PANEL);
        tft.drawRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, GOLD);
        Hud.centered(tft, "PLAY", PLAY_Y + 6, 2, GOLD, Hud.PANEL);
    }

    private static boolean waitForPlay(TftTouchShield tft, int started, int duration) {
        boolean touching = true;
        while (CLOCK.millis() - started < duration) {
            if (tft.readTouch()) {
                if (!touching && onPlay(tft.touchX(), tft.touchY())) {
                    waitForRelease(tft);
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

    private static void text(TftTouchShield tft, int x, int y, int size, int color, String value) {
        tft.setTextSize(size);
        tft.setTextColor(color, Hud.PANEL);
        tft.setCursor(x, y);
        tft.print(value);
    }

    private static void waitForRelease(TftTouchShield tft) {
        int misses = 0;
        while (misses < 3) {
            if (tft.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }
}
