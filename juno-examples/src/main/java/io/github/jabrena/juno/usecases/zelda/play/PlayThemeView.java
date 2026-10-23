package io.github.jabrena.juno.usecases.zelda.play;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.KeyboardView;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
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
    private static final int FOOTER_TOP = 107;

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;

    public PlayThemeView(Navigator navigator, Display display, ZeldaOcarinaModel model) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
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
        drawFooter();
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
        String title = model.themeTitle();
        display.setTextSize(title.length() > 16 ? 1 : 2);
        display.setTextColor(Display.YELLOW, Display.BLACK);
        display.setCursor(8, 39);
        display.println(title);
    }

    private void drawStatus() {
        display.setTextSize(1);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setCursor(8, 72);
        display.println(model.isPlaying() ? "PLAYING" : "STOPPED");
    }

    private void drawFooter() {
        display.setTextSize(1);
        display.fillRect(0, FOOTER_TOP, 240, 28, Display.BLUE);
        display.fillRect(0, FOOTER_TOP, 240, 2, Display.YELLOW);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setCursor(4, 117);
        display.println("Back: Esc | Volume: ^v | Stop: Enter");
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
