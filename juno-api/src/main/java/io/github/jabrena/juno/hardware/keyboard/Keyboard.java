package io.github.jabrena.juno.hardware.keyboard;

/**
 * A keyboard polled by the board program. Call {@link #update()} in the main loop, then inspect the current keys when
 * {@link #isChanged()} reports a transition. Key values are Unicode/ASCII character codes plus the control constants
 * declared here.
 */
public interface Keyboard {
    int NONE = 0;
    int BACKSPACE = 8;
    int TAB = 9;
    int ENTER = 10;

    /** Initializes the keyboard hardware. */
    void begin();

    /** Refreshes the current key and modifier state. */
    void update();

    /** Whether the number of pressed keys changed during the last update. */
    boolean isChanged();

    /** Whether at least one key, including a modifier, is currently pressed. */
    boolean isPressed();

    /** Whether {@code key} is currently pressed. Character codes and {@link #BACKSPACE}, {@link #TAB}, and {@link #ENTER} are accepted. */
    boolean isKeyPressed(int key);

    /** Number of printable or control keys available through {@link #keyAt(int)}. */
    int keyCount();

    /** Returns one current printable/control key, or {@link #NONE} when {@code index} is outside {@link #keyCount()}. */
    int keyAt(int index);

    boolean isShiftPressed();

    boolean isControlPressed();

    boolean isAltPressed();

    boolean isFnPressed();

    boolean isOptionPressed();
}
