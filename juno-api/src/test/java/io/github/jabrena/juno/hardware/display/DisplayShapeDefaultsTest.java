package io.github.jabrena.juno.hardware.display;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Display shape methods as defaults")
class DisplayShapeDefaultsTest {

    @Test
    @DisplayName("A horizontal line is a one-pixel-high fill")
    void shouldDrawAHorizontalLine() {
        // Given
        RecordingDisplay display = new RecordingDisplay();

        // When
        display.drawHorizontalLine(1, 2, 3, Display.WHITE);

        // Then
        assertThat(display.lit).containsExactlyInAnyOrder("1,2", "2,2", "3,2");
    }

    @Test
    @DisplayName("A vertical line is a one-pixel-wide fill")
    void shouldDrawAVerticalLine() {
        // Given
        RecordingDisplay display = new RecordingDisplay();

        // When
        display.drawVerticalLine(4, 0, 2, Display.WHITE);

        // Then
        assertThat(display.lit).containsExactlyInAnyOrder("4,0", "4,1");
    }

    @Test
    @DisplayName("A rectangle outline lights its border and leaves the interior dark")
    void shouldDrawTheOutlineOfARectangle() {
        // Given
        RecordingDisplay display = new RecordingDisplay();

        // When
        display.drawRect(0, 0, 3, 3, Display.WHITE);

        // Then
        assertThat(display.lit).containsExactlyInAnyOrder(
                "0,0", "1,0", "2,0", "0,1", "2,1", "0,2", "1,2", "2,2");
    }

    @Test
    @DisplayName("A filled circle of radius 1 is a plus sign of five pixels")
    void shouldFillACircle() {
        // Given
        RecordingDisplay display = new RecordingDisplay();

        // When
        display.fillCircle(0, 0, 1, Display.WHITE);

        // Then
        assertThat(display.lit).containsExactlyInAnyOrder("-1,0", "0,0", "1,0", "0,-1", "0,1");
    }

    @Test
    @DisplayName("A circle outline of radius 1 is the four pixels around the center")
    void shouldDrawACircleOutline() {
        // Given
        RecordingDisplay display = new RecordingDisplay();

        // When
        display.drawCircle(0, 0, 1, Display.WHITE);

        // Then
        assertThat(display.lit).containsExactlyInAnyOrder("1,0", "0,1", "-1,0", "0,-1");
    }

    @Test
    @DisplayName("flush is a no-op on a display that draws as it goes")
    void shouldDoNothingOnFlushByDefault() {
        // Given
        RecordingDisplay display = new RecordingDisplay();
        display.drawPixel(0, 0, Display.WHITE);

        // When
        display.flush();

        // Then
        assertThat(display.lit).containsExactly("0,0");
    }

    /** Records every pixel drawn with a non-black color, and implements only the core methods the defaults need. */
    private static final class RecordingDisplay implements Display {
        private final Set<String> lit = new HashSet<>();

        @Override
        public int width() {
            return 16;
        }

        @Override
        public int height() {
            return 16;
        }

        @Override
        public void fillScreen(int color) {
        }

        @Override
        public void drawPixel(int x, int y, int color) {
            if (color != BLACK) {
                lit.add(x + "," + y);
            }
        }

        @Override
        public void fillRect(int x, int y, int width, int height, int color) {
            for (int row = y; row < y + height; row++) {
                for (int col = x; col < x + width; col++) {
                    drawPixel(col, row, color);
                }
            }
        }

        @Override
        public void setCursor(int x, int y) {
        }

        @Override
        public void setTextColor(int foreground, int background) {
        }

        @Override
        public void setTextSize(int size) {
        }

        @Override
        public void print(char character) {
        }

        @Override
        public void print(String text) {
        }

        @Override
        public void println(String text) {
        }
    }
}
