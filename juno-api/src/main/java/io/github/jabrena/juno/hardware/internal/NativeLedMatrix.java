package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.fonts.AsciiFont;
import org.jspecify.annotations.Nullable;

/**
 * The board's built-in LED matrix as a {@link Display}: 12x8 on the UNO R4 WiFi, 13x8 on the UNO Q, one color. A color
 * other than {@link #BLACK} lights a pixel. Drawing changes a four-word buffer and {@link #flush()} sends it to the
 * matrix, so a program draws a whole frame before it flushes. Text is one line of 6x8 cells at size 1, the only size
 * that fits the 8 rows: {@link #setTextSize} is accepted and ignored, and text past the right edge is clipped, not
 * wrapped.
 *
 * <p>{@link io.github.jabrena.juno.hardware.MCU#getDisplay()} hands this out on the UNO R4 WiFi and the UNO Q. The
 * four matrix operations are intrinsics the compiler lowers to the {@code Arduino_LED_Matrix} library; everything else
 * is plain Java over the buffer, so a unit test can draw into it without the board.
 */
public final class NativeLedMatrix implements Display {
    /** The board's LED matrix: the only instance the board uses. */
    public static final NativeLedMatrix INSTANCE = new NativeLedMatrix();

    private static final int HEIGHT = 8;
    private static final int CELL_WIDTH = 6;
    private static final int CELL_HEIGHT = 8;

    /** What {@link #display()} returns instead of {@link #INSTANCE}: null on the board. */
    private static @Nullable Display replacement;
    private static boolean begun;

    /** The frame, packed MSB-first, left-to-right and top-to-bottom, exactly as {@link #loadFrame} reads it. */
    private final int[] words = new int[4];
    /** Zero until the first {@link #width()} asks the board; a unit test sets it through the constructor below. */
    private int columns;
    private int cursorX;
    private int cursorY;
    private int foreground = WHITE;
    private int background = BLACK;

    private NativeLedMatrix() {
    }

    /** A matrix of {@code columns} columns with no board behind it: a unit test draws into it without the intrinsics. */
    NativeLedMatrix(int columns) {
        this.columns = columns;
    }

    /**
     * The display {@link io.github.jabrena.juno.hardware.MCU#getDisplay()} hands out on the UNO R4 WiFi and the UNO Q:
     * the board's matrix, started on first use, unless a whole-program emulator or a test replaced it.
     */
    public static Display display() {
        Display current = replacement;
        if (current != null) {
            return current;
        }
        if (!begun) {
            INSTANCE.begin();
            begun = true;
        }
        return INSTANCE;
    }

    /** Makes {@link #display()} return {@code display}: for a whole-program emulator or a unit test, never on the board. */
    public static void setDisplay(Display display) {
        replacement = display;
    }

    /** Starts the matrix. */
    public native void begin();

    /** The number of columns of the matrix on the board this program is compiled for: 12 or 13. */
    public native int columns();

    /**
     * Loads a frame packed MSB-first into four 32-bit words, exactly as the underlying
     * {@code loadFrame(const uint32_t[...])} of the board's {@code Arduino_LED_Matrix} library expects: the UNO R4's 96
     * pixels use the first three words and ignore {@code word3}; the UNO Q's 104 pixels use all four.
     */
    public native void loadFrame(int word0, int word1, int word2, int word3);

    /** Turns every pixel off. */
    public native void clear();

    @Override
    public int width() {
        if (columns == 0) {
            columns = columns();
        }
        return columns;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    /** Sends the buffered frame to the matrix. */
    @Override
    public void flush() {
        loadFrame(words[0], words[1], words[2], words[3]);
    }

    @Override
    public void fillScreen(int color) {
        fillRect(0, 0, width(), HEIGHT, color);
    }

    /** Sets the pixel at ({@code x}, {@code y}): lit for any color other than {@link #BLACK}. Off-matrix pixels are ignored. */
    @Override
    public void drawPixel(int x, int y, int color) {
        int columns = width();
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

    /** The packed word {@code index} of the buffer, for a unit test. */
    int word(int index) {
        return words[index];
    }

    /** The color of one pixel of a character cell: lit inside the 5x7 glyph, the background everywhere else. */
    private int glyphColor(char character, int col, int row) {
        boolean lit = col < AsciiFont.GLYPH_WIDTH && row < AsciiFont.GLYPH_HEIGHT
                && AsciiFont.charPixel(character, col, row);
        return lit ? foreground : background;
    }
}
