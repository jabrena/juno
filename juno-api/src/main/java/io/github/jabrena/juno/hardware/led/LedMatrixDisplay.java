package io.github.jabrena.juno.hardware.led;

import io.github.jabrena.juno.hardware.display.Display;

/**
 * The board's built-in LED matrix as a {@link Display}: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q, one color. A color
 * other than {@link #BLACK} lights a pixel. Drawing changes a four-word buffer and {@link #flush()} sends it to the
 * matrix, so a program draws a whole frame before it flushes. Text is one line of 6x8 cells at size 1, which is the
 * only size that fits the 8 rows: {@link #setTextSize} is accepted and ignored, and text past the right edge is
 * clipped, not wrapped.
 *
 * <p>Programs get the board's instance from {@code MCU.getDisplay()} on the UNO R4 WiFi and UNO Q. The constructor is
 * for a unit test, which passes {@code mock(LedMatrix.class)} and checks the words passed to
 * {@link LedMatrix#loadFrame(int, int, int, int)}; the buffer is allocated once, so drawing allocates nothing.
 */
public final class LedMatrixDisplay implements Display {
    private static final int HEIGHT = 8;
    private static final int CELL_WIDTH = 6;
    private static final int CELL_HEIGHT = 8;

    private final LedMatrix matrix;
    private final int columns;
    /** The frame, packed MSB-first, left-to-right and top-to-bottom, exactly as {@link LedMatrix#loadFrame} reads it. */
    private final int[] words = new int[4];
    private int cursorX;
    private int cursorY;
    private int foreground = WHITE;
    private int background = BLACK;

    /** Starts {@code matrix} and creates the frame buffer. Create it once, at startup. */
    public LedMatrixDisplay(LedMatrix matrix) {
        this.matrix = matrix;
        matrix.begin();
        this.columns = matrix.columns();
    }

    @Override
    public int width() {
        return columns;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    /** Sends the buffered frame to the matrix. */
    @Override
    public void flush() {
        matrix.loadFrame(words[0], words[1], words[2], words[3]);
    }

    @Override
    public void fillScreen(int color) {
        fillRect(0, 0, columns, HEIGHT, color);
    }

    /** Sets the pixel at ({@code x}, {@code y}): lit for any color other than {@link #BLACK}. Off-matrix pixels are ignored. */
    @Override
    public void drawPixel(int x, int y, int color) {
        if (x < 0 || x >= columns || y < 0 || y >= HEIGHT) {
            return;
        }
        int pixel = y * columns + x;
        int word = pixel >> 5;
        int mask = 1 << (31 - (pixel & 31));
        words[word] = color != BLACK ? words[word] | mask : words[word] & ~mask;
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int color) {
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                drawPixel(col, row, color);
            }
        }
    }

    @Override
    public void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
    }

    /** Sets the lit and unlit colors of the text. Each character paints its whole cell, so text overwrites what was there. */
    @Override
    public void setTextColor(int foreground, int background) {
        this.foreground = foreground;
        this.background = background;
    }

    /** Accepted and ignored: the matrix shows text at size 1 only. */
    @Override
    public void setTextSize(int size) {
    }

    /** Prints one character at the cursor and advances it one cell. A newline moves the cursor to the next text line. */
    @Override
    public void print(char character) {
        if (character == '\n') {
            cursorX = 0;
            cursorY += CELL_HEIGHT;
            return;
        }
        for (int row = 0; row < CELL_HEIGHT; row++) {
            for (int col = 0; col < CELL_WIDTH; col++) {
                drawPixel(cursorX + col, cursorY + row, glyphColor(character, col, row));
            }
        }
        cursorX += CELL_WIDTH;
    }

    @Override
    public void print(String text) {
        for (int i = 0; i < text.length(); i++) {
            print(text.charAt(i));
        }
    }

    @Override
    public void println(String text) {
        print(text);
        print('\n');
    }

    /** The color of one pixel of a character cell: lit inside the 5x7 glyph, the background everywhere else. */
    private int glyphColor(char character, int col, int row) {
        boolean lit = col < LedMatrixFontAscii.GLYPH_WIDTH && row < LedMatrixFontAscii.GLYPH_HEIGHT
                && LedMatrixFontAscii.charPixel(character, col, row);
        return lit ? foreground : background;
    }
}
