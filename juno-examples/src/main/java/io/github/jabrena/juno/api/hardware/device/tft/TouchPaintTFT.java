package io.github.jabrena.juno.api.hardware.device.tft;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

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
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);

        // Portrait with the palette on the edge opposite the shield's native row 0.
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        clearCanvas(tft);
        drawPalette(tft);
        selectSwatch(tft, 0);

        int brushColor = paletteColor(0);
        while (true) {
            if (!tft.readTouch()) {
                continue;
            }
            int x = tft.touchX();
            int y = tft.touchY();
            logTouch(tft, x, y);

            if (y < PALETTE_SIZE) {
                int selected = x / PALETTE_SIZE;
                if (selected == CLEAR) {
                    clearCanvas(tft);
                } else {
                    brushColor = paletteColor(selected);
                    selectSwatch(tft, selected);
                }
            } else if (y - BRUSH / 2 >= PALETTE_SIZE) {
                tft.fillRect(x - BRUSH / 2, y - BRUSH / 2, BRUSH, BRUSH, brushColor);
            }
        }
    }

    private static void logTouch(TftTouchShield tft, int x, int y) {
        Serial.print("x=");
        Serial.print(x);
        Serial.print(" y=");
        Serial.print(y);
        Serial.print(" rawX=");
        Serial.print(tft.touchRawX());
        Serial.print(" rawY=");
        Serial.print(tft.touchRawY());
        Serial.print(" z=");
        Serial.println(tft.touchPressure());
    }

    /** Paints every swatch, with the eraser's black center on the last one. */
    private static void drawPalette(TftTouchShield tft) {
        for (int i = 0; i < PALETTE_COLORS; i++) {
            tft.fillRect(i * PALETTE_SIZE, 0, PALETTE_SIZE, PALETTE_SIZE, paletteColor(i));
        }
        tft.fillRect(CLEAR * PALETTE_SIZE + PALETTE_SIZE / 4, PALETTE_SIZE / 4,
                PALETTE_SIZE / 2, PALETTE_SIZE / 2, TftTouchShield.BLACK);
    }

    /** Outlines the selected color swatch in white and every other color swatch in its own color. */
    private static void selectSwatch(TftTouchShield tft, int selected) {
        for (int i = 0; i < CLEAR; i++) {
            int outline = i == selected ? TftTouchShield.WHITE : paletteColor(i);
            tft.drawRect(i * PALETTE_SIZE, 0, PALETTE_SIZE, PALETTE_SIZE, outline);
        }
    }

    /** Blanks everything below the palette. */
    private static void clearCanvas(TftTouchShield tft) {
        tft.fillRect(0, PALETTE_SIZE, tft.width(),
                tft.height() - PALETTE_SIZE, TftTouchShield.BLACK);
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
