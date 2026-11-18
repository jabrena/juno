package io.github.jabrena.juno;

/** Runtime constants shared by analysis and the current UNO R4 backend. */
public final class RuntimeLimits {
    public static final int ARENA_CAPACITY_BYTES = 8192;

    /**
     * The arena of an AVR board when the build sets none;
     * {@link #AVR_MAX_ARENA_BYTES} is the most it accepts, leaving the rest for the stack, the core and its libraries.
     */
    public static final int AVR_ARENA_CAPACITY_BYTES = 1024;
    public static final int AVR_MAX_ARENA_BYTES = 3072;

    /**
     * Size (bytes, including the NUL terminator) of the stack buffer a number is formatted into before it is
     * copied into an arena {@code String} or a concatenation: room for a {@code long} or a formatted
     * {@code double} with its sign.
     */
    public static final int NUMBER_TEXT_BYTES = 32;

    /** Bounded native storage allocated for one {@code java.util.Properties} instance. */
    public static final int PROPERTIES_STORAGE_BYTES = 4 + 16 * (32 + 64);

    /**
     * Most threads that can run at once, the main thread included, on a board whose threads each own a stack (AVR);
     * elsewhere virtual threads are continuations on carriers and only the arena bounds them.
     */
    public static final int MAX_THREADS = 4;

    /**
     * Most threads that can run at once, the main thread included, when threads run on two carriers (one FreeRTOS
     * task per slot). A fixed slot table, since the other carrier reads it without the runtime lock; each slot's task
     * stack comes from the FreeRTOS heap only once a thread uses the slot.
     */
    public static final int MAX_DUAL_CARRIER_THREADS = 16;

    /**
     * Stack bytes of each extra task on an AVR board, and the most
     * {@code juno.Xss} may ask for there.
     */
    public static final int AVR_THREAD_STACK_BYTES = 512;
    public static final int AVR_MAX_THREAD_STACK_BYTES = 1024;

    /** Stack bytes of the carrier on the smallest board (UNO R4 WiFi). */
    public static final int MIN_THREAD_STACK_BYTES = 2048;

    /** Arena bytes of one thread/task handle, including structured-task result and failure state. */
    public static final int THREAD_OBJECT_BYTES = 28;

    /** Arena bytes of one bounded structured-task scope and its active-task table. */
    public static final int STRUCTURED_TASK_SCOPE_BYTES = 40;

    /** Arena bytes of one structured-task scope configuration handle. */
    public static final int STRUCTURED_TASK_CONFIGURATION_BYTES = 4;

    /** Most distinct monitors that may be held or contended at once. */
    public static final int MAX_MONITORS = 8;

    private RuntimeLimits() {
    }
}
