package io.github.jabrena.juno.api.hardware.led;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LED matrix font")
class LedMatrixFontTest {

    @Test
    @DisplayName("Digits use the ASCII glyphs of their characters")
    void shouldUseTheAsciiGlyphsOfTheirCharactersForDigits() {
        // Given
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> expected = new ArrayList<>();
        for (int digit = 0; digit <= 9; digit++) {
            for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
                for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                    actual.add(LedMatrixFont.digitPixel(digit, col, row));
                    expected.add(LedMatrixFontAscii.charPixel('0' + digit, col, row));
                }
            }
        }

        // When & Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("Letters use the ASCII glyphs of their characters")
    void shouldUseTheAsciiGlyphsOfTheirCharactersForLetters() {
        // Given
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> expected = new ArrayList<>();
        for (int letter = 0; letter <= 25; letter++) {
            for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
                for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                    actual.add(LedMatrixFont.letterPixel(letter, col, row));
                    expected.add(LedMatrixFontAscii.charPixel('A' + letter, col, row));
                }
            }
        }

        // When & Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("The glyph box is five pixels wide by seven high")
    void shouldUseAFiveByLessSevenGlyphBox() {
        // When & Then
        assertThat(new int[] {LedMatrixFont.GLYPH_WIDTH, LedMatrixFont.GLYPH_HEIGHT}).containsExactly(5, 7);
    }
}
