package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

/** Instance and static field loads/stores; a {@code long}/{@code double} field is two words, low word first. */
final class FieldAccessLowering {
    private final AsmEmitter asm;
    private final ProgramLayout layout;

    FieldAccessLowering(AsmEmitter asm, ProgramLayout layout) {
        this.asm = asm;
        this.layout = layout;
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
                    .append("    str r2, [r1]\n")
                    .append("    str r3, [r1, #").append(AsmEmitter.WORD).append("]\n");
            return;
        }
        asm.load(output, frame, "r0", store.value());
        output.append("    ldr r1, =").append(symbol).append('\n')
                .append("    str r0, [r1]\n");
    }

    private void loadFrom(StringBuilder output, FrameLayout frame, String base, int offset, Value target) {
        output.append("    ldr r1, [").append(base).append(", #").append(offset).append("]\n");
        if (FrameLayout.isWide(target.type())) {
            output.append("    ldr r2, [").append(base).append(", #").append(offset + AsmEmitter.WORD).append("]\n");
            asm.store64(output, frame, "r1", "r2", target);
        } else {
            asm.store(output, frame, "r1", target);
        }
    }

    /** {@code base} must not be r1/r2, which carry the value. */
    private void storeTo(StringBuilder output, FrameLayout frame, String base, int offset, Value value) {
        if (FrameLayout.isWide(value.type())) {
            asm.load64(output, frame, "r1", "r2", value);
            output.append("    str r1, [").append(base).append(", #").append(offset).append("]\n");
            output.append("    str r2, [").append(base).append(", #").append(offset + AsmEmitter.WORD).append("]\n");
        } else {
            asm.load(output, frame, "r1", value);
            output.append("    str r1, [").append(base).append(", #").append(offset).append("]\n");
        }
    }
}
