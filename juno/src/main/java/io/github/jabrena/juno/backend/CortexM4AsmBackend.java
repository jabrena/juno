package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Experimental; Blink has been flashed and confirmed on real hardware.</strong> Emits GNU ARM
 * (Cortex-M4, Thumb-2) assembly straight from Juno IR: every reachable method becomes its own
 * function (real {@code bl} calls between them, full AAPCS parameter passing including stack-passed
 * arguments beyond the first four), with branches, {@code switch}, {@code int} arithmetic/comparisons,
 * fixed-size arrays, arena-allocated objects with fields, mutable static fields, GPIO/delay,
 * {@code LedMatrix}, {@code Serial}, and {@code Mouse} support. Anything else (long/float/double,
 * calls Juno can't statically resolve) fails loudly with {@link CompileException} rather than
 * emitting code that looks plausible but was never checked.
 *
 * <h2>Storage model</h2>
 * Unlike a register-allocating backend, every IR {@code Value} and every JVM local variable slot
 * lives in a fixed offset in its method's own stack frame (never in a register across instructions) —
 * see {@link #frameLayout}. This trades code density for a design that can't run out of registers
 * regardless of how many live values a method has, which matters once methods stop being
 * one-block-with-three-intrinsic-calls (see {@code LedMatrixSnake}, whose helper methods take up to 19
 * {@code int} parameters, and {@code LedMatrixAsciiScroll}, whose full-ASCII-font dispatch tree reaches
 * 116 methods). Registers are used only as scratch within a single instruction's codegen. Arena
 * objects and mutable static fields follow the same principle, without needing any class-file metadata
 * this backend doesn't already have: an object's fields are exactly whichever ones some
 * {@code LoadField}/{@code StoreField} in the whole program actually touches, laid out in
 * {@code FieldRef.displayName()} order (mirrors {@link ArduinoCppBackend}'s own object-layout logic);
 * a static field gets one {@code .bss} slot, addressed via {@code ldr Rd,=symbol}.
 *
 * <h2>Runtime shim</h2>
 * Some things the generated code calls aren't free C functions Juno can reach directly:
 * {@code ArduinoLEDMatrix} and {@code Serial} (C++-only objects — {@code ArduinoLEDMatrix}'s methods
 * are inline-only with private timer/frame state, and {@code Serial} is a {@code HardwareSerial}
 * instance with virtual dispatch; neither has a stable symbol to call without knowing its private
 * layout or vtable), and the arena allocator/panic handler (simple, but writing a bump-pointer
 * allocator directly in hand-rolled assembly buys nothing over calling a five-line C function).
 * {@link #generate} returns a small {@code extern "C"} shim with plain wrapper functions for these —
 * compiled once as ordinary C++, so the generated assembly only ever calls plain functions
 * taking/returning plain ints, never a mangled name or a {@code this} pointer.
 *
 * <p>The output is a {@code .S} file (plus the shim {@code .cpp}, always present) meant to sit
 * alongside a tiny {@code .ino} wrapper that declares the entry point's generated function
 * {@code extern "C"} and calls it from {@code setup()}.
 */
public final class CortexM4AsmBackend {
    private static final int WORD = 4;
    /** Bytes {@code push {r4-r11, lr}} reserves — every prologue/epilogue is built around this. */
    private static final int PUSH_BYTES = 9 * WORD;

    /**
     * {@code runtimeShim} is a small {@code extern "C"} C++ source that must be compiled alongside
     * {@code assembly} in the same sketch — see this class's doc.
     */
    public record Output(String assembly, String runtimeShim) {
    }

    private final Map<MethodRef, String> functionLabels = new LinkedHashMap<>();
    private final Map<String, Integer> objectSizes = new LinkedHashMap<>();
    private final Map<FieldRef, Integer> fieldOffsets = new LinkedHashMap<>();
    private final Map<FieldRef, String> staticSymbols = new LinkedHashMap<>();
    private MethodRef entryPoint;
    private String clinitLabel;
    private int labelCounter;
    private boolean usesMouse;

    public Output generate(IrProgram program) {
        entryPoint = program.entryPoint();
        for (int index = 0; index < program.methods().size(); index++) {
            IrMethod method = program.methods().get(index);
            String label = method.reference().equals(entryPoint)
                    ? asmFunctionName(method) : "juno_fn" + index;
            functionLabels.put(method.reference(), label);
            if (method.reference().name().equals("<clinit>")) {
                clinitLabel = label;
            }
        }
        computeLayouts(program);

        StringBuilder output = new StringBuilder();
        output.append("@ Generated by Juno's experimental Cortex-M4 assembly backend. Do not edit.\n")
                .append("@ EXPERIMENTAL: verify on real hardware before trusting a new program shape.\n")
                .append("@ Entry point: ").append(program.entryPoint().displayName()).append('\n')
                .append("    .syntax unified\n")
                .append("    .thumb\n");
        emitStaticStorage(output);
        output.append("    .text\n");
        for (IrMethod method : program.methods()) {
            emitMethod(output, method);
        }
        return new Output(output.toString(), runtimeShim());
    }

    /**
     * Mirrors {@link ArduinoCppBackend#emitObjectLayouts}: an object's fields are exactly the ones
     * some {@code LoadField}/{@code StoreField} in the whole program actually touches (there's no
     * class-file field table available here, only the IR), laid out in {@code FieldRef.displayName()}
     * order — sequential 4-byte {@code int} slots, since every field this backend has seen is one.
     * Also assigns each {@code LoadStatic}/{@code StoreStatic} field a stable {@code .bss} symbol.
     */
    private void computeLayouts(IrProgram program) {
        Map<String, java.util.TreeMap<String, FieldRef>> layouts = new java.util.TreeMap<>();
        for (IrMethod method : program.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    switch (instruction) {
                        case IrInstruction.NewObject object ->
                                layouts.computeIfAbsent(object.className(), unused -> new java.util.TreeMap<>());
                        case IrInstruction.LoadField load -> layouts
                                .computeIfAbsent(load.field().owner(), unused -> new java.util.TreeMap<>())
                                .put(load.field().displayName(), load.field());
                        case IrInstruction.StoreField store -> layouts
                                .computeIfAbsent(store.field().owner(), unused -> new java.util.TreeMap<>())
                                .put(store.field().displayName(), store.field());
                        case IrInstruction.LoadStatic load -> staticSymbols.computeIfAbsent(
                                load.field(), field -> "juno_static_" + sanitize(field.displayName()));
                        case IrInstruction.StoreStatic store -> staticSymbols.computeIfAbsent(
                                store.field(), field -> "juno_static_" + sanitize(field.displayName()));
                        default -> { }
                    }
                }
            }
        }
        for (Map.Entry<String, java.util.TreeMap<String, FieldRef>> layout : layouts.entrySet()) {
            int offset = 0;
            for (FieldRef field : layout.getValue().values()) {
                fieldOffsets.put(field, offset);
                offset += WORD;
            }
            objectSizes.put(layout.getKey(), Math.max(offset, WORD));
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private void emitStaticStorage(StringBuilder output) {
        if (staticSymbols.isEmpty()) {
            return;
        }
        output.append("    .bss\n")
                .append("    .align 2\n");
        for (String symbol : staticSymbols.values()) {
            output.append(symbol).append(":\n")
                    .append("    .space 4\n");
        }
    }

    private String asmFunctionName(IrMethod method) {
        String owner = method.reference().owner();
        String simpleName = owner.substring(owner.lastIndexOf('/') + 1);
        return "juno_" + simpleName + "_asm";
    }

    /** Every value/local's fixed stack offset, and the total (8-byte-aligned) frame size to reserve. */
    private record FrameLayout(int frameSize, int[] valueOffsets, int[] localOffsets) {
        int valueOffset(Value value) {
            return valueOffsets[value.id()];
        }

        int localOffset(int local) {
            return localOffsets[local];
        }
    }

    private FrameLayout frameLayout(IrMethod method) {
        int numValues = method.values().size();
        int numLocals = method.maxLocals();
        int[] valueOffsets = new int[numValues];
        for (int i = 0; i < numValues; i++) {
            valueOffsets[i] = i * WORD;
        }
        int[] localOffsets = new int[numLocals];
        for (int i = 0; i < numLocals; i++) {
            localOffsets[i] = (numValues + i) * WORD;
        }
        int rawSize = (numValues + numLocals) * WORD;
        // PUSH_BYTES (36) isn't itself a multiple of 8; round the *total* prologue adjustment up to
        // the next multiple of 8 so `sp` stays AAPCS-aligned for every `bl`, then subtract PUSH_BYTES
        // back out to get the frame size to actually `sub sp` by.
        int frameSize = roundUp(PUSH_BYTES + rawSize, 8) - PUSH_BYTES;
        return new FrameLayout(frameSize, valueOffsets, localOffsets);
    }

    private int roundUp(int value, int multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }

    private void emitMethod(StringBuilder output, IrMethod method) {
        String label = functionLabels.get(method.reference());
        boolean isEntryPoint = method.reference().equals(entryPoint);
        FrameLayout frame = frameLayout(method);
        List<String> parameterTypes = Descriptor.parse(method.reference().descriptor()).parameters();

        output.append('\n');
        if (isEntryPoint) {
            output.append("    .global ").append(label).append('\n');
        }
        output.append("    .type ").append(label).append(", %function\n")
                .append(label).append(":\n")
                .append("    push {r4-r11, lr}\n");
        if (frame.frameSize() > 0) {
            // A plain immediate `sub sp,sp,#N` only encodes up to 4095, and Snake-sized frames exceed
            // that; r12 (AAPCS "ip", always caller-saved/scratch) is free here without disturbing the
            // incoming r0-r3 parameters emitParameterSpill is about to read.
            emitLoadImmediate(output, "r12", frame.frameSize());
            output.append("    sub sp, sp, r12\n");
        }
        emitParameterSpill(output, frame, parameterTypes);
        // Mirrors ArduinoCppBackend's setup(): <clinit> is its own reachable method in the IR, but
        // nothing calls it there either — the C++ backend invokes it explicitly, so this does too.
        if (isEntryPoint && clinitLabel != null) {
            output.append("    bl ").append(clinitLabel).append('\n');
        }

        for (IrBasicBlock block : method.blocks()) {
            output.append(".L").append(label).append("block").append(block.start()).append(":\n");
            for (IrInstruction instruction : block.instructions()) {
                emitInstruction(output, frame, instruction);
            }
            emitTerminator(output, frame, label, block);
        }
    }

    /**
     * Copies incoming parameters (register-passed args 0-3, stack-passed args 4+) into their JVM
     * local slots (JVMS 2.6.1: a static method's formal parameters occupy locals 0..k-1). Stack-passed
     * args sit at {@code [caller's sp at the `bl`] + 4*(i-4)}; after this function's own prologue that
     * address is {@code frame.frameSize() + PUSH_BYTES} higher than the current {@code sp}.
     */
    private void emitParameterSpill(StringBuilder output, FrameLayout frame, List<String> parameterTypes) {
        for (int i = 0; i < parameterTypes.size(); i++) {
            int localOffset = frame.localOffset(i);
            if (i < 4) {
                emitStore(output, "r" + i, localOffset);
            } else {
                int callerOffset = frame.frameSize() + PUSH_BYTES + (i - 4) * WORD;
                emitLoad(output, "r0", callerOffset);
                emitStore(output, "r0", localOffset);
            }
        }
    }

    private void emitInstruction(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.Const constant -> {
                emitLoadImmediate(output, "r0", constant.value());
                store(output, frame, "r0", constant.target());
            }
            case IrInstruction.LoadLocal load -> {
                emitLoad(output, "r0", frame.localOffset(load.local()));
                store(output, frame, "r0", load.target());
            }
            case IrInstruction.StoreLocal storeLocal -> {
                load(output, frame, "r0", storeLocal.value());
                emitStore(output, "r0", frame.localOffset(storeLocal.local()));
            }
            case IrInstruction.Binary binary -> emitBinary(output, frame, binary);
            case IrInstruction.Unary unary -> emitUnary(output, frame, unary);
            case IrInstruction.Compare compare -> emitCompare(output, frame, compare);
            case IrInstruction.Call call -> emitCall(output, frame, call);
            case IrInstruction.IntrinsicCall call -> emitIntrinsicCall(output, frame, call);
            case IrInstruction.NewArray newArray -> {
                int elementSize = elementSize(newArray.elementType());
                emitLoadImmediate(output, "r0", elementSize * newArray.length());
                emitLoadImmediate(output, "r1", elementSize);
                output.append("    bl juno_alloc\n");
                store(output, frame, "r0", newArray.target());
            }
            case IrInstruction.NewMultiArray array -> emitNewMultiArray(output, frame, array);
            case IrInstruction.ArrayLoad load -> emitArrayLoad(output, frame, load);
            case IrInstruction.ArrayStore store -> emitArrayStore(output, frame, store);
            case IrInstruction.BoundsCheck check -> emitBoundsCheck(output, frame, check);
            case IrInstruction.Panic ignored -> output.append("    bl juno_panic\n");
            case IrInstruction.NewObject object -> {
                emitLoadImmediate(output, "r0", objectSizes.get(object.className()));
                emitLoadImmediate(output, "r1", WORD);
                output.append("    bl juno_alloc\n");
                store(output, frame, "r0", object.target());
            }
            case IrInstruction.LoadField load -> {
                load(output, frame, "r0", load.receiver());
                output.append("    ldr r1, [r0, #").append(fieldOffsets.get(load.field())).append("]\n");
                store(output, frame, "r1", load.target());
            }
            case IrInstruction.StoreField storeField -> {
                load(output, frame, "r0", storeField.receiver());
                load(output, frame, "r1", storeField.value());
                output.append("    str r1, [r0, #").append(fieldOffsets.get(storeField.field())).append("]\n");
            }
            case IrInstruction.LoadStatic load -> {
                output.append("    ldr r0, =").append(staticSymbols.get(load.field())).append('\n')
                        .append("    ldr r0, [r0]\n");
                store(output, frame, "r0", load.target());
            }
            case IrInstruction.StoreStatic storeStatic -> {
                load(output, frame, "r0", storeStatic.value());
                output.append("    ldr r1, =").append(staticSymbols.get(storeStatic.field())).append('\n')
                        .append("    str r0, [r1]\n");
            }
            default -> throw unsupported(instruction.getClass().getSimpleName());
        }
    }

    private void emitBinary(StringBuilder output, FrameLayout frame, IrInstruction.Binary binary) {
        load(output, frame, "r0", binary.left());
        load(output, frame, "r1", binary.right());
        switch (binary.operation()) {
            case ADD -> output.append("    add r0, r0, r1\n");
            case SUBTRACT -> output.append("    sub r0, r0, r1\n");
            case MULTIPLY -> output.append("    mul r0, r0, r1\n");
            case DIVIDE -> output.append("    sdiv r0, r0, r1\n");
            case REMAINDER -> output.append("    sdiv r2, r0, r1\n")
                    .append("    mls r0, r2, r1, r0\n");
            case SHIFT_LEFT -> output.append("    and r1, r1, #31\n").append("    lsl r0, r0, r1\n");
            case SHIFT_RIGHT -> output.append("    and r1, r1, #31\n").append("    asr r0, r0, r1\n");
            case UNSIGNED_SHIFT_RIGHT -> output.append("    and r1, r1, #31\n").append("    lsr r0, r0, r1\n");
            case AND -> output.append("    and r0, r0, r1\n");
            case OR -> output.append("    orr r0, r0, r1\n");
            case XOR -> output.append("    eor r0, r0, r1\n");
        }
        store(output, frame, "r0", binary.target());
    }

    private void emitUnary(StringBuilder output, FrameLayout frame, IrInstruction.Unary unary) {
        load(output, frame, "r0", unary.value());
        switch (unary.operation()) {
            case NEGATE -> output.append("    rsb r0, r0, #0\n");
            case TO_BYTE -> output.append("    sxtb r0, r0\n");
            case TO_CHAR -> output.append("    uxth r0, r0\n");
            case TO_SHORT -> output.append("    sxth r0, r0\n");
        }
        store(output, frame, "r0", unary.target());
    }

    private void emitCompare(StringBuilder output, FrameLayout frame, IrInstruction.Compare compare) {
        String trueLabel = ".Lcmptrue" + (labelCounter++);
        String doneLabel = ".Lcmpdone" + (labelCounter++);
        load(output, frame, "r0", compare.left());
        load(output, frame, "r1", compare.right());
        output.append("    cmp r0, r1\n")
                .append("    ").append(branchInstruction(compare.condition())).append(' ').append(trueLabel).append('\n')
                .append("    movs r0, #0\n")
                .append("    b ").append(doneLabel).append('\n')
                .append(trueLabel).append(":\n")
                .append("    movs r0, #1\n")
                .append(doneLabel).append(":\n");
        store(output, frame, "r0", compare.target());
    }

    private String branchInstruction(Condition condition) {
        return switch (condition) {
            case EQUAL -> "beq";
            case NOT_EQUAL -> "bne";
            case LESS_THAN -> "blt";
            case GREATER_EQUAL -> "bge";
            case GREATER_THAN -> "bgt";
            case LESS_EQUAL -> "ble";
        };
    }

    /** Loads/stores each argument, staging any beyond the first four onto a transient stack area (AAPCS). */
    private void emitCall(StringBuilder output, FrameLayout frame, IrInstruction.Call call) {
        String label = functionLabels.get(call.method());
        if (label == null) {
            throw unsupported("call to unresolved method " + call.method().displayName());
        }
        List<Value> arguments = call.arguments();
        int extra = Math.max(0, arguments.size() - 4);
        int reserved = roundUp(extra * WORD, 8);
        if (reserved > 0) {
            output.append("    sub sp, sp, #").append(reserved).append('\n');
            for (int i = 4; i < arguments.size(); i++) {
                emitLoad(output, "r0", frame.valueOffset(arguments.get(i)) + reserved);
                emitStore(output, "r0", (i - 4) * WORD);
            }
        }
        for (int i = 0; i < Math.min(4, arguments.size()); i++) {
            emitLoad(output, "r" + i, frame.valueOffset(arguments.get(i)) + reserved);
        }
        output.append("    bl ").append(label).append('\n');
        if (reserved > 0) {
            output.append("    add sp, sp, #").append(reserved).append('\n');
        }
        call.target().ifPresent(target -> store(output, frame, "r0", target));
    }

    private void emitIntrinsicCall(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call) {
        switch (call.intrinsic()) {
            case DIGITAL_OUTPUT_OF -> {
                load(output, frame, "r0", call.arguments().get(0));
                emitLoadImmediate(output, "r1", 1); // OUTPUT
                output.append("    bl pinMode\n");
                load(output, frame, "r0", call.arguments().get(0));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case GPIO_PIN_MODE -> {
                load(output, frame, "r0", call.arguments().get(0));
                load(output, frame, "r1", call.arguments().get(1));
                output.append("    bl pinMode\n");
            }
            case DIGITAL_OUTPUT_HIGH, DIGITAL_OUTPUT_LOW -> {
                load(output, frame, "r0", call.receiver().orElseThrow());
                emitLoadImmediate(output, "r1", call.intrinsic() == Intrinsic.DIGITAL_OUTPUT_HIGH ? 1 : 0);
                output.append("    bl digitalWrite\n");
            }
            case GPIO_DIGITAL_WRITE -> {
                load(output, frame, "r0", call.arguments().get(0));
                load(output, frame, "r1", call.arguments().get(1));
                output.append("    bl digitalWrite\n");
            }
            case DELAY_MILLIS -> {
                load(output, frame, "r0", call.arguments().get(0));
                output.append("    bl delay\n");
            }
            case LED_MATRIX_BEGIN -> output.append("    bl juno_led_matrix_begin\n");
            case LED_MATRIX_LOAD_FRAME -> {
                load(output, frame, "r0", call.arguments().get(0));
                load(output, frame, "r1", call.arguments().get(1));
                load(output, frame, "r2", call.arguments().get(2));
                output.append("    bl juno_led_matrix_load_frame\n");
            }
            case LED_MATRIX_CLEAR -> output.append("    bl juno_led_matrix_clear\n");
            case SERIAL_BEGIN -> {
                load(output, frame, "r0", call.arguments().get(0));
                output.append("    bl juno_serial_begin\n");
            }
            case SERIAL_PRINT -> {
                load(output, frame, "r0", call.arguments().get(0));
                output.append("    bl juno_serial_print\n");
            }
            case SERIAL_PRINTLN -> {
                load(output, frame, "r0", call.arguments().get(0));
                output.append("    bl juno_serial_println\n");
            }
            case MOUSE_BEGIN -> {
                usesMouse = true;
                output.append("    bl juno_mouse_begin\n");
            }
            case MOUSE_MOVE -> {
                usesMouse = true;
                load(output, frame, "r0", call.arguments().get(0));
                load(output, frame, "r1", call.arguments().get(1));
                output.append("    bl juno_mouse_move\n");
            }
            default -> throw unsupported("intrinsic " + call.intrinsic());
        }
    }

    /** Allocates the outer (row-handle) array, then each leaf row — mirrors ArduinoCppBackend's model. */
    private void emitNewMultiArray(StringBuilder output, FrameLayout frame, IrInstruction.NewMultiArray array) {
        if (array.dimensions().size() != 2) {
            throw unsupported("array nesting deeper than 2 dimensions: " + array.dimensions());
        }
        int outerCount = array.dimensions().get(0);
        int innerCount = array.dimensions().get(1);
        int elementSize = elementSize(array.leafType());
        emitLoadImmediate(output, "r0", outerCount * WORD);
        emitLoadImmediate(output, "r1", WORD);
        output.append("    bl juno_alloc\n");
        store(output, frame, "r0", array.target());
        for (int index = 0; index < outerCount; index++) {
            emitLoadImmediate(output, "r0", innerCount * elementSize);
            emitLoadImmediate(output, "r1", elementSize);
            output.append("    bl juno_alloc\n");
            output.append("    mov r1, r0\n");
            load(output, frame, "r0", array.target());
            output.append("    str r1, [r0, #").append(index * WORD).append("]\n");
        }
    }

    private void emitArrayLoad(StringBuilder output, FrameLayout frame, IrInstruction.ArrayLoad load) {
        load(output, frame, "r0", load.array());
        load(output, frame, "r1", load.index());
        String loadInstruction = switch (load.elementType()) {
            case BYTE -> "ldrsb r2, [r0, r1]\n";
            case CHAR -> "lsls r1, r1, #1\n    ldrh r2, [r0, r1]\n";
            case SHORT -> "lsls r1, r1, #1\n    ldrsh r2, [r0, r1]\n";
            case INT, REFERENCE -> "lsls r1, r1, #2\n    ldr r2, [r0, r1]\n";
            case LONG, FLOAT, DOUBLE -> throw unsupported("array element type " + load.elementType());
        };
        output.append("    ").append(loadInstruction);
        store(output, frame, "r2", load.target());
    }

    private void emitArrayStore(StringBuilder output, FrameLayout frame, IrInstruction.ArrayStore store) {
        load(output, frame, "r0", store.array());
        load(output, frame, "r1", store.index());
        load(output, frame, "r2", store.value());
        String storeInstruction = switch (store.elementType()) {
            case BYTE -> "strb r2, [r0, r1]\n";
            case CHAR, SHORT -> "lsls r1, r1, #1\n    strh r2, [r0, r1]\n";
            case INT, REFERENCE -> "lsls r1, r1, #2\n    str r2, [r0, r1]\n";
            case LONG, FLOAT, DOUBLE -> throw unsupported("array element type " + store.elementType());
        };
        output.append("    ").append(storeInstruction);
    }

    private void emitBoundsCheck(StringBuilder output, FrameLayout frame, IrInstruction.BoundsCheck check) {
        String panicLabel = ".Lbcpanic" + (labelCounter++);
        String okLabel = ".Lbcok" + (labelCounter++);
        load(output, frame, "r0", check.index());
        emitLoadImmediate(output, "r1", check.length());
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
            case LONG, FLOAT, DOUBLE -> throw unsupported("array element type " + type);
        };
    }

    private void emitTerminator(StringBuilder output, FrameLayout frame, String label, IrBasicBlock block) {
        switch (block.terminator()) {
            case IrTerminator.Jump jump -> {
                emitYieldIfBackedge(output, block.start(), jump.target());
                output.append("    b .L").append(label).append("block").append(jump.target()).append('\n');
            }
            case IrTerminator.Branch branch -> {
                emitYieldIfBackedge(output, block.start(), branch.trueTarget());
                emitYieldIfBackedge(output, block.start(), branch.falseTarget());
                load(output, frame, "r0", branch.condition());
                // A conditional branch (beq/bne/...) only has a short encoded range; the true/false
                // blocks can be arbitrarily far away in a large method. So the *conditional* hop only
                // ever jumps a few bytes, to a label right here, and the actual (possibly far) jumps
                // are unconditional `b`, which the assembler widens to whatever range it needs.
                String falseLabel = ".Lbranchfalse" + (labelCounter++);
                output.append("    cmp r0, #0\n")
                        .append("    beq ").append(falseLabel).append('\n')
                        .append("    b .L").append(label).append("block").append(branch.trueTarget()).append('\n')
                        .append(falseLabel).append(":\n")
                        .append("    b .L").append(label).append("block").append(branch.falseTarget()).append('\n');
            }
            case IrTerminator.Return returned -> {
                returned.value().ifPresent(value -> load(output, frame, "r0", value));
                if (frame.frameSize() > 0) {
                    emitLoadImmediate(output, "r12", frame.frameSize());
                    output.append("    add sp, sp, r12\n");
                }
                output.append("    pop {r4-r11, pc}\n");
            }
            case IrTerminator.Switch switched -> {
                for (int target : switched.targets()) {
                    emitYieldIfBackedge(output, block.start(), target);
                }
                emitYieldIfBackedge(output, block.start(), switched.defaultTarget());
                load(output, frame, "r0", switched.selector());
                for (int i = 0; i < switched.keys().size(); i++) {
                    // Same short-conditional-hop/long-unconditional-jump idiom as Branch, chained:
                    // each case either jumps straight to its (possibly far) target, or falls through
                    // to the next case's check.
                    String nextCheckLabel = ".Lswitchnext" + (labelCounter++);
                    emitLoadImmediate(output, "r1", switched.keys().get(i));
                    output.append("    cmp r0, r1\n")
                            .append("    bne ").append(nextCheckLabel).append('\n')
                            .append("    b .L").append(label).append("block")
                            .append(switched.targets().get(i)).append('\n')
                            .append(nextCheckLabel).append(":\n");
                }
                output.append("    b .L").append(label).append("block")
                        .append(switched.defaultTarget()).append('\n');
            }
            default -> throw unsupported(block.terminator().getClass().getSimpleName());
        }
    }

    /** Mirrors ArduinoCppBackend: keep the core's USB service polled on every loop backedge. */
    private void emitYieldIfBackedge(StringBuilder output, int blockStart, int target) {
        if (target <= blockStart) {
            output.append("    bl yield\n");
        }
    }

    /** {@code movw}/{@code movt} loads any 32-bit bit pattern; {@code movs} is just a shorter encoding. */
    private void emitLoadImmediate(StringBuilder output, String register, int value) {
        if (value >= 0 && value <= 255) {
            output.append("    movs ").append(register).append(", #").append(value).append('\n');
            return;
        }
        int low16 = value & 0xFFFF;
        int high16 = (value >>> 16) & 0xFFFF;
        output.append("    movw ").append(register).append(", #").append(low16).append('\n');
        if (high16 != 0) {
            output.append("    movt ").append(register).append(", #").append(high16).append('\n');
        }
    }

    private void load(StringBuilder output, FrameLayout frame, String register, Value value) {
        emitLoad(output, register, frame.valueOffset(value));
    }

    private void store(StringBuilder output, FrameLayout frame, String register, Value value) {
        emitStore(output, register, frame.valueOffset(value));
    }

    /**
     * {@code ldr Rd,[sp,#imm]} only encodes offsets up to 4095; a method with enough live
     * values/locals (see {@code LedMatrixSnake}) exceeds that easily. Past that, compute the address
     * in r12 (AAPCS "ip", always caller-saved/scratch, never used to hold a Java value here) instead.
     */
    private void emitLoad(StringBuilder output, String destinationRegister, int offset) {
        if (offset <= 4095) {
            output.append("    ldr ").append(destinationRegister).append(", [sp, #").append(offset).append("]\n");
        } else {
            emitLoadImmediate(output, "r12", offset);
            output.append("    add r12, r12, sp\n")
                    .append("    ldr ").append(destinationRegister).append(", [r12]\n");
        }
    }

    /** See {@link #emitLoad}. */
    private void emitStore(StringBuilder output, String sourceRegister, int offset) {
        if (offset <= 4095) {
            output.append("    str ").append(sourceRegister).append(", [sp, #").append(offset).append("]\n");
        } else {
            emitLoadImmediate(output, "r12", offset);
            output.append("    add r12, r12, sp\n")
                    .append("    str ").append(sourceRegister).append(", [r12]\n");
        }
    }

    /**
     * {@code extern "C"} wrappers around things the generated assembly can't call directly: the arena
     * allocator/panic handler (same logic as {@link ArduinoCppBackend}'s own, already hardware-verified
     * there) and {@code ArduinoLEDMatrix} (a C++-only object with inline-only methods and private
     * timer/frame state — no stable symbol to call without knowing its private layout).
     */
    private String runtimeShim() {
        StringBuilder shim = new StringBuilder();
        shim.append("""
                // Generated by Juno's experimental Cortex-M4 assembly backend. Do not edit.
                #include <Arduino.h>
                #include "Arduino_LED_Matrix.h"
                """);
        // Mouse is an optional library (arduino-cli lib install Mouse), unlike the core-bundled
        // LED matrix/Serial above — only pull it in when the program actually uses it, so every
        // other generated sketch keeps compiling without that library installed.
        if (usesMouse) {
            shim.append("#include <Mouse.h>\n");
        }
        shim.append("""

                extern "C" [[noreturn]] void juno_panic() {
                  noInterrupts();
                  for (;;) {}
                }

                static uint8_t juno_arena[${JUNO_ARENA_CAPACITY}] __attribute__((aligned(8)));
                static uint32_t juno_arena_used = 0;

                extern "C" void* juno_alloc(uint32_t size, uint32_t alignment) {
                  uint32_t aligned = (juno_arena_used + alignment - 1u) & ~(alignment - 1u);
                  if (aligned > sizeof(juno_arena) || size > sizeof(juno_arena) - aligned) juno_panic();
                  uint8_t* memory = &juno_arena[aligned];
                  for (uint32_t index = 0; index < size; index++) memory[index] = 0;
                  juno_arena_used = aligned + size;
                  return memory;
                }

                static ArduinoLEDMatrix juno_led_matrix;

                extern "C" void juno_led_matrix_begin() {
                  juno_led_matrix.begin();
                }

                extern "C" void juno_led_matrix_load_frame(int32_t word0, int32_t word1, int32_t word2) {
                  const uint32_t frame[3] = {
                    static_cast<uint32_t>(word0),
                    static_cast<uint32_t>(word1),
                    static_cast<uint32_t>(word2)
                  };
                  juno_led_matrix.loadFrame(frame);
                }

                extern "C" void juno_led_matrix_clear() {
                  const uint32_t frame[3] = {0, 0, 0};
                  juno_led_matrix.loadFrame(frame);
                }

                extern "C" void juno_serial_begin(int32_t baud) {
                  Serial.begin(static_cast<unsigned long>(baud));
                }

                extern "C" void juno_serial_print(int32_t value) {
                  Serial.print(value);
                }

                extern "C" void juno_serial_println(int32_t value) {
                  Serial.println(value);
                }
                """.replace("${JUNO_ARENA_CAPACITY}", Integer.toString(RuntimeLimits.ARENA_CAPACITY_BYTES)));
        if (usesMouse) {
            shim.append("""

                    extern "C" void juno_mouse_begin() {
                      Mouse.begin();
                    }

                    extern "C" void juno_mouse_move(int32_t x, int32_t y) {
                      Mouse.move(static_cast<signed char>(x), static_cast<signed char>(y));
                    }
                    """);
        }
        return shim.toString();
    }

    private CompileException unsupported(String detail) {
        return new CompileException(
                "The experimental Cortex-M4 assembly backend does not support this yet: " + detail);
    }
}
