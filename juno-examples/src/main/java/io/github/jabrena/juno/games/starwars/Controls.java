package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The touch panel: the pilot screen, then in flight the crosshair. Drag moves the crosshair; a short
 * tap that did not move fires where it landed, on release. Tapping the header hands the X-wing to the
 * CPU or back.
 */
final class Controls {
    /** A touch this short (in frames) that barely moved is a tap, and fires on release. */
    private static final int TAP_FRAMES = 8;
    private static final int DRAG_PIXELS = 20;

    // The HUMAN and CPU buttons of the pilot screen.
    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;

    static int crossX = Camera.CENTER_X;
    static int crossY = Camera.CENTER_Y;
    static boolean autopilot;
    private static boolean touching;
    private static boolean dragged;
    private static int touchFrames;
    private static int releaseMisses;
    private static int startX;
    private static int startY;
    private static boolean headerPressed;

    private Controls() {
    }

    static void handleTouch(int[] ents) {
        boolean down = TftTouchShield.readTouch();
        if (down && TftTouchShield.touchY() < DisplayList.HEADER) {
            if (!headerPressed) {
                autopilot = !autopilot;
                touching = false;
                Hud.drawHeader();
            }
            headerPressed = true;
            return;
        }
        headerPressed = false;
        if (autopilot) {
            return;
        }
        if (down) {
            int x = Math.max(8, Math.min(TftTouchShield.touchX(), DisplayList.WIDTH - 9));
            int y = Math.max(DisplayList.HEADER + 8, Math.min(TftTouchShield.touchY(), DisplayList.HEIGHT - 9));
            crossX = x;
            crossY = y;
            if (!touching) {
                touching = true;
                dragged = false;
                touchFrames = 0;
                startX = x;
                startY = y;
            } else {
                touchFrames = touchFrames + 1;
                if (Math.abs(x - startX) + Math.abs(y - startY) > DRAG_PIXELS) {
                    dragged = true;
                }
            }
            releaseMisses = 0;
        } else if (touching) {
            // Two frames without contact, so a flicker of the resistive panel is not a release.
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 2) {
                touching = false;
                if (touchFrames <= TAP_FRAMES && !dragged) {
                    Combat.fire(ents);
                }
            }
        }
    }

    /** The pilot screen: waits for a tap on HUMAN or CPU. */
    static void choosePilot() {
        TftTouchShield.fillScreen(DisplayList.SPACE);
        Hud.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.YELLOW);
        Hud.showCentered("Who flies the X-wing?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You aim and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        Hud.showCentered("Tap the header in game to switch", 206, 1, SceneRenderer.STAR);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        touching = false;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You aim and fire" : "Autopilot plays", true);
        waitForRelease();
    }

    /** The pilot button at (x, y): 0 for HUMAN, 1 for CPU, or -1. */
    static int choiceAt(int x, int y) {
        if (y < CHOICE_Y || y >= CHOICE_Y + CHOICE_HEIGHT) {
            return -1;
        }
        for (int index = 0; index < 2; index++) {
            int left = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
            if (x >= left && x < left + CHOICE_WIDTH) {
                return index;
            }
        }
        return -1;
    }

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        int x = CHOICE_X + index * (CHOICE_WIDTH + CHOICE_GAP);
        int color = chosen ? SceneRenderer.STRUCTURE : 0x2124;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT,
                chosen ? TftTouchShield.WHITE : SceneRenderer.STRUCTURE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }

    static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        waitForRelease();
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
