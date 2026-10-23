package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.display.Display;

/** The M5Stack Cardputer's {@link Display}, implemented by compiler intrinsics over M5StackCardputer.Display. */
public final class NativeDisplay implements Display {
    public static final NativeDisplay INSTANCE;

    private static Display display;

    static {
        INSTANCE = new NativeDisplay();
        display = INSTANCE;
    }

    private NativeDisplay() {
    }

    /** The display the M5StackCardputer facade hands out. */
    public static Display display() {
        return display;
    }

    /** Makes the M5StackCardputer facade hand out {@code replacement}: for a whole-program emulator in a test, never on the board. */
    public static void setDisplay(Display replacement) {
        display = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native int width();

    @Override
    public native int height();

    @Override
    public native void fillScreen(int color);

    @Override
    public native void drawPixel(int x, int y, int color);

    @Override
    public native void fillRect(int x, int y, int width, int height, int color);

    @Override
    public native void setCursor(int x, int y);

    @Override
    public native void setTextColor(int foreground, int background);

    @Override
    public native void setTextSize(int size);

    @Override
    public native void print(char character);

    @Override
    public native void print(String text);

    @Override
    public native void println(String text);
}
