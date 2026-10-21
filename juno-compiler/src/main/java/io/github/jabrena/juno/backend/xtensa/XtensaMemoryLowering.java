package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.ProgramLayout;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.ArrayList;
import java.util.List;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.C;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.WORD;

/**
 * Arena objects, their fields, static fields and arrays, with the same layout as on the other targets: an object is
 * its words (a type id first when the program dispatches on it), an array's elements follow a length word, and a
 * {@code long}/{@code double} is a low then a high word.
 */
final class XtensaMemoryLowering {
    /** Bytes of the element count stored in front of an array's first element. */
    static final int LENGTH_BYTES = 4;
    /** Largest offset {@code l32i}/{@code s32i} encode. */
    private static final int MAX_WORD_OFFSET = 1020;

    private final XtensaEmitter xtensa;
    private final ProgramLayout layout;

    XtensaMemoryLowering(XtensaEmitter xtensa, ProgramLayout layout) {
        this.xtensa = xtensa;
        this.layout = layout;
    }

    void emitNewObject(StringBuilder output, FrameLayout frame, IrInstruction.NewObject object) {
        emitNewObject(output, frame, object.className(), object.target());
    }

    /** Allocates a zero-filled {@code className} object, tags it with its type id and stores it to {@code target}. */
    void emitNewObject(StringBuilder output, FrameLayout frame, String className, Value target) {
        allocate(output, layout.objectSize(className), WORD);
        int classId = layout.throwableClasses().indexOf(className);
        Integer typeId = layout.objectTypeId(className);
        if (classId >= 0) {
            // juno_alloc zero-fills, so the message word already starts out null.
            xtensa.loadImmediate(output, B, classId);
            xtensa.storeWordAt(output, B, C, ThrowableTypes.CLASS_ID_OFFSET);
        } else if (typeId != null) {
            xtensa.loadImmediate(output, B, typeId);
            xtensa.storeWordAt(output, B, C, 0);
        }
        xtensa.store(output, frame, C, target);
    }

    /** {@code juno_alloc(size, alignment)}; the zero-filled block comes back in {@code a10}. */
    private void allocate(StringBuilder output, int size, int alignment) {
        xtensa.loadImmediate(output, "a10", size);
        xtensa.loadImmediate(output, "a11", alignment);
        xtensa.call(output, "juno_alloc");
    }

    void emitLoadField(StringBuilder output, FrameLayout frame, IrInstruction.LoadField load) {
        xtensa.load(output, frame, A, load.receiver());
        loadFrom(output, frame, layout.fieldOffset(load.field()), load.target());
    }

    void emitStoreField(StringBuilder output, FrameLayout frame, IrInstruction.StoreField store) {
        xtensa.load(output, frame, A, store.receiver());
        storeTo(output, frame, layout.fieldOffset(store.field()), store.value());
    }

    void emitLoadStatic(StringBuilder output, FrameLayout frame, IrInstruction.LoadStatic load) {
        xtensa.loadAddress(output, A, layout.staticSymbol(load.field()));
        loadFrom(output, frame, 0, load.target());
    }

    void emitStoreStatic(StringBuilder output, FrameLayout frame, IrInstruction.StoreStatic store) {
        xtensa.loadAddress(output, A, layout.staticSymbol(store.field()));
        storeTo(output, frame, 0, store.value());
    }

    /** Loads the field {@code offset} bytes into the object {@code a8} points at. */
    private void loadFrom(StringBuilder output, FrameLayout frame, int offset, Value target) {
        int reachable = reach(output, offset, target.type().isWide());
        xtensa.loadWordAt(output, B, A, reachable);
        if (target.type().isWide()) {
            xtensa.loadWordAt(output, C, A, reachable + WORD);
            xtensa.store64(output, frame, B, C, target);
        } else {
            xtensa.store(output, frame, B, target);
        }
    }

    /** Stores {@code value} into the field {@code offset} bytes into the object {@code a8} points at. */
    private void storeTo(StringBuilder output, FrameLayout frame, int offset, Value value) {
        int reachable = reach(output, offset, value.type().isWide());
        if (value.type().isWide()) {
            xtensa.load64(output, frame, B, C, value);
            xtensa.storeWordAt(output, B, A, reachable);
            xtensa.storeWordAt(output, C, A, reachable + WORD);
        } else {
            xtensa.load(output, frame, B, value);
            xtensa.storeWordAt(output, B, A, reachable);
        }
    }

    /** Moves {@code a8} forward when {@code offset} is beyond {@code l32i}'s reach; returns the offset left to use. */
    private int reach(StringBuilder output, int offset, boolean wide) {
        if (offset + (wide ? WORD : 0) <= MAX_WORD_OFFSET) {
            return offset;
        }
        xtensa.loadImmediate(output, C, offset);
        output.append("    add a8, a8, a10\n");
        return 0;
    }

    void emitIntArrayConst(StringBuilder output, FrameLayout frame, IrInstruction.IntArrayConst array) {
        xtensa.loadAddress(output, A, layout.intArraySymbol(array));
        xtensa.store(output, frame, A, array.target());
    }

    void emitConstantTableRef(StringBuilder output, FrameLayout frame, IrInstruction.ConstantTableRef table) {
        xtensa.loadAddress(output, A, layout.constantTableSymbol(table.table().field()));
        xtensa.store(output, frame, A, table.target());
    }

    void emitNewArray(StringBuilder output, FrameLayout frame, IrInstruction.NewArray newArray) {
        allocateArray(output, newArray.length(), elementSize(newArray.elementType()));
        xtensa.store(output, frame, C, newArray.target());
    }

