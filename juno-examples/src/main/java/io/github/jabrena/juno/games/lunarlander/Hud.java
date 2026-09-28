package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.api.tft.TftTouchShield;

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

    static void drawHeader() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(4, 4);
        TftTouchShield.print("SCORE ");
        printPadded(Flight.score, 5);
        TftTouchShield.print("   FUEL ");
        TftTouchShield.setTextColor(Flight.fuel < 300 ? TftTouchShield.RED : TftTouchShield.WHITE,
                HEADER_BACKGROUND);
        printPadded(Flight.fuel, 4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print("  BEST ");
        printPadded(Flight.best, 5);
        drawSpeeds();
    }

    private static void drawSpeeds() {
        TftTouchShield.setCursor(4, 18);
        TftTouchShield.print(Controls.autopilot ? "CPU H-SP " : "HUM H-SP ");
        speedColor(Math.abs(Flight.vx) > Flight.SAFE_HORIZONTAL);
        printPadded(Math.round(Flight.vx * 10), 3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print(" V-SP ");
        speedColor(Flight.vy > Flight.SAFE_VERTICAL);
        printPadded(Math.round(Flight.vy * 10), 3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.print(" #");
        TftTouchShield.print(Flight.descent);
        TftTouchShield.print(" ");
        shownAutopilot = Controls.autopilot;
    }

    private static void speedColor(boolean tooFast) {
        TftTouchShield.setTextColor(tooFast ? TftTouchShield.RED : TftTouchShield.GREEN, HEADER_BACKGROUND);
    }

    private static void printPadded(int value, int width) {
        int digits = 1;
        int rest = Math.abs(value);
        while (rest >= 10) {
            rest = rest / 10;
            digits = digits + 1;
        }
        if (value < 0) digits = digits + 1;
        for (int index = digits; index < width; index++) TftTouchShield.print(" ");
        TftTouchShield.print(value);
    }

    static void drawButtons() {
        shownPressed = -2;
        updateButtons(Controls.NONE);
    }

    static void updateButtons(int pressed) {
        if (pressed == shownPressed && shownAutopilot == Controls.autopilot) return;
        drawButton(Controls.LEFT, "<", pressed);
        drawButton(Controls.BURN, "BURN", pressed);
        drawButton(Controls.RIGHT, ">", pressed);
        shownPressed = pressed;
        shownAutopilot = Controls.autopilot;
    }

    private static void drawButton(int index, String label, int pressed) {
        int x = 4 + index * 80;
        int color = index == pressed ? BUTTON_PRESSED : BUTTON;
        TftTouchShield.fillRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, color);
        TftTouchShield.drawRect(x, BUTTON_Y, 72, BUTTON_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
        TftTouchShield.setCursor(x + (72 - label.length() * 12) / 2, BUTTON_Y + 15);
        TftTouchShield.print(label);
    }

    static void drawChoice(int x, String label, boolean selected) {
        int color = selected ? CHOICE_CHOSEN : CHOICE;
        TftTouchShield.fillRect(x, 104, 104, 72, color);
        TftTouchShield.drawRect(x, 104, 104, 72, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (104 - label.length() * 12) / 2, 132);
        TftTouchShield.print(label);
    }

    static void showCentered(String text, int y, int size, int color) {
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, Terrain.SKY);
        TftTouchShield.setCursor((Terrain.WIDTH - text.length() * 6 * size) / 2, y);
        TftTouchShield.print(text);
    }
}
