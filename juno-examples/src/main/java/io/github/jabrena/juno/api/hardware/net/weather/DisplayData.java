package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.util.Delay;

/** Displays time and weather data on the UNO R4 WiFi LED matrix. */
public final class DisplayData {
    private static final int CHAR_SPACING = 6;
    private static final int MAX_MESSAGE_CHARS = 24;

    private final boolean[][] frame;
    private final int[] message;

    /** Creates the fixed display buffers once, for reuse throughout the application. */
    public DisplayData() {
        frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        message = new int[MAX_MESSAGE_CHARS];
    }

    /** Displays {@code message} as a scrolling message, verbatim. */
    public void showMessage(String message) {
        scrollMessage(loadMessage(message));
    }

    /** Displays Madrid's local time as a scrolling message. */
    public void showTime(String time) {
        StringBuilder text = new StringBuilder(MAX_MESSAGE_CHARS);
        text.append("Madrid Time: ");
        text.append(time);
        scrollMessage(loadMessage(text.toString()));
    }

    /** Displays Madrid's temperature as a scrolling message. */
    public void showTemperature(String temperature) {
        StringBuilder text = new StringBuilder(MAX_MESSAGE_CHARS);
        text.append("Madrid Weather: ");
        text.append(temperature);
        text.append('C');
        scrollMessage(loadMessage(text.toString()));
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
            LedCanvas.clear(frame);
            LedCanvas.show(frame);
            offset = offset - 1;
        }
    }
}
