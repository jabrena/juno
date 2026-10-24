package io.github.jabrena.juno.games.starwars;

import io.github.jabrena.juno.games.common.PilotMenu;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * The touch panel: the pilot screen, then in flight the crosshair. Drag moves the crosshair; a short
 * tap that did not move fires where it landed, on release. Tapping the header hands the X-wing to the
 * CPU or back.
 */
final class Controls {
    /** A touch this short (in frames) that barely moved is a tap, and fires on release. */
    private static final int TAP_FRAMES = 8;
    private static final int DRAG_PIXELS = 20;

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
        int choice = PilotMenu.waitForChoice();
        autopilot = choice == 1;
        touching = false;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU", choice == 0 ? "You aim and fire" : "Autopilot plays", true);
        PilotMenu.waitForRelease();
    }

    /** The pilot button at (x, y): 0 for HUMAN, 1 for CPU, or -1. */
    static int choiceAt(int x, int y) {
        return PilotMenu.choiceAt(x, y);
    }

    private static void drawChoice(int index, String label, String hint, boolean chosen) {
        PilotMenu.drawChoice(index, label, hint, chosen, SceneRenderer.STRUCTURE, 0x2124,
                TftTouchShield.WHITE, SceneRenderer.STRUCTURE, TftTouchShield.YELLOW, TftTouchShield.WHITE);
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(int timeoutMillis) {
        return PilotMenu.waitForTap(timeoutMillis);
    }
}
