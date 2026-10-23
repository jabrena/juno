package io.github.jabrena.juno.usecases.zelda;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.zelda.common.KeyboardViewRouter;
import io.github.jabrena.juno.usecases.zelda.cover.CoverView;
import io.github.jabrena.juno.usecases.zelda.play.PlayThemeView;
import io.github.jabrena.juno.usecases.zelda.selector.ThemeSelectorView;
import io.github.jabrena.juno.util.Delay;

/** Zelda fan app for the Cardputer: cover, theme selection, and ocarina playback screens. */
@Board(M5StackCardputerBoard.class)
public final class ZeldaOcarinaCardputer {
    private static final int FRAME_MILLIS = 20;
    private static final int COVER = 0;
    private static final int SELECTOR = 1;
    private static final int PLAY = 2;

    private ZeldaOcarinaCardputer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        Serial.println("Zelda Ocarina Cardputer ready");

        Display display = M5StackCardputer.getDisplay();
        display.begin();
        display.setTextSize(1);
        Keyboard keyboard = M5StackCardputer.getKeyboard();
        keyboard.begin();

        ZeldaOcarinaModel model = new ZeldaOcarinaModel(M5StackCardputer.getAudio());
        KeyboardViewRouter controller = new KeyboardViewRouter(FRAME_MILLIS, keyboard);
        PlayThemeView play = new PlayThemeView(controller, display, model);
        controller.register(COVER, new CoverView(controller, display));
        controller.register(SELECTOR, new ThemeSelectorView(controller, display, model));
        controller.register(PLAY, play);
        controller.background(play);
        controller.start(COVER);
        controller.run();
    }
}
