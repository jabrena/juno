package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;

/** Arena-allocated arrays: allocation, element loads/stores, and bounds checks. */
final class ArrayLowering {
    private final AsmEmitter asm;

    ArrayLowering(AsmEmitter asm) {
        this.asm = asm;
    }

    void emitNewArray(StringBuilder output, FrameLayout frame, IrInstruction.NewArray newArray) {
        int elementSize = elementSize(newArray.elementType());
        asm.emitLoadImmediate(output, "r0", elementSize * newArray.length());
        asm.emitLoadImmediate(output, "r1", Math.min(elementSize, AsmEmitter.WORD));
        output.append("    bl juno_alloc\n");
        asm.store(output, frame, "r0", newArray.target());
    }

    /** Allocates the outer (row-handle) array, then each leaf row. */
    void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        if (array.dimensions().size() != 2) {
            throw Thumb2AsmBackend.unsupported("array nesting deeper than 2 dimensions: " + array.dimensions());
        }
        int outerCount = array.dimensions().get(0);
        int innerCount = array.dimensions().get(1);
        int elementSize = elementSize(array.leafType());
        asm.emitLoadImmediate(output, "r0", outerCount * AsmEmitter.WORD);
        asm.emitLoadImmediate(output, "r1", AsmEmitter.WORD);
        output.append("    bl juno_alloc\n");
        asm.store(output, frame, "r0", array.target());
        for (int index = 0; index < outerCount; index++) {
            asm.emitLoadImmediate(output, "r0", innerCount * elementSize);
            asm.emitLoadImmediate(output, "r1", Math.min(elementSize, AsmEmitter.WORD));
            output.append("    bl juno_alloc\n");
            output.append("    mov r1, r0\n");
            asm.load(output, frame, "r0", array.target());
            output.append("    str r1, [r0, #").append(index * AsmEmitter.WORD).append("]\n");
        }
    }

    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        asm.load(output, frame, "r0", load.array());
        asm.load(output, frame, "r1", load.index());
        if (FrameLayout.isWide(load.target().type())) {
            output.append("    lsls r1, r1, #3\n")
                    .append("    add r0, r0, r1\n")
                    .append("    ldr r2, [r0]\n")
                    .append("    ldr r3, [r0, #").append(AsmEmitter.WORD).append("]\n");
            asm.store64(output, frame, "r2", "r3", load.target());
            return;
        }
        String loadInstruction = switch (load.elementType()) {
            case BYTE -> "ldrsb r2, [r0, r1]\n";
            case CHAR -> "lsls r1, r1, #1\n    ldrh r2, [r0, r1]\n";
            case SHORT -> "lsls r1, r1, #1\n    ldrsh r2, [r0, r1]\n";
            case INT, REFERENCE, FLOAT -> "lsls r1, r1, #2\n    ldr r2, [r0, r1]\n";
            case LONG, DOUBLE -> throw new IllegalStateException("wide elements handled above");
        };
        output.append("    ").append(loadInstruction);
        asm.store(output, frame, "r2", load.target());
    }

    void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store) {
        asm.load(output, frame, "r0", store.array());
        asm.load(output, frame, "r1", store.index());
        if (FrameLayout.isWide(store.value().type())) {
            asm.load64(output, frame, "r2", "r3", store.value());
            output.append("    lsls r1, r1, #3\n")
                    .append("    add r0, r0, r1\n")
                    .append("    str r2, [r0]\n")
                    .append("    str r3, [r0, #").append(AsmEmitter.WORD).append("]\n");
            return;
        }
        asm.load(output, frame, "r2", store.value());
        String storeInstruction = switch (store.elementType()) {
            case BYTE -> "strb r2, [r0, r1]\n";
            case CHAR, SHORT -> "lsls r1, r1, #1\n    strh r2, [r0, r1]\n";
            case INT, REFERENCE, FLOAT -> "lsls r1, r1, #2\n    str r2, [r0, r1]\n";
            case LONG, DOUBLE -> throw new IllegalStateException("wide elements handled above");
        };
        output.append("    ").append(storeInstruction);
    }

    /**
     * Panics unless {@code 0 <= index < length}. One unsigned comparison covers both ends, since a negative index
     * reads as a huge unsigned number; a constant index is decided here.
     */
    void emitBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.BoundsCheck check) {
        Integer constant = frame.constant(check.index());
        if (constant != null) {
            if (constant < 0 || constant >= check.length()) {
                output.append("    bl juno_panic\n");
            }
            return;
        }
        String okLabel = asm.newLabel(".Lbcok");
        asm.load(output, frame, "r0", check.index());
        if (AsmEmitter.isModifiedImmediate(check.length())) {
            output.append("    cmp r0, #").append(check.length()).append('\n');
        } else {
            asm.emitLoadImmediate(output, "r1", check.length());
            output.append("    cmp r0, r1\n");
        }
        output.append("    blo ").append(okLabel).append('\n')
                .append("    bl juno_panic\n")
                .append(okLabel).append(":\n");
    }

    private int elementSize(ArrayElementType type) {
        return switch (type) {
            case BYTE -> 1;
            case CHAR, SHORT -> 2;
            case INT, REFERENCE, FLOAT -> 4;
            case LONG, DOUBLE -> 8;
        };
    }
}
