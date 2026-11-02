package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Flight instruments, controls, centered messages, and pilot-choice buttons. */
final class Hud {
    static final int HEADER_HEIGHT = 30;
    static final int HEADER_BACKGROUND = 0x2945;
    private static final int BUTTON_Y = 274;
    private static final int BUTTON_HEIGHT = 44;
    private static final int BUTTON = 0xCD05;
    private static final int BUTTON_PRESSED = TftTouchShield.YELLOW;
    private static final int CHOICE = 0x2124;
    private static final int CHOICE_CHOSEN = 0x7800;
    private static int shownPressed = -2;
    private static boolean shownAutopilot;

    private Hud() {
    }

    static void invalidate() {
        shownAutopilot = !Controls.autopilot;
    }

    static void drawHeader(TftTouchShield tft) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(4, 4);
        tft.print("SCORE ");
        printPadded(tft, Flight.score, 5);
        tft.print("   FUEL ");
        tft.setTextColor(Flight.fuel < 300 ? TftTouchShield.RED : TftTouchShield.WHITE,
                HEADER_BACKGROUND);
        printPadded(tft, Flight.fuel, 4);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.print("  BEST ");
        printPadded(tft, Flight.best, 5);
        drawSpeeds(tft);
    }

    private static void drawSpeeds(TftTouchShield tft) {
        tft.setCursor(4, 18);
        tft.print(Controls.autopilot ? "CPU H-SP " : "HUM H-SP ");
        speedColor(tft, Math.abs(Flight.vx) > Flight.SAFE_HORIZONTAL);
        printPadded(tft, Math.round(Flight.vx * 10), 3);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.print(" V-SP ");
        speedColor(tft, Flight.vy > Flight.SAFE_VERTICAL);
        printPadded(tft, Math.round(Flight.vy * 10), 3);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.print(" #");
        tft.print(Flight.descent);
        tft.print(" ");
        shownAutopilot = Controls.autopilot;
    }

    private static void speedColor(TftTouchShield tft, boolean tooFast) {
        tft.setTextColor(tooFast ? TftTouchShield.RED : TftTouchShield.GREEN, HEADER_BACKGROUND);
    }

    private static void printPadded(TftTouchShield tft, int value, int width) {
        int digits = 1;
        int rest = Math.abs(value);
        while (rest >= 10) {
            rest = rest / 10;
            digits = digits + 1;
        }
        if (value < 0) digits = digits + 1;
        for (int index = digits; index < width; index++) tft.print(" ");
        tft.print(value);
    }

    static void drawButtons(TftTouchShield tft) {
        shownPressed = -2;
        updateButtons(tft, Controls.NONE);
    }

    static void updateButtons(TftTouchShield tft, int pressed) {
        if (pressed == shownPressed && shownAutopilot == Controls.autopilot) return;
        drawButton(tft, Controls.LEFT, "<", pressed);
        drawButton(tft, Controls.BURN, "BURN", pressed);
        drawButton(tft, Controls.RIGHT, ">", pressed);
        shownPressed = pressed;
        shownAutopilot = Controls.autopilot;
    }

    private static void drawButton(TftTouchShield tft, int index, String label, int pressed) {
        int x = 4 + index * 80;
        int color = index == pressed ? BUTTON_PRESSED : BUTTON;
        tft.fillRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, color);
        tft.drawRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, color);
        tft.setCursor(x + (72 - label.length() * 12) / 2, BUTTON_Y + 15);
        tft.print(label);
    }

    static void drawChoice(TftTouchShield tft, int x, String label, boolean selected) {
        int color = selected ? CHOICE_CHOSEN : CHOICE;
        tft.fillRect(x, 104, 104, 72, color);
        tft.drawRect(x, 104, 104, 72, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(x + (104 - label.length() * 12) / 2, 132);
        tft.print(label);
    }

    static void showCentered(TftTouchShield tft, String text, int y, int size, int color) {
        tft.setTextSize(size);
        tft.setTextColor(color, Terrain.SKY);
        tft.setCursor((Terrain.WIDTH - text.length() * 6 * size) / 2, y);
        tft.print(text);
    }
}
