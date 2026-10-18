package io.github.jabrena.juno.api.hardware;

/** Test double for the native {@link io.github.jabrena.juno.api.hardware.BoardRuntime}: the emulator models an UNO Q. */
public final class BoardRuntime {
    private BoardRuntime() {
    }

    public static boolean isQ() {
        return true;
    }

    public static boolean isR4() {
        return false;
    }

    public static boolean isR3() {
        return false;
    }
}
