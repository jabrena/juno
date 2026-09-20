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

    /** Displays {@code message} as a scrolling message, verbatim. */
    public void showMessage(String message) {
        scrollMessage(loadMessage(message));
    }

    /** Displays Madrid's local time as a scrolling message. */
    public void showTime(String time) {
        StringBuilder text = new StringBuilder(MAX_MESSAGE_CHARS);
        text.append("Madrid Time: ");
        appendRuntimeString(text, time);
        scrollMessage(loadMessage(text.toString()));
    }

    /** Displays Madrid's temperature as a scrolling message. */
    public void showTemperature(String temperature) {
        StringBuilder text = new StringBuilder(MAX_MESSAGE_CHARS);
        text.append("Madrid Weather: ");
        appendRuntimeString(text, temperature);
        text.append('C');
        scrollMessage(loadMessage(text.toString()));
    }

    /**
     * Appends {@code value} to {@code text} one {@code char} at a time — {@code StringBuilder}'s
     * {@code append(String)} only accepts a compile-time string literal, never a runtime value
     * like a JSON-extracted temperature or formatted time, so a literal-free runtime value has to
     * go through {@code append(char)}/{@code String.charAt} instead.
     */
    private void appendRuntimeString(StringBuilder text, String value) {
        int length = value.length();
        int index = 0;
        while (index < length) {
            text.append(value.charAt(index));
            index = index + 1;
        }
    }

    /** Copies {@code text} into {@link #message} and returns its length. */
    private int loadMessage(String text) {
        int length = text.length();
        int index = 0;
        while (index < length) {
            message[index] = text.charAt(index);
            index = index + 1;
        }
        return length;
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
