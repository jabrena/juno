package io.github.jabrena.juno.games.doom2.commons;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * A DOOM-style menu: a column of full-width rows, each with a title and a one-line detail, that can be greyed out or
 * marked as chosen. The episode and skill views share it, so one copy of the drawing and hit-testing code serves both.
 */
public final class ChoiceList {
    private static final int LEFT = 20;
    private static final int ROW_WIDTH = 280;
    private static final int ROW_GAP = 4;
    private static final int CHOICE_CHOSEN = 0x7800;
    private static final int DISABLED = 0x18C3;
    private static final int WALL_FAR = 0x2424;

    private final int top;
    private final int rowHeight;
    private final int rows;

    /** A list of {@code rows} rows, each {@code rowHeight} pixels high, starting at {@code top}. */
    public ChoiceList(int top, int rowHeight, int rows) {
        this.top = top;
        this.rowHeight = rowHeight;
        this.rows = rows;
    }

    /** The row at ({@code x}, {@code y}), from 0, or -1 for a gap or outside the list. */
    public int rowAt(int x, int y) {
        if (x < LEFT || x >= LEFT + ROW_WIDTH || y < top) {
            return -1;
        }
        int pitch = rowHeight + ROW_GAP;
        int row = (y - top) / pitch;
        return row < rows && (y - top) % pitch < rowHeight ? row : -1;
    }

    /** Draws {@code row}: greyed out when not {@code enabled}, highlighted when {@code chosen}. */
    public void draw(int row, String title, String detail, boolean enabled, boolean chosen) {
        int y = top + row * (rowHeight + ROW_GAP);
        int color = !enabled ? DISABLED : chosen ? CHOICE_CHOSEN : Screen.BUTTON;
        TftTouchShield.fillRect(LEFT, y, ROW_WIDTH, rowHeight, color);
        TftTouchShield.drawRect(LEFT, y, ROW_WIDTH, rowHeight, chosen ? TftTouchShield.WHITE : WALL_FAR);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(enabled ? TftTouchShield.WHITE : WALL_FAR, color);
        TftTouchShield.setCursor(LEFT + (ROW_WIDTH - title.length() * 12) / 2, y + 2);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(enabled ? TftTouchShield.YELLOW : WALL_FAR, color);
        TftTouchShield.setCursor(LEFT + (ROW_WIDTH - detail.length() * 6) / 2, y + rowHeight - 11);
        TftTouchShield.print(detail);
    }
}
