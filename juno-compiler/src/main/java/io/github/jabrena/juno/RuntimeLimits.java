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

    /** Stack bytes of each extra thread on the smallest board (UNO R4 WiFi); the UNO Q gives 4096. */
    public static final int MIN_THREAD_STACK_BYTES = 2048;

    /** Arena bytes of one thread/task handle, including structured-task result and failure state. */
    public static final int THREAD_OBJECT_BYTES = 28;

    /** Arena bytes of one bounded structured-task scope and its active-task table. */
    public static final int STRUCTURED_TASK_SCOPE_BYTES = 36;

    /** Most distinct monitors that may be held or contended at once. */
    public static final int MAX_MONITORS = 8;

    private RuntimeLimits() {
    }
}
