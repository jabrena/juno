package io.github.jabrena.juno.hardware;

/**
 * Read-only introspection into Juno's arena allocator and the program's flash footprint.
 * {@link ArduinoUno#getBoardMemory()} is the board's own answer, which the compiler lowers to its runtime shim; a unit test
 * passes {@code mock(BoardMemory.class)} instead. The arena figures are useful for a health-check heartbeat over
 * {@link io.github.jabrena.juno.hardware.serial.Serial}: a value that keeps climbing and never comes back down across
 * many collection cycles indicates something is holding references it shouldn't (an unintended leak), whereas one
 * that rises and falls as requests are served and reclaimed is healthy.
 */
public interface BoardMemory {

    /** Bytes currently allocated in Juno's arena (never negative, capped at the arena's fixed capacity). */
    int arenaUsedBytes();

    /**
     * The arena's total size in bytes, fixed when the program is compiled (the Maven plugin's {@code juno.Xmx}).
     * Subtract {@link #arenaUsedBytes()} to tell whether a large allocation fits before making it.
     */
    int arenaCapacityBytes();

    /**
     * Bytes of program flash the running image occupies: code, constants and the initial values of static data.
     * On the UNO Q, whose sketch is loaded from its own flash partition, it is the size of that sketch image.
     */
    int flashUsedBytes();

    /** Bytes of program flash available to a program on this board, the limit {@link #flashUsedBytes()} counts toward. */
    int flashCapacityBytes();
}
