package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.usecases.zelda.common.ux.OptionList;
import io.github.jabrena.juno.usecases.zelda.common.ux.OptionListItems;

/**
 * A keyboard-navigated list that prefixes each title with its number and wraps it onto as many lines as it needs,
 * instead of the one-line marquee of {@link OptionList}.
 */
public final class NumberedList {
    private static final int DISPLAY_WIDTH = 240;
    private static final int CONTENT_HEIGHT = 107;
    private static final int FIRST_ROW_Y = 2;
    private static final int TEXT_SIZE = 2;
    private static final int TEXT_LEFT = 4;
    private static final int TEXT_RIGHT = 220;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int LINE_HEIGHT = 9 * TEXT_SIZE;
    private static final int ITEM_GAP = 3;
    private static final int PREFIX_COLUMNS = 3;
    private static final int TEXT_COLUMNS = (TEXT_RIGHT - TEXT_LEFT) / CHARACTER_WIDTH - PREFIX_COLUMNS;

    private final Display display;
    private final OptionListItems items;
    private int itemCount;
    private int selectedIndex = -1;
    private int firstVisibleIndex;
    private int nextLineStart;

    public NumberedList(Display display, OptionListItems items) {
        this.display = display;
        this.items = items;
        itemCount = items.count();
    }

    public void select(int index) {
        itemCount = items.count();
        if (itemCount == 0) {
            selectedIndex = -1;
            firstVisibleIndex = 0;
            drawEmpty();
            return;
        }
        selectedIndex = index < 0 ? 0 : index >= itemCount ? itemCount - 1 : index;
        firstVisibleIndex = 0;
        revealSelection();
        draw();
    }

    public boolean hasSelection() {
        return selectedIndex >= 0;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void move(int direction) {
        itemCount = items.count();
        if (selectedIndex < 0) {
            return;
        }
        int nextIndex = selectedIndex + direction;
        if (nextIndex < 0 || nextIndex >= itemCount) {
            return;
        }
        selectedIndex = nextIndex;
        revealSelection();
        draw();
    }

    /** Redraws when the number of items changed since the last draw. */
    public void render() {
        if (items.count() != itemCount) {
            select(selectedIndex);
        }
    }

    private void revealSelection() {
        if (selectedIndex < firstVisibleIndex) {
            firstVisibleIndex = selectedIndex;
        }
        while (firstVisibleIndex < selectedIndex && heightBetween(firstVisibleIndex, selectedIndex) > CONTENT_HEIGHT) {
            firstVisibleIndex = firstVisibleIndex + 1;
        }
    }

    private int heightBetween(int first, int last) {
        int height = FIRST_ROW_Y;
        for (int index = first; index <= last; index++) {
            height = height + lineCount(index) * LINE_HEIGHT + ITEM_GAP;
        }
        return height;
    }

    private void draw() {
        display.fillRect(0, 0, DISPLAY_WIDTH, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        int y = FIRST_ROW_Y;
        int index = firstVisibleIndex;
        while (index < itemCount) {
            int height = lineCount(index) * LINE_HEIGHT;
            if (index > firstVisibleIndex && y + height > CONTENT_HEIGHT) {
                break;
            }
            drawItem(index, y);
            y = y + height + ITEM_GAP;
            index = index + 1;
        }
        drawNavigationIndicators(index);
    }

    private void drawItem(int index, int y) {
        display.setTextColor(index == selectedIndex ? Display.YELLOW : Display.WHITE, Display.BLACK);
        display.setCursor(TEXT_LEFT, y);
        display.print((char) ('1' + index));
        display.print(". ");
        int length = items.titleLength(index);
        int start = 0;
        do {
            int end = lineEnd(index, start, length);
            display.setCursor(TEXT_LEFT + PREFIX_COLUMNS * CHARACTER_WIDTH, y);
            for (int position = start; position < end; position++) {
                display.print(items.titleCharacter(index, position));
            }
            y = y + LINE_HEIGHT;
            start = nextLineStart;
        } while (start < length && y < CONTENT_HEIGHT);
    }

    private int lineCount(int index) {
        int length = items.titleLength(index);
        int lines = 0;
        int start = 0;
        do {
            lineEnd(index, start, length);
            lines = lines + 1;
            start = nextLineStart;
        } while (start < length);
        return lines;
    }

    /** Returns where the line beginning at {@code start} ends, breaking on a space when one fits. */
    private int lineEnd(int index, int start, int length) {
        int end = start;
        int lastSpace = -1;
        while (end < length && end - start < TEXT_COLUMNS) {
            if (items.titleCharacter(index, end) == ' ') {
                lastSpace = end;
            }
            end = end + 1;
        }
        if (end >= length) {
            nextLineStart = end;
            return end;
        }
        if (items.titleCharacter(index, end) == ' ') {
            nextLineStart = end + 1;
            return end;
        }
        if (lastSpace > start) {
            nextLineStart = lastSpace + 1;
            return lastSpace;
        }
        nextLineStart = end;
        return end;
    }

    private void drawEmpty() {
        display.fillRect(0, 0, DISPLAY_WIDTH, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(TEXT_LEFT, 42);
        display.print("No messages");
    }

    private void drawNavigationIndicators(int lastVisibleIndex) {
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        if (firstVisibleIndex > 0) {
            display.setCursor(224, 6);
            display.print("^");
        }
        if (lastVisibleIndex < itemCount) {
            display.setCursor(224, 94);
            display.print("v");
        }
    }
}
