package io.github.jabrena.juno.hardware.led;

import io.github.jabrena.juno.hardware.display.Display;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LED matrix as a Display")
class LedMatrixDisplayTest {
    private static final int TOP_LEFT = 0x80000000;

    private static LedMatrix matrix(int columns) {
        LedMatrix matrix = mock(LedMatrix.class);
        when(matrix.columns()).thenReturn(columns);
        return matrix;
    }

    @Test
    @DisplayName("Reports the matrix's width and eight rows, and starts the matrix")
    void shouldReportTheMatrixSize() {
        // Given
        LedMatrix r4 = matrix(12);
        LedMatrix q = matrix(13);

        // When
        Display onR4 = new LedMatrixDisplay(r4);
        Display onQ = new LedMatrixDisplay(q);

        // Then
        assertThat(onR4.width()).isEqualTo(12);
        assertThat(onQ.width()).isEqualTo(13);
        assertThat(onR4.height()).isEqualTo(8);
        verify(r4).begin();
        verify(q).begin();
    }

    @Test
    @DisplayName("Nothing reaches the matrix before flush")
    void shouldLoadTheFrameOnlyOnFlush() {
        // Given
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);

        // When
        display.fillRect(0, 0, 12, 8, Display.WHITE);

        // Then
        verify(matrix, never()).loadFrame(anyInt(), anyInt(), anyInt(), anyInt());

        // When
        display.flush();

        // Then
        verify(matrix).loadFrame(-1, -1, -1, 0);
    }

    @Test
    @DisplayName("Any color other than black lights a pixel, and black turns it off")
    void shouldApplyTheMonochromeRule() {
        // Given
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);

        // When
        display.drawPixel(0, 0, Display.RED);
        display.drawPixel(1, 0, Display.BLACK);
        display.flush();
        display.drawPixel(0, 0, Display.BLACK);
        display.flush();

        // Then
        InOrder order = inOrder(matrix);
        order.verify(matrix).loadFrame(TOP_LEFT, 0, 0, 0);
        order.verify(matrix).loadFrame(0, 0, 0, 0);
    }

    @Test
    @DisplayName("Pixels outside the matrix are clipped without an exception")
    void shouldClipOutOfRangePixels() {
        // Given
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);

        // When
        display.drawPixel(12, 0, Display.WHITE);
        display.drawPixel(0, 8, Display.WHITE);
        display.drawPixel(-1, 0, Display.WHITE);
        display.flush();

        // Then
        verify(matrix).loadFrame(0, 0, 0, 0);
    }

    @Test
    @DisplayName("The Q's last row ends in its fourth word, at pixel 96 (row 7, column 5 of 13)")
    void shouldPackThe13ColumnMatrixIntoFourWords() {
        // Given
        LedMatrix matrix = matrix(13);
        Display display = new LedMatrixDisplay(matrix);

        // When
        display.drawPixel(5, 7, Display.WHITE);
        display.flush();

        // Then
        verify(matrix).loadFrame(0, 0, 0, TOP_LEFT);
    }

    @Test
    @DisplayName("Places pixel p = row * columns + column at bit 31 - p % 32 of word p / 32")
    void shouldPackPixelsRowByRow() {
        // Given
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);

        // When: pixel 12 is (row 1, column 0) and pixel 35 is (row 2, column 11)
        display.drawPixel(0, 1, Display.WHITE);
        display.drawPixel(11, 2, Display.WHITE);
        display.flush();

        // Then
        verify(matrix).loadFrame(0x00080000, 0x10000000, 0, 0);
    }

    @Test
    @DisplayName("Draws a 5x7 glyph at the cursor; the size request is ignored")
    void shouldDrawAGlyphAtTheCursorAtSizeOne() {
        // Given: '!' is lit in column 2 of rows 0-3 and row 5, so on a 12-column matrix that is pixels 2, 14, 26, 38 and 62
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setTextSize(3);

        // When
        display.setCursor(0, 0);
        display.print('!');
        display.flush();

        // Then
        verify(matrix).loadFrame(0x20020020, 0x02000002, 0, 0);
    }

    @Test
    @DisplayName("Text past the right edge is clipped, not wrapped")
    void shouldClipTextAtTheRightEdge() {
        // Given: the '!' column 2 lands at x = 13, past the 12-column matrix
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);
        display.setTextColor(Display.WHITE, Display.BLACK);

        // When
        display.setCursor(11, 0);
        display.print('!');
        display.setCursor(0, 8);
        display.print('!');
        display.flush();

        // Then: nothing lands on the matrix, and the second line is below its eight rows
        verify(matrix).loadFrame(0, 0, 0, 0);
    }

    @Test
    @DisplayName("A character cell paints its background, so printing over a lit screen darkens the cell")
    void shouldPaintTheBackgroundOfEachCell() {
        // Given: a white screen; the cell at (0, 0) is 6x8, of which the '!' glyph is lit and the rest is background
        LedMatrix matrix = matrix(12);
        Display display = new LedMatrixDisplay(matrix);
        display.fillScreen(Display.WHITE);
        display.setTextColor(Display.WHITE, Display.BLACK);

        // When
        display.setCursor(0, 0);
        display.print('!');
        display.flush();

        // Then: lit outside the cell, and inside it only where the glyph is
        BiPredicate<Integer, Integer> expected = (x, y) -> x >= 6 || (x < 5 && y < 7 && LedMatrixFontAscii.charPixel('!', x, y));
        int[] words = words12(expected);
        verify(matrix).loadFrame(words[0], words[1], words[2], 0);
    }

    /** The first three words of a 12-column frame in which pixel (x, y) is lit when {@code lit} is true. */
    private static int[] words12(BiPredicate<Integer, Integer> lit) {
        int[] words = new int[3];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) {
                if (lit.test(x, y)) {
                    int pixel = y * 12 + x;
                    words[pixel >> 5] |= 1 << (31 - (pixel & 31));
                }
            }
        }
        return words;
    }
}
