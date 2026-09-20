package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;

/** Displays time and weather data on the UNO R4 WiFi LED matrix. */
public final class DisplayData {
    private static final int CHAR_SPACING = 6;
    private static final int MAX_MESSAGE_CHARS = 24;

    private final boolean[][] frame;
    private final int[] message;

    /** Creates the fixed display buffers once for reuse throughout the application. */
    public DisplayData() {
        frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
        message = new int[MAX_MESSAGE_CHARS];
    }

    /** Initializes the board's LED matrix. */
    public void begin() {
        LedMatrix.begin();
    }

    /** Displays Madrid's local time as a scrolling message. */
    public void showTime(byte[] timeText) {
        int length = 0;
        length = appendChar(length, 'M');
        length = appendChar(length, 'a');
        length = appendChar(length, 'd');
        length = appendChar(length, 'r');
        length = appendChar(length, 'i');
        length = appendChar(length, 'd');
        length = appendChar(length, ' ');
        length = appendChar(length, 'T');
        length = appendChar(length, 'i');
        length = appendChar(length, 'm');
        length = appendChar(length, 'e');
        length = appendChar(length, ':');
        length = appendChar(length, ' ');
        length = TimeClient.appendTime(timeText, message, length);

        scrollMessage(length);
    }

    /** Displays Madrid's temperature as a scrolling message. */
    public void showTemperature(String temperature) {
        int length = 0;
        length = appendChar(length, 'M');
        length = appendChar(length, 'a');
        length = appendChar(length, 'd');
        length = appendChar(length, 'r');
        length = appendChar(length, 'i');
        length = appendChar(length, 'd');
        length = appendChar(length, ' ');
        length = appendChar(length, 'W');
        length = appendChar(length, 'e');
        length = appendChar(length, 'a');
        length = appendChar(length, 't');
        length = appendChar(length, 'h');
        length = appendChar(length, 'e');
        length = appendChar(length, 'r');
        length = appendChar(length, ':');
        length = appendChar(length, ' ');
        int index = 0;
        int temperatureLength = temperature.length();
        while (index < temperatureLength) {
            length = appendChar(length, temperature.charAt(index));
            index = index + 1;
        }
        length = appendChar(length, 'C');

        scrollMessage(length);
    }

    private int appendChar(int index, int code) {
        message[index] = code;
        return index + 1;
    }

    private void scrollMessage(int messageLength) {
        int messageWidth = messageLength * CHAR_SPACING;
        int startOffset = LedCanvas.WIDTH;
        int endOffset = -messageWidth;
        int offset = startOffset;
        while (offset >= endOffset) {
            LedCanvas.clear(frame);
            int i = 0;
            while (i < messageLength) {
                LedCanvas.drawChar(frame, message[i], offset + i * CHAR_SPACING, 0);
                i = i + 1;
            }
            LedCanvas.show(frame);
            Delay.millis(80);
            LedMatrix.clear();
            offset = offset - 1;
        }
    }
}
