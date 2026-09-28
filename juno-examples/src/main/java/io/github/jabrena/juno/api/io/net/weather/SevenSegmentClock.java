package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** {@link WeatherTFT}'s large seven-segment {@code HH:MM} clock. */
final class SevenSegmentClock {
    private static final int CLOCK_HOURS = 0xFE60;
    private static final int CLOCK_MINUTES = 0xF904;
    private static final int MARGIN = 8;
    private static final int DIGIT_WIDTH = 36;
    private static final int DIGIT_HEIGHT = 72;
    private static final int SEGMENT = 8;
    private static final int CLOCK_Y = 66;
    private static final int COLON_X = 94;

    private SevenSegmentClock() {
    }

    static void drawClock(int hour, int minute) {
        drawDigit(MARGIN, hour / 10, CLOCK_HOURS);
        drawDigit(MARGIN + DIGIT_WIDTH + 6, hour % 10, CLOCK_HOURS);
        drawDigit(COLON_X + 14, minute / 10, CLOCK_MINUTES);
        drawDigit(COLON_X + 14 + DIGIT_WIDTH + 6, minute % 10, CLOCK_MINUTES);
    }

    static void drawColon(boolean on) {
        int color = TftTouchShield.BLACK;
        if (on) {
            color = TftTouchShield.WHITE;
        }
        TftTouchShield.fillRect(COLON_X, CLOCK_Y + 20, 6, 6, color);
        TftTouchShield.fillRect(COLON_X, CLOCK_Y + DIGIT_HEIGHT - 26, 6, 6, color);
    }

    // Segments a-g as bits 0-6: a top, b upper right, c lower right, d bottom, e lower left,
    // f upper left, g middle. Unlit segments are painted black, so no separate erase is needed.
    private static void drawDigit(int x, int digit, int color) {
        int segments = segmentsOf(digit);
        int half = DIGIT_HEIGHT / 2;
        int vertical = half - SEGMENT - SEGMENT / 2;
        int horizontal = DIGIT_WIDTH - 2 * SEGMENT;
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y, horizontal, SEGMENT, segmentColor(segments, 0, color));
        TftTouchShield.fillRect(x + DIGIT_WIDTH - SEGMENT, CLOCK_Y + SEGMENT, SEGMENT, vertical,
                segmentColor(segments, 1, color));
        TftTouchShield.fillRect(x + DIGIT_WIDTH - SEGMENT, CLOCK_Y + half + SEGMENT / 2, SEGMENT, vertical,
                segmentColor(segments, 2, color));
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y + DIGIT_HEIGHT - SEGMENT, horizontal, SEGMENT,
                segmentColor(segments, 3, color));
        TftTouchShield.fillRect(x, CLOCK_Y + half + SEGMENT / 2, SEGMENT, vertical, segmentColor(segments, 4, color));
        TftTouchShield.fillRect(x, CLOCK_Y + SEGMENT, SEGMENT, vertical, segmentColor(segments, 5, color));
        TftTouchShield.fillRect(x + SEGMENT, CLOCK_Y + half - SEGMENT / 2, horizontal, SEGMENT,
                segmentColor(segments, 6, color));
    }

    private static int segmentColor(int segments, int segment, int color) {
        if (((segments >> segment) & 1) != 0) {
            return color;
        }
        return TftTouchShield.BLACK;
    }

    private static int segmentsOf(int digit) {
        if (digit == 0) {
            return 0x3F;
        }
        if (digit == 1) {
            return 0x06;
        }
        if (digit == 2) {
            return 0x5B;
        }
        if (digit == 3) {
            return 0x4F;
        }
        if (digit == 4) {
            return 0x66;
        }
        if (digit == 5) {
            return 0x6D;
        }
        if (digit == 6) {
            return 0x7D;
        }
        if (digit == 7) {
            return 0x07;
        }
        if (digit == 8) {
            return 0x7F;
        }
        return 0x6F;
    }
}
