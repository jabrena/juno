package io.github.jabrena.juno;

import java.util.Optional;
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

    /**
     * The default configuration with the sizes given in the JVM's {@code -Xmx}/{@code -Xss} syntax (see
     * {@link #parseBytes}); an empty or blank size keeps the default.
     */
    public static RuntimeConfig of(Optional<String> arenaSize, Optional<String> threadStackSize) {
        Optional<String> arena = arenaSize.filter(size -> !size.isBlank());
        Optional<String> stack = threadStackSize.filter(size -> !size.isBlank());
        return new RuntimeConfig(arena.map(size -> parseBytes("juno.Xmx", size)).orElse(DEFAULT.arenaBytes()),
                DEFAULT.maxThreads(),
                stack.map(size -> OptionalInt.of(parseBytes("juno.Xss", size))).orElse(DEFAULT.threadStackBytes()));
    }

    /** A size as the JVM writes {@code -Xmx}/{@code -Xss}: bytes, or a whole number of {@code k}, {@code m} or {@code g}. */
    static int parseBytes(String option, String size) {
        String trimmed = size.trim();
        int unit = switch (Character.toLowerCase(trimmed.charAt(trimmed.length() - 1))) {
            case 'k' -> 1024;
            case 'm' -> 1024 * 1024;
            case 'g' -> 1024 * 1024 * 1024;
            default -> 1;
        };
        String digits = unit == 1 ? trimmed : trimmed.substring(0, trimmed.length() - 1);
        try {
            return Math.multiplyExact(Integer.parseInt(digits), unit);
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new CompileException(option + " must be a size such as 49152, 48k or 1m, not '" + size + "'");
        }
    }

    /** The task stack size to use on a board whose own size is {@code boardDefault}. */
    public int threadStackBytes(int boardDefault) {
        return threadStackBytes.orElse(boardDefault);
    }
}
