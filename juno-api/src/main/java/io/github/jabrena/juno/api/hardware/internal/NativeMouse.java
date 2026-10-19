package io.github.jabrena.juno.api.hardware.internal;

import io.github.jabrena.juno.api.hardware.hid.Mouse;

/**
 * The board's {@link Mouse}: each method is a USB HID intrinsic the compiler lowers to the Arduino {@code Mouse}
 * library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link Mouse} instead.
 */
public final class NativeMouse implements Mouse {
    /** The board's USB mouse: the only instance. */
    public static final NativeMouse INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.api.hardware.MCU#getMouse()} returns: the board's mouse, unless a
     * whole-program emulator in a test replaced it.
     */
    private static Mouse mouse;

    static {
        INSTANCE = new NativeMouse();
        mouse = INSTANCE;
    }

    private NativeMouse() {
    }

    /** The mouse {@link io.github.jabrena.juno.api.hardware.MCU#getMouse()} hands out. */
    public static Mouse mouse() {
        return mouse;
    }

    /**
     * Makes {@link io.github.jabrena.juno.api.hardware.MCU#getMouse()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setMouse(Mouse replacement) {
        mouse = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native void move(int x, int y);
}
