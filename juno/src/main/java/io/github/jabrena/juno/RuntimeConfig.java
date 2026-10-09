package io.github.jabrena.juno;

import java.util.OptionalInt;

/**
 * The capacities of the generated runtime: how much RAM the arena and the task stacks take. They change how much a
 * program can hold, never what it means, like a JVM's {@code -Xmx} and {@code -Xss}. {@link #DEFAULT} reproduces the
 * constants in {@link RuntimeLimits}, so a compilation without an explicit configuration is unchanged.
 *
 * @param arenaBytes size of the garbage-collected arena, a multiple of 8
 * @param maxThreads scheduler slots, the main thread included; each slot beyond the first owns a stack
 * @param threadStackBytes stack of each extra task, a multiple of 8; empty keeps the board's own size
 *     ({@link RuntimeLimits#MIN_THREAD_STACK_BYTES} on the UNO R4 WiFi, 4096 on the UNO Q)
 */
public record RuntimeConfig(int arenaBytes, int maxThreads, OptionalInt threadStackBytes) {
    public static final RuntimeConfig DEFAULT = new RuntimeConfig(RuntimeLimits.ARENA_CAPACITY_BYTES,
            RuntimeLimits.MAX_THREADS, OptionalInt.empty());

    static final int MIN_ARENA_BYTES = 1024;
    static final int MIN_STACK_BYTES = 512;

    public RuntimeConfig {
        if (arenaBytes < MIN_ARENA_BYTES || arenaBytes % 8 != 0) {
            throw new CompileException("The arena size must be at least " + MIN_ARENA_BYTES
                    + " bytes and a multiple of 8, not " + arenaBytes);
        }
        if (maxThreads < 2) {
            throw new CompileException("The runtime needs at least 2 scheduler slots, not " + maxThreads);
        }
        if (threadStackBytes.isPresent()
                && (threadStackBytes.getAsInt() < MIN_STACK_BYTES || threadStackBytes.getAsInt() % 8 != 0)) {
            throw new CompileException("The task stack size must be at least " + MIN_STACK_BYTES
                    + " bytes and a multiple of 8, not " + threadStackBytes.getAsInt());
        }
    }

    /** The task stack size to use on a board whose own size is {@code boardDefault}. */
    public int threadStackBytes(int boardDefault) {
        return threadStackBytes.orElse(boardDefault);
    }
}
