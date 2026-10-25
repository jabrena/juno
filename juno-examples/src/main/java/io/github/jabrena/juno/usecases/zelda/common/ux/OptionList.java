package io.github.jabrena.juno.usecases.zelda.common.ux;

import io.github.jabrena.juno.hardware.display.Display;

/** A keyboard-navigated option list with paging indicators and a marquee for the selected label. */
public final class OptionList {
    private static final int DISPLAY_WIDTH = 240;
    private static final int CONTENT_HEIGHT = 107;
    private static final int VISIBLE_ROWS = 4;
    private static final int FIRST_ROW_Y = 2;
    private static final int ROW_HEIGHT = 25;
    private static final int TEXT_SIZE = 3;
    private static final int TEXT_LEFT = 4;
    private static final int TEXT_RIGHT = 220;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int MARQUEE_FRAME_PERIOD = 5;
    private static final int MARQUEE_STEP_PIXELS = 3;
    private static final int MARQUEE_PAUSE_FRAMES = 10;

    private final Display display;
    private final OptionListItems items;
    private int itemCount;
    private int selectedIndex = -1;
    private int firstVisibleIndex;
    private int marqueeFrameCounter;
    private int marqueeOffset;
    private int marqueeDirection;
    private int marqueePauseFrames;

    public OptionList(Display display, OptionListItems items) {
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
        } else {
            selectedIndex = index < 0 ? 0 : index >= itemCount ? itemCount - 1 : index;
            firstVisibleIndex = selectedIndex / VISIBLE_ROWS * VISIBLE_ROWS;
            draw();
        }
        resetMarquee();
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
        if (nextIndex < 0) {
            nextIndex = 0;
        } else if (nextIndex >= itemCount) {
            nextIndex = itemCount - 1;
        }
        if (nextIndex == selectedIndex) {
            return;
        }
        selectedIndex = nextIndex;
        if (selectedIndex < firstVisibleIndex) {
            firstVisibleIndex = selectedIndex;
        } else if (selectedIndex >= firstVisibleIndex + VISIBLE_ROWS) {
            firstVisibleIndex = selectedIndex - VISIBLE_ROWS + 1;
        }
        resetMarquee();
        draw();
    }

    public void render() {
        int currentItemCount = items.count();
        if (currentItemCount != itemCount) {
            itemCount = currentItemCount;
            if (itemCount == 0) {
                selectedIndex = -1;
                firstVisibleIndex = 0;
                drawEmpty();
                return;
            }
            if (selectedIndex < 0 || selectedIndex >= itemCount) {
                selectedIndex = 0;
            }
            if (firstVisibleIndex >= itemCount) {
                firstVisibleIndex = Math.max(0, itemCount - VISIBLE_ROWS);
            }
            draw();
        }
        if (selectedIndex < 0) {
            return;
        }
        marqueeFrameCounter = marqueeFrameCounter + 1;
        if (marqueeFrameCounter < MARQUEE_FRAME_PERIOD) {
            return;
        }
        marqueeFrameCounter = 0;

        int maximumOffset = items.titleLength(selectedIndex) * CHARACTER_WIDTH - (TEXT_RIGHT - TEXT_LEFT);
        if (maximumOffset <= 0) {
            return;
        }
        if (marqueePauseFrames > 0) {
            marqueePauseFrames = marqueePauseFrames - 1;
            return;
        }

        marqueeOffset = marqueeOffset + marqueeDirection * MARQUEE_STEP_PIXELS;
        if (marqueeOffset >= maximumOffset) {
            marqueeOffset = maximumOffset;
            marqueeDirection = -1;
            marqueePauseFrames = MARQUEE_PAUSE_FRAMES;
        } else if (marqueeOffset <= 0) {
            marqueeOffset = 0;
            marqueeDirection = 1;
            marqueePauseFrames = MARQUEE_PAUSE_FRAMES;
        }
        drawSelectedRow();
    }

    private void draw() {
        display.fillRect(0, 0, DISPLAY_WIDTH, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);

        int lastVisibleIndex = visibleEndIndex();
        for (int index = firstVisibleIndex; index < lastVisibleIndex; index++) {
            drawRow(index);
        }
        drawNavigationIndicators(lastVisibleIndex);
    }

    private void drawEmpty() {
        display.fillRect(0, 0, DISPLAY_WIDTH, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(2);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(TEXT_LEFT, 42);
        display.print("No messages");
    }

    private void drawRow(int index) {
        int y = FIRST_ROW_Y + (index - firstVisibleIndex) * ROW_HEIGHT;
        if (index == selectedIndex) {
            display.setTextColor(Display.YELLOW, Display.BLACK);
            drawScrollingTitle(index, y);
            return;
        }
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(TEXT_LEFT, y);
        for (int position = 0; position < items.labelLength(index); position++) {
            display.print(items.labelCharacter(index, position));
        }
    }

    private void drawScrollingTitle(int itemIndex, int y) {
        for (int position = 0; position < items.titleLength(itemIndex); position++) {
            int x = TEXT_LEFT + position * CHARACTER_WIDTH - marqueeOffset;
            if (x > -CHARACTER_WIDTH && x < TEXT_RIGHT) {
                display.setCursor(x, y);
                display.print(items.titleCharacter(itemIndex, position));
            }
        }
    }

    private void drawSelectedRow() {
        int y = FIRST_ROW_Y + (selectedIndex - firstVisibleIndex) * ROW_HEIGHT;
        display.fillRect(0, y, TEXT_RIGHT, ROW_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        drawRow(selectedIndex);
        drawNavigationIndicators(visibleEndIndex());
    }

    private void drawNavigationIndicators(int lastVisibleIndex) {
        display.fillRect(TEXT_RIGHT, 0, DISPLAY_WIDTH - TEXT_RIGHT, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        if (firstVisibleIndex > 0) {
            display.setCursor(224, 6);
            display.println("^");
        }
        if (lastVisibleIndex < itemCount) {
            display.setCursor(224, 94);
            display.println("v");
        }
    }

    private int visibleEndIndex() {
        int lastVisibleIndex = firstVisibleIndex + VISIBLE_ROWS;
        return lastVisibleIndex < itemCount ? lastVisibleIndex : itemCount;
    }

    private void resetMarquee() {
        marqueeFrameCounter = 0;
        marqueeOffset = 0;
        marqueeDirection = 1;
        marqueePauseFrames = MARQUEE_PAUSE_FRAMES;
    }
}
