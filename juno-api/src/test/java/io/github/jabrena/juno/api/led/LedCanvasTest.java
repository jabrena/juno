package io.github.jabrena.juno.api.led;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class LedCanvasTest {

    @BeforeEach
    void useTheUnoR4Matrix() {
        LedMatrix.columns = 12;
    }

    @Test
    void setPixelIgnoresCoordinatesOutsideTheMatrix() {
        boolean[][] frame = newFrame();

        LedCanvas.setPixel(frame, -1, 0);
        LedCanvas.setPixel(frame, 0, -1);
        LedCanvas.setPixel(frame, 12, 0);
        LedCanvas.setPixel(frame, 0, LedCanvas.HEIGHT);

        assertThat(litCount(frame)).isZero();
        assertThat(LedCanvas.inBounds(11, 7)).isTrue();
        assertThat(LedCanvas.inBounds(12, 0)).isFalse();
    }

    @Test
    void theUnoQWidthReachesTheThirteenthColumn() {
        LedMatrix.columns = 13;
        boolean[][] frame = newFrame();

        LedCanvas.setPixel(frame, 12, 7);

        assertThat(frame[7][12]).isTrue();
        assertThat(LedCanvas.width()).isEqualTo(13);
    }

    @Test
    void clearLeavesTheSpareColumnOfTheR4Untouched() {
        boolean[][] frame = newFrame();
        frame[0][0] = true;
        frame[0][12] = true;

        LedCanvas.clear(frame);

        assertThat(frame[0][0]).isFalse();
        assertThat(frame[0][12]).as("column 12 is not part of the R4 matrix").isTrue();
    }

    @Test
    void packWordPacksMsbFirstAndLeavesTheSpareWordZero() {
        boolean[][] frame = newFrame();
        LedCanvas.setPixel(frame, 0, 0);
        LedCanvas.setPixel(frame, 11, 2);
        LedCanvas.setPixel(frame, 0, 7);

        assertThat(LedCanvas.packWord(frame, 0)).isEqualTo(0x80000000);
        assertThat(LedCanvas.packWord(frame, 1)).isEqualTo(1 << 28);
        assertThat(LedCanvas.packWord(frame, 2)).isEqualTo(1 << 11);
        assertThat(LedCanvas.packWord(frame, 3)).as("the R4 uses only three words").isZero();
    }

    @Test
    void showLoadsTheFourPackedWords() {
        boolean[][] frame = newFrame();
        LedCanvas.setPixel(frame, 0, 0);

        LedCanvas.show(frame);

        assertThat(LedMatrix.lastFrame).containsExactly(0x80000000, 0, 0, 0);
    }

    @Test
    void fillRectLightsEveryPixelInside() {
        boolean[][] frame = newFrame();

        LedCanvas.fillRect(frame, 2, 1, 3, 2);

        assertThat(litCount(frame)).isEqualTo(6);
        assertThat(frame[1][2]).isTrue();
        assertThat(frame[2][4]).isTrue();
        assertThat(frame[3][2]).isFalse();
    }

    @Test
    void drawRectLightsOnlyTheBorder() {
        boolean[][] frame = newFrame();

        LedCanvas.drawRect(frame, 1, 1, 4, 3);

        assertThat(litCount(frame)).isEqualTo(10);
        assertThat(frame[2][2]).as("interior stays dark").isFalse();
        assertThat(frame[1][4]).isTrue();
        assertThat(frame[3][1]).isTrue();
    }

    @Test
    void drawLineCoversBothEndpointsInEveryDirection() {
        boolean[][] frame = newFrame();

        LedCanvas.drawLine(frame, 0, 0, 5, 0);
        LedCanvas.drawLine(frame, 0, 7, 0, 3);
        LedCanvas.drawLine(frame, 3, 3, 0, 0);
        LedCanvas.drawLine(frame, 6, 5, 2, 7);

        assertThat(frame[0][5]).isTrue();
        assertThat(frame[3][0]).isTrue();
        assertThat(frame[0][0]).isTrue();
        assertThat(frame[7][2]).isTrue();
        assertThat(frame[5][6]).isTrue();
    }

    @Test
    void fillCircleOfRadiusOneIsAPlus() {
        boolean[][] frame = newFrame();

        LedCanvas.fillCircle(frame, 5, 4, 1);

        assertThat(litCount(frame)).isEqualTo(5);
        assertThat(frame[3][5]).as("above the centre").isTrue();
        assertThat(frame[3][4]).as("corner of the box is outside the circle").isFalse();
    }

    @Test
    void drawCircleLightsItsOutlineOnly() {
        boolean[][] frame = newFrame();

        LedCanvas.drawCircle(frame, 5, 4, 3);

        assertThat(frame[4][8]).isTrue();
        assertThat(frame[1][5]).isTrue();
        assertThat(frame[4][5]).as("centre stays dark").isFalse();
    }

    @Test
    void fillTriangleFillsItsInteriorAndVertices() {
        boolean[][] frame = newFrame();

        LedCanvas.fillTriangle(frame, 0, 0, 4, 0, 0, 4);

        assertThat(frame[0][0]).isTrue();
        assertThat(frame[0][4]).isTrue();
        assertThat(frame[1][1]).as("inside the hypotenuse").isTrue();
        assertThat(frame[4][4]).as("outside the hypotenuse").isFalse();
    }

    @Test
    void drawTriangleLightsTheThreeVertices() {
        boolean[][] frame = newFrame();

        LedCanvas.drawTriangle(frame, 1, 1, 9, 2, 5, 6);

        assertThat(frame[1][1]).isTrue();
        assertThat(frame[2][9]).isTrue();
        assertThat(frame[6][5]).isTrue();
        assertThat(frame[4][5]).as("interior stays dark").isFalse();
    }

    @Test
    void glyphsLightExactlyTheirPixelsInsideTheirBox() {
        assertGlyphAt(frame -> LedCanvas.drawDigit(frame, 7, 2, 1),
                (col, row) -> LedMatrixFont.digitPixel(7, col, row), 2, 1,
                LedMatrixFont.GLYPH_WIDTH, LedMatrixFont.GLYPH_HEIGHT);
        assertGlyphAt(frame -> LedCanvas.drawLetter(frame, 12, 2, 1),
                (col, row) -> LedMatrixFont.letterPixel(12, col, row), 2, 1,
                LedMatrixFont.GLYPH_WIDTH, LedMatrixFont.GLYPH_HEIGHT);
        assertGlyphAt(frame -> LedCanvas.drawChar(frame, 'g', 2, 0),
                (col, row) -> LedMatrixFontAscii.charPixel('g', col, row), 2, 0,
                LedMatrixFontAscii.GLYPH_WIDTH, LedMatrixFontAscii.GLYPH_HEIGHT);
        assertGlyphAt(frame -> LedCanvas.drawSmallDigit(frame, 8, 1, 1),
                (col, row) -> LedMatrixFontSmall.digitPixel(8, col, row), 1, 1,
                LedMatrixFontSmall.GLYPH_WIDTH, LedMatrixFontSmall.GLYPH_HEIGHT);
    }

    @Test
    void drawDecimalSetsTheDigitsAndThePointBetweenThem() {
        boolean[][] frame = newFrame();

        LedCanvas.drawDecimal(frame, 2, 5, 1, 0);

        assertThat(frame[LedMatrixFontSmall.GLYPH_HEIGHT - 1][5]).as("decimal point on the baseline").isTrue();
        assertThat(frame[0][5]).as("no point above the baseline").isFalse();
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            for (int col = 0; col < LedMatrixFontSmall.GLYPH_WIDTH; col++) {
                assertThat(frame[row][1 + col]).isEqualTo(LedMatrixFontSmall.digitPixel(2, col, row));
                assertThat(frame[row][7 + col]).isEqualTo(LedMatrixFontSmall.digitPixel(5, col, row));
            }
        }
    }

    /** Draws one glyph at (originX, originY) and checks every pixel of the frame against the glyph's shape. */
    private static void assertGlyphAt(Consumer<boolean[][]> draw, Glyph glyph, int originX, int originY,
            int glyphWidth, int glyphHeight) {
        boolean[][] frame = newFrame();

        draw.accept(frame);

        for (int y = 0; y < LedCanvas.HEIGHT; y++) {
            for (int x = 0; x < frame[y].length; x++) {
                boolean inside = x >= originX && x < originX + glyphWidth && y >= originY && y < originY + glyphHeight;
                boolean expected = inside && glyph.lit(x - originX, y - originY);
                assertThat(frame[y][x]).as("pixel (%d, %d)", x, y).isEqualTo(expected);
            }
        }
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
