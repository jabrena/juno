package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.keyboard.Keyboard;

/** The M5Stack Cardputer's {@link Keyboard}, implemented by compiler intrinsics over M5StackCardputer.Keyboard. */
public final class NativeKeyboard implements Keyboard {
    public static final NativeKeyboard INSTANCE;

    private static Keyboard keyboard;

    static {
        INSTANCE = new NativeKeyboard();
        keyboard = INSTANCE;
    }

    private NativeKeyboard() {
    }

    /** The keyboard the M5StackCardputer facade hands out. */
    public static Keyboard keyboard() {
        return keyboard;
    }

    /** Makes the M5StackCardputer facade hand out {@code replacement}: for a whole-program emulator in a test, never on the board. */
    public static void setKeyboard(Keyboard replacement) {
        keyboard = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native void update();

    @Override
    public native boolean isChanged();

    @Override
    public native boolean isPressed();

    @Override
    public native boolean isKeyPressed(int key);

    @Override
    public native int keyCount();

    @Override
    public native int keyAt(int index);

    @Override
    public native boolean isShiftPressed();

    @Override
    public native boolean isControlPressed();

    @Override
    public native boolean isAltPressed();

    @Override
    public native boolean isFnPressed();

    @Override
    public native boolean isOptionPressed();
}
