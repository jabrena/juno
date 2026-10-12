package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;

import java.util.List;

/**
 * Arena-allocated arrays: allocation, element loads/stores, and bounds checks. An array reference points at the first
 * element; the element count is stored in the {@link #LENGTH_BYTES} bytes in front of it.
 */
final class ArrayLowering {
    /** Width of the length word stored in front of an array's elements. */
    static final int LENGTH_BYTES = 4;

    /**
     * Checks the index in r1 against the stored length of the array in r0, keeping r0-r3 and clobbering only r12
     * and the flags. One shared copy keeps each check to a single 4-byte call: on the UNO Q the code is loaded into
     * the same RAM as the arena, so an inline check at every access of a large program costs kilobytes of arena.
     * Unsigned, so a negative index fails too; a failure reports the index, the length and the call site.
     */
    private static final String CHECK_HELPER = """

                .type juno_array_check, %function
            juno_array_check:
                ldr r12, [r0, #-4]
                cmp r1, r12
                it lo
                bxlo lr
                mov r0, r1
                mov r1, r12
                mov r2, lr
                bl juno_array_index_panic
            """;

    private final AsmEmitter asm;
    private boolean checkHelperUsed;

    ArrayLowering(AsmEmitter asm) {
        this.asm = asm;
    }

    /** Emits the shared {@code juno_array_check}, once, after the program's methods, when any check calls it. */
    void emitHelpers(StringBuilder output) {
        if (checkHelperUsed) {
            output.append(CHECK_HELPER);
        }
    }

    /** Calls {@code juno_array_check} with the array in r0 and the index in r1; both survive the call. */
    private void checkStoredLength(StringBuilder output) {
        checkHelperUsed = true;
        output.append("    bl juno_array_check\n");
    }

    void emitNewArray(StringBuilder output, FrameLayout frame, IrInstruction.NewArray newArray) {
        int elementSize = elementSize(newArray.elementType());
        allocateWithLength(output, newArray.length(), elementSize);
        asm.store(output, frame, "r0", newArray.target());
    }

    /** Allocates {@code length} elements behind a stored length word; r0 comes back pointing at the first element. */
    private void allocateWithLength(StringBuilder output, int length, int elementSize) {
        asm.emitLoadImmediate(output, "r0", LENGTH_BYTES + elementSize * length);
        asm.emitLoadImmediate(output, "r1", Math.min(elementSize, AsmEmitter.WORD));
        output.append("    bl juno_alloc\n");
        asm.emitLoadImmediate(output, "r1", length);
        output.append("    str r1, [r0]\n")
                .append("    adds r0, r0, #").append(LENGTH_BYTES).append('\n');
    }

    /** Allocates the outer (row-handle) array, then each leaf row. */
    void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        if (array.dimensions().size() != 2) {
            throw Architecture.THUMB2.unsupported("array nesting deeper than 2 dimensions: " + array.dimensions());
        }
        int outerCount = array.dimensions().get(0);
        int innerCount = array.dimensions().get(1);
        int elementSize = elementSize(array.leafType());
        allocateWithLength(output, outerCount, AsmEmitter.WORD);
        asm.store(output, frame, "r0", array.target());
        for (int index = 0; index < outerCount; index++) {
            allocateWithLength(output, innerCount, elementSize);
            output.append("    mov r1, r0\n");
            asm.load(output, frame, "r0", array.target());
            output.append("    str r1, [r0, #").append(index * AsmEmitter.WORD).append("]\n");
        }
    }

    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        emitArrayLoad(output, frame, load, false);
    }

    /** {@code checked}: first checks the index against the array's stored length, fusing an ArrayBoundsCheck. */
    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load, boolean checked) {
        asm.load(output, frame, "r0", load.array());
        asm.load(output, frame, "r1", load.index());
        if (checked) {
            checkStoredLength(output);
        }
        if (load.target().type().isWide()) {
            output.append("""
                        lsls r1, r1, #3
                        add r0, r0, r1
                        ldr r2, [r0]
                    """)
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
        emitArrayStore(output, frame, store, false);
    }

    /** {@code checked}: first checks the index against the array's stored length, fusing an ArrayBoundsCheck. */
    void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store, boolean checked) {
        asm.load(output, frame, "r0", store.array());
        asm.load(output, frame, "r1", store.index());
        if (checked) {
            checkStoredLength(output);
        }
        if (store.value().type().isWide()) {
            asm.load64(output, frame, "r2", "r3", store.value());
            output.append("""
                        lsls r1, r1, #3
                        add r0, r0, r1
                        str r2, [r0]
                    """)
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
        if (asm.isModifiedImmediate(check.length())) {
            output.append("    cmp r0, #").append(check.length()).append('\n');
        } else {
            asm.emitLoadImmediate(output, "r1", check.length());
            output.append("    cmp r0, r1\n");
        }
        output.append("    blo ").append(okLabel).append('\n')
                .append("    bl juno_panic\n")
                .append(okLabel).append(":\n");
    }

    /** {@code target = array.length}: one load of the word in front of the elements. */
    void emitArrayLength(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLength length) {
        asm.load(output, frame, "r0", length.array());
        output.append("    ldr r0, [r0, #-").append(LENGTH_BYTES).append("]\n");
        asm.store(output, frame, "r0", length.target());
    }

    /**
     * Like {@link #emitBoundsCheck}, against the length word stored with the array. Used only when the check is not
     * directly followed by the access it guards, which then carries the check itself.
     */
    void emitArrayBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.ArrayBoundsCheck check) {
        asm.load(output, frame, "r0", check.array());
        asm.load(output, frame, "r1", check.index());
        checkStoredLength(output);
    }

    /**
     * When {@code instructions[index]} is a length-word check directly followed by the load or store it guards,
     * emits the two as one access, which already holds the array and the index in registers, and returns
     * {@code true}; the caller then skips the access.
     */
    boolean emitGuardedAccess(StringBuilder output, FrameLayout frame, List<IrInstruction> instructions,
                              int index) {
        if (!(instructions.get(index) instanceof IrInstruction.ArrayBoundsCheck check)
                || index + 1 >= instructions.size()) {
            return false;
        }
        switch (instructions.get(index + 1)) {
            case IrInstruction.ArrayLoad load when load.array().equals(check.array())
                    && load.index().equals(check.index()) -> emitArrayLoad(output, frame, load, true);
            case IrInstruction.ArrayStore store when store.array().equals(check.array())
                    && store.index().equals(check.index()) -> emitArrayStore(output, frame, store, true);
            default -> {
                return false;
            }
        }
        return true;
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
