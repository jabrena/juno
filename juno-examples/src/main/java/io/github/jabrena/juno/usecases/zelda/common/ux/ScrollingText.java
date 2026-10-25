package io.github.jabrena.juno.usecases.zelda.common.ux;

import io.github.jabrena.juno.hardware.display.Display;

/** A keyboard-scrolled, word-wrapped text panel with the same paging indicators as {@link OptionList}. */
public final class ScrollingText {
    private static final int DISPLAY_WIDTH = 240;
    private static final int TEXT_SIZE = 2;
    private static final int TEXT_LEFT = 4;
    private static final int TEXT_RIGHT = 220;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int LINE_HEIGHT = 9 * TEXT_SIZE;
    private static final int COLUMNS = (TEXT_RIGHT - TEXT_LEFT) / CHARACTER_WIDTH;
    private static final int MAX_LINES = 128;

    private final Display display;
    private final int top;
    private final int height;
    private final int visibleLines;
    private final int[] lineStarts = new int[MAX_LINES];
    private final int[] lineEnds = new int[MAX_LINES];
    private byte[] text;
    private int lineCount;
    private int firstVisibleLine;

    public ScrollingText(Display display, int top, int height) {
        this.display = display;
        this.top = top;
        this.height = height;
        visibleLines = height / LINE_HEIGHT;
    }

    /** Wraps {@code length} bytes of {@code value} into lines and draws them from the first line. */
    public void show(byte[] value, int length) {
        text = value;
        firstVisibleLine = 0;
        wrap(length);
        draw();
    }

    /** Moves the view one line up ({@code -1}) or down ({@code 1}) when there is more text that way. */
    public void scroll(int direction) {
        int nextLine = firstVisibleLine + direction;
        int lastFirstLine = lineCount > visibleLines ? lineCount - visibleLines : 0;
        if (nextLine < 0) {
            nextLine = 0;
        } else if (nextLine > lastFirstLine) {
            nextLine = lastFirstLine;
        }
        if (nextLine != firstVisibleLine) {
            firstVisibleLine = nextLine;
            draw();
        }
    }

    private void wrap(int length) {
        lineCount = 0;
        int start = 0;
        while (start < length && lineCount < MAX_LINES) {
            int end = start;
            int lastSpace = -1;
            while (end < length && text[end] != '\n' && end - start < COLUMNS) {
                if (text[end] == ' ') {
                    lastSpace = end;
                }
                end = end + 1;
            }
            int next;
            if (end >= length) {
                next = end;
            } else if (text[end] == '\n' || text[end] == ' ') {
                next = end + 1;
            } else if (lastSpace > start) {
                end = lastSpace;
                next = lastSpace + 1;
            } else {
                next = end;
            }
            lineStarts[lineCount] = start;
            lineEnds[lineCount] = end;
            lineCount = lineCount + 1;
            start = next;
        }
    }

    private void draw() {
        display.fillRect(0, top, DISPLAY_WIDTH, height, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        display.setTextColor(Display.WHITE, Display.BLACK);
        int lastVisibleLine = firstVisibleLine + visibleLines < lineCount ? firstVisibleLine + visibleLines : lineCount;
        for (int line = firstVisibleLine; line < lastVisibleLine; line++) {
            display.setCursor(TEXT_LEFT, top + (line - firstVisibleLine) * LINE_HEIGHT);
            for (int position = lineStarts[line]; position < lineEnds[line]; position++) {
                display.print((char) (text[position] & 0xff));
            }
        }
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        if (firstVisibleLine > 0) {
            display.setCursor(224, top + 4);
            display.print("^");
        }
        if (lastVisibleLine < lineCount) {
            display.setCursor(224, top + height - 12);
            display.print("v");
        }
    }
}
