package io.github.jabrena.juno.util.mvc;

/** A {@link View} that receives a key transition and the current Fn modifier state. */
public interface KeyboardView extends View {
    /** Handles one key currently pressed during a keyboard transition. */
    void key(int key, boolean fnPressed);
}
