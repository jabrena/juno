package io.github.jabrena.juno.api.hardware.led;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix small font")
class LedMatrixFontSmallTest {

    @Test
    @DisplayName("Zero has its classic shape")
    void shouldDrawZeroWithItsClassicShape() {
        // When & Then
        assertThat(rows(0)).containsExactly("###", "#.#", "#.#", "#.#", "###");
    }

    @Test
    @DisplayName("One has its classic shape")
    void shouldDrawOneWithItsClassicShape() {
        // When & Then
        assertThat(rows(1)).containsExactly(".#.", "##.", ".#.", ".#.", "###");
    }

    @Test
    @DisplayName("Every digit has its own shape")
    void shouldGiveEveryDigitADistinctShape() {
        // Given
        String[] shapes = new String[10];
        for (int digit = 0; digit <= 9; digit++) {
            shapes[digit] = String.join("/", rows(digit));
        }

        // When & Then
        assertThat(shapes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Every digit fits its glyph box and lights at least one pixel")
    void shouldFitEveryDigitInsideItsGlyphBoxAndLightAPixel() {
        for (int digit = 0; digit <= 9; digit++) {
            // Given
            String[] rows = rows(digit);

            // When & Then
            assertThat(rows)
                    .hasSize(LedMatrixFontSmall.GLYPH_HEIGHT)
                    .allSatisfy(row -> assertThat(row).hasSize(LedMatrixFontSmall.GLYPH_WIDTH));
            assertThat(String.join("", rows)).contains("#");
        }
    }

    @Test
    @DisplayName("The decimal point is a single baseline pixel")
    void shouldLightOnlyTheBaselinePixelForTheDecimalPoint() {
        // Given
        boolean[] actual = new boolean[LedMatrixFontSmall.GLYPH_HEIGHT];
        for (int row = 0; row < LedMatrixFontSmall.GLYPH_HEIGHT; row++) {
            actual[row] = LedMatrixFontSmall.pointPixel(row);
        }
        boolean[] expected = new boolean[LedMatrixFontSmall.GLYPH_HEIGHT];
        expected[LedMatrixFontSmall.GLYPH_HEIGHT - 1] = true;

        // When & Then
        assertThat(actual).isEqualTo(expected);
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
