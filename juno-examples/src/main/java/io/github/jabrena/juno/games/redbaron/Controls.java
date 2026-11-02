package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.games.common.PilotMenu;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Touch joystick, pilot selection, and switching between human and CPU flight. */
final class Controls {
    private static final int TAP_FRAMES = 7;
    private static final int DRAG_PIXELS = 14;
    private static final int STICK_TRAVEL = 70;

    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;

    static int stickX;
    static int stickY;
    static boolean autopilot;
    private static boolean touching;
    private static boolean dragged;
    private static int touchFrames;
    private static int releaseMisses;
    private static int originX;
    private static int originY;
    private static boolean headerPressed;

    private Controls() {
    }

    static void center() {
        stickX = 0;
        stickY = 0;
        touching = false;
    }

    static void handleTouch(TftTouchShield tft, int[] ents) {
        boolean down = tft.readTouch();
        if (down && tft.touchY() < DisplayList.HEADER) {
            if (!headerPressed) {
                autopilot = !autopilot;
                center();
                Hud.drawHeader(tft);
            }
            headerPressed = true;
            return;
        }
        headerPressed = false;
        if (autopilot) {
            return;
        }
        if (down) {
            int x = tft.touchX();
            int y = tft.touchY();
            if (!touching) {
                touching = true;
                dragged = false;
                touchFrames = 0;
                originX = x;
                originY = y;
            } else {
                touchFrames = touchFrames + 1;
                if (Math.abs(x - originX) + Math.abs(y - originY) > DRAG_PIXELS) {
                    dragged = true;
                }
            }
            stickX = Camera.clamp((x - originX) * 100 / STICK_TRAVEL, -100, 100);
            stickY = Camera.clamp((originY - y) * 100 / STICK_TRAVEL, -100, 100);
            releaseMisses = 0;
        } else if (touching) {
            releaseMisses = releaseMisses + 1;
            if (releaseMisses >= 2) {
                touching = false;
                stickX = 0;
                stickY = 0;
                if (touchFrames <= TAP_FRAMES && !dragged) {
                    Combat.fire(ents);
                }
            }
        }
    }

    static void choosePilot(TftTouchShield tft) {
        tft.fillScreen(DisplayList.SKY);
        Hud.showCentered(tft, "CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        Hud.showCentered(tft, "Who flies the biplane?", 74, 1, TftTouchShield.WHITE);
        drawChoice(tft, 0, "HUMAN", "You fly and fire", false);
        drawChoice(tft, 1, "CPU", "Autopilot plays", false);
        Hud.showCentered(tft, "Tap the header in game to switch", 206, 1, SceneRenderer.HORIZON);
        int choice = PilotMenu.waitForChoice(tft);
        autopilot = choice == 1;
        touching = false;
        drawChoice(tft, choice, choice == 0 ? "HUMAN" : "CPU",
                choice == 0 ? "You fly and fire" : "Autopilot plays", true);
        PilotMenu.waitForRelease(tft);
    }

    static int choiceAt(int x, int y) {
        return PilotMenu.choiceAt(x, y);
    }

    private static void drawChoice(TftTouchShield tft, int index, String label, String hint, boolean chosen) {
        PilotMenu.drawChoice(tft, index, label, hint, chosen, CHOICE_CHOSEN, CHOICE,
                TftTouchShield.WHITE, SceneRenderer.HORIZON, TftTouchShield.WHITE, TftTouchShield.YELLOW);
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(TftTouchShield tft, int timeoutMillis) {
        return PilotMenu.waitForTap(tft, timeoutMillis);
    }
}
