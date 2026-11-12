package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.ResolvedRuntimeConfig.Source;
import io.github.jabrena.juno.RuntimeConfig;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One board's memory budget: its internal SRAM, the arena and carrier-stack sizes it defaults to and the largest it
 * accepts, which component decides fit below that largest size, and the PSRAM it carries (report-only: the arena
 * never uses PSRAM).
 *
 * <p>The maximums are hard ceilings for a minimal program on that board, not budgets for every program. Sizes are
 * multiples of 8.
 *
 * @param internalSramBytes the RAM region the linker places sections in (the static DRAM segment on the ESP32 boards)
 * @param defaultArenaBytes the arena when {@code juno.Xmx} is unset
 * @param maxArenaBytes the largest arena {@code juno.Xmx} accepts
 * @param defaultStackBytes the carrier (on AVR, extra task) stack when {@code juno.Xss} is unset
 * @param maxStackBytes the largest stack {@code juno.Xss} accepts
 * @param authority the component that decides whether an arena below the maximum fits
 * @param psramBytes external PSRAM the board carries, or empty when none is recorded; metadata only
 */
public record BoardMemoryProfile(int internalSramBytes, int defaultArenaBytes, int maxArenaBytes,
                                 int defaultStackBytes, int maxStackBytes, MemoryAuthority authority,
                                 OptionalInt psramBytes) {

    public static final int MIN_ARENA_BYTES = RuntimeConfig.MIN_ARENA_BYTES;
    public static final int MIN_STACK_BYTES = RuntimeConfig.MIN_STACK_BYTES;

    public BoardMemoryProfile {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(psramBytes, "psramBytes");
        requireMultipleOf8("internalSramBytes", internalSramBytes);
        requireMultipleOf8("defaultArenaBytes", defaultArenaBytes);
        requireMultipleOf8("maxArenaBytes", maxArenaBytes);
        requireMultipleOf8("defaultStackBytes", defaultStackBytes);
        requireMultipleOf8("maxStackBytes", maxStackBytes);
        if (defaultArenaBytes < MIN_ARENA_BYTES || defaultArenaBytes > maxArenaBytes
                || maxArenaBytes >= internalSramBytes) {
            throw new IllegalArgumentException("Arena profile must satisfy " + MIN_ARENA_BYTES
                    + " <= default <= max < internal SRAM, not " + defaultArenaBytes + " <= " + maxArenaBytes
                    + " < " + internalSramBytes);
        }
        if (defaultStackBytes < MIN_STACK_BYTES || defaultStackBytes > maxStackBytes) {
            throw new IllegalArgumentException("Stack profile must satisfy " + MIN_STACK_BYTES
                    + " <= default <= max, not " + defaultStackBytes + " <= " + maxStackBytes);
        }
    }

    private static void requireMultipleOf8(String name, int bytes) {
        if (bytes <= 0 || bytes % 8 != 0) {
            throw new IllegalArgumentException(name + " must be a positive multiple of 8, not " + bytes);
        }
    }

    /** The RAM the maximum arena leaves for the core, stacks and libraries: derived, never stored. */
    public int reservedBytes() {
        return internalSramBytes - maxArenaBytes;
    }

    /**
     * Applies the requested configuration to this board: unset sizes take the defaults, explicit sizes must stay within
     * the maximums, and the carrier override must not exceed the core's carriers.
     */
    public ResolvedRuntimeConfig resolve(RuntimeConfig requested, Board board) {
        int arena = defaultArenaBytes;
        Source arenaSource = Source.BOARD_DEFAULT;
        if (requested.arenaBytes().isPresent()) {
            arena = requested.arenaBytes().getAsInt();
            arenaSource = Source.OVERRIDE;
            if (arena > maxArenaBytes) {
                throw new CompileException(arenaMessage(board, arena));
            }
        }
        int stack = defaultStackBytes;
        Source stackSource = Source.BOARD_DEFAULT;
        OptionalInt requestedStack = requested.threadStackBytes();
        if (requestedStack.isPresent()) {
            stack = requestedStack.getAsInt();
            stackSource = Source.OVERRIDE;
            if (stack > maxStackBytes) {
                throw new CompileException(stackMessage(board, stack));
            }
        }
        int carriers = board.core().carrierCount();
        if (requested.carrierCountOverride().isPresent()) {
            carriers = requested.carrierCountOverride().getAsInt();
            if (carriers > board.core().carrierCount()) {
                throw new CompileException("juno.carriers=" + carriers + " exceeds this board's "
                        + board.core().carrierCount() + " carrier(s)");
            }
        }
        return new ResolvedRuntimeConfig(board, arena, arenaSource, stack, stackSource, carriers);
    }

    private String arenaMessage(Board board, int arena) {
        return "The " + board.displayName() + " has too little RAM for a " + arena + "-byte arena; juno.Xmx can be at"
                + " most " + maxArenaBytes + " bytes there (" + internalSramBytes + " bytes internal SRAM, "
                + reservedBytes() + " bytes reserved for the core, stacks and libraries; default "
                + defaultArenaBytes + ")." + " The arena never uses PSRAM." + authoritySentence();
    }

    private String stackMessage(Board board, int stack) {
        String heapSlots = board.core().carrierCount() > 1
                ? " Each running thread slot takes its stack from the internal heap." : "";
        return "The " + board.displayName() + " has too little RAM for " + stack + "-byte task stacks; juno.Xss can be"
                + " at most " + maxStackBytes + " bytes there." + heapSlots;
    }

    private String authoritySentence() {
        return switch (authority) {
            case LINKER -> "";
            case RUNTIME_HEAP -> " Below that maximum, the run-time heap decides whether the arena fits.";
            case LOADER_POOL -> " Below that maximum, the loader pool decides whether the arena fits, and the"
                    + " program's code shares it.";
        };
    }
}
