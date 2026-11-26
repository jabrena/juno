package io.github.jabrena.juno.jdk.lang;

/** The small board-side subset of {@link java.lang.System} used by board-compatible libraries. */
@SuppressWarnings("AvoidCommonTypeNames")
public final class System {
    private System() {
    }

    /** Copies primitive array words; the compiler lowers this call to the board runtime. */
    public static native void arraycopy(Object source, int sourcePosition, Object destination, int destinationPosition,
            int length);
}
