package io.github.jabrena.juno.usecases.zelda.common.ux;

/** Supplies compact labels and full selected titles to an {@link OptionList}. */
public interface OptionListItems {
    int count();

    String label(int index);

    String title(int index);
}
