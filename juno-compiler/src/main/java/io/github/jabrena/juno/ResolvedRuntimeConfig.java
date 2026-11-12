package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Board;

/**
 * The runtime configuration as one board runs it: every size is the board's default or an explicit override that the
 * board accepted, and the carrier count is the one the build uses. Backends, shims, analyzers and reports consume only
 * this type, so an unresolved request cannot reach them.
 *
 * @param board the board the sizes were resolved for
 * @param arenaBytes the arena in bytes
 * @param arenaSource whether {@link #arenaBytes} is the board's default or a {@code juno.Xmx} override
 * @param threadStackBytes the carrier (on AVR, extra task) stack in bytes
 * @param stackSource whether {@link #threadStackBytes} is the board's default or a {@code juno.Xss} override
 * @param carrierCount the carriers the program has when it runs threads
 */
public record ResolvedRuntimeConfig(Board board, int arenaBytes, Source arenaSource, int threadStackBytes,
                                    Source stackSource, int carrierCount) {

    /** Where a resolved size came from. An explicit value is an override even when it equals the board default. */
    public enum Source {
        BOARD_DEFAULT,
        OVERRIDE
    }

    /** The carriers a program runs on: the configured count when it has threads, else the one carrier. */
    public int carrierCount(boolean threads) {
        return threads ? carrierCount : 1;
    }
}
