package io.github.jabrena.juno.hardware.fonts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix ASCII font")
class AsciiFontTest {

    @Test
    @DisplayName("Lowercase g has a closed bowl and a visible descender")
    void shouldDrawLowercaseGWithAClosedBowlAndAVisibleDescender() {
        // When & Then
        assertThat(rows('g')).containsExactly(".....", ".....", ".####", "#...#", ".####", "....#", ".###.");
    }

    private static String[] rows(int character) {
        String[] rows = new String[AsciiFont.GLYPH_HEIGHT];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder pixels = new StringBuilder(AsciiFont.GLYPH_WIDTH);
            for (int column = 0; column < AsciiFont.GLYPH_WIDTH; column++) {
                pixels.append(AsciiFont.charPixel(character, column, row) ? '#' : '.');
            }
            rows[row] = pixels.toString();
        }
        return rows;
    }
}
