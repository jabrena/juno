package io.github.jabrena.juno.usecases.zelda.play;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
import io.github.jabrena.juno.usecases.zelda.common.KeyboardView;
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
    private static final int THEME_TEXT_SIZE = 3;
    private static final int TITLE_TOP = 35;
    private static final int TITLE_LEFT = 4;
    private static final int CHARACTER_WIDTH = 6 * THEME_TEXT_SIZE;
    private static final int MARQUEE_FRAME_PERIOD = 5;
    private static final int MARQUEE_STEP_PIXELS = 3;
    private static final int MARQUEE_PAUSE_FRAMES = 10;

    private final Navigator navigator;
    private final Display display;
    private final ZeldaOcarinaModel model;
    private int marqueeFrameCounter;
    private int marqueeOffset;
    private int marqueeDirection;
    private int marqueePauseFrames;

    public PlayThemeView(Navigator navigator, Display display, ZeldaOcarinaModel model) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
    }

    @Override
    public void enter() {
        model.startTheme();
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

        String title = model.themeTitle();
        int maximumOffset = title.length() * CHARACTER_WIDTH - (240 - TITLE_LEFT);
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
        display.fillRect(0, TITLE_TOP, 240, THEME_TEXT_SIZE * 8, Display.BLACK);
        drawThemeTitle();
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
        display.setTextSize(THEME_TEXT_SIZE);
        display.setTextColor(Display.YELLOW, Display.BLACK);
        for (int index = 0; index < title.length(); index++) {
            int x = TITLE_LEFT + index * CHARACTER_WIDTH - marqueeOffset;
            if (x > -CHARACTER_WIDTH && x < 240) {
                display.setCursor(x, TITLE_TOP);
                display.print(title.charAt(index));
            }
        }
    }

    private void drawStatus() {
        display.setTextSize(THEME_TEXT_SIZE);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setCursor(4, 72);
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

    private void resetMarquee() {
        marqueeFrameCounter = 0;
        marqueeOffset = 0;
        marqueeDirection = 1;
        marqueePauseFrames = MARQUEE_PAUSE_FRAMES;
    }
}