    /** Allocates {@code length} elements behind a stored length word; {@code a10} comes back at the first element. */
    private void allocateArray(StringBuilder output, int length, int elementSize) {
        allocate(output, LENGTH_BYTES + elementSize * length, Math.min(elementSize, WORD));
        xtensa.loadImmediate(output, B, length);
        xtensa.storeWordAt(output, B, C, 0);
        output.append("    addi a10, a10, ").append(LENGTH_BYTES).append('\n');
    }

    void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        allocateArray(output, array.dimensions().getFirst(), WORD);
        xtensa.store(output, frame, C, array.target());
        emitNestedArrays(output, frame, array, List.of());
    }

    /** Materializes each reference array in depth-first order, resolving its parent from the root after every call. */
    private void emitNestedArrays(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array,
                                  List<Integer> parentIndexes) {
        int parentDimension = parentIndexes.size();
        if (parentDimension == array.dimensions().size() - 1) {
            return;
        }
        int childLength = array.dimensions().get(parentDimension + 1);
        int childElementSize = parentDimension + 1 == array.dimensions().size() - 1
                ? elementSize(array.leafType()) : WORD;
        for (int index = 0; index < array.dimensions().get(parentDimension); index++) {
            allocateArray(output, childLength, childElementSize);
            loadNestedArray(output, frame, array.target(), parentIndexes);
            xtensa.storeWordAt(output, C, A, index * WORD);
            List<Integer> childIndexes = new ArrayList<>(parentIndexes);
            childIndexes.add(index);
            emitNestedArrays(output, frame, array, List.copyOf(childIndexes));
        }
    }

    /** Resolves a reference array through its known compile-time indexes into {@code a8}. */
    private void loadNestedArray(StringBuilder output, FrameLayout frame, Value root, List<Integer> indexes) {
        xtensa.load(output, frame, A, root);
        for (int index : indexes) {
            xtensa.loadWordAt(output, B, A, index * WORD);
            output.append("    mov a8, a9\n");
        }
    }

    /** Leaves the element's address in {@code a8}, from the array and index values. */
    private void elementAddress(StringBuilder output, FrameLayout frame, Value array, Value index,
                                ArrayElementType type) {
        xtensa.load(output, frame, A, array);
        xtensa.load(output, frame, B, index);
        switch (elementSize(type)) {
            case 1 -> output.append("    add a8, a8, a9\n");
            case 2 -> output.append("    addx2 a8, a9, a8\n");
            case 4 -> output.append("    addx4 a8, a9, a8\n");
            default -> output.append("    addx8 a8, a9, a8\n");
        }
    }

    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        elementAddress(output, frame, load.array(), load.index(), load.elementType());
        switch (load.elementType()) {
            case BYTE -> output.append("    l8ui a9, a8, 0\n    sext a9, a9, 7\n");
            case CHAR -> output.append("    l16ui a9, a8, 0\n");
            case SHORT -> output.append("    l16si a9, a8, 0\n");
            case INT, REFERENCE, FLOAT -> xtensa.loadWordAt(output, B, A, 0);
            case LONG, DOUBLE -> {
                xtensa.loadWordAt(output, B, A, 0);
                xtensa.loadWordAt(output, C, A, WORD);
                xtensa.store64(output, frame, B, C, load.target());
                return;
            }
        }
        xtensa.store(output, frame, B, load.target());
    }

    void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store) {
        elementAddress(output, frame, store.array(), store.index(), store.elementType());
        if (store.value().type().isWide()) {
            xtensa.load64(output, frame, B, C, store.value());
            xtensa.storeWordAt(output, B, A, 0);
            xtensa.storeWordAt(output, C, A, WORD);
            return;
        }
        xtensa.load(output, frame, B, store.value());
        switch (store.elementType()) {
            case BYTE -> output.append("    s8i a9, a8, 0\n");
            case CHAR, SHORT -> output.append("    s16i a9, a8, 0\n");
            default -> xtensa.storeWordAt(output, B, A, 0);
        }
    }

    /**
     * Panics unless {@code 0 <= index < length}: one unsigned comparison covers both ends, since a negative index
     * reads as a huge unsigned number; a constant index is decided here.
     */
    void emitBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.BoundsCheck check) {
        Integer constant = frame.constant(check.index());
        if (constant != null) {
            if (constant < 0 || constant >= check.length()) {
                xtensa.call(output, "juno_panic");
            }
            return;
        }
        xtensa.load(output, frame, A, check.index());
        xtensa.loadImmediate(output, B, check.length());
        panicUnlessBelow(output);
    }

    /** Like {@link #emitBoundsCheck}, against the length word stored in front of the array's elements. */
    void emitArrayBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.ArrayBoundsCheck check) {
        xtensa.load(output, frame, B, check.array());
        output.append("    addi a9, a9, -").append(LENGTH_BYTES).append('\n');
        xtensa.loadWordAt(output, B, B, 0);
        xtensa.load(output, frame, A, check.index());
        panicUnlessBelow(output);
    }

    /** Panics unless {@code a8 < a9}, unsigned. */
    private void panicUnlessBelow(StringBuilder output) {
        String ok = xtensa.newLabel(".LboundsOk");
        output.append("    bltu a8, a9, ").append(ok).append('\n');
        xtensa.call(output, "juno_panic");
        output.append(ok).append(":\n");
    }

    void emitArrayLength(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLength length) {
        xtensa.load(output, frame, A, length.array());
        output.append("    addi a8, a8, -").append(LENGTH_BYTES).append('\n');
        xtensa.loadWordAt(output, A, A, 0);
        xtensa.store(output, frame, A, length.target());
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
