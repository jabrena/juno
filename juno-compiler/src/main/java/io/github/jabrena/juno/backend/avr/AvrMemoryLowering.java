package io.github.jabrena.juno.backend.avr;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.ProgramLayout;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.Objects;

/**
 * Objects, arrays and fields on AVR: arena allocation through {@code juno_avr_alloc}, field and element access through
 * Z, and static fields by their absolute address. Fields and elements keep the Thumb-2 layout: four-byte words (a
 * reference's 16 bits sign-extended), eight bytes for a {@code long}/{@code double}.
 */
final class AvrMemoryLowering {
    private static final int A = AvrEmitter.A;
    private static final int B = AvrEmitter.B;

    private final AvrEmitter avr;
    private final ProgramLayout layout;

    AvrMemoryLowering(AvrEmitter avr, ProgramLayout layout) {
        this.avr = avr;
        this.layout = layout;
    }

    void emitLoadField(StringBuilder output, FrameLayout frame, IrInstruction.LoadField load) {
        avr.loadPointer(output, frame, load.receiver());
        loadFromZ(output, frame, layout.fieldOffset(load.field()), load.target());
    }

    void emitStoreField(StringBuilder output, FrameLayout frame, IrInstruction.StoreField store) {
        storeValueToZ(output, frame, store.value(), () -> avr.loadPointer(output, frame, store.receiver()),
                layout.fieldOffset(store.field()));
    }

    void emitLoadStatic(StringBuilder output, FrameLayout frame, IrInstruction.LoadStatic load) {
        String symbol = layout.staticSymbol(load.field());
        loadStatic(output, A, symbol, 0);
        if (FrameLayout.isWide(load.target().type())) {
            loadStatic(output, B, symbol, AvrEmitter.WORD);
            avr.store64(output, frame, A, B, load.target());
        } else {
            avr.store(output, frame, A, load.target());
        }
    }

    void emitStoreStatic(StringBuilder output, FrameLayout frame, IrInstruction.StoreStatic store) {
        String symbol = layout.staticSymbol(store.field());
        if (FrameLayout.isWide(store.value().type())) {
            avr.load64(output, frame, A, B, store.value());
            storeStatic(output, A, symbol, 0);
            storeStatic(output, B, symbol, AvrEmitter.WORD);
        } else {
            avr.load(output, frame, A, store.value());
            storeStatic(output, A, symbol, 0);
        }
    }

    private static void loadStatic(StringBuilder output, int quad, String symbol, int offset) {
        for (int index = 0; index < AvrEmitter.WORD; index++) {
            output.append("    lds ").append(AvrEmitter.r(quad + index)).append(", ").append(symbol).append('+')
                    .append(offset + index).append('\n');
        }
    }

    private static void storeStatic(StringBuilder output, int quad, String symbol, int offset) {
        for (int index = 0; index < AvrEmitter.WORD; index++) {
            output.append("    sts ").append(symbol).append('+').append(offset + index).append(", ")
                    .append(AvrEmitter.r(quad + index)).append('\n');
        }
    }

    /** Loads {@code target} (one or two words) from Z+{@code offset}. */
    private void loadFromZ(StringBuilder output, FrameLayout frame, int offset, Value target) {
        if (FrameLayout.isWide(target.type())) {
            AvrEmitter.loadFromZ(output, B, offset, 2 * AvrEmitter.WORD);
            avr.store64(output, frame, B, A, target);
        } else {
            AvrEmitter.loadFromZ(output, A, offset, AvrEmitter.WORD);
            avr.store(output, frame, A, target);
        }
    }

    /** Loads {@code value} first, since reaching a far frame slot can go through X but never Z, then sets Z. */
    void storeValueToZ(StringBuilder output, FrameLayout frame, Value value, Runnable pointZ, int offset) {
        if (FrameLayout.isWide(value.type())) {
            avr.load64(output, frame, B, A, value);
            pointZ.run();
            AvrEmitter.storeToZ(output, B, offset, 2 * AvrEmitter.WORD);
        } else {
            avr.load(output, frame, A, value);
            pointZ.run();
            AvrEmitter.storeToZ(output, A, offset, AvrEmitter.WORD);
        }
    }

    /** {@code bytes} zeroed arena bytes; the reference comes back in A. */
    void emitAllocate(StringBuilder output, int bytes) {
        avr.loadImmediate(output, A, bytes);
        output.append("    call juno_avr_alloc\n");
    }

