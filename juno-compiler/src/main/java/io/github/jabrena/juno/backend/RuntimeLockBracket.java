package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.intrinsic.Intrinsic;

/**
 * Brackets a shim call from generated code with the dual-carrier runtime lock, and orders a monitor's accesses with a
 * memory barrier: after {@code monitorenter}, before {@code monitorexit}. With one carrier it emits nothing, so
 * single-carrier output is unchanged.
 */
final class RuntimeLockBracket {
    private final IntrinsicTarget target;
    private final boolean locked;

    RuntimeLockBracket(IntrinsicTarget target, boolean locked) {
        this.target = target;
        this.locked = locked;
    }

    /** Before a shim call: takes the runtime lock. */
    void enter(StringBuilder output) {
        if (locked) target.emitRuntimeLockEnter(output);
    }

    /** After a shim call: releases the runtime lock. */
    void exit(StringBuilder output) {
        if (locked) target.emitRuntimeLockExit(output);
    }

    /** Before the shim call for {@code intrinsic}: takes the lock, then a barrier ahead of a monitor exit. */
    void enter(StringBuilder output, Intrinsic intrinsic) {
        enter(output);
        if (locked && intrinsic == Intrinsic.MONITOR_EXIT) target.emitMemoryBarrier(output);
    }

    /** After the shim call for {@code intrinsic}: a barrier after a monitor enter, then releases the lock. */
    void exit(StringBuilder output, Intrinsic intrinsic) {
        if (locked && intrinsic == Intrinsic.MONITOR_ENTER) target.emitMemoryBarrier(output);
        exit(output);
    }
}
