package io.github.jabrena.juno.usecases.dashboard.commons.ux;

import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;

/**
 * One line of text at a fixed position, in a chosen font size and foreground color, on the dashboard's navy
 * background. A static label is drawn once with {@link #draw}. A value that changes uses {@link #set}, which remembers
 * what it last drew and redraws only when the text, number or color changes. {@link #reset} forgets it, for when a
 * screen was cleared. Juno has no runtime strings to hold, so the text is always a literal chosen by the caller, or a
 * number followed by a literal suffix.
 */
public final class TftTextWidget {
    private static final int RIGHT_EDGE = 308;
    private static final int NOTHING_SHOWN = Integer.MIN_VALUE;

    private final int x;
    private final int y;
    private final int size;

    private String shownText;
    private int shownNumber = NOTHING_SHOWN;
    private int shownColor = NOTHING_SHOWN;

    public TftTextWidget(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    /** Draws {@code text} unconditionally, for a label that never changes. */
    public void draw(String text, int color) {
        begin(color);
        TftTouchShield.print(text);
    }

    /** Shows {@code text} in {@code color}, redrawing only if it differs from what is already shown. */
    public void set(String text, int color) {
        if (text == shownText && shownNumber == NOTHING_SHOWN && color == shownColor) {
            return;
        }
        shownText = text;
        shownNumber = NOTHING_SHOWN;
        shownColor = color;
        draw(text, color);
    }

    /** Shows {@code number} followed by {@code suffix}, redrawing only if it differs from what is already shown. */
    public void set(int number, String suffix, int color) {
        if (number == shownNumber && suffix == shownText && color == shownColor) {
            return;
        }
        shownText = suffix;
        shownNumber = number;
        shownColor = color;
        begin(color);
        TftTouchShield.print(number);
        TftTouchShield.print(suffix);
    }

    /** Forgets what was drawn, so the next {@code set} redraws; called after the screen was cleared. */
    public void reset() {
        shownText = null;
        shownNumber = NOTHING_SHOWN;
        shownColor = NOTHING_SHOWN;
    }

    /** Blanks the line's area, which a shorter text would otherwise leave part of the old one in, and sets up the text. */
    private void begin(int color) {
        TftTouchShield.fillRect(x, y, RIGHT_EDGE - x, TftTouchShield.CHAR_HEIGHT * size, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(size);
        TftTouchShield.setTextColor(color, TftTouchShield.NAVY);
        TftTouchShield.setCursor(x, y);
    }
}
