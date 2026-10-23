package io.github.jabrena.juno.usecases.zelda.theme;

import io.github.jabrena.juno.usecases.zelda.common.ux.OptionListItems;

/** Theme labels exposed to the reusable Zelda option list. */
public final class ZeldaThemeOptions implements OptionListItems {
    @Override
    public int count() {
        return ZeldaSoundtrackThemes.COUNT;
    }

    @Override
    public String label(int index) {
        return ZeldaSoundtrackThemes.label(index);
    }

    @Override
    public String title(int index) {
        return ZeldaSoundtrackThemes.title(index);
    }
}
