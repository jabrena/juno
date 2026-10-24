package io.github.jabrena.juno.usecases.zelda.play;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.Task;

/** Playback screen and background task for the Zelda ocarina theme. */
public final class PlayThemeView implements KeyboardView, Task {
    private static final int SELECTOR_VIEW = 1;
    private static final int FRAME_MILLIS = 20;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final int HEADER_HEIGHT = 28;
    private static final int THEME_TEXT_SIZE = 2;
    private static final int TITLE_TOP = 35;
    private static final String FOOTER_LEGEND = "Back: Esc | Volume: ^v | Stop: Enter";

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;
    private final Footer footer;

    public PlayThemeView(Navigator navigator, Display display, ZeldaOcarinaModel model, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
        this.footer = footer;
    }

    @Override
    public void enter() {
        model.startTheme();
        draw();
    }

    @Override
    public void render() {
        // Playback advances in the background; redraw only when its visible state changes.
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        drawHeader();
        drawThemeTitle();
        drawStatus();
        footer.draw(FOOTER_LEGEND);
    }

    private void drawHeader() {
        display.setTextSize(1);
        display.fillRect(0, 0, 240, HEADER_HEIGHT, Display.BLUE);
        display.fillRect(0, HEADER_HEIGHT - 2, 240, 2, Display.YELLOW);
        display.setTextColor(Display.YELLOW, Display.BLUE);
        display.setCursor(8, 9);
        display.println("VOLUME");
        display.fillRect(72, 9, 160, 8, Display.WHITE);
        display.fillRect(72, 9, 160 * model.volume() / ZeldaOcarinaModel.VOLUME_MAX, 8, Display.GREEN);
    }

    private void drawThemeTitle() {
        display.setTextSize(THEME_TEXT_SIZE);
        display.setTextColor(Display.YELLOW, Display.BLACK);
        display.setCursor(0, TITLE_TOP);
        display.println(model.themeTitle());
    }

    private void drawStatus() {
        display.setTextSize(THEME_TEXT_SIZE);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setCursor(4, 72);
        display.println(model.isPlaying() ? "PLAYING" : "STOPPED");
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (fnPressed) {
            if (key == UP) {
                model.increaseVolume();
                draw();
            } else if (key == DOWN) {
                model.decreaseVolume();
                draw();
            }
        } else if (key == Keyboard.ENTER) {
            if (model.isPlaying()) {
                model.stopTheme();
            } else {
                model.startTheme();
            }
            draw();
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            model.stopTheme();
            navigator.show(SELECTOR_VIEW);
        }
    }

    @Override
    public void step() {
        if (model.advance(FRAME_MILLIS)) {
            draw();
        }
    }

    @Override
    public void touch(int x, int y) {
    }
}
