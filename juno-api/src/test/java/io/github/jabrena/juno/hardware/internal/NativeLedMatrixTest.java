package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.fonts.AsciiFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix display")
class NativeLedMatrixTest {
    private static final int TOP_LEFT = 0x80000000;

    @AfterEach
    void restoreTheBoardDisplay() {
        NativeLedMatrix.setDisplay(NativeLedMatrix.INSTANCE);
    }

    @Test
    @DisplayName("Reports the matrix's width and eight rows")
    void shouldReportTheMatrixSize() {
        assertThat(new NativeLedMatrix(12).width()).isEqualTo(12);
        assertThat(new NativeLedMatrix(13).width()).isEqualTo(13);
        assertThat(new NativeLedMatrix(13).height()).isEqualTo(8);
    }

    @Test
    @DisplayName("Any color other than black lights a pixel, and black turns it off")
    void shouldApplyTheMonochromeRule() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);

        matrix.drawPixel(0, 0, Display.RED);
        matrix.drawPixel(1, 0, Display.BLACK);
        assertThat(matrix.word(0)).isEqualTo(TOP_LEFT);

        matrix.drawPixel(0, 0, Display.BLACK);
        assertThat(matrix.word(0)).isZero();
    }

    @Test
    @DisplayName("Pixels outside the matrix are clipped without an exception")
    void shouldClipOutOfRangePixels() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);

        matrix.drawPixel(12, 0, Display.WHITE);
        matrix.drawPixel(0, 8, Display.WHITE);
        matrix.drawPixel(-1, 0, Display.WHITE);

        assertThat(new int[] {matrix.word(0), matrix.word(1), matrix.word(2), matrix.word(3)})
                .containsOnly(0);
    }

    @Test
    @DisplayName("Places pixel p = row * columns + column at bit 31 - p % 32 of word p / 32")
    void shouldPackPixelsRowByRow() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);

        // Pixel 12 is (row 1, column 0) and pixel 35 is (row 2, column 11)
        matrix.drawPixel(0, 1, Display.WHITE);
        matrix.drawPixel(11, 2, Display.WHITE);

        assertThat(matrix.word(0)).isEqualTo(0x00080000);
        assertThat(matrix.word(1)).isEqualTo(0x10000000);
    }

    @Test
    @DisplayName("The 13-column matrix ends its last row in the fourth word, at pixel 96 (row 7, column 5)")
    void shouldPackThe13ColumnMatrixIntoFourWords() {
        NativeLedMatrix matrix = new NativeLedMatrix(13);

        matrix.drawPixel(5, 7, Display.WHITE);

        assertThat(matrix.word(3)).isEqualTo(TOP_LEFT);
    }

    @Test
    @DisplayName("Draws a 5x7 glyph at the cursor; the size request is ignored")
    void shouldDrawAGlyphAtTheCursorAtSizeOne() {
        // '!' is lit in column 2 of rows 0-3 and row 5: pixels 2, 14, 26, 38 and 62 on a 12-column matrix
        NativeLedMatrix matrix = new NativeLedMatrix(12);
        matrix.setTextColor(Display.WHITE, Display.BLACK);
        matrix.setTextSize(3);

        matrix.setCursor(0, 0);
        matrix.print('!');

        assertThat(matrix.word(0)).isEqualTo(0x20020020);
        assertThat(matrix.word(1)).isEqualTo(0x02000002);
    }

    @Test
    @DisplayName("Text past the right edge is clipped, not wrapped")
    void shouldClipTextAtTheRightEdge() {
        // The '!' column 2 lands at x = 13, past the 12-column matrix; the second line is below the eight rows
        NativeLedMatrix matrix = new NativeLedMatrix(12);
        matrix.setTextColor(Display.WHITE, Display.BLACK);

        matrix.setCursor(11, 0);
        matrix.print('!');
        matrix.setCursor(0, 8);
        matrix.print('!');

        assertThat(new int[] {matrix.word(0), matrix.word(1), matrix.word(2)}).containsOnly(0);
    }

    @Test
    @DisplayName("A character cell paints its background, so printing over a lit screen darkens the cell")
    void shouldPaintTheBackgroundOfEachCell() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);
        matrix.fillScreen(Display.WHITE);
        matrix.setTextColor(Display.WHITE, Display.BLACK);

        matrix.setCursor(0, 0);
        matrix.print('!');

        // Lit outside the 6x8 cell, and inside it only where the '!' glyph is
        int[] expected = new int[3];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) {
                boolean lit = x >= 6 || (x < 5 && y < 7 && AsciiFont.charPixel('!', x, y));
                if (lit) {
                    int pixel = y * 12 + x;
                    expected[pixel >> 5] |= 1 << (31 - (pixel & 31));
                }
            }
        }
        assertThat(new int[] {matrix.word(0), matrix.word(1), matrix.word(2)}).containsExactly(expected);
    }

    @Test
    @DisplayName("fillScreen lights every pixel of the matrix")
    void shouldFillTheWholeMatrix() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);

        matrix.fillScreen(Display.WHITE);

        assertThat(new int[] {matrix.word(0), matrix.word(1), matrix.word(2), matrix.word(3)})
                .containsExactly(-1, -1, -1, 0);
    }

    @Test
    @DisplayName("print(String) draws each character in turn, one cell after another")
    void shouldPrintEachCharacterInItsCell() {
        NativeLedMatrix printed = new NativeLedMatrix(12);
        NativeLedMatrix charByChar = new NativeLedMatrix(12);
        printed.setTextColor(Display.WHITE, Display.BLACK);
        charByChar.setTextColor(Display.WHITE, Display.BLACK);

        printed.print("!!");
        charByChar.print('!');
        charByChar.print('!');

        assertThat(new int[] {printed.word(0), printed.word(1), printed.word(2)})
                .containsExactly(new int[] {charByChar.word(0), charByChar.word(1), charByChar.word(2)});
    }

    @Test
    @DisplayName("println prints the text and then moves to the next line, which the one-line matrix does not show")
    void shouldMoveToTheNextLineAfterPrintln() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);
        matrix.setTextColor(Display.WHITE, Display.BLACK);

        matrix.println("");
        matrix.setCursor(0, 0);
        matrix.print('!');

        assertThat(new int[] {matrix.word(0), matrix.word(1)}).containsExactly(0x20020020, 0x02000002);
    }

    @Test
    @DisplayName("A newline moves the cursor to the start of the next text line, below the eight rows")
    void shouldMoveTheCursorOnANewline() {
        NativeLedMatrix matrix = new NativeLedMatrix(12);
        matrix.setTextColor(Display.WHITE, Display.BLACK);

        matrix.print('\n');
        matrix.print('!');

        assertThat(new int[] {matrix.word(0), matrix.word(1), matrix.word(2)}).containsOnly(0);
    }

    @Test
    @DisplayName("display() hands out the display a test or an emulator installed")
    void shouldHandOutTheInstalledDisplay() {
        NativeLedMatrix replacement = new NativeLedMatrix(13);

        NativeLedMatrix.setDisplay(replacement);

        assertThat(NativeLedMatrix.display()).isSameAs(replacement);
    }
}
