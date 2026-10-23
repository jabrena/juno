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

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;
    private int selectedThemeIndex;
    private int firstVisibleIndex;
    private boolean themeSelected;

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
        draw();
    }

    @Override
    public void render() {
        // The selector is static between key events.
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.setTextSize(3);

        int lastVisibleIndex = firstVisibleIndex + VISIBLE_ROWS;
        if (lastVisibleIndex > ZeldaSoundtrackThemes.COUNT) {
            lastVisibleIndex = ZeldaSoundtrackThemes.COUNT;
        }
        for (int index = firstVisibleIndex; index < lastVisibleIndex; index++) {
            display.setTextColor(themeSelected && index == selectedThemeIndex ? Display.YELLOW : Display.WHITE, Display.BLACK);
            display.setCursor(4, FIRST_ROW_Y + (index - firstVisibleIndex) * ROW_HEIGHT);
            display.println(ZeldaSoundtrackThemes.label(index));
        }
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

        display.fillRect(0, 107, 240, 28, Display.BLUE);
        display.fillRect(0, 107, 240, 2, Display.YELLOW);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setCursor(8, 117);
        display.println("Back: Esc | Play: Enter");
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
        draw();
    }
}
