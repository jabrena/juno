package io.github.jabrena.juno.api.hardware.led;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix ASCII font")
class LedMatrixFontAsciiTest {

    @Test
    @DisplayName("Lowercase g has a closed bowl and a visible descender")
    void shouldDrawLowercaseGWithAClosedBowlAndAVisibleDescender() {
        // When & Then
        assertThat(rows('g')).containsExactly(".....", ".....", ".####", "#...#", ".####", "....#", ".###.");
    }

    private static String[] rows(int character) {
        String[] rows = new String[LedMatrixFontAscii.GLYPH_HEIGHT];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder pixels = new StringBuilder(LedMatrixFontAscii.GLYPH_WIDTH);
            for (int column = 0; column < LedMatrixFontAscii.GLYPH_WIDTH; column++) {
                pixels.append(LedMatrixFontAscii.charPixel(character, column, row) ? '#' : '.');
            }
            rows[row] = pixels.toString();
        }
        return rows;
    }
}
