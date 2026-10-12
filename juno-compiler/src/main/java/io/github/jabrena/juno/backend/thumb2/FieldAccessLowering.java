package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.ProgramLayout;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

/**
 * Instance and static field loads/stores; a {@code long}/{@code double} field is two words, low word first. Its two
 * accesses run with interrupts masked in a program with threads, so a preempting thread never sees one word of a store without the other: the
 * atomicity the JLS (17.7) requires of a {@code volatile} {@code long}/{@code double}, given to every such field.
 */
final class FieldAccessLowering {
    /** Saves PRIMASK in r12 (scratch) and masks interrupts; {@link #RESTORE_INTERRUPTS} puts PRIMASK back. */
    private static final String MASK_INTERRUPTS = "    mrs r12, primask\n    cpsid i\n";
    private static final String RESTORE_INTERRUPTS = "    msr primask, r12\n";

    private final AsmEmitter asm;
    private final ProgramLayout layout;
    private final String maskInterrupts;
    private final String restoreInterrupts;

    FieldAccessLowering(AsmEmitter asm, ProgramLayout layout, boolean preemptible) {
        this.asm = asm;
        this.layout = layout;
        this.maskInterrupts = preemptible ? MASK_INTERRUPTS : "";
        this.restoreInterrupts = preemptible ? RESTORE_INTERRUPTS : "";
    }

    void emitLoadField(StringBuilder output, FrameLayout frame, IrInstruction.LoadField load) {
        asm.load(output, frame, "r0", load.receiver());
        loadFrom(output, frame, "r0", layout.fieldOffset(load.field()), load.target());
    }

    void emitStoreField(StringBuilder output, FrameLayout frame, IrInstruction.StoreField store) {
        asm.load(output, frame, "r0", store.receiver());
        storeTo(output, frame, "r0", layout.fieldOffset(store.field()), store.value());
    }

    void emitLoadStatic(StringBuilder output, FrameLayout frame, IrInstruction.LoadStatic load) {
        output.append("    ldr r0, =").append(layout.staticSymbol(load.field())).append('\n');
        loadFrom(output, frame, "r0", 0, load.target());
    }

    void emitStoreStatic(StringBuilder output, FrameLayout frame, IrInstruction.StoreStatic store) {
        String symbol = layout.staticSymbol(store.field());
        if (FrameLayout.isWide(store.value().type())) {
            asm.load64(output, frame, "r2", "r3", store.value());
            output.append("    ldr r1, =").append(symbol).append('\n')
                    .append(maskInterrupts)
                    .append("    str r2, [r1]\n")
                    .append("    str r3, [r1, #").append(AsmEmitter.WORD).append("]\n")
                    .append(restoreInterrupts);
            return;
        }
        asm.load(output, frame, "r0", store.value());
        output.append("    ldr r1, =").append(symbol).append('\n')
                .append("    str r0, [r1]\n");
    }

    private void loadFrom(StringBuilder output, FrameLayout frame, String base, int offset, Value target) {
        if (FrameLayout.isWide(target.type())) {
            output.append(maskInterrupts)
                    .append("    ldr r1, [").append(base).append(", #").append(offset).append("]\n")
                    .append("    ldr r2, [").append(base).append(", #").append(offset + AsmEmitter.WORD).append("]\n")
                    .append(restoreInterrupts);
            asm.store64(output, frame, "r1", "r2", target);
        } else {
            output.append("    ldr r1, [").append(base).append(", #").append(offset).append("]\n");
            asm.store(output, frame, "r1", target);
        }
    }

    /** {@code base} must not be r1/r2, which carry the value. */
    private void storeTo(StringBuilder output, FrameLayout frame, String base, int offset, Value value) {
        if (FrameLayout.isWide(value.type())) {
            asm.load64(output, frame, "r1", "r2", value);
            output.append(maskInterrupts)
                    .append("    str r1, [").append(base).append(", #").append(offset).append("]\n")
                    .append("    str r2, [").append(base).append(", #").append(offset + AsmEmitter.WORD).append("]\n")
                    .append(restoreInterrupts);
        } else {
            asm.load(output, frame, "r1", value);
            output.append("    str r1, [").append(base).append(", #").append(offset).append("]\n");
        }
    }
}
