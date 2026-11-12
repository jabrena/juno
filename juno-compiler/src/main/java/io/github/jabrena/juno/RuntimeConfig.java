package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Board;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The requested configuration of the generated runtime: the RAM sizes and the optional single-carrier compatibility
 * mode the build asked for. An empty size means "unset": the board's default applies. {@link #resolveFor} turns this
 * request into the {@link ResolvedRuntimeConfig} that everything downstream consumes.
 *
 * @param arenaBytes the requested arena, a multiple of 8, or empty for the board's default
 * @param threadStackBytes the requested carrier (on AVR, extra task) stack, a multiple of 8, or empty for the board's
 *     default
 * @param carrierCountOverride optional compatibility override for environments that can execute only one carrier;
 *     empty keeps the board core's carrier count
 */
public record RuntimeConfig(OptionalInt arenaBytes, OptionalInt threadStackBytes, OptionalInt carrierCountOverride) {

    /** The configuration with nothing requested: every size is the board's default. */
    public static final RuntimeConfig DEFAULT = new RuntimeConfig(OptionalInt.empty(), OptionalInt.empty(),
            OptionalInt.empty());

    public static final int MIN_ARENA_BYTES = 512;
    public static final int MIN_STACK_BYTES = 512;

    /** A configuration with an explicit arena and stack, given in bytes; {@code arenaBytes} must be a valid size. */
    public RuntimeConfig(int arenaBytes, OptionalInt threadStackBytes) {
        this(OptionalInt.of(arenaBytes), threadStackBytes, OptionalInt.empty());
    }

    public RuntimeConfig {
        if (arenaBytes.isPresent() && (arenaBytes.getAsInt() < MIN_ARENA_BYTES || arenaBytes.getAsInt() % 8 != 0)) {
            throw new CompileException("The arena size must be at least " + MIN_ARENA_BYTES
                    + " bytes and a multiple of 8, not " + arenaBytes.getAsInt());
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
     * The request with the sizes given in the JVM's {@code -Xmx}/{@code -Xss} syntax (see {@link #parseBytes}); an
     * empty or blank size is unset.
     */
    public static RuntimeConfig of(Optional<String> arenaSize, Optional<String> threadStackSize) {
        return of(arenaSize, threadStackSize, Optional.empty());
    }

    /**
     * The request with optional runtime sizes and carrier-count compatibility override. An empty or blank carrier count
     * keeps the board core's normal count; CI environments without dual-core emulation pass 1.
     */
    public static RuntimeConfig of(Optional<String> arenaSize, Optional<String> threadStackSize,
                                   Optional<String> carrierCount) {
        Optional<String> arena = arenaSize.filter(size -> !size.isBlank());
        Optional<String> stack = threadStackSize.filter(size -> !size.isBlank());
        Optional<String> carriers = carrierCount.filter(count -> !count.isBlank());
        return new RuntimeConfig(
                arena.map(size -> OptionalInt.of(parseBytes("juno.Xmx", size))).orElse(OptionalInt.empty()),
                stack.map(size -> OptionalInt.of(parseBytes("juno.Xss", size))).orElse(OptionalInt.empty()),
                carriers.map(RuntimeConfig::parseCarrierCount).orElse(OptionalInt.empty()));
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
     * This request as {@code board} runs it. Unset sizes take the board's profile defaults, explicit sizes must not
     * exceed its maximums on any architecture, and the carrier override must fit the board's core.
     */
    public ResolvedRuntimeConfig resolveFor(Board board) {
        return board.memoryProfile().resolve(this, board);
    }
}
