package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.ir.Value;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.C;

/**
 * Field accesses that two carriers on two cores must not tear or reorder: a {@code long}/{@code double} goes through
 * the runtime's spinlocked {@code juno_wide_load}/{@code juno_wide_store}, and a {@code volatile} word access is
 * preceded by the core's memory barrier ({@code memw}). With one carrier it emits nothing and handles nothing, so
 * the plain accesses stay as they were.
 */
final class XtensaCarrierAccess {
    private final XtensaEmitter xtensa;
    private final boolean dualCarrier;
    private final String memoryBarrier;

    XtensaCarrierAccess(XtensaEmitter xtensa, boolean dualCarrier, String memoryBarrierInstruction) {
        this.xtensa = xtensa;
        this.dualCarrier = dualCarrier;
        this.memoryBarrier = dualCarrier && !memoryBarrierInstruction.isEmpty()
                ? "    " + memoryBarrierInstruction + "\n" : "";
    }

    /** Loads the wide field {@code a8} points at into {@code target}; false when the plain two-word load applies. */
    boolean loadWide(StringBuilder output, FrameLayout frame, Value target) {
        if (!dualCarrier || !target.type().isWide()) return false;
        output.append("    mov a10, ").append(A).append('\n');
        xtensa.call(output, "juno_wide_load");
        xtensa.store64(output, frame, "a10", "a11", target);
        return true;
    }

    /** Stores the wide value held in {@code a9}/{@code a10} into the field {@code a8} points at; false when plain. */
    boolean storeWide(StringBuilder output) {
        if (!dualCarrier) return false;
        output.append("    mov a12, ").append(C).append('\n')
                .append("    mov a11, ").append(B).append('\n')
                .append("    mov a10, ").append(A).append('\n')
                .append("    call8 juno_wide_store\n");
        return true;
    }

    /** The barrier ahead of a word access to a {@code volatile} field, else nothing. */
    String barrier(boolean volatileField) {
        return volatileField ? memoryBarrier : "";
    }
}
