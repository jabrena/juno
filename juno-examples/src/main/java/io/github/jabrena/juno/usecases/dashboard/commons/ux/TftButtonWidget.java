package io.github.jabrena.juno.usecases.dashboard.commons.ux;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** A labelled, touchable rectangle: drawn in its fill color with a white border and its label centered. */
public final class TftButtonWidget {
    private static final int TEXT_SIZE = 2;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String label;
    private final int color;

    public TftButtonWidget(int x, int y, int width, int height, String label, int color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.color = color;
    }

    public void draw() {
        TftTouchShield.fillRect(x, y, width, height, color);
        TftTouchShield.drawRect(x, y, width, height, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(TEXT_SIZE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        int labelWidth = label.length() * TftTouchShield.CHAR_WIDTH * TEXT_SIZE;
        TftTouchShield.setCursor(x + (width - labelWidth) / 2, y + (height - TftTouchShield.CHAR_HEIGHT * TEXT_SIZE) / 2);
        TftTouchShield.print(label);
    }

    /** Whether a touch at ({@code touchX}, {@code touchY}) lands on the button. */
    public boolean contains(int touchX, int touchY) {
        return touchX >= x && touchX < x + width && touchY >= y && touchY < y + height;
    }
}
