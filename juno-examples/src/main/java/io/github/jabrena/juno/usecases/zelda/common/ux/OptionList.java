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
    private final int itemCount;
    private int selectedIndex = -1;
    private int firstVisibleIndex;
    private int marqueeFrameCounter;
    private int marqueeOffset;
    private int marqueeDirection;
    private int marqueePauseFrames;

    public OptionList(Display display, OptionListItems items) {
        this.display = display;
        this.items = items;
        this.itemCount = items.count();
    }

    public void select(int index) {
        if (itemCount == 0) {
            selectedIndex = -1;
            firstVisibleIndex = 0;
        } else {
            selectedIndex = index < 0 ? 0 : index >= itemCount ? itemCount - 1 : index;
            firstVisibleIndex = selectedIndex / VISIBLE_ROWS * VISIBLE_ROWS;
        }
        resetMarquee();
        draw();
    }

    public boolean hasSelection() {
        return selectedIndex >= 0;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void move(int direction) {
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
        if (selectedIndex < 0) {
            return;
        }
        marqueeFrameCounter = marqueeFrameCounter + 1;
        if (marqueeFrameCounter < MARQUEE_FRAME_PERIOD) {
            return;
        }
        marqueeFrameCounter = 0;

        String title = items.title(selectedIndex);
        int maximumOffset = title.length() * CHARACTER_WIDTH - (TEXT_RIGHT - TEXT_LEFT);
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

    private void drawRow(int index) {
        int y = FIRST_ROW_Y + (index - firstVisibleIndex) * ROW_HEIGHT;
        if (index == selectedIndex) {
            display.setTextColor(Display.YELLOW, Display.BLACK);
            drawScrollingTitle(items.title(index), y);
            return;
        }
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(TEXT_LEFT, y);
        display.println(items.label(index));
    }

    private void drawScrollingTitle(String title, int y) {
        for (int index = 0; index < title.length(); index++) {
            int x = TEXT_LEFT + index * CHARACTER_WIDTH - marqueeOffset;
            if (x > -CHARACTER_WIDTH && x < TEXT_RIGHT) {
                display.setCursor(x, y);
                display.print(title.charAt(index));
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
