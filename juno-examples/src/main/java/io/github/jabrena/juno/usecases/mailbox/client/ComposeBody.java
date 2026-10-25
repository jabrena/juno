package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;

/**
 * The composer's message body: its text, wrapped from the row of its label onto full-width rows below, and how far it
 * is scrolled back. A body longer than its rows follows the cursor until scrolled; ^ and v mark hidden rows, the way
 * the Zelda option lists do.
 */
public final class ComposeBody {
    private static final int CAPACITY = MailboxModel.MAX_COMPOSE_CHARS;
    private static final int TEXT_SIZE = 2;
    private static final int TEXT_LEFT = 4;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int LINE_HEIGHT = 9 * TEXT_SIZE;
    private static final int COLUMNS = 18;
    private static final int TOP = 52;
    private static final int ROWS = 3;
    private static final int INDICATOR_X = 228;
    private static final String LABEL = "Body:";
    private static final int FIRST_ROW_COLUMNS = COLUMNS - LABEL.length() - 1;
    private static final char CURSOR = '_';

    private final Display display;
    private final char[] text = new char[CAPACITY];
    private int length;
    /** How many rows the body is scrolled up from the rows that hold the cursor. */
    private int scrollBack;

    public ComposeBody(Display display) {
        this.display = display;
    }

    public static int capacity() {
        return CAPACITY;
    }

    public char[] text() {
        return text;
    }

    public int length() {
        return length;
    }

    /** Appends {@code character} and follows the cursor again; false when the body is full. */
    public boolean append(char character) {
        scrollBack = 0;
        if (length == text.length) {
            return false;
        }
        text[length] = character;
        length = length + 1;
        return true;
    }

    public void erase() {
        scrollBack = 0;
        if (length > 0) {
            length = length - 1;
        }
    }

    public void clear() {
        scrollBack = 0;
        length = 0;
    }

    /** Scrolls back {@code rows} rows (forward when negative); true when the visible rows changed. */
    public boolean scroll(int rows, boolean focused) {
        int hidden = hiddenRows(walk(shown(focused), Integer.MAX_VALUE));
        int next = Math.max(0, Math.min(hidden, scrollBack + rows));
        if (next == scrollBack) {
            return false;
        }
        scrollBack = next;
        return true;
    }

    /** Draws the label, the visible rows (with the cursor when {@code focused}) and the scroll indicators. */
    public void draw(boolean focused) {
        int shown = shown(focused);
        int lineCount = walk(shown, Integer.MAX_VALUE);
        int hidden = hiddenRows(lineCount);
        scrollBack = Math.min(scrollBack, hidden);
        int firstLine = hidden - scrollBack;
        display.setTextSize(TEXT_SIZE);
        if (firstLine == 0) {
            display.setTextColor(focused ? Display.YELLOW : Display.CYAN, Display.BLACK);
            display.setCursor(TEXT_LEFT, TOP);
            display.print(LABEL);
        }
        display.setTextColor(Display.WHITE, Display.BLACK);
        walk(shown, firstLine);
        drawIndicators(firstLine > 0, firstLine + ROWS < lineCount);
    }

    private int shown(boolean focused) {
        return focused ? length + 1 : length;
    }

    private int hiddenRows(int lineCount) {
        return lineCount > ROWS ? lineCount - ROWS : 0;
    }

    private void drawIndicators(boolean above, boolean below) {
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        if (above) {
            display.setCursor(INDICATOR_X, TOP + 2);
            display.print("^");
        }
        if (below) {
            display.setCursor(INDICATOR_X, TOP + ROWS * LINE_HEIGHT - 12);
            display.print("v");
        }
        display.setTextSize(TEXT_SIZE);
    }

    /**
     * Splits the first {@code shown} characters into rows, drawing the visible ones from {@code firstLine}, and
     * returns how many rows there are. The rows are walked twice instead of stored, to keep the arena free.
     */
    private int walk(int shown, int firstLine) {
        int line = 0;
        int column = 0;
        int capacity = FIRST_ROW_COLUMNS;
        startRow(line, firstLine);
        for (int position = 0; position < shown; position++) {
            boolean newline = position < length && text[position] == '\n';
            if (column == capacity || newline) {
                line = line + 1;
                column = 0;
                capacity = COLUMNS;
                startRow(line, firstLine);
            }
            if (!newline) {
                if (visible(line, firstLine)) {
                    display.print(position == length ? CURSOR : text[position]);
                }
                column = column + 1;
            }
        }
        return line + 1;
    }

    private boolean visible(int line, int firstLine) {
        return line >= firstLine && line - firstLine < ROWS;
    }

    private void startRow(int line, int firstLine) {
        if (visible(line, firstLine)) {
            int column = line == 0 ? LABEL.length() + 1 : 0;
            display.setCursor(TEXT_LEFT + column * CHARACTER_WIDTH, TOP + (line - firstLine) * LINE_HEIGHT);
        }
    }
}
