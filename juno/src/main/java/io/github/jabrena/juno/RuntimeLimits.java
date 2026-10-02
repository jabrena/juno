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

    /** Bounded native storage allocated for one {@code java.util.Properties} instance. */
    public static final int PROPERTIES_STORAGE_BYTES = 4 + 16 * (32 + 64);

    /** Most threads that can run at once, the main thread included; each extra one owns a stack of its own. */
    public static final int MAX_THREADS = 4;

    /** Arena bytes of one {@code java.lang.Thread} handle (runnable, flags, id, spare). */
    public static final int THREAD_OBJECT_BYTES = 16;

    /** Most distinct monitors that may be held or contended at once. */
    public static final int MAX_MONITORS = 8;

    private RuntimeLimits() {
    }
}
