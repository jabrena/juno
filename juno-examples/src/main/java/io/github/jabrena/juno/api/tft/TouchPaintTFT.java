package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * A minimal finger-paint program for the ELEGOO 2.8" TFT touch screen shield: pick a color from
 * the palette along the top edge, then draw by touching the rest of the screen. The last palette
 * swatch (white with a black center) is the eraser: touching it clears the canvas. Every touch is
 * also logged to Serial with both its screen coordinates and raw readings, which is what
 * {@link TftTouchShield#calibrateTouch} needs if a panel reports offset or mirrored positions.
 */
public final class TouchPaintTFT {
    private static final int PALETTE_SIZE = 40;
    private static final int PALETTE_COLORS = 6;
    private static final int CLEAR = PALETTE_COLORS - 1;
    private static final int BRUSH = 4;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        TftTouchShield.begin();
        // Portrait with the palette on the edge opposite the shield's native row 0.
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        clearCanvas();
        drawPalette();
        selectSwatch(0);

        int brushColor = paletteColor(0);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            logTouch(x, y);

            if (y < PALETTE_SIZE) {
                int selected = x / PALETTE_SIZE;
                if (selected == CLEAR) {
                    clearCanvas();
                } else {
                    brushColor = paletteColor(selected);
                    selectSwatch(selected);
                }
            } else if (y - BRUSH / 2 >= PALETTE_SIZE) {
                TftTouchShield.fillRect(x - BRUSH / 2, y - BRUSH / 2, BRUSH, BRUSH, brushColor);
            }
        }
    }

    private static void logTouch(int x, int y) {
        Serial.println("x=" + x + " y=" + y
                + " rawX=" + TftTouchShield.touchRawX()
                + " rawY=" + TftTouchShield.touchRawY()
                + " z=" + TftTouchShield.touchPressure());
    }

    /** Paints every swatch, with the eraser's black center on the last one. */
    private static void drawPalette() {
        for (int i = 0; i < PALETTE_COLORS; i++) {
            TftTouchShield.fillRect(i * PALETTE_SIZE, 0, PALETTE_SIZE, PALETTE_SIZE, paletteColor(i));
        }
        TftTouchShield.fillRect(CLEAR * PALETTE_SIZE + PALETTE_SIZE / 4, PALETTE_SIZE / 4,
                PALETTE_SIZE / 2, PALETTE_SIZE / 2, TftTouchShield.BLACK);
    }

    /** Outlines the selected color swatch in white and every other color swatch in its own color. */
    private static void selectSwatch(int selected) {
        for (int i = 0; i < CLEAR; i++) {
            int outline = i == selected ? TftTouchShield.WHITE : paletteColor(i);
            TftTouchShield.drawRect(i * PALETTE_SIZE, 0, PALETTE_SIZE, PALETTE_SIZE, outline);
        }
    }

    /** Blanks everything below the palette. */
    private static void clearCanvas() {
        TftTouchShield.fillRect(0, PALETTE_SIZE, TftTouchShield.width(),
                TftTouchShield.height() - PALETTE_SIZE, TftTouchShield.BLACK);
    }

    private static int paletteColor(int index) {
        if (index == 0) {
            return TftTouchShield.RED;
        }
        if (index == 1) {
            return TftTouchShield.YELLOW;
        }
        if (index == 2) {
            return TftTouchShield.GREEN;
        }
        if (index == 3) {
            return TftTouchShield.CYAN;
        }
        if (index == 4) {
            return TftTouchShield.BLUE;
        }
        return TftTouchShield.WHITE;
    }
}
