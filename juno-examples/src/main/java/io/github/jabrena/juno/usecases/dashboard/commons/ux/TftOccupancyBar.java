package io.github.jabrena.juno.usecases.dashboard.commons.ux;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * One occupancy row of the storage screen: an accent dot and label, the share used as a percentage, a 20-segment bar
 * that turns yellow and then red as it fills, and a "used / capacity" line. It remembers what it last drew and
 * redraws only when the value changes.
 */
public final class TftOccupancyBar {
    private static final int X = 12;
    private static final int WIDTH = 296;
    private static final int HEIGHT = 48;
    private static final int BAR_OFFSET_Y = 20;
    private static final int BAR_HEIGHT = 16;
    private static final int SEGMENTS = 20;
    private static final int SEGMENT_STEP = 14;
    private static final int SEGMENT_WIDTH = 12;
    private static final int DETAIL_OFFSET_Y = 40;
    private static final int WARNING_PERCENT = 60;
    private static final int CRITICAL_PERCENT = 85;
    /** RGB565 (24, 32, 64): the unlit segments, a dim blue that reads as an empty track on navy. */
    private static final int TRACK = 0x1908;

    // What a row with no value shows; kept in the same fields as a value, which is never negative.
    private static final int PENDING = -1;
    private static final int UNAVAILABLE = -2;
    private static final int UNSHOWN = Integer.MIN_VALUE;

    private final int y;
    private final String label;
    private final int accentColor;
    private final int divisor;
    private final String unit;
    private final String pendingText;
    private final String unavailableText;

    private int shownUsed = UNSHOWN;
    private int shownCapacity = UNSHOWN;

    /**
     * A row at {@code y}; its "used / capacity" line divides both values by {@code divisor} and appends {@code unit}.
     * {@code pendingText} shows while the value is still being measured, {@code unavailableText} when there is none.
     */
    public TftOccupancyBar(int y, String label, int accentColor, int divisor, String unit, String pendingText,
                    String unavailableText) {
        this.y = y;
        this.label = label;
        this.accentColor = accentColor;
        this.divisor = divisor;
        this.unit = unit;
        this.pendingText = pendingText;
        this.unavailableText = unavailableText;
    }

    /** Forgets what was drawn, so the next update redraws the row; called when the screen is cleared. */
    public void reset() {
        shownUsed = UNSHOWN;
        shownCapacity = UNSHOWN;
    }

    public void update(TftTouchShield tft, int used, int capacity) {
        if (capacity <= 0 || used < 0) {
            showUnavailable(tft);
        } else if (changed(used, capacity)) {
            int percent = percent(used, capacity);
            int color = levelColor(percent);
            clear(tft);
            drawPercent(tft, percent, color);
            drawBar(tft, percent, color);
            drawDetail(tft, used, capacity);
        }
    }

    public void showPending(TftTouchShield tft) {
        if (changed(PENDING, PENDING)) {
            clear(tft);
            drawBar(tft, 0, TftTouchShield.GRAY);
            drawMessage(tft, pendingText);
        }
    }

    public void showUnavailable(TftTouchShield tft) {
        if (changed(UNAVAILABLE, UNAVAILABLE)) {
            clear(tft);
            drawBar(tft, 0, TftTouchShield.GRAY);
            drawMessage(tft, unavailableText);
        }
    }

    private boolean changed(int used, int capacity) {
        if (shownUsed == used && shownCapacity == capacity) {
            return false;
        }
        shownUsed = used;
        shownCapacity = capacity;
        return true;
    }

    /** Blanks the row and draws its accent dot and label, which every state shows. */
    private void clear(TftTouchShield tft) {
        tft.fillRect(X, y, WIDTH, HEIGHT, TftTouchShield.NAVY);
        tft.fillCircle(X + 5, y + 7, 5, accentColor);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(X + 16, y);
        tft.print(label);
    }

    private void drawPercent(TftTouchShield tft, int percent, int color) {
        int digits = percent >= 100 ? 3 : percent >= 10 ? 2 : 1;
        tft.setTextSize(2);
        tft.setTextColor(color, TftTouchShield.NAVY);
        tft.setCursor(X + WIDTH - (digits + 1) * 12, y);
        tft.print(percent);
        tft.print("%");
    }

    private void drawBar(TftTouchShield tft, int percent, int color) {
        int lit = (percent * SEGMENTS + 99) / 100;
        int barY = y + BAR_OFFSET_Y;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int x = X + segment * SEGMENT_STEP + (WIDTH - SEGMENTS * SEGMENT_STEP) / 2;
            tft.fillRect(x, barY, SEGMENT_WIDTH, BAR_HEIGHT, segment < lit ? color : TRACK);
        }
    }

    private void drawDetail(TftTouchShield tft, int used, int capacity) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(X + 4, y + DETAIL_OFFSET_Y);
        tft.print(used / divisor);
        tft.print(" / ");
        tft.print(capacity / divisor);
        tft.print(" ");
        tft.print(unit);
        tft.print(" used");
    }

    private void drawMessage(TftTouchShield tft, String message) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, TftTouchShield.NAVY);
        tft.setCursor(X + 4, y + DETAIL_OFFSET_Y);
        tft.print(message);
    }

    /** {@code used} as a share of {@code capacity}, 0..100, without overflowing on an SD card's KiB count. */
    private static int percent(int used, int capacity) {
        int share = capacity > 20_000_000 ? used / (capacity / 100) : used * 100 / capacity;
        return share < 0 ? 0 : share > 100 ? 100 : share;
    }

    private static int levelColor(int percent) {
        if (percent >= CRITICAL_PERCENT) {
            return TftTouchShield.RED;
        }
        if (percent >= WARNING_PERCENT) {
            return TftTouchShield.YELLOW;
        }
        return TftTouchShield.GREEN;
    }
}
