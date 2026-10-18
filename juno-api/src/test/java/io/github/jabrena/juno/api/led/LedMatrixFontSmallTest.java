package io.github.jabrena.juno.api.led;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedMatrixFontSmallTest {

    @Test
    void zeroAndOneHaveTheirClassicShapes() {
        assertThat(rows(0)).containsExactly("###", "#.#", "#.#", "#.#", "###");
        assertThat(rows(1)).containsExactly(".#.", "##.", ".#.", ".#.", "###");
    }

    @Test
    void everyDigitHasItsOwnShape() {
        String[] shapes = new String[10];
        for (int digit = 0; digit <= 9; digit++) {
            shapes[digit] = String.join("/", rows(digit));
        }

        assertThat(shapes).doesNotHaveDuplicates();
    }

    @Test
    void everyDigitFitsItsGlyphBoxAndLightsSomething() {
        for (int digit = 0; digit <= 9; digit++) {
            String[] rows = rows(digit);
            assertThat(rows).hasSize(LedMatrixFontSmall.GLYPH_HEIGHT);
            for (String row : rows) {
                assertThat(row).hasSize(LedMatrixFontSmall.GLYPH_WIDTH);
            }
            assertThat(String.join("", rows)).contains("#");
        }
    }

    @Test
    void decimalPointIsASingleBaselinePixel() {
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            assertThat(LedMatrixFontSmall.pointPixel(row)).isEqualTo(row == LedMatrixFontSmall.GLYPH_HEIGHT - 1);
        }
    }

    private static String[] rows(int digit) {
        String[] rows = new String[LedMatrixFontSmall.GLYPH_HEIGHT];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder pixels = new StringBuilder(LedMatrixFontSmall.GLYPH_WIDTH);
            for (int column = 0; column < LedMatrixFontSmall.GLYPH_WIDTH; column++) {
                pixels.append(LedMatrixFontSmall.digitPixel(digit, column, row) ? '#' : '.');
            }
            rows[row] = pixels.toString();
        }
        return rows;
    }
}
