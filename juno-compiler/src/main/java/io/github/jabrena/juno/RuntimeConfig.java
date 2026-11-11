package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Configuration of the generated runtime: RAM capacities plus the optional single-carrier compatibility mode used by
 * constrained test pipelines. {@link #DEFAULT} reproduces the constants in {@link RuntimeLimits} and the selected
 * board's physical carrier count, so a compilation without explicit configuration is unchanged. There is no virtual
 * thread-count setting: virtual threads keep their frames in the arena while parked, so the arena bounds how many can
 * be alive (the AVR boards, with a stack per thread, run at most {@link RuntimeLimits#MAX_THREADS}).
 *
 * @param arenaBytes size of the garbage-collected arena, a multiple of 8
 * @param threadStackBytes stack of each carrier (on AVR, of each extra thread), a multiple of 8; empty keeps the
 *     board's own size
 * @param carrierCountOverride optional compatibility override for environments that can execute only one carrier;
 *     empty keeps the board core's carrier count
 */
public record RuntimeConfig(int arenaBytes, OptionalInt threadStackBytes, OptionalInt carrierCountOverride) {
    public static final RuntimeConfig DEFAULT = new RuntimeConfig(RuntimeLimits.ARENA_CAPACITY_BYTES,
            OptionalInt.empty(), OptionalInt.empty());

    static final int MIN_ARENA_BYTES = 512;
    static final int MIN_STACK_BYTES = 512;

    public RuntimeConfig(int arenaBytes, OptionalInt threadStackBytes) {
        this(arenaBytes, threadStackBytes, OptionalInt.empty());
    }

    public RuntimeConfig {
        if (arenaBytes < MIN_ARENA_BYTES || arenaBytes % 8 != 0) {
            throw new CompileException("The arena size must be at least " + MIN_ARENA_BYTES
                    + " bytes and a multiple of 8, not " + arenaBytes);
        }
        if (threadStackBytes.isPresent()
                && (threadStackBytes.getAsInt() < MIN_STACK_BYTES || threadStackBytes.getAsInt() % 8 != 0)) {
            throw new CompileException("The task stack size must be at least " + MIN_STACK_BYTES
                    + " bytes and a multiple of 8, not " + threadStackBytes.getAsInt());
        }
        if (carrierCountOverride.isPresent()
                && (carrierCountOverride.getAsInt() < 1 || carrierCountOverride.getAsInt() > 2)) {
            throw new CompileException("juno.carriers must be 1 or 2, not " + carrierCountOverride.getAsInt());
        }
    }

    /**
     * The default configuration with the sizes given in the JVM's {@code -Xmx}/{@code -Xss} syntax (see
     * {@link #parseBytes}); an empty or blank size keeps the default.
     */
    public static RuntimeConfig of(Optional<String> arenaSize, Optional<String> threadStackSize) {
        return of(arenaSize, threadStackSize, Optional.empty());
    }

    /**
     * The default configuration with optional runtime sizes and carrier-count compatibility override. An empty or
     * blank carrier count keeps the board core's normal count; CI environments without dual-core emulation pass 1.
     */
    public static RuntimeConfig of(Optional<String> arenaSize, Optional<String> threadStackSize,
                                   Optional<String> carrierCount) {
        Optional<String> arena = arenaSize.filter(size -> !size.isBlank());
        Optional<String> stack = threadStackSize.filter(size -> !size.isBlank());
        Optional<String> carriers = carrierCount.filter(count -> !count.isBlank());
        return new RuntimeConfig(arena.map(size -> parseBytes("juno.Xmx", size)).orElse(DEFAULT.arenaBytes()),
                stack.map(size -> OptionalInt.of(parseBytes("juno.Xss", size))).orElse(DEFAULT.threadStackBytes()),
                carriers.map(RuntimeConfig::parseCarrierCount).orElse(DEFAULT.carrierCountOverride()));
    }

    private static OptionalInt parseCarrierCount(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException invalid) {
            throw new CompileException("juno.carriers must be 1 or 2, not '" + value + "'");
        }
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

    /**
     * This configuration as {@code board} runs it. An AVR core has only a few KB of RAM, so its core-specific default
     * and maximum arena and task-stack sizes apply; every other architecture keeps the configuration unchanged.
     */
    public RuntimeConfig forBoard(Board board) {
        carrierCount(board.core().carrierCount());
        if (board.core().architecture() != Architecture.AVR) {
            return this;
        }
        if (threadStackBytes.isPresent() && threadStackBytes.getAsInt() > board.core().maxThreadStackBytes()) {
            throw new CompileException("The " + board.displayName() + " has too little RAM for "
                    + threadStackBytes.getAsInt() + "-byte task stacks; juno.Xss can be at most "
                    + board.core().maxThreadStackBytes() + " bytes there");
        }
        if (arenaBytes == DEFAULT.arenaBytes) {
            return new RuntimeConfig(board.core().defaultArenaBytes(), threadStackBytes, carrierCountOverride);
        }
        if (arenaBytes > board.core().maxArenaBytes()) {
            throw new CompileException("The " + board.displayName() + " has too little RAM for a " + arenaBytes
                    + "-byte arena; juno.Xmx can be at most " + board.core().maxArenaBytes() + " bytes there");
        }
        return this;
    }

    /** The configured carrier count, checked against what the selected core physically supports. */
    public int carrierCount(int coreCarrierCount) {
        int configured = carrierCountOverride.orElse(coreCarrierCount);
        if (configured > coreCarrierCount) {
            throw new CompileException("juno.carriers=" + configured + " exceeds this board's " + coreCarrierCount
                    + " carrier(s)");
        }
        return configured;
    }

    /** The carrier (on AVR, task) stack size to use on a board whose own size is {@code boardDefault}. */
    public int threadStackBytes(int boardDefault) {
        return threadStackBytes.orElse(boardDefault);
    }
}
