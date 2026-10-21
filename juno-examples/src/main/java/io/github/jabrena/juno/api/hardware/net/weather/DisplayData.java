package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

/** Displays time and weather data on the UNO R4 WiFi LED matrix. */
public final class DisplayData {
    private static final int CHAR_SPACING = 6;
    private static final int MAX_MESSAGE_CHARS = 24;

    private final LedMatrix matrix;
    private final boolean[][] frame;
    private final int[] message;

    /** Takes the board's LED matrix and creates the fixed display buffers once for reuse throughout the application. */
    public DisplayData() {
        matrix = Arduino.getLedMatrix();
        frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        message = new int[MAX_MESSAGE_CHARS];
    }

    /** Initializes the board's LED matrix. */
    public void begin() {
        matrix.begin();
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
        int startOffset = LedCanvas.width();
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
            matrix.clear();
            offset = offset - 1;
        }
    }
}
