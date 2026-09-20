package io.github.jabrena.juno;

/** Runtime constants shared by analysis and the current UNO R4 backend. */
public final class RuntimeLimits {
    public static final int ARENA_CAPACITY_BYTES = 8192;

    /**
     * Size (bytes, including the NUL terminator) of each slot in the rotating pool backing every
     * runtime {@code String} ({@code String.valueOf}, {@code Json.getString(byte[],int,String)},
     * {@code StringBuilder.toString()}). A value that would need {@code >= this} bytes panics
     * rather than truncating or overflowing — see the backends' {@code runtimeStringHelpers()}.
     */
    public static final int STRING_SLOT_CAPACITY_BYTES = 32;

    private RuntimeLimits() {
    }
}
