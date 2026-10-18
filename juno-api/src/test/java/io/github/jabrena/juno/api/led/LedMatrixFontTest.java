package io.github.jabrena.juno.api.led;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedMatrixFontTest {

    @Test
    void digitsUseTheAsciiGlyphsOfTheirCharacters() {
        for (int digit = 0; digit <= 9; digit++) {
            for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
                for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                    assertThat(LedMatrixFont.digitPixel(digit, col, row))
                            .isEqualTo(LedMatrixFontAscii.charPixel('0' + digit, col, row));
                }
            }
        }
    }

    @Test
    void lettersUseTheAsciiGlyphsOfTheirCharacters() {
        for (int letter = 0; letter <= 25; letter++) {
            for (int row = 0; row < LedMatrixFont.GLYPH_HEIGHT; row++) {
                for (int col = 0; col < LedMatrixFont.GLYPH_WIDTH; col++) {
                    assertThat(LedMatrixFont.letterPixel(letter, col, row))
                            .isEqualTo(LedMatrixFontAscii.charPixel('A' + letter, col, row));
                }
            }
        }
    }

    @Test
    void glyphBoxIsFiveWideBySevenHigh() {
        assertThat(LedMatrixFont.GLYPH_WIDTH).isEqualTo(5);
        assertThat(LedMatrixFont.GLYPH_HEIGHT).isEqualTo(7);
    }
}
