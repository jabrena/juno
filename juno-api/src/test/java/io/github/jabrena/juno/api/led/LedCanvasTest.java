package io.github.jabrena.juno.api.led;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix canvas")
class LedCanvasTest {

    @BeforeEach
    void useTheUnoR4Matrix() {
        LedMatrix.columns = 12;
    }

    @Test
    @DisplayName("Set pixel ignores coordinates outside the matrix")
    void shouldIgnoreCoordinatesOutsideTheMatrix() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.setPixel(frame, -1, 0);
        LedCanvas.setPixel(frame, 0, -1);
        LedCanvas.setPixel(frame, 12, 0);
        LedCanvas.setPixel(frame, 0, LedCanvas.HEIGHT);

        // Then
        assertThat(litCount(frame)).isZero();
    }

    @ParameterizedTest(name = "in bounds at ({0}, {1}) is {2}")
    @CsvSource({"11, 7, true", "12, 0, false"})
    @DisplayName("In bounds is true only inside the matrix")
    void shouldReportInBoundsOnlyInsideTheMatrix(int x, int y, boolean expected) {
        // When & Then
        assertThat(LedCanvas.inBounds(x, y)).isEqualTo(expected);
    }

    @Test
    @DisplayName("The UNO Q width reaches the thirteenth column")
    void shouldLightTheThirteenthColumnOnTheUnoQ() {
        // Given
        LedMatrix.columns = 13;
        boolean[][] frame = newFrame();

        // When
        LedCanvas.setPixel(frame, 12, 7);

        // Then
        assertThat(frame[7][12]).isTrue();
    }

    @Test
    @DisplayName("The width is thirteen columns on the UNO Q")
    void shouldReportAThirteenColumnWidthOnTheUnoQ() {
        // Given
        LedMatrix.columns = 13;

        // When & Then
        assertThat(LedCanvas.width()).isEqualTo(13);
    }

    @Test
    @DisplayName("Clear turns off the lit pixels of the R4 matrix")
    void shouldClearTheLitPixelsOfTheR4Matrix() {
        // Given
        boolean[][] frame = newFrame();
        frame[0][0] = true;

        // When
        LedCanvas.clear(frame);

        // Then
        assertThat(frame[0][0]).isFalse();
    }

    @Test
    @DisplayName("Clear leaves the spare column of the R4 untouched")
    void shouldLeaveTheSpareColumnOfTheR4Untouched() {
        // Given
        boolean[][] frame = newFrame();
        frame[0][12] = true;

        // When
        LedCanvas.clear(frame);

        // Then
        assertThat(frame[0][12]).as("column 12 is not part of the R4 matrix").isTrue();
    }

    @Test
    @DisplayName("Pack word packs each row MSB first and leaves the spare word zero")
    void shouldPackWordsMsbFirstAndLeaveTheSpareWordZero() {
        // Given
        boolean[][] frame = newFrame();
        LedCanvas.setPixel(frame, 0, 0);
        LedCanvas.setPixel(frame, 11, 2);
        LedCanvas.setPixel(frame, 0, 7);

        // When
        int[] words = {
                LedCanvas.packWord(frame, 0),
                LedCanvas.packWord(frame, 1),
                LedCanvas.packWord(frame, 2),
                LedCanvas.packWord(frame, 3)};

        // Then
        assertThat(words).as("the R4 uses only three words").containsExactly(0x80000000, 1 << 28, 1 << 11, 0);
    }

    @Test
    @DisplayName("Show loads the four packed words")
    void shouldLoadTheFourPackedWordsOnShow() {
        // Given
        boolean[][] frame = newFrame();
        LedCanvas.setPixel(frame, 0, 0);

        // When
        LedCanvas.show(frame);

        // Then
        assertThat(LedMatrix.lastFrame).containsExactly(0x80000000, 0, 0, 0);
    }

    @Test
    @DisplayName("Fill rect lights every pixel inside the rectangle")
    void shouldLightEveryPixelInsideTheRectangle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.fillRect(frame, 2, 1, 3, 2);

        // Then
        assertThat(litCount(frame)).isEqualTo(6);
    }

    @Test
    @DisplayName("Fill rect lights only pixels inside the rectangle")
    void shouldLightOnlyPixelsInsideTheRectangle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.fillRect(frame, 2, 1, 3, 2);

        // Then
        assertThat(new boolean[] {frame[1][2], frame[2][4], frame[3][2]}).containsExactly(true, true, false);
    }

    @Test
    @DisplayName("Draw rect lights only the border")
    void shouldLightOnlyTheBorderOfARectangle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawRect(frame, 1, 1, 4, 3);

        // Then
        assertThat(litCount(frame)).isEqualTo(10);
    }

    @Test
    @DisplayName("Draw rect leaves the interior dark and lights the border pixels")
    void shouldLeaveTheRectangleInteriorDarkAndLightTheBorder() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawRect(frame, 1, 1, 4, 3);

        // Then
        assertThat(new boolean[] {frame[2][2], frame[1][4], frame[3][1]})
                .as("interior stays dark, border is lit")
                .containsExactly(false, true, true);
    }

    @Test
    @DisplayName("Draw line covers both endpoints in every direction")
    void shouldCoverBothEndpointsInEveryDirection() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawLine(frame, 0, 0, 5, 0);
        LedCanvas.drawLine(frame, 0, 7, 0, 3);
        LedCanvas.drawLine(frame, 3, 3, 0, 0);
        LedCanvas.drawLine(frame, 6, 5, 2, 7);

        // Then
        assertThat(new boolean[] {frame[0][5], frame[3][0], frame[0][0], frame[7][2], frame[5][6]}).containsOnly(true);
    }

    @Test
    @DisplayName("Fill circle of radius one is a plus sign")
    void shouldFillARadiusOneCircleAsAPlus() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.fillCircle(frame, 5, 4, 1);

        // Then
        assertThat(litCount(frame)).isEqualTo(5);
    }

    @Test
    @DisplayName("Fill circle lights the centre cross and leaves the box corner dark")
    void shouldLeaveTheBoxCornerOfACircleDark() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.fillCircle(frame, 5, 4, 1);

        // Then
        assertThat(new boolean[] {frame[3][5], frame[3][4]})
                .as("above the centre is lit, the corner of the box is outside the circle")
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("Draw circle lights its outline only")
    void shouldLightOnlyTheOutlineOfACircle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawCircle(frame, 5, 4, 3);

        // Then
        assertThat(new boolean[] {frame[4][8], frame[1][5], frame[4][5]})
                .as("the centre stays dark")
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("Fill triangle fills its interior and vertices")
    void shouldFillTheInteriorAndVerticesOfATriangle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.fillTriangle(frame, 0, 0, 4, 0, 0, 4);

        // Then
        assertThat(new boolean[] {frame[0][0], frame[0][4], frame[1][1], frame[4][4]})
                .as("vertices and the inside of the hypotenuse are lit, the outside is dark")
                .containsExactly(true, true, true, false);
    }

    @Test
    @DisplayName("Draw triangle lights its three vertices")
    void shouldLightTheThreeVerticesOfATriangle() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawTriangle(frame, 1, 1, 9, 2, 5, 6);

        // Then
        assertThat(new boolean[] {frame[1][1], frame[2][9], frame[6][5], frame[4][5]})
                .as("the interior stays dark")
                .containsExactly(true, true, true, false);
    }

    @Test
    @DisplayName("A large digit lights exactly its pixels inside its box")
    void shouldLightExactlyTheLargeDigitPixelsInsideItsBox() {
        assertGlyphAt(frame -> LedCanvas.drawDigit(frame, 7, 2, 1),
                (col, row) -> LedMatrixFont.digitPixel(7, col, row), 2, 1,
                LedMatrixFont.GLYPH_WIDTH, LedMatrixFont.GLYPH_HEIGHT);
    }

    @Test
    @DisplayName("A large letter lights exactly its pixels inside its box")
    void shouldLightExactlyTheLargeLetterPixelsInsideItsBox() {
        assertGlyphAt(frame -> LedCanvas.drawLetter(frame, 12, 2, 1),
                (col, row) -> LedMatrixFont.letterPixel(12, col, row), 2, 1,
                LedMatrixFont.GLYPH_WIDTH, LedMatrixFont.GLYPH_HEIGHT);
    }

    @Test
    @DisplayName("An ASCII character lights exactly its pixels inside its box")
    void shouldLightExactlyTheAsciiCharacterPixelsInsideItsBox() {
        assertGlyphAt(frame -> LedCanvas.drawChar(frame, 'g', 2, 0),
                (col, row) -> LedMatrixFontAscii.charPixel('g', col, row), 2, 0,
                LedMatrixFontAscii.GLYPH_WIDTH, LedMatrixFontAscii.GLYPH_HEIGHT);
    }

    @Test
    @DisplayName("A small digit lights exactly its pixels inside its box")
    void shouldLightExactlyTheSmallDigitPixelsInsideItsBox() {
        assertGlyphAt(frame -> LedCanvas.drawSmallDigit(frame, 8, 1, 1),
                (col, row) -> LedMatrixFontSmall.digitPixel(8, col, row), 1, 1,
                LedMatrixFontSmall.GLYPH_WIDTH, LedMatrixFontSmall.GLYPH_HEIGHT);
    }

    @Test
    @DisplayName("Draw decimal puts the point on the baseline and leaves the space above it dark")
    void shouldPutTheDecimalPointOnTheBaseline() {
        // Given
        boolean[][] frame = newFrame();

        // When
        LedCanvas.drawDecimal(frame, 2, 5, 1, 0);

        // Then
        assertThat(new boolean[] {frame[LedMatrixFontSmall.GLYPH_HEIGHT - 1][5], frame[0][5]})
                .as("decimal point on the baseline, no point above it")
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("Draw decimal draws each digit in its box")
    void shouldDrawEachDigitOfADecimalInItsBox() {
        // Given
        boolean[][] frame = newFrame();
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> expected = new ArrayList<>();

        // When
        LedCanvas.drawDecimal(frame, 2, 5, 1, 0);
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFontSmall.GLYPH_WIDTH; col++) {
                actual.add(frame[row][1 + col]);
                expected.add(LedMatrixFontSmall.digitPixel(2, col, row));
                actual.add(frame[row][7 + col]);
                expected.add(LedMatrixFontSmall.digitPixel(5, col, row));
            }
        }

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    /** Draws one glyph at (originX, originY) and checks the frame against the glyph's shape, pixel by pixel. */
    private static void assertGlyphAt(Consumer<boolean[][]> draw, Glyph glyph, int originX, int originY,
            int glyphWidth, int glyphHeight) {
        // Given
        boolean[][] frame = newFrame();
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> expected = new ArrayList<>();

        // When
        draw.accept(frame);
        for (int y = 0; y < LedCanvas.HEIGHT; y++) {
            for (int x = 0; x < frame[y].length; x++) {
                boolean inside = x >= originX && x < originX + glyphWidth && y >= originY && y < originY + glyphHeight;
                actual.add(frame[y][x]);
                expected.add(inside && glyph.lit(x - originX, y - originY));
            }
        }

        // Then
        assertThat(actual).as("pixels of the glyph drawn at (%d, %d)", originX, originY).isEqualTo(expected);
    }

    @FunctionalInterface
    private interface Glyph {
        boolean lit(int col, int row);
    }

    private static boolean[][] newFrame() {
        return new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
    }

    private static int litCount(boolean[][] frame) {
        int count = 0;
        for (boolean[] row : frame) {
            for (boolean pixel : row) {
                if (pixel) {
                    count++;
                }
            }
        }
        return count;
    }
}
