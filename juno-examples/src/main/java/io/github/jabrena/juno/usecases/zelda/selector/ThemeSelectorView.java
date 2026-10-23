package io.github.jabrena.juno.usecases.zelda.selector;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.KeyboardView;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Theme selection screen for the Cardputer Zelda fan app. */
public final class ThemeSelectorView implements KeyboardView {
    private static final int PLAY_VIEW = 2;
    private static final int COVER_VIEW = 0;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final String[] THEME_LABELS = {"1. ZELDA TITLE THEME"};

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;
    private int selectedThemeIndex;
    private boolean themeSelected;

    public ThemeSelectorView(Navigator navigator, Display display, ZeldaOcarinaModel model) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
    }

    @Override
    public void enter() {
        selectedThemeIndex = 0;
        themeSelected = false;
        draw();
    }

    @Override
    public void render() {
        // The selector is static between key events.
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.setTextSize(1);
        display.fillRect(0, 0, 240, 28, Display.BLUE);
        display.fillRect(0, 26, 240, 2, Display.YELLOW);
        display.setTextColor(Display.YELLOW, Display.BLUE);
        display.setCursor(8, 9);
        display.println("Select the theme");

        for (int index = 0; index < THEME_LABELS.length; index++) {
            display.setTextColor(themeSelected && index == selectedThemeIndex ? Display.YELLOW : Display.WHITE, Display.BLACK);
            display.setCursor(12, 45 + index * 16);
            display.println(THEME_LABELS[index]);
        }
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(224, 40);
        display.println("^");
        display.setCursor(224, 56);
        display.println("v");

        display.fillRect(0, 107, 240, 28, Display.BLUE);
        display.fillRect(0, 107, 240, 2, Display.YELLOW);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setCursor(8, 117);
        display.println("Esc | Enter");
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
            model.selectTheme();
            navigator.show(PLAY_VIEW);
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.show(COVER_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void moveSelection(int direction) {
        if (THEME_LABELS.length == 0) {
            return;
        }
        int nextIndex = selectedThemeIndex + direction;
        if (nextIndex < 0) {
            nextIndex = 0;
        } else if (nextIndex >= THEME_LABELS.length) {
            nextIndex = THEME_LABELS.length - 1;
        }
        selectedThemeIndex = nextIndex;
        themeSelected = true;
        draw();
    }
}
