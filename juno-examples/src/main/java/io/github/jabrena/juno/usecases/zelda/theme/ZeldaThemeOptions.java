package io.github.jabrena.juno.usecases.zelda.theme;

import io.github.jabrena.juno.usecases.zelda.common.ux.OptionListItems;

/** Theme labels exposed to the reusable Zelda option list. */
public final class ZeldaThemeOptions implements OptionListItems {
    @Override
    public int count() {
        return ZeldaSoundtrackThemes.COUNT;
    }

    @Override
    public int labelLength(int index) {
        return ZeldaSoundtrackThemes.label(index).length();
    }

    @Override
    public char labelCharacter(int index, int position) {
        return ZeldaSoundtrackThemes.label(index).charAt(position);
    }

    @Override
    public int titleLength(int index) {
        return ZeldaSoundtrackThemes.title(index).length();
    }

    @Override
    public char titleCharacter(int index, int position) {
        return ZeldaSoundtrackThemes.title(index).charAt(position);
    }
}