    void emitNewObject(StringBuilder output, FrameLayout frame, String className, Value target) {
        emitAllocate(output, layout.objectSize(className));
        int classId = layout.throwableClasses().indexOf(className);
        Integer typeId = layout.objectTypeId(className);
        if (classId >= 0 || typeId != null) {
            output.append("    movw r30, r22\n");
            avr.loadImmediate(output, B, classId >= 0 ? classId : Objects.requireNonNull(typeId));
            AvrEmitter.storeToZ(output, B, classId >= 0 ? ThrowableTypes.CLASS_ID_OFFSET : 0, AvrEmitter.WORD);
        }
        avr.store(output, frame, A, target);
    }

    void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        if (array.dimensions().size() != 2) {
            throw AvrAsmBackend.unsupported("array nesting deeper than 2 dimensions: " + array.dimensions());
        }
        int outerCount = array.dimensions().get(0);
        int innerCount = array.dimensions().get(1);
        emitAllocate(output, outerCount * AvrEmitter.WORD);
        avr.store(output, frame, A, array.target());
        for (int index = 0; index < outerCount; index++) {
            emitAllocate(output, innerCount * elementSize(array.leafType()));
            avr.loadPointer(output, frame, array.target());
            AvrEmitter.storeToZ(output, A, index * AvrEmitter.WORD, AvrEmitter.WORD);
        }
    }

    /** Z = array + index * {@code size}; the index's low 16 bits are all a 16-bit address space needs. */
    private void pointAtElement(StringBuilder output, FrameLayout frame, Value array, Value index, int size) {
        avr.load(output, frame, B, index);
        for (int shift = size; shift > 1; shift >>= 1) {
            output.append("    lsl r18\n    rol r19\n");
        }
        avr.loadPointer(output, frame, array);
        output.append("    add r30, r18\n    adc r31, r19\n");
    }

    void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        int size = elementSize(load.elementType());
        pointAtElement(output, frame, load.array(), load.index(), size);
        switch (load.elementType()) {
            case BYTE -> output.append("    ld r22, Z\n    mov r23, r22\n    lsl r23\n    sbc r23, r23\n")
                    .append("    mov r24, r23\n    mov r25, r23\n");
            case CHAR -> output.append("    ld r22, Z\n    ldd r23, Z+1\n    ldi r24, 0\n    ldi r25, 0\n");
            case SHORT -> output.append("    ld r22, Z\n    ldd r23, Z+1\n")
                    .append("    mov r24, r23\n    lsl r24\n    sbc r24, r24\n    mov r25, r24\n");
            case INT, REFERENCE, FLOAT -> AvrEmitter.loadFromZ(output, A, 0, AvrEmitter.WORD);
            case LONG, DOUBLE -> {
                AvrEmitter.loadFromZ(output, B, 0, 2 * AvrEmitter.WORD);
                avr.store64(output, frame, B, A, load.target());
                return;
            }
        }
        avr.store(output, frame, A, load.target());
    }

    void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store) {
        int size = elementSize(store.elementType());
        // The element address goes to the stack slot-free registers first, then the value is loaded around it.
        pointAtElement(output, frame, store.array(), store.index(), size);
        output.append("    movw r20, r30\n");
        if (FrameLayout.isWide(store.value().type())) {
            avr.load64(output, frame, 10, 14, store.value());
            output.append("    movw r30, r20\n");
            AvrEmitter.storeToZ(output, 10, 0, 2 * AvrEmitter.WORD);
            return;
        }
        avr.load(output, frame, A, store.value());
        output.append("    movw r30, r20\n");
        AvrEmitter.storeToZ(output, A, 0, size);
    }

    /** Panics unless {@code 0 <= index < length}: one unsigned comparison covers both ends. */
    void emitBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.BoundsCheck check) {
        Integer constant = frame.constant(check.index());
        if (constant != null) {
            if (constant < 0 || constant >= check.length()) {
                output.append("    call juno_panic\n");
            }
            return;
        }
        String ok = avr.newLabel(".Lbcok");
        avr.load(output, frame, A, check.index());
        avr.loadImmediate(output, B, check.length());
        AvrEmitter.compareQuads(output, A, B);
        output.append("    brlo ").append(ok).append('\n')
                .append("    call juno_panic\n")
                .append(ok).append(":\n");
    }

    static int elementSize(ArrayElementType type) {
        return switch (type) {
            case BYTE -> 1;
            case CHAR, SHORT -> 2;
            case INT, REFERENCE, FLOAT -> 4;
            case LONG, DOUBLE -> 8;
        };
    }
}
