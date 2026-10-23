package io.github.jabrena.juno.usecases.zelda.selector;

import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.ZeldaOcarinaModel;
import io.github.jabrena.juno.usecases.zelda.common.mvc.KeyboardView;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.usecases.zelda.common.ux.OptionList;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Theme selection screen for the Cardputer Zelda fan app. */
public final class ThemeSelectorView implements KeyboardView {
    private static final int PLAY_VIEW = 2;
    private static final int COVER_VIEW = 0;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final String FOOTER_LEGEND = "Back: Esc | Play: Enter";

    private final Navigator navigator;
    private final ZeldaOcarinaModel model;
    private final OptionList options;
    private final Footer footer;

    public ThemeSelectorView(Navigator navigator, ZeldaOcarinaModel model, OptionList options, Footer footer) {
        this.navigator = navigator;
        this.model = model;
        this.options = options;
        this.footer = footer;
    }

    @Override
    public void enter() {
        options.select(model.selectedTheme());
        footer.draw(FOOTER_LEGEND);
    }

    @Override
    public void render() {
        options.render();
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == UP) {
            options.move(-1);
            return;
        }
        if (key == DOWN) {
            options.move(1);
            return;
        }
        if (fnPressed) {
            return;
        }
        if (key == Keyboard.ENTER && options.hasSelection()) {
            model.selectTheme(options.selectedIndex());
            navigator.show(PLAY_VIEW);
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.show(COVER_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }
}
