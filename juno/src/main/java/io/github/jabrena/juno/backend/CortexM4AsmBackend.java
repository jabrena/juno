package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
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
 * {@code LedMatrix}, {@code Serial}, {@code Mouse}, {@code Wifi}, {@code HttpClient}/{@code HttpsClient},
 * {@code Json}, and {@code long}/{@code float}/{@code double} support. Calls Juno can't statically
 * resolve, and a handful of instruction kinds with no codegen yet ({@link IrInstruction.IntArrayConst},
 * used for enum switch maps), fail loudly with {@link CompileException} rather than emitting code that
 * looks plausible but was never checked.
 *
 * <h2>{@code long}/{@code float}/{@code double}</h2>
 * The RA4M1 (UNO R4's Cortex-M4) has no hardware FPU, and 64-bit values don't fit a single register,
 * so — consistent with this class's existing shim-delegation philosophy — every nontrivial
 * {@code long}/{@code float}/{@code double} operation (arithmetic, comparison, conversion) is a call
 * to a small {@code extern "C"} runtime-shim function (see {@link #longHelpers}/{@link #floatHelpers}/
 * {@link #doubleHelpers}) rather than hand-rolled soft-float assembly. A {@code long} keeps this
 * backend's existing split-low/high-word representation (two ordinary {@code int32} stack slots);
 * {@code float}/{@code double} are new {@link JunoType#FLOAT32}/{@link JunoType#FLOAT64} stack slots
 * (4/8 bytes, holding the raw IEEE-754 bit pattern) — see {@link #frameLayout}. Every JVM local slot
 * is now a full 8 bytes regardless of its actual type (mirroring {@link ArduinoCppBackend}'s
 * {@code JunoSlot} union locals array), so a {@code double} local can never overlap the next slot's
 * storage.
 *
 * <h2>{@code HttpClient}/{@code HttpsClient}/{@code Json}</h2>
 * Backed by {@code extern "C"} mirrors of {@link ArduinoCppBackend}'s own HTTP/1.1 codec and
 * allocation-free JSON scanner (duplicated rather than shared, consistent with every other shim in
 * this class already duplicating its {@link ArduinoCppBackend} counterpart instead of sharing code
 * between the two independent backends). A call needing more than four argument words (e.g.
 * {@code Json.getString}'s five) spills the overflow onto a transient stack area via
 * {@link #emitShimCall}, the same mechanism {@link #emitCall} already uses for user methods.
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
    private final Map<String, String> stringLiteralSymbols = new LinkedHashMap<>();
    private MethodRef entryPoint;
    private String clinitLabel;
    private int labelCounter;
    private boolean usesMouse;
    private boolean usesWifi;
    private boolean usesLong;
    private boolean usesFloat;
    private boolean usesDouble;
    private boolean usesHttp;
    private boolean usesHttps;
    private boolean usesJson;

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
        emitStringLiteralStorage(output);
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
                        case IrInstruction.IntrinsicCall call -> {
                            for (String literal : call.literalArguments()) {
                                stringLiteralSymbols.computeIfAbsent(literal,
                                        unused -> "juno_str" + stringLiteralSymbols.size());
                            }
                        }
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

    /**
     * Every distinct string literal used anywhere in the program (deduplicated by content) gets one
     * {@code .rodata} symbol, filled in by {@link #computeLayouts}; a call site just loads its address.
     */
    private void emitStringLiteralStorage(StringBuilder output) {
        if (stringLiteralSymbols.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    .align 2\n");
        for (Map.Entry<String, String> entry : stringLiteralSymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    .asciz \"").append(asmStringLiteral(entry.getKey())).append("\"\n");
        }
    }

    /** Escapes {@code value} for a GNU {@code as} {@code .asciz} directive. */
    private String asmStringLiteral(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20 || character > 0x7E) {
                        escaped.append(String.format("\\%03o", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
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

    /**
     * {@code long}/{@code double} boundary {@link Value}s ({@link JunoType#INT64}/{@link JunoType#FLOAT64})
     * need 8 bytes; everything else (including {@link JunoType#FLOAT32}, a raw bit pattern) fits in 4.
     */
    private int valueWidth(JunoType type) {
        return (type == JunoType.INT64 || type == JunoType.FLOAT64) ? 8 : WORD;
    }

    private FrameLayout frameLayout(IrMethod method) {
        List<Value> values = method.values();
        int numValues = values.size();
        int numLocals = method.maxLocals();
        int[] valueOffsets = new int[numValues];
        int valueBytes = 0;
        for (int i = 0; i < numValues; i++) {
            valueOffsets[i] = valueBytes;
            valueBytes += valueWidth(values.get(i).type());
        }
        int[] localOffsets = new int[numLocals];
        // Every local gets a full 8-byte slot regardless of its actual type, mirroring
        // ArduinoCppBackend's `JunoSlot` union locals array: a JVM long/double local only ever uses
        // ONE local index (the low-numbered half of the two JVMS reserves), but reusing this backend's
        // otherwise-compact 4-byte-per-index model just for that one index would let its 8 bytes spill
        // into local index N+1's own storage. Uniform 8-byte slots make that overlap impossible by
        // construction, at the cost of wasting 4 bytes per plain int local.
        for (int i = 0; i < numLocals; i++) {
            localOffsets[i] = valueBytes + i * 8;
        }
        int rawSize = valueBytes + numLocals * 8;
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
            // Flushes the literal pool (every `ldr rN, =symbol` — string literals, static fields —
            // pending since the last flush) right here. Thumb-2's PC-relative `ldr` only reaches 4095
            // bytes forward, and a method as large as MadridWeather's easily exceeds that if the pool
            // is left to accumulate until end-of-file (the assembler's default). Safe unconditionally:
            // every block's terminator above already ends in an unconditional branch or return, so
            // execution can never fall through into this data.
            output.append("    .ltorg\n");
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
                if (load.target().type() == JunoType.FLOAT64) {
                    emitLoad(output, "r0", frame.localOffset(load.local()));
                    emitLoad(output, "r1", frame.localOffset(load.local()) + WORD);
                    store64(output, frame, "r0", "r1", load.target());
                } else {
                    emitLoad(output, "r0", frame.localOffset(load.local()));
                    store(output, frame, "r0", load.target());
                }
            }
            case IrInstruction.StoreLocal storeLocal -> {
                if (storeLocal.value().type() == JunoType.FLOAT64) {
                    load64(output, frame, "r0", "r1", storeLocal.value());
                    emitStore(output, "r0", frame.localOffset(storeLocal.local()));
                    emitStore(output, "r1", frame.localOffset(storeLocal.local()) + WORD);
                } else {
                    load(output, frame, "r0", storeLocal.value());
                    emitStore(output, "r0", frame.localOffset(storeLocal.local()));
                }
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
            case IrInstruction.LongConst constant -> {
                emitLoadImmediate(output, "r0", (int) constant.value());
                store(output, frame, "r0", constant.targetLow());
                emitLoadImmediate(output, "r0", (int) (constant.value() >>> 32));
                store(output, frame, "r0", constant.targetHigh());
            }
            case IrInstruction.LongBinary binary -> {
                usesLong = true;
                load(output, frame, "r0", binary.leftLow());
                load(output, frame, "r1", binary.leftHigh());
                load(output, frame, "r2", binary.rightLow());
                load(output, frame, "r3", binary.rightHigh());
                output.append("    bl ").append(longHelperFor(binary.operation())).append('\n');
                store(output, frame, "r0", binary.targetLow());
                store(output, frame, "r1", binary.targetHigh());
            }
            case IrInstruction.LongShift shift -> {
                usesLong = true;
                load(output, frame, "r0", shift.valueLow());
                load(output, frame, "r1", shift.valueHigh());
                load(output, frame, "r2", shift.shiftAmount());
                output.append("    bl ").append(longShiftHelperFor(shift.operation())).append('\n');
                store(output, frame, "r0", shift.targetLow());
                store(output, frame, "r1", shift.targetHigh());
            }
            case IrInstruction.LongNegate negate -> {
                usesLong = true;
                load(output, frame, "r0", negate.valueLow());
                load(output, frame, "r1", negate.valueHigh());
                output.append("    bl juno_lneg\n");
                store(output, frame, "r0", negate.targetLow());
                store(output, frame, "r1", negate.targetHigh());
            }
            case IrInstruction.LongCompare compare -> {
                usesLong = true;
                load(output, frame, "r0", compare.leftLow());
                load(output, frame, "r1", compare.leftHigh());
                load(output, frame, "r2", compare.rightLow());
                load(output, frame, "r3", compare.rightHigh());
                output.append("    bl juno_lcmp\n");
                store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToLong widen -> {
                // Sign-extending needs no arithmetic: the low half is the value unchanged, and the high
                // half is all-0s or all-1s depending on its sign, i.e. value >> 31.
                load(output, frame, "r0", widen.value());
                store(output, frame, "r0", widen.targetLow());
                output.append("    asr r0, r0, #31\n");
                store(output, frame, "r0", widen.targetHigh());
            }
            case IrInstruction.LongToInt narrow -> {
                load(output, frame, "r0", narrow.valueLow());
                store(output, frame, "r0", narrow.target());
            }
            case IrInstruction.FloatConst constant -> {
                emitLoadImmediate(output, "r0", Float.floatToRawIntBits(constant.value()));
                store(output, frame, "r0", constant.target());
            }
            case IrInstruction.FloatBinary binary -> {
                usesFloat = true;
                load(output, frame, "r0", binary.left());
                load(output, frame, "r1", binary.right());
                output.append("    bl ").append(floatHelperFor(binary.operation())).append('\n');
                store(output, frame, "r0", binary.target());
            }
            case IrInstruction.FloatNegate negate -> {
                // Flips the sign bit directly; no soft-float call needed for plain negation.
                load(output, frame, "r0", negate.value());
                output.append("    eor r0, r0, #0x80000000\n");
                store(output, frame, "r0", negate.target());
            }
            case IrInstruction.FloatCompare compare -> {
                usesFloat = true;
                load(output, frame, "r0", compare.left());
                load(output, frame, "r1", compare.right());
                emitLoadImmediate(output, "r2", compare.nanResult());
                output.append("    bl juno_fcmp\n");
                store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToFloat conversion -> {
                usesFloat = true;
                load(output, frame, "r0", conversion.value());
                output.append("    bl juno_i2f\n");
                store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToInt conversion -> {
                usesFloat = true;
                load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2i\n");
                store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.DoubleConst constant -> {
                long bits = Double.doubleToRawLongBits(constant.value());
                emitLoadImmediate(output, "r0", (int) bits);
                emitLoadImmediate(output, "r1", (int) (bits >>> 32));
                store64(output, frame, "r0", "r1", constant.target());
            }
            case IrInstruction.DoubleBinary binary -> {
                usesDouble = true;
                load64(output, frame, "r0", "r1", binary.left());
                load64(output, frame, "r2", "r3", binary.right());
                output.append("    bl ").append(doubleHelperFor(binary.operation())).append('\n');
                store64(output, frame, "r0", "r1", binary.target());
            }
            case IrInstruction.DoubleNegate negate -> {
                // The sign bit of a low-word/high-word split double is bit 31 of the high word.
                load64(output, frame, "r0", "r1", negate.value());
                output.append("    eor r1, r1, #0x80000000\n");
                store64(output, frame, "r0", "r1", negate.target());
            }
            case IrInstruction.DoubleCompare compare -> {
                usesDouble = true;
                emitShimCall(output, frame, "juno_dcmp", List.of(
                        new WordSource.FromValueLow(compare.left()), new WordSource.FromValueHigh(compare.left()),
                        new WordSource.FromValueLow(compare.right()), new WordSource.FromValueHigh(compare.right()),
                        new WordSource.Immediate(compare.nanResult())));
                store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToDouble conversion -> {
                usesDouble = true;
                load(output, frame, "r0", conversion.value());
                output.append("    bl juno_i2d\n");
                store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToInt conversion -> {
                usesDouble = true;
                load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2i\n");
                store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToDouble conversion -> {
                usesDouble = true;
                load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2d\n");
                store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToFloat conversion -> {
                usesDouble = true;
                load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2f\n");
                store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.LongToDouble conversion -> {
                usesDouble = true;
                load(output, frame, "r0", conversion.valueLow());
                load(output, frame, "r1", conversion.valueHigh());
                output.append("    bl juno_l2d\n");
                store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToLong conversion -> {
                usesDouble = true;
                load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2l\n");
                store(output, frame, "r0", conversion.targetLow());
                store(output, frame, "r1", conversion.targetHigh());
            }
            case IrInstruction.PackLong packed -> {
                load(output, frame, "r0", packed.valueLow());
                load(output, frame, "r1", packed.valueHigh());
                store64(output, frame, "r0", "r1", packed.target());
            }
            case IrInstruction.UnpackLong unpacked -> {
                load64(output, frame, "r0", "r1", unpacked.value());
                store(output, frame, "r0", unpacked.targetLow());
                store(output, frame, "r1", unpacked.targetHigh());
            }
            case IrInstruction.LongToFloat conversion -> {
                usesFloat = true;
                load(output, frame, "r0", conversion.valueLow());
                load(output, frame, "r1", conversion.valueHigh());
                output.append("    bl juno_l2f\n");
                store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToLong conversion -> {
                usesFloat = true;
                load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2l\n");
                store(output, frame, "r0", conversion.targetLow());
                store(output, frame, "r1", conversion.targetHigh());
            }
            default -> throw unsupported(instruction.getClass().getSimpleName());
        }
    }

    private String longHelperFor(BinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_ladd";
            case SUBTRACT -> "juno_lsub";
            case MULTIPLY -> "juno_lmul";
            case DIVIDE -> "juno_ldiv";
            case REMAINDER -> "juno_lrem";
            case AND -> "juno_land";
            case OR -> "juno_lor";
            case XOR -> "juno_lxor";
            case SHIFT_LEFT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT ->
                    throw new IllegalStateException("Long shifts use longShiftHelperFor: " + operation);
        };
    }

    private String longShiftHelperFor(BinaryOp operation) {
        return switch (operation) {
            case SHIFT_LEFT -> "juno_lshl";
            case SHIFT_RIGHT -> "juno_lshr";
            case UNSIGNED_SHIFT_RIGHT -> "juno_lushr";
            default -> throw new IllegalStateException("Not a long shift operation: " + operation);
        };
    }

    private String floatHelperFor(FloatBinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_fadd";
            case SUBTRACT -> "juno_fsub";
            case MULTIPLY -> "juno_fmul";
            case DIVIDE -> "juno_fdiv";
            case REMAINDER -> "juno_frem";
        };
    }

    private String doubleHelperFor(FloatBinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_dadd";
            case SUBTRACT -> "juno_dsub";
            case MULTIPLY -> "juno_dmul";
            case DIVIDE -> "juno_ddiv";
            case REMAINDER -> "juno_drem";
        };
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
            case SERIAL_PRINT_STRING -> {
                output.append("    ldr r0, =")
                        .append(stringLiteralSymbols.get(call.literalArguments().get(0))).append('\n');
                output.append("    bl juno_serial_print_str\n");
            }
            case SERIAL_PRINTLN_STRING -> {
                output.append("    ldr r0, =")
                        .append(stringLiteralSymbols.get(call.literalArguments().get(0))).append('\n');
                output.append("    bl juno_serial_println_str\n");
            }
            case WIFI_BEGIN -> {
                usesWifi = true;
                output.append("    ldr r0, =")
                        .append(stringLiteralSymbols.get(call.literalArguments().get(0))).append('\n');
                output.append("    ldr r1, =")
                        .append(stringLiteralSymbols.get(call.literalArguments().get(1))).append('\n');
                output.append("    bl juno_wifi_begin\n");
            }
            case WIFI_STATUS -> {
                usesWifi = true;
                output.append("    bl juno_wifi_status\n");
                call.target().ifPresent(target -> store(output, frame, "r0", target));
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
            case HTTP_GET, HTTP_DELETE -> {
                usesHttp = true;
                emitShimCall(output, frame,
                        call.intrinsic() == Intrinsic.HTTP_GET ? "juno_http_get" : "juno_http_delete",
                        List.of(new WordSource.StringAddress(call.literalArguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.StringAddress(call.literalArguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(2))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case HTTP_POST, HTTP_PATCH, HTTP_QUERY -> {
                usesHttp = true;
                String function = switch (call.intrinsic()) {
                    case HTTP_POST -> "juno_http_post";
                    case HTTP_PATCH -> "juno_http_patch";
                    default -> "juno_http_query";
                };
                emitShimCall(output, frame, function,
                        List.of(new WordSource.StringAddress(call.literalArguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.StringAddress(call.literalArguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(2)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(2))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case HTTPS_GET, HTTPS_DELETE -> {
                usesHttps = true;
                emitShimCall(output, frame,
                        call.intrinsic() == Intrinsic.HTTPS_GET ? "juno_https_get" : "juno_https_delete",
                        List.of(new WordSource.StringAddress(call.literalArguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.StringAddress(call.literalArguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(2))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case HTTPS_POST, HTTPS_PATCH, HTTPS_QUERY -> {
                usesHttps = true;
                String function = switch (call.intrinsic()) {
                    case HTTPS_POST -> "juno_https_post";
                    case HTTPS_PATCH -> "juno_https_patch";
                    default -> "juno_https_query";
                };
                emitShimCall(output, frame, function,
                        List.of(new WordSource.StringAddress(call.literalArguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.StringAddress(call.literalArguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(2)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.FromValue(call.arguments().get(2))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case JSON_TYPE, JSON_GET_INT, JSON_GET_BOOL, JSON_ARRAY_SIZE -> {
                usesJson = true;
                String function = switch (call.intrinsic()) {
                    case JSON_TYPE -> "juno_json_type";
                    case JSON_GET_INT -> "juno_json_get_int";
                    case JSON_GET_BOOL -> "juno_json_get_bool";
                    default -> "juno_json_array_size";
                };
                emitShimCall(output, frame, function,
                        List.of(new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(0))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
            }
            case JSON_GET_LONG -> {
                usesJson = true;
                emitShimCall(output, frame, "juno_json_get_long",
                        List.of(new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(0))));
                call.target().ifPresent(target -> store64(output, frame, "r0", "r1", target));
            }
            case JSON_GET_DOUBLE -> {
                usesJson = true;
                emitShimCall(output, frame, "juno_json_get_double",
                        List.of(new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(0))));
                call.target().ifPresent(target -> store64(output, frame, "r0", "r1", target));
            }
            case JSON_GET_STRING -> {
                usesJson = true;
                emitShimCall(output, frame, "juno_json_get_string",
                        List.of(new WordSource.FromValue(call.arguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(1)),
                                new WordSource.StringAddress(call.literalArguments().get(0)),
                                new WordSource.FromValue(call.arguments().get(2)),
                                new WordSource.FromValue(call.arguments().get(3))));
                call.target().ifPresent(target -> store(output, frame, "r0", target));
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
     * Loads/stores an 8-byte {@link JunoType#INT64}/{@link JunoType#FLOAT64} value as two consecutive
     * words (low word first, at the lower offset/address — matching AAPCS's own 64-bit register-pair
     * convention and real little-endian {@code int64_t}/{@code double} memory layout, so these slots'
     * bytes are bit-identical to what a real C++ variable of the same value would hold).
     */
    private void load64(StringBuilder output, FrameLayout frame, String lowRegister, String highRegister, Value value) {
        emitLoad(output, lowRegister, frame.valueOffset(value));
        emitLoad(output, highRegister, frame.valueOffset(value) + WORD);
    }

    private void store64(StringBuilder output, FrameLayout frame, String lowRegister, String highRegister, Value value) {
        emitStore(output, lowRegister, frame.valueOffset(value));
        emitStore(output, highRegister, frame.valueOffset(value) + WORD);
    }

    /** One AAPCS argument word for {@link #emitShimCall}. */
    private sealed interface WordSource {
        record FromValue(Value value) implements WordSource {
        }

        record FromValueLow(Value value) implements WordSource {
        }

        record FromValueHigh(Value value) implements WordSource {
        }

        record StringAddress(String literal) implements WordSource {
        }

        record Immediate(int value) implements WordSource {
        }
    }

    private void loadWord(StringBuilder output, FrameLayout frame, WordSource source, String register, int extraSpOffset) {
        switch (source) {
            case WordSource.FromValue from -> emitLoad(output, register, frame.valueOffset(from.value()) + extraSpOffset);
            case WordSource.FromValueLow from -> emitLoad(output, register, frame.valueOffset(from.value()) + extraSpOffset);
            case WordSource.FromValueHigh from ->
                    emitLoad(output, register, frame.valueOffset(from.value()) + WORD + extraSpOffset);
            case WordSource.StringAddress address ->
                    output.append("    ldr ").append(register).append(", =")
                            .append(stringLiteralSymbols.get(address.literal())).append('\n');
            case WordSource.Immediate immediate -> emitLoadImmediate(output, register, immediate.value());
        }
    }

    /**
     * Calls an {@code extern "C"} runtime-shim function taking exactly {@code words.size()} plain
     * 32-bit AAPCS argument words (register args 0-3, any beyond that on a transient stack area) —
     * the same overflow-to-stack mechanism as {@link #emitCall}, generalized to sources that aren't
     * necessarily a single whole {@link Value} (a wide {@code long}/{@code double} argument supplies
     * two of these, one low-word source and one high-word source).
     */
    private void emitShimCall(StringBuilder output, FrameLayout frame, String functionName, List<WordSource> words) {
        int total = words.size();
        int extra = Math.max(0, total - 4);
        int reserved = roundUp(extra * WORD, 8);
        if (reserved > 0) {
            output.append("    sub sp, sp, #").append(reserved).append('\n');
            for (int i = 4; i < total; i++) {
                loadWord(output, frame, words.get(i), "r0", reserved);
                emitStore(output, "r0", (i - 4) * WORD);
            }
        }
        for (int i = 0; i < Math.min(4, total); i++) {
            loadWord(output, frame, words.get(i), "r" + i, reserved);
        }
        output.append("    bl ").append(functionName).append('\n');
        if (reserved > 0) {
            output.append("    add sp, sp, #").append(reserved).append('\n');
        }
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
                #include <stdint.h>
                #include "Arduino_LED_Matrix.h"
                """);
        // Mouse is an optional library (arduino-cli lib install Mouse), unlike the core-bundled
        // LED matrix/Serial above — only pull it in when the program actually uses it, so every
        // other generated sketch keeps compiling without that library installed.
        if (usesMouse) {
            shim.append("#include <Mouse.h>\n");
        }
        if (usesWifi || usesHttp || usesHttps) {
            shim.append("#include <WiFiS3.h>\n");
        }
        if (usesHttps) {
            shim.append("#include <WiFiSSLClient.h>\n");
        }
        if (usesHttp || usesHttps || usesJson) {
            shim.append("#include <string.h>\n");
        }
        if (usesFloat || usesDouble || usesJson) {
            shim.append("#include <math.h>\n");
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

                extern "C" void juno_serial_print_str(const char* value) {
                  Serial.print(value);
                }

                extern "C" void juno_serial_println_str(const char* value) {
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
        if (usesWifi) {
            shim.append("""

                    extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
                      WiFi.begin(ssid, password);
                    }

                    extern "C" int32_t juno_wifi_status() {
                      return static_cast<int32_t>(WiFi.status());
                    }
                    """);
        }
        if (usesLong) {
            shim.append(longHelpers());
        }
        if (usesFloat) {
            shim.append(floatHelpers());
        }
        if (usesDouble) {
            shim.append(doubleHelpers());
        }
        if (usesJson) {
            shim.append(jsonHelpers());
        }
        if (usesHttp || usesHttps) {
            shim.append(httpHelpers());
        }
        return shim.toString();
    }

    /**
     * {@code extern "C"} mirrors of {@link ArduinoCppBackend}'s own {@code juno_l*} helpers (same
     * bodies, already hardware-verified there via {@code long}'s own step-8 round trip) — reused
     * as-is rather than hand-rolled 64-bit assembly, consistent with this backend's existing
     * shim-delegation philosophy for anything nontrivial (see this class's doc).
     */
    private String longHelpers() {
        return """

                extern "C" int64_t juno_ladd(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_lsub(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_lmul(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) * static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_ldiv(int64_t a, int64_t b) {
                  if (b == 0) juno_panic();
                  if (a == INT64_MIN && b == -1) return INT64_MIN;
                  return a / b;
                }
                extern "C" int64_t juno_lrem(int64_t a, int64_t b) {
                  if (b == 0) juno_panic();
                  if (a == INT64_MIN && b == -1) return 0;
                  return a % b;
                }
                extern "C" int64_t juno_lneg(int64_t value) {
                  return static_cast<int64_t>(0ull - static_cast<uint64_t>(value));
                }
                extern "C" int64_t juno_lshl(int64_t a, int32_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) << (b & 63));
                }
                extern "C" int64_t juno_lshr(int64_t a, int32_t b) {
                  uint32_t shift = static_cast<uint32_t>(b) & 63u;
                  uint64_t value = static_cast<uint64_t>(a);
                  if (shift == 0 || a >= 0) return static_cast<int64_t>(value >> shift);
                  return static_cast<int64_t>((value >> shift) | (~0ull << (64u - shift)));
                }
                extern "C" int64_t juno_lushr(int64_t a, int32_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) >> (b & 63));
                }
                extern "C" int64_t juno_land(int64_t a, int64_t b) { return a & b; }
                extern "C" int64_t juno_lor(int64_t a, int64_t b) { return a | b; }
                extern "C" int64_t juno_lxor(int64_t a, int64_t b) { return a ^ b; }
                extern "C" int32_t juno_lcmp(int64_t a, int64_t b) {
                  return (a > b) - (a < b);
                }

                """;
    }

    /** {@code extern "C"} soft-float helpers — the RA4M1 (UNO R4's Cortex-M4) has no hardware FPU. */
    private String floatHelpers() {
        return """

                extern "C" float juno_fadd(float a, float b) { return a + b; }
                extern "C" float juno_fsub(float a, float b) { return a - b; }
                extern "C" float juno_fmul(float a, float b) { return a * b; }
                extern "C" float juno_fdiv(float a, float b) { return a / b; }
                extern "C" float juno_frem(float a, float b) { return fmodf(a, b); }
                extern "C" int32_t juno_fcmp(float a, float b, int32_t nanResult) {
                  if (isnan(a) || isnan(b)) return nanResult;
                  return (a > b) - (a < b);
                }
                extern "C" float juno_i2f(int32_t value) { return static_cast<float>(value); }
                extern "C" int32_t juno_f2i(float value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p31f) return INT32_MAX;
                  if (value <= -0x1.0p31f) return INT32_MIN;
                  return static_cast<int32_t>(value);
                }
                extern "C" float juno_l2f(int64_t value) { return static_cast<float>(value); }
                extern "C" int64_t juno_f2l(float value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p63f) return INT64_MAX;
                  if (value <= -0x1.0p63f) return INT64_MIN;
                  return static_cast<int64_t>(value);
                }

                """;
    }

    /** {@code extern "C"} soft-float helpers for {@code double}; see {@link #floatHelpers}. */
    private String doubleHelpers() {
        return """

                extern "C" double juno_dadd(double a, double b) { return a + b; }
                extern "C" double juno_dsub(double a, double b) { return a - b; }
                extern "C" double juno_dmul(double a, double b) { return a * b; }
                extern "C" double juno_ddiv(double a, double b) { return a / b; }
                extern "C" double juno_drem(double a, double b) { return fmod(a, b); }
                extern "C" int32_t juno_dcmp(double a, double b, int32_t nanResult) {
                  if (isnan(a) || isnan(b)) return nanResult;
                  return (a > b) - (a < b);
                }
                extern "C" double juno_i2d(int32_t value) { return static_cast<double>(value); }
                extern "C" int32_t juno_d2i(double value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p31) return INT32_MAX;
                  if (value <= -0x1.0p31) return INT32_MIN;
                  return static_cast<int32_t>(value);
                }
                extern "C" double juno_l2d(int64_t value) { return static_cast<double>(value); }
                extern "C" int64_t juno_d2l(double value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p63) return INT64_MAX;
                  if (value <= -0x1.0p63) return INT64_MIN;
                  return static_cast<int64_t>(value);
                }
                extern "C" double juno_f2d(float value) { return static_cast<double>(value); }
                extern "C" float juno_d2f(double value) { return static_cast<float>(value); }

                """;
    }

    /**
     * A bounded, non-allocating JSON scanner — an {@code extern "C"}-entry-point copy of
     * {@link ArduinoCppBackend#jsonHelpers}'s identical scanner (see its own doc for the algorithm);
     * duplicated rather than shared, consistent with every other shim in this backend already
     * duplicating its C++ backend counterpart (LedMatrix/Serial/Wifi) instead of sharing code across
     * the two independent backends.
     */
    private String jsonHelpers() {
        return """

                static constexpr int32_t JUNO_JSON_MISSING = 0;
                static constexpr int32_t JUNO_JSON_NULL = 1;
                static constexpr int32_t JUNO_JSON_BOOLEAN = 2;
                static constexpr int32_t JUNO_JSON_NUMBER = 3;
                static constexpr int32_t JUNO_JSON_STRING = 4;
                static constexpr int32_t JUNO_JSON_OBJECT = 5;
                static constexpr int32_t JUNO_JSON_ARRAY = 6;
                static constexpr int32_t JUNO_JSON_INVALID = 7;
                static constexpr int32_t JUNO_JSON_MAX_DEPTH = 32;

                static bool juno_json_is_space(char c) {
                  return c == ' ' || c == '\\t' || c == '\\r' || c == '\\n';
                }

                static void juno_json_skip_space(const char** cursor, const char* end) {
                  while (*cursor < end && juno_json_is_space(**cursor)) (*cursor)++;
                }

                static bool juno_json_is_hex(char c) {
                  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                }

                static bool juno_json_parse_string(const char* start, const char* end, const char** after) {
                  if (start >= end || *start != '"') return false;
                  const char* p = start + 1;
                  while (p < end) {
                    unsigned char c = static_cast<unsigned char>(*p++);
                    if (c == '"') { *after = p; return true; }
                    if (c < 0x20) return false;
                    if (c != '\\\\') continue;
                    if (p >= end) return false;
                    char escape = *p++;
                    if (escape == '"' || escape == '\\\\' || escape == '/' || escape == 'b'
                        || escape == 'f' || escape == 'n' || escape == 'r' || escape == 't') continue;
                    if (escape != 'u' || end - p < 4) return false;
                    for (int32_t i = 0; i < 4; i++) if (!juno_json_is_hex(p[i])) return false;
                    p += 4;
                  }
                  return false;
                }

                static bool juno_json_parse_number(const char* start, const char* end, const char** after) {
                  const char* p = start;
                  if (p < end && *p == '-') p++;
                  if (p >= end) return false;
                  if (*p == '0') {
                    p++;
                    if (p < end && *p >= '0' && *p <= '9') return false;
                  } else {
                    if (*p < '1' || *p > '9') return false;
                    do { p++; } while (p < end && *p >= '0' && *p <= '9');
                  }
                  if (p < end && *p == '.') {
                    p++;
                    if (p >= end || *p < '0' || *p > '9') return false;
                    do { p++; } while (p < end && *p >= '0' && *p <= '9');
                  }
                  if (p < end && (*p == 'e' || *p == 'E')) {
                    p++;
                    if (p < end && (*p == '+' || *p == '-')) p++;
                    if (p >= end || *p < '0' || *p > '9') return false;
                    do { p++; } while (p < end && *p >= '0' && *p <= '9');
                  }
                  *after = p;
                  return true;
                }

                static bool juno_json_parse_value(const char* start, const char* end,
                                                   const char** after, int32_t depth);

                static bool juno_json_parse_object(const char* start, const char* end,
                                                    const char** after, int32_t depth) {
                  const char* p = start + 1;
                  juno_json_skip_space(&p, end);
                  if (p < end && *p == '}') { *after = p + 1; return true; }
                  while (p < end) {
                    const char* keyEnd;
                    if (!juno_json_parse_string(p, end, &keyEnd)) return false;
                    p = keyEnd;
                    juno_json_skip_space(&p, end);
                    if (p >= end || *p++ != ':') return false;
                    juno_json_skip_space(&p, end);
                    if (!juno_json_parse_value(p, end, &p, depth + 1)) return false;
                    juno_json_skip_space(&p, end);
                    if (p < end && *p == '}') { *after = p + 1; return true; }
                    if (p >= end || *p++ != ',') return false;
                    juno_json_skip_space(&p, end);
                    if (p < end && *p == '}') return false;
                  }
                  return false;
                }

                static bool juno_json_parse_array(const char* start, const char* end,
                                                   const char** after, int32_t depth) {
                  const char* p = start + 1;
                  juno_json_skip_space(&p, end);
                  if (p < end && *p == ']') { *after = p + 1; return true; }
                  while (p < end) {
                    if (!juno_json_parse_value(p, end, &p, depth + 1)) return false;
                    juno_json_skip_space(&p, end);
                    if (p < end && *p == ']') { *after = p + 1; return true; }
                    if (p >= end || *p++ != ',') return false;
                    juno_json_skip_space(&p, end);
                    if (p < end && *p == ']') return false;
                  }
                  return false;
                }

                static bool juno_json_parse_value(const char* start, const char* end,
                                                   const char** after, int32_t depth) {
                  if (depth > JUNO_JSON_MAX_DEPTH) return false;
                  const char* p = start;
                  juno_json_skip_space(&p, end);
                  if (p >= end) return false;
                  if (*p == '"') return juno_json_parse_string(p, end, after);
                  if (*p == '{') return juno_json_parse_object(p, end, after, depth);
                  if (*p == '[') return juno_json_parse_array(p, end, after, depth);
                  if (end - p >= 4 && strncmp(p, "true", 4) == 0) { *after = p + 4; return true; }
                  if (end - p >= 5 && strncmp(p, "false", 5) == 0) { *after = p + 5; return true; }
                  if (end - p >= 4 && strncmp(p, "null", 4) == 0) { *after = p + 4; return true; }
                  return juno_json_parse_number(p, end, after);
                }

                static bool juno_json_scan_object(const char* objectStart, const char* objectEnd,
                                                   const char* key, int32_t keyLength,
                                                   const char** valueStart, const char** valueEnd) {
                  const char* p = objectStart + 1;
                  juno_json_skip_space(&p, objectEnd);
                  while (p < objectEnd && *p != '}') {
                    const char* contentStart = p + 1;
                    const char* keyEnd;
                    if (!juno_json_parse_string(p, objectEnd, &keyEnd)) return false;
                    bool matches = (keyEnd - contentStart - 1) == keyLength
                        && strncmp(contentStart, key, static_cast<size_t>(keyLength)) == 0;
                    p = keyEnd;
                    juno_json_skip_space(&p, objectEnd);
                    if (p >= objectEnd || *p++ != ':') return false;
                    juno_json_skip_space(&p, objectEnd);
                    const char* candidateStart = p;
                    const char* candidateEnd;
                    if (!juno_json_parse_value(p, objectEnd, &candidateEnd, 1)) return false;
                    if (matches) {
                      *valueStart = candidateStart;
                      *valueEnd = candidateEnd;
                      return true;
                    }
                    p = candidateEnd;
                    juno_json_skip_space(&p, objectEnd);
                    if (p < objectEnd && *p == ',') { p++; juno_json_skip_space(&p, objectEnd); }
                  }
                  return false;
                }

                static bool juno_json_scan_array(const char* arrayStart, const char* arrayEnd, int32_t index,
                                                  const char** valueStart, const char** valueEnd) {
                  const char* p = arrayStart + 1;
                  juno_json_skip_space(&p, arrayEnd);
                  int32_t current = 0;
                  while (p < arrayEnd && *p != ']') {
                    const char* candidateStart = p;
                    const char* candidateEnd;
                    if (!juno_json_parse_value(p, arrayEnd, &candidateEnd, 1)) return false;
                    if (current == index) {
                      *valueStart = candidateStart;
                      *valueEnd = candidateEnd;
                      return true;
                    }
                    current++;
                    p = candidateEnd;
                    juno_json_skip_space(&p, arrayEnd);
                    if (p < arrayEnd && *p == ',') { p++; juno_json_skip_space(&p, arrayEnd); }
                  }
                  return false;
                }

                static int32_t juno_json_locate(const uint8_t* buffer, int32_t length, const char* path,
                                                 const char** valueStart, const char** valueEnd) {
                  if (buffer == nullptr || path == nullptr || length < 0) return -1;
                  const char* documentStart = reinterpret_cast<const char*>(buffer);
                  const char* documentEnd = documentStart + length;
                  const char* rootStart = documentStart;
                  juno_json_skip_space(&rootStart, documentEnd);
                  const char* rootEnd;
                  if (!juno_json_parse_value(rootStart, documentEnd, &rootEnd, 0)) return -1;
                  const char* trailing = rootEnd;
                  juno_json_skip_space(&trailing, documentEnd);
                  if (trailing != documentEnd) return -1;

                  const char* currentStart = rootStart;
                  const char* currentEnd = rootEnd;
                  const char* cursor = path;
                  if (*cursor == '\\0') {
                    *valueStart = currentStart;
                    *valueEnd = currentEnd;
                    return 1;
                  }

                  while (*cursor != '\\0') {
                    if (*cursor == '[') {
                      if (*currentStart != '[') return 0;
                      cursor++;
                      if (*cursor < '0' || *cursor > '9') return -1;
                      int32_t index = 0;
                      if (*cursor == '0' && cursor[1] >= '0' && cursor[1] <= '9') return -1;
                      while (*cursor >= '0' && *cursor <= '9') {
                        int32_t digit = *cursor++ - '0';
                        if (index > (INT32_MAX - digit) / 10) return -1;
                        index = index * 10 + digit;
                      }
                      if (*cursor++ != ']') return -1;
                      if (!juno_json_scan_array(currentStart, currentEnd, index, &currentStart, &currentEnd)) return 0;
                    } else {
                      if (*currentStart != '{') return 0;
                      const char* segmentStart = cursor;
                      while (*cursor != '\\0' && *cursor != '.' && *cursor != '[') cursor++;
                      int32_t segmentLength = static_cast<int32_t>(cursor - segmentStart);
                      if (segmentLength == 0) return -1;
                      if (!juno_json_scan_object(currentStart, currentEnd, segmentStart, segmentLength,
                                                 &currentStart, &currentEnd)) return 0;
                    }

                    if (*cursor == '\\0') break;
                    if (*cursor == '[') continue;
                    if (*cursor != '.') return -1;
                    cursor++;
                    if (*cursor == '\\0' || *cursor == '.' || *cursor == '[') return -1;
                  }
                  *valueStart = currentStart;
                  *valueEnd = currentEnd;
                  return 1;
                }

                extern "C" int32_t juno_json_type(const uint8_t* buffer, int32_t length, const char* path) {
                  const char* start;
                  const char* end;
                  int32_t status = juno_json_locate(buffer, length, path, &start, &end);
                  if (status < 0) return JUNO_JSON_INVALID;
                  if (status == 0) return JUNO_JSON_MISSING;
                  if (*start == 'n') return JUNO_JSON_NULL;
                  if (*start == 't' || *start == 'f') return JUNO_JSON_BOOLEAN;
                  if (*start == '"') return JUNO_JSON_STRING;
                  if (*start == '{') return JUNO_JSON_OBJECT;
                  if (*start == '[') return JUNO_JSON_ARRAY;
                  return JUNO_JSON_NUMBER;
                }

                static bool juno_json_parse_long(const char* start, const char* end, int64_t* result) {
                  const char* parsedEnd;
                  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
                  bool negative = *start == '-';
                  const char* p = start + (negative ? 1 : 0);
                  uint64_t limit = negative ? static_cast<uint64_t>(INT64_MAX) + 1u
                                            : static_cast<uint64_t>(INT64_MAX);
                  uint64_t value = 0;
                  while (p < end) {
                    if (*p < '0' || *p > '9') return false;
                    uint64_t digit = static_cast<uint64_t>(*p++ - '0');
                    if (value > (limit - digit) / 10u) return false;
                    value = value * 10u + digit;
                  }
                  if (negative && value == static_cast<uint64_t>(INT64_MAX) + 1u) {
                    *result = INT64_MIN;
                  } else {
                    *result = negative ? -static_cast<int64_t>(value) : static_cast<int64_t>(value);
                  }
                  return true;
                }

                extern "C" int32_t juno_json_get_int(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
                  int64_t value;
                  if (!juno_json_parse_long(start, end, &value) || value < INT32_MIN || value > INT32_MAX) return 0;
                  return static_cast<int32_t>(value);
                }

                extern "C" int64_t juno_json_get_long(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
                  int64_t value;
                  return juno_json_parse_long(start, end, &value) ? value : 0;
                }

                static bool juno_json_parse_double(const char* start, const char* end, double* result) {
                  const char* parsedEnd;
                  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
                  bool negative = *start == '-';
                  const char* p = start + (negative ? 1 : 0);
                  double significand = 0.0;
                  int32_t significantDigits = 0;
                  int64_t decimalExponent = 0;
                  while (p < end && *p >= '0' && *p <= '9') {
                    int32_t digit = *p++ - '0';
                    if (significantDigits < 18 && (significantDigits > 0 || digit != 0)) {
                      significand = significand * 10.0 + digit;
                      significantDigits++;
                    } else if (significantDigits >= 18) {
                      decimalExponent++;
                    }
                  }
                  if (p < end && *p == '.') {
                    p++;
                    while (p < end && *p >= '0' && *p <= '9') {
                      int32_t digit = *p++ - '0';
                      if (significantDigits == 0 && digit == 0) {
                        decimalExponent--;
                      } else if (significantDigits < 18) {
                        significand = significand * 10.0 + digit;
                        significantDigits++;
                        decimalExponent--;
                      }
                    }
                  }
                  if (p < end && (*p == 'e' || *p == 'E')) {
                    p++;
                    bool exponentNegative = false;
                    if (*p == '+' || *p == '-') { exponentNegative = *p == '-'; p++; }
                    int32_t exponent = 0;
                    while (p < end && *p >= '0' && *p <= '9') {
                      int32_t digit = *p++ - '0';
                      if (exponent < 100000) exponent = exponent * 10 + digit;
                    }
                    decimalExponent += exponentNegative ? -static_cast<int64_t>(exponent) : exponent;
                  }
                  if (significand == 0.0) { *result = negative ? -0.0 : 0.0; return true; }
                  if (decimalExponent > 400) return false;
                  if (decimalExponent < -400) { *result = negative ? -0.0 : 0.0; return true; }
                  double scale = 1.0;
                  int32_t scalePower = static_cast<int32_t>(decimalExponent < 0 ? -decimalExponent : decimalExponent);
                  for (int32_t i = 0; i < scalePower; i++) scale *= 10.0;
                  double value = decimalExponent < 0 ? significand / scale : significand * scale;
                  if (!isfinite(value)) return false;
                  *result = negative ? -value : value;
                  return true;
                }

                extern "C" double juno_json_get_double(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0.0;
                  double value;
                  return juno_json_parse_double(start, end, &value) ? value : 0.0;
                }

                extern "C" int32_t juno_json_get_bool(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
                  return (end - start == 4 && strncmp(start, "true", 4) == 0) ? 1 : 0;
                }

                static int32_t juno_json_hex_value(char c) {
                  if (c >= '0' && c <= '9') return c - '0';
                  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                  return c - 'A' + 10;
                }

                static uint32_t juno_json_hex4(const char* p) {
                  uint32_t value = 0;
                  for (int32_t i = 0; i < 4; i++) value = value * 16u + static_cast<uint32_t>(juno_json_hex_value(p[i]));
                  return value;
                }

                static int32_t juno_json_utf8_length(uint32_t codePoint) {
                  if (codePoint <= 0x7fu) return 1;
                  if (codePoint <= 0x7ffu) return 2;
                  if (codePoint <= 0xffffu) return 3;
                  return 4;
                }

                static void juno_json_write_utf8(uint32_t codePoint, uint8_t* out, int32_t* written) {
                  int32_t p = *written;
                  if (codePoint <= 0x7fu) {
                    out[p++] = static_cast<uint8_t>(codePoint);
                  } else if (codePoint <= 0x7ffu) {
                    out[p++] = static_cast<uint8_t>(0xc0u | (codePoint >> 6));
                    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
                  } else if (codePoint <= 0xffffu) {
                    out[p++] = static_cast<uint8_t>(0xe0u | (codePoint >> 12));
                    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 6) & 0x3fu));
                    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
                  } else {
                    out[p++] = static_cast<uint8_t>(0xf0u | (codePoint >> 18));
                    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 12) & 0x3fu));
                    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 6) & 0x3fu));
                    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
                  }
                  *written = p;
                }

                extern "C" int32_t juno_json_get_string(const uint8_t* buffer, int32_t length, const char* key,
                                                          uint8_t* out, int32_t outLength) {
                  const char* start;
                  const char* end;
                  if (out == nullptr || outLength <= 0
                      || juno_json_locate(buffer, length, key, &start, &end) != 1
                      || start >= end || *start != '"') return 0;
                  const char* p = start + 1;
                  const char* contentEnd = end - 1;
                  int32_t written = 0;
                  while (p < contentEnd) {
                    unsigned char c = static_cast<unsigned char>(*p++);
                    if (c != '\\\\') {
                      if (written >= outLength) break;
                      out[written++] = c;
                      continue;
                    }
                    char escape = *p++;
                    if (escape != 'u') {
                      uint8_t decoded = static_cast<uint8_t>(escape);
                      if (escape == 'b') decoded = '\\b';
                      else if (escape == 'f') decoded = '\\f';
                      else if (escape == 'n') decoded = '\\n';
                      else if (escape == 'r') decoded = '\\r';
                      else if (escape == 't') decoded = '\\t';
                      if (written >= outLength) break;
                      out[written++] = decoded;
                      continue;
                    }
                    uint32_t codePoint = juno_json_hex4(p);
                    p += 4;
                    if (codePoint >= 0xd800u && codePoint <= 0xdbffu) {
                      if (contentEnd - p < 6 || p[0] != '\\\\' || p[1] != 'u') return 0;
                      uint32_t low = juno_json_hex4(p + 2);
                      if (low < 0xdc00u || low > 0xdfffu) return 0;
                      codePoint = 0x10000u + ((codePoint - 0xd800u) << 10) + (low - 0xdc00u);
                      p += 6;
                    } else if (codePoint >= 0xdc00u && codePoint <= 0xdfffu) {
                      return 0;
                    }
                    int32_t utf8Length = juno_json_utf8_length(codePoint);
                    if (written > outLength - utf8Length) break;
                    juno_json_write_utf8(codePoint, out, &written);
                  }
                  return written;
                }

                extern "C" int32_t juno_json_array_size(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1 || *start != '[') return -1;
                  const char* p = start + 1;
                  juno_json_skip_space(&p, end);
                  int32_t size = 0;
                  while (p < end && *p != ']') {
                    const char* elementEnd;
                    if (!juno_json_parse_value(p, end, &elementEnd, 1)) return -1;
                    if (size == INT32_MAX) return -1;
                    size++;
                    p = elementEnd;
                    juno_json_skip_space(&p, end);
                    if (p < end && *p == ',') { p++; juno_json_skip_space(&p, end); }
                  }
                  return size;
                }

                """;
    }

    /** Backs the HTTP/HTTPS APIs with a shared HTTP/1.1 codec over plain or TLS WiFi clients. */
    private String httpHelpers() {
        StringBuilder helpers = new StringBuilder("""

                template <typename Client>
                static int32_t juno_http_request(Client& client, const char* method, const char* host, int32_t port,
                                                  const char* path, const char* body,
                                                  uint8_t* responseBuffer, int32_t responseBufferLength) {
                  if (!client.connect(host, static_cast<uint16_t>(port))) {
                    return -1;
                  }
                  client.print(method);
                  client.print(' ');
                  client.print(path);
                  client.print(" HTTP/1.1\\r\\nHost: ");
                  client.print(host);
                  client.print("\\r\\nConnection: close\\r\\n");
                  if (body != nullptr) {
                    client.print("Content-Type: application/json\\r\\nContent-Length: ");
                    client.print(static_cast<unsigned long>(strlen(body)));
                    client.print("\\r\\n\\r\\n");
                    client.print(body);
                  } else {
                    client.print("\\r\\n");
                  }

                  const unsigned long deadline = millis() + 5000;
                  bool inBody = false;
                  char recent[4] = {0, 0, 0, 0};
                  bool chunked = false;
                  int32_t chunkedMatch = 0;
                  const char* chunkedMarker = "chunked";

                  int32_t written = 0;
                  int32_t chunkState = 0; // 0 = reading hex size, 1 = chunk data, 2 = trailing CRLF, 3 = done
                  int32_t chunkRemaining = 0;
                  int32_t chunkSizeValue = 0;
                  bool chunkExtension = false;

                  while (millis() < deadline) {
                    if (!client.available()) {
                      if (!client.connected()) break;
                      delay(1);
                      continue;
                    }
                    int value = client.read();
                    if (value < 0) break;
                    char c = static_cast<char>(value);

                    if (!inBody) {
                      char lower = (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
                      if (lower == chunkedMarker[chunkedMatch]) {
                        chunkedMatch++;
                        if (chunkedMarker[chunkedMatch] == 0) chunked = true;
                      } else {
                        chunkedMatch = (lower == chunkedMarker[0]) ? 1 : 0;
                      }
                      recent[0] = recent[1];
                      recent[1] = recent[2];
                      recent[2] = recent[3];
                      recent[3] = c;
                      if (recent[0] == '\\r' && recent[1] == '\\n' && recent[2] == '\\r' && recent[3] == '\\n') {
                        inBody = true;
                      }
                      continue;
                    }

                    if (!chunked) {
                      if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
                      written++;
                      continue;
                    }

                    switch (chunkState) {
                      case 0:
                        if (c == '\\r') break;
                        if (c == '\\n') {
                          chunkRemaining = chunkSizeValue;
                          chunkSizeValue = 0;
                          chunkExtension = false;
                          chunkState = (chunkRemaining == 0) ? 3 : 1;
                          break;
                        }
                        if (chunkExtension) break;
                        if (c >= '0' && c <= '9') chunkSizeValue = chunkSizeValue * 16 + (c - '0');
                        else if (c >= 'a' && c <= 'f') chunkSizeValue = chunkSizeValue * 16 + (c - 'a' + 10);
                        else if (c >= 'A' && c <= 'F') chunkSizeValue = chunkSizeValue * 16 + (c - 'A' + 10);
                        else chunkExtension = true;
                        break;
                      case 1:
                        if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
                        written++;
                        chunkRemaining--;
                        if (chunkRemaining == 0) chunkState = 2;
                        break;
                      case 2:
                        if (c == '\\n') chunkState = 0;
                        break;
                      default:
                        break;
                    }
                    if (chunkState == 3) break;
                  }
                  client.stop();
                  return written < responseBufferLength ? written : responseBufferLength;
                }

                """);
        if (usesHttp) {
            helpers.append("""

                    extern "C" int32_t juno_http_get(const char* host, int32_t port, const char* path,
                                                      uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiClient client;
                      return juno_http_request(client, "GET", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_http_post(const char* host, int32_t port, const char* path, const char* body,
                                                       uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiClient client;
                      return juno_http_request(client, "POST", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_http_delete(const char* host, int32_t port, const char* path,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiClient client;
                      return juno_http_request(client, "DELETE", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_http_patch(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiClient client;
                      return juno_http_request(client, "PATCH", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_http_query(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiClient client;
                      return juno_http_request(client, "QUERY", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    """);
        }
        if (usesHttps) {
            helpers.append("""

                    extern "C" int32_t juno_https_get(const char* host, int32_t port, const char* path,
                                                       uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiSSLClient client;
                      return juno_http_request(client, "GET", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_https_post(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiSSLClient client;
                      return juno_http_request(client, "POST", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_https_delete(const char* host, int32_t port, const char* path,
                                                          uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiSSLClient client;
                      return juno_http_request(client, "DELETE", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_https_patch(const char* host, int32_t port, const char* path, const char* body,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiSSLClient client;
                      return juno_http_request(client, "PATCH", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    extern "C" int32_t juno_https_query(const char* host, int32_t port, const char* path, const char* body,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength) {
                      WiFiSSLClient client;
                      return juno_http_request(client, "QUERY", host, port, path, body,
                                               responseBuffer, responseBufferLength);
                    }

                    """);
        }
        return helpers.toString();
    }

    private CompileException unsupported(String detail) {
        return new CompileException(
                "The experimental Cortex-M4 assembly backend does not support this yet: " + detail);
    }
}
