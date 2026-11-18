package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.fonts.AsciiFont;

/**
 * The text cursor, size and colors of a {@link Mhs35TftDisplay}, and the glyphs it prints: each character is one
 * window of 6x8 cells scaled by the text size and the display's {@link Viewport}, streamed in a single pass. Split
 * out of the display to keep its cyclomatic complexity down.
 */
final class Mhs35Text {
    private final Mhs35TftDisplay display;
    private final Ili9486Bus bus;
    private final Viewport viewport;
    private int cursorX;
    private int cursorY;
    private int size = 1;
    private int foreground = Display.WHITE;
    private int background = Display.BLACK;

    Mhs35Text(Mhs35TftDisplay display, Ili9486Bus bus, Viewport viewport) {
        this.display = display;
        this.bus = bus;
        this.viewport = viewport;
    }

    void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
    }

    void setColors(int foregroundColor, int backgroundColor) {
        foreground = foregroundColor;
        background = backgroundColor;
    }

    void setSize(int textSize) {
        size = Math.max(textSize, 1);
    }

    void print(String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            write(text.charAt(i));
        }
    }

    /** {@code value} in decimal, digit by digit, without building a string. */
    void print(int value) {
        int rest = value;
        if (rest < 0) {
            write('-');
            if (rest == Integer.MIN_VALUE) {
                print("2147483648");
                return;
            }
            rest = -rest;
        }
        int divisor = 1;
        while (rest / divisor >= 10) {
            divisor = divisor * 10;
        }
        while (divisor > 0) {
            write('0' + rest / divisor % 10);
            divisor = divisor / 10;
        }
    }

    /** One character at the cursor, advancing it; {@code '\n'} starts the next line, and text wraps at the edge. */
    void write(int character) {
        if (character == '\n') {
            newLine();
            return;
        }
        int cellWidth = Mhs35TftDisplay.CHAR_WIDTH * size;
        if (cursorX + cellWidth > display.width()) {
            newLine();
        }
        drawChar(cursorX, cursorY, character);
        cursorX = cursorX + cellWidth;
    }

    void newLine() {
        cursorX = 0;
        cursorY = cursorY + Mhs35TftDisplay.CHAR_HEIGHT * size;
    }

    private void drawChar(int x, int y, int character) {
        int code = character < 32 || character > 126 ? 32 : character;
        int cellWidth = Mhs35TftDisplay.CHAR_WIDTH * size;
        int cellHeight = Mhs35TftDisplay.CHAR_HEIGHT * size;
        if (x < 0 || y < 0 || x + cellWidth > display.width() || y + cellHeight > display.height()) {
            return;
        }
        // The cell's panel window; each panel pixel shows the glyph pixel of the logical position it covers.
        int left = viewport.panelX(x);
        int right = viewport.panelX(x + cellWidth);
        int top = viewport.panelY(y);
        int bottom = viewport.panelY(y + cellHeight);
        bus.beginWindow(left, top, right - 1, bottom - 1);
        for (int panelRow = top; panelRow < bottom; panelRow++) {
            int row = (viewport.logicalY(panelRow) - y) / size;
            for (int panelColumn = left; panelColumn < right; panelColumn++) {
                int column = (viewport.logicalX(panelColumn) - x) / size;
                bus.pixel(lit(code, column, row) ? foreground : background);
            }
        }
        bus.endPixels();
    }

    // The 6x8 cell's spacing column and row stay background.
    private static boolean lit(int code, int column, int row) {
        return column < AsciiFont.GLYPH_WIDTH && row < AsciiFont.GLYPH_HEIGHT && AsciiFont.charPixel(code, column, row);
    }
}
