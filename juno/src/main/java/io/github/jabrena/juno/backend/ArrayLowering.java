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
        asm.emitLoadImmediate(output, "r1", elementSize);
        output.append("    bl juno_alloc\n");
        asm.store(output, frame, "r0", newArray.target());
    }

    /** Allocates the outer (row-handle) array, then each leaf row. */
    void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        if (array.dimensions().size() != 2) {
            throw CortexM4AsmBackend.unsupported("array nesting deeper than 2 dimensions: " + array.dimensions());
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
            asm.emitLoadImmediate(output, "r1", elementSize);
            output.append("    bl juno_alloc\n");
            output.append("    mov r1, r0\n");
            asm.load(output, frame, "r0", array.target());
            output.append("    str r1, [r0, #").append(index * AsmEmitter.WORD).append("]\n");
        }
    }

    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        asm.load(output, frame, "r0", load.array());
        asm.load(output, frame, "r1", load.index());
        String loadInstruction = switch (load.elementType()) {
            case BYTE -> "ldrsb r2, [r0, r1]\n";
            case CHAR -> "lsls r1, r1, #1\n    ldrh r2, [r0, r1]\n";
            case SHORT -> "lsls r1, r1, #1\n    ldrsh r2, [r0, r1]\n";
            case INT, REFERENCE -> "lsls r1, r1, #2\n    ldr r2, [r0, r1]\n";
            case LONG, FLOAT, DOUBLE -> throw CortexM4AsmBackend.unsupported("array element type " + load.elementType());
        };
        output.append("    ").append(loadInstruction);
        asm.store(output, frame, "r2", load.target());
    }

    void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store) {
        asm.load(output, frame, "r0", store.array());
        asm.load(output, frame, "r1", store.index());
        asm.load(output, frame, "r2", store.value());
        String storeInstruction = switch (store.elementType()) {
            case BYTE -> "strb r2, [r0, r1]\n";
            case CHAR, SHORT -> "lsls r1, r1, #1\n    strh r2, [r0, r1]\n";
            case INT, REFERENCE -> "lsls r1, r1, #2\n    str r2, [r0, r1]\n";
            case LONG, FLOAT, DOUBLE -> throw CortexM4AsmBackend.unsupported("array element type " + store.elementType());
        };
        output.append("    ").append(storeInstruction);
    }

    void emitBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.BoundsCheck check) {
        String panicLabel = asm.newLabel(".Lbcpanic");
        String okLabel = asm.newLabel(".Lbcok");
        asm.load(output, frame, "r0", check.index());
        asm.emitLoadImmediate(output, "r1", check.length());
        output.append("    cmp r0, #0\n")
                .append("    blt ").append(panicLabel).append('\n')
                .append("    cmp r0, r1\n")
                .append("    bge ").append(panicLabel).append('\n')
                .append("    b ").append(okLabel).append('\n')
                .append(panicLabel).append(":\n")
                .append("    bl juno_panic\n")
                .append(okLabel).append(":\n");
    }

    private int elementSize(ArrayElementType type) {
        return switch (type) {
            case BYTE -> 1;
            case CHAR, SHORT -> 2;
            case INT, REFERENCE -> 4;
            case LONG, FLOAT, DOUBLE -> throw CortexM4AsmBackend.unsupported("array element type " + type);
        };
    }
}
