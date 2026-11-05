package io.github.jabrena.juno.usecases.mailbox.client.ux;

/** Supplies compact labels and full selected titles to an {@link OptionList}. */
public interface OptionListItems {
    int count();

    int labelLength(int index);

    char labelCharacter(int index, int position);

    int titleLength(int index);

    char titleCharacter(int index, int position);
}
