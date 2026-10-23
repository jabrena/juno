package io.github.jabrena.juno.usecases.zelda.selector;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
import io.github.jabrena.juno.usecases.zelda.common.KeyboardView;
import io.github.jabrena.juno.usecases.zelda.theme.ZeldaSoundtrackThemes;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Theme selection screen for the Cardputer Zelda fan app. */
public final class ThemeSelectorView implements KeyboardView {
    private static final int PLAY_VIEW = 2;
    private static final int COVER_VIEW = 0;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
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

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;
    private int selectedThemeIndex;
    private int firstVisibleIndex;
    private boolean themeSelected;
    private int marqueeFrameCounter;
    private int marqueeOffset;
    private int marqueeDirection;
    private int marqueePauseFrames;

    public ThemeSelectorView(Navigator navigator, Display display, ZeldaOcarinaModel model) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
    }

    @Override
    public void enter() {
        selectedThemeIndex = model.selectedTheme();
        firstVisibleIndex = selectedThemeIndex / VISIBLE_ROWS * VISIBLE_ROWS;
        themeSelected = true;
        resetMarquee();
        draw();
    }

    @Override
    public void render() {
        marqueeFrameCounter = marqueeFrameCounter + 1;
        if (marqueeFrameCounter < MARQUEE_FRAME_PERIOD) {
            return;
        }
        marqueeFrameCounter = 0;

        String title = ZeldaSoundtrackThemes.title(selectedThemeIndex);
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
        display.fillScreen(Display.BLACK);
        display.setTextSize(TEXT_SIZE);

        int lastVisibleIndex = firstVisibleIndex + VISIBLE_ROWS;
        if (lastVisibleIndex > ZeldaSoundtrackThemes.COUNT) {
            lastVisibleIndex = ZeldaSoundtrackThemes.COUNT;
        }
        for (int index = firstVisibleIndex; index < lastVisibleIndex; index++) {
            drawThemeRow(index);
        }
        drawNavigationIndicators(lastVisibleIndex);

        drawFooter();
    }

    private void drawThemeRow(int index) {
        int y = FIRST_ROW_Y + (index - firstVisibleIndex) * ROW_HEIGHT;
        if (themeSelected && index == selectedThemeIndex) {
            display.setTextColor(Display.YELLOW, Display.BLACK);
            drawScrollingTitle(ZeldaSoundtrackThemes.title(index), y);
            return;
        }
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(TEXT_LEFT, y);
        display.println(ZeldaSoundtrackThemes.label(index));
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
        int y = FIRST_ROW_Y + (selectedThemeIndex - firstVisibleIndex) * ROW_HEIGHT;
        display.fillRect(0, y, TEXT_RIGHT, ROW_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        drawThemeRow(selectedThemeIndex);
        drawNavigationIndicators(visibleEndIndex());
    }

    private void drawNavigationIndicators(int lastVisibleIndex) {
        display.fillRect(TEXT_RIGHT, 0, 240 - TEXT_RIGHT, 107, Display.BLACK);
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        if (firstVisibleIndex > 0) {
            display.setCursor(224, 6);
            display.println("^");
        }
        if (lastVisibleIndex < ZeldaSoundtrackThemes.COUNT) {
            display.setCursor(224, 94);
            display.println("v");
        }
    }

    private void drawFooter() {
        display.fillRect(0, 107, 240, 28, Display.BLUE);
        display.fillRect(0, 107, 240, 2, Display.YELLOW);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setCursor(8, 117);
        display.println("Back: Esc | Play: Enter");
    }

    private int visibleEndIndex() {
        int lastVisibleIndex = firstVisibleIndex + VISIBLE_ROWS;
        return lastVisibleIndex < ZeldaSoundtrackThemes.COUNT ? lastVisibleIndex : ZeldaSoundtrackThemes.COUNT;
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == UP) {
            moveSelection(-1);
            return;
        }
        if (key == DOWN) {
            moveSelection(1);
            return;
        }
        if (fnPressed) {
            return;
        }
        if (key == Keyboard.ENTER && themeSelected) {
            model.selectTheme(selectedThemeIndex);
            navigator.show(PLAY_VIEW);
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.show(COVER_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void moveSelection(int direction) {
        int nextIndex = selectedThemeIndex + direction;
        if (nextIndex < 0) {
            nextIndex = 0;
        } else if (nextIndex >= ZeldaSoundtrackThemes.COUNT) {
            nextIndex = ZeldaSoundtrackThemes.COUNT - 1;
        }
        selectedThemeIndex = nextIndex;
        if (selectedThemeIndex < firstVisibleIndex) {
            firstVisibleIndex = selectedThemeIndex;
        } else if (selectedThemeIndex >= firstVisibleIndex + VISIBLE_ROWS) {
            firstVisibleIndex = selectedThemeIndex - VISIBLE_ROWS + 1;
        }
        themeSelected = true;
        resetMarquee();
        draw();
    }

    private void resetMarquee() {
        marqueeFrameCounter = 0;
        marqueeOffset = 0;
        marqueeDirection = 1;
        marqueePauseFrames = MARQUEE_PAUSE_FRAMES;
    }
}
