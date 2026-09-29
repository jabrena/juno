package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Touch joystick, pilot selection, and switching between human and CPU flight. */
final class Controls {
    private static final int TAP_FRAMES = 7;
    private static final int DRAG_PIXELS = 14;
    private static final int STICK_TRAVEL = 70;

    private static final int CHOICE_X = 20;
    private static final int CHOICE_Y = 104;
    private static final int CHOICE_WIDTH = 130;
    private static final int CHOICE_HEIGHT = 72;
    private static final int CHOICE_GAP = 20;
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

    static void handleTouch(int[] ents) {
        boolean down = TftTouchShield.readTouch();
        if (down && TftTouchShield.touchY() < DisplayList.HEADER) {
            if (!headerPressed) {
                autopilot = !autopilot;
                center();
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
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
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

    static void choosePilot() {
        TftTouchShield.fillScreen(DisplayList.SKY);
        Hud.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        Hud.showCentered("Who flies the biplane?", 74, 1, TftTouchShield.WHITE);
        drawChoice(0, "HUMAN", "You fly and fire", false);
        drawChoice(1, "CPU", "Autopilot plays", false);
        Hud.showCentered("Tap the header in game to switch", 206, 1, SceneRenderer.HORIZON);
        int choice = -1;
        while (choice < 0) {
            if (TftTouchShield.readTouch()) {
                choice = choiceAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            Delay.millis(10);
        }
        autopilot = choice == 1;
        touching = false;
        drawChoice(choice, choice == 0 ? "HUMAN" : "CPU",
                choice == 0 ? "You fly and fire" : "Autopilot plays", true);
        waitForRelease();
    }

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
        int color = chosen ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT, color);
        TftTouchShield.drawRect(x, CHOICE_Y, CHOICE_WIDTH, CHOICE_HEIGHT,
                chosen ? TftTouchShield.WHITE : SceneRenderer.HORIZON);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - label.length() * 18) / 2, CHOICE_Y + 16);
        TftTouchShield.print(label);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, color);
        TftTouchShield.setCursor(x + (CHOICE_WIDTH - hint.length() * 6) / 2, CHOICE_Y + 52);
        TftTouchShield.print(hint);
    }

    /** Waits for a tap, giving up and returning {@code false} if none arrives within {@code timeoutMillis}. */
    static boolean waitForTap(int timeoutMillis) {
        int start = Clock.millis();
        while (!TftTouchShield.readTouch()) {
            if (Clock.millis() - start >= timeoutMillis) {
                return false;
            }
            Delay.millis(10);
        }
        waitForRelease();
        return true;
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
