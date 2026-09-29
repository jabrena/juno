package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emits GNU ARM (Cortex-M4, Thumb-2) assembly straight from Juno IR: every reachable method becomes its own
 * function (real {@code bl} calls between them, full AAPCS parameter passing including stack-passed
 * arguments beyond the first four), with branches, {@code switch}, {@code int} arithmetic/comparisons,
 * fixed-size arrays, arena-allocated objects with fields, mutable static fields, GPIO/delay,
 * {@code LedMatrix}, {@code Serial}, {@code Mouse}, {@code Wifi}, {@code HttpClient}/{@code HttpsClient},
 * {@code Json}, compiler-generated constant arrays used by enums, and
 * {@code long}/{@code float}/{@code double} support. Calls Juno can't statically resolve, and
 * instruction kinds with no codegen yet fail loudly with {@link CompileException} rather than emitting
 * code that looks plausible but was never checked.
 *
 * <h2>{@code long}/{@code float}/{@code double}</h2>
 * 64-bit values don't fit a single register and the generated assembly keeps float values in core
 * registers (never VFP registers), so — consistent with this class's existing shim-delegation philosophy — every nontrivial
 * {@code long}/{@code float}/{@code double} operation (arithmetic, comparison, conversion) is a call
 * to a small {@code extern "C"} runtime-shim function (see {@code ShimLibraries}'s
 * {@code long}/{@code float}/{@code double} helpers) rather than hand-rolled soft-float assembly. A {@code long} keeps this
 * backend's existing split-low/high-word representation (two ordinary {@code int32} stack slots);
 * {@code float}/{@code double} are new {@link JunoType#FLOAT32}/{@link JunoType#FLOAT64} stack slots
 * (4/8 bytes, holding the raw IEEE-754 bit pattern) — see {@code FrameLayout}. Every JVM local slot
 * is a full 8 bytes regardless of its actual type, so a {@code double} local can never overlap the next slot's
 * storage.
 *
 * <h2>{@code HttpClient}/{@code HttpsClient}/{@code Json}</h2>
 * Backed by an {@code extern "C"} HTTP/1.1 codec and allocation-free JSON scanner in the generated
 * runtime shim. A call needing more than four argument words (e.g.
 * {@code Json.getString}'s five) spills the overflow onto a transient stack area via
 * {@code AsmEmitter#emitShimCall}, the same mechanism {@link #emitCall} already uses for user methods.
 *
 * <h2>Storage model</h2>
 * Unlike a register-allocating backend, every IR {@code Value} and every JVM local variable slot
 * lives in a fixed offset in its method's own stack frame (never in a register across instructions) —
 * see {@code FrameLayout}. This trades code density for a design that can't run out of registers
 * regardless of how many live values a method has, which matters once methods stop being
 * one-block-with-three-intrinsic-calls (helper methods with many {@code int} parameters, or
 * {@code LedMatrixAsciiScroll}, whose full-ASCII-font dispatch tree reaches 116 methods). Registers are used only as scratch within a single instruction's codegen. Arena
 * objects and mutable static fields follow the same principle, without needing any class-file metadata
 * this backend doesn't already have: an object's fields are exactly whichever ones some
 * {@code LoadField}/{@code StoreField} in the whole program actually touches, laid out in
 * {@code FieldRef.displayName()} order;
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
 *
 * <h2>Structure</h2>
 * This class drives code generation — function labels, prologues/epilogues, calls between
 * generated methods, control flow, and the per-instruction dispatch — and delegates the rest to
 * package-private collaborators: {@code ProgramLayout} (object/field/static/literal storage and the
 * data sections), {@code AsmEmitter} (stack-slot moves, immediates, AAPCS shim calls, labels),
 * {@code IntArithmeticLowering}, {@code WideArithmeticLowering}, {@code ArrayLowering},
 * {@code IntrinsicLowering} (a table of intrinsic-to-shim-call lowerings), and {@code RuntimeShim}
 * (the C++ shim, assembled from the {@code ShimFeature}s the lowerings recorded).
 */
public final class CortexM4AsmBackend {
    /**
     * Bytes of stack the entry-point prologue zeroes below the captured {@code juno_gc_stack_top}
     * before anything else runs. A reset (watchdog, reset-button, or otherwise) reloads {@code sp}
     * from the vector table and the standard startup sequence re-zeroes {@code .bss}/{@code .data}
     * (which is why {@code juno_arena} and the allocator's own bookkeeping always come back clean),
     * but it does not clear the stack region itself — confirmed on real hardware: a program that
     * panics mid-collection, then resets, starts its next run with the previous run's stack bytes
     * (including arena-address-shaped garbage) still physically present, until fresh calls overwrite
     * them. The conservative collector can't tell that stale data apart from a real live root, so
     * without this, a post-reset run can find its own arena looking artificially "full of reachable
     * garbage" from its very first collection. Fixed and conservative rather than reaching for the
     * real linker-script stack limit: comfortably above what a Juno program's deepest call path
     * typically needs (see {@code RuntimeRiskAnalyzer}'s own "generated locals on deepest call path"
     * estimate), without depending on linker-script details this backend doesn't otherwise use.
     */
    private static final int STACK_ZERO_BYTES = 4096;

    /**
     * {@code runtimeShim} is a small {@code extern "C"} C++ source that must be compiled alongside
     * {@code assembly} in the same sketch — see this class's doc.
     */
    public record Output(String assembly, String runtimeShim, String entryPointSymbol) {
    }

    private final Map<MethodRef, String> functionLabels = new LinkedHashMap<>();
    private final Set<ShimFeature> features = EnumSet.noneOf(ShimFeature.class);
    private final Set<Intrinsic> usedMath = EnumSet.noneOf(Intrinsic.class);
    private final boolean gcLoggingEnabled;
    private final Board board;
    private MethodRef entryPoint;
    private final List<String> clinitLabels = new ArrayList<>();
    private boolean usesWatchdog;
    private ProgramLayout layout;
    private AsmEmitter asm;
    private IntArithmeticLowering ints;
    private WideArithmeticLowering wides;
    private ArrayLowering arrays;
    private IntrinsicLowering intrinsics;

    public CortexM4AsmBackend() {
        this(false);
    }

    public CortexM4AsmBackend(boolean gcLoggingEnabled) {
        this(gcLoggingEnabled, Board.DEFAULT);
    }

    /**
     * @param gcLoggingEnabled when {@code true}, {@code juno_gc_collect()} in the generated runtime
     *     shim prints one {@code Serial} line per collection (arena bytes used before/after), letting
     *     {@code juno:monitor} show reclamation happening in real time. {@code false} (the default)
     *     costs zero extra flash/RAM/time: the print statements aren't emitted at all, not merely
     *     disabled at runtime.
     * @param board the target board; its {@link Board#core() Arduino core} selects the
     *     {@link CoreRuntime} glue, and its capabilities gate optional shim blocks
     */
    public CortexM4AsmBackend(boolean gcLoggingEnabled, Board board) {
        this.gcLoggingEnabled = gcLoggingEnabled;
        this.board = board;
    }

    public Output generate(IrProgram program) {
        entryPoint = program.entryPoint();
        usesWatchdog = program.watchdogTimeoutMillis().isPresent();
        for (int index = 0; index < program.methods().size(); index++) {
            IrMethod method = program.methods().get(index);
            String label = method.reference().equals(entryPoint)
                    ? asmFunctionName(method) : "juno_fn" + index;
            functionLabels.put(method.reference(), label);
            if (method.reference().name().equals("<clinit>")) {
                clinitLabels.add(label);
            }
        }
        layout = new ProgramLayout(program);
        asm = new AsmEmitter(layout.stringLiteralSymbols());
        ints = new IntArithmeticLowering(asm);
        wides = new WideArithmeticLowering(asm, features);
        arrays = new ArrayLowering(asm);
        CoreRuntime coreRuntime = CoreRuntime.of(board.core());
        intrinsics = new IntrinsicLowering(asm, coreRuntime, features, usedMath);

        StringBuilder output = new StringBuilder();
        output.append("@ Generated by Juno's Cortex-M4 assembly backend. Do not edit.\n")
                .append("@ Author: Juan Antonio Brena Moral\n")
                .append("@ Entry point: ")
                .append(program.entryPoint().displayName())
                .append('\n')
                .append("    .syntax unified\n")
                .append("    .thumb\n");
        layout.emitDataSections(output);
        output.append("    .text\n");
        for (IrMethod method : program.methods()) {
            emitMethod(output, method);
        }
        String runtimeShim = new RuntimeShim(board, coreRuntime, gcLoggingEnabled, features, usedMath,
                layout.throwableClasses(), program.watchdogTimeoutMillis()).generate();
        return new Output(output.toString(), runtimeShim, functionLabels.get(entryPoint));
    }

    private void emitMethod(StringBuilder output, IrMethod method) {
        String label = functionLabels.get(method.reference());
        boolean isEntryPoint = method.reference().equals(entryPoint);
        FrameLayout frame = FrameLayout.of(method);
        // JVMS 2.6.1: for a non-static method, local 0 is the implicit `this`/receiver, which the
        // descriptor's own parameter list never includes — the caller side (BytecodeToIr's lowering
        // of a Call) already prepends the receiver to `arguments()`, so it always arrives as the
        // first incoming word (r0), ahead of any declared parameter.
        int parameterCount = Descriptor.parse(method.reference().descriptor()).parameters().size()
                + (method.isStatic() ? 0 : 1);

        output.append('\n');
        if (isEntryPoint) {
            output.append("    .global ").append(label).append('\n');
        }
        output.append("    .type ").append(label).append(", %function\n")
                .append(label).append(":\n");
        if (isEntryPoint) {
            // Captured before this function's own prologue touches sp at all: everything the
            // program ever runs happens in frames below this point, so this is a sound upper bound
            // for the conservative GC's stack scan (see runtimeShim's juno_gc_stack_top/juno_gc_mark).
            output.append("    ldr r0, =juno_gc_stack_top\n")
                    .append("    mov r1, sp\n")
                    .append("    str r1, [r0]\n");
            // Zero STACK_ZERO_BYTES below the captured top before anything else runs (see that
            // constant's doc): a reset doesn't clear the stack the way it clears .bss/.data, so
            // stale pointer-shaped bytes from a PREVIOUS run could otherwise be misread as live
            // roots by this run's own first collection. Done here in plain assembly, before the
            // push below, since a called C++ helper couldn't safely zero this region without first
            // spilling lr into it.
            asm.emitLoadImmediate(output, "r2", STACK_ZERO_BYTES);
            output.append("    sub r2, r1, r2\n")
                    .append("    movs r3, #0\n")
                    .append(".LjunoZeroStack:\n")
                    .append("    cmp r2, r1\n")
                    // Addresses are unsigned: bhs (unsigned >=), not bge (signed >=).
                    .append("    bhs .LjunoZeroStackDone\n")
                    .append("    str r3, [r2]\n")
                    .append("    add r2, r2, #4\n")
                    .append("    b .LjunoZeroStack\n")
                    .append(".LjunoZeroStackDone:\n");
        }
        output.append("    push {r4-r11, lr}\n");
        if (frame.frameSize() > 0) {
            // A plain immediate `sub sp,sp,#N` only encodes up to 4095, and large frames exceed
            // that; r12 (AAPCS "ip", always caller-saved/scratch) is free here without disturbing the
            // incoming r0-r3 parameters emitParameterSpill is about to read.
            asm.emitLoadImmediate(output, "r12", frame.frameSize());
            output.append("    sub sp, sp, r12\n");
        }
        emitParameterSpill(output, frame, parameterCount);
        // Enabled before anything else the program does (including <clinit>, below), so @Watchdog
        // protects the whole program lifetime, not just the user's own main() body.
        if (isEntryPoint && usesWatchdog) {
            output.append("    bl juno_watchdog_begin\n");
        }
        // Each reachable <clinit> is its own method in the IR, but nothing calls it there — the entry
        // point invokes every one explicitly, before its own first block, so each runs exactly once
        // up front, in the linker's order (the main class's own initializer first).
        if (isEntryPoint) {
            for (String clinitLabel : clinitLabels) {
                output.append("    bl ").append(clinitLabel).append('\n');
            }
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
     * local slots (JVMS 2.6.1: a static method's formal parameters occupy locals 0..k-1; a non-static
     * method's receiver occupies local 0 first, with {@code parameterCount} including it — see the
     * caller). Stack-passed args sit at {@code [caller's sp at the `bl`] + 4*(i-4)}; after this
     * function's own prologue that address is {@code frame.frameSize() + AsmEmitter.PUSH_BYTES} higher than the
     * current {@code sp}.
     */
    private void emitParameterSpill(StringBuilder output, FrameLayout frame, int parameterCount) {
        for (int i = 0; i < parameterCount; i++) {
            int localOffset = frame.localOffset(i);
            if (i < 4) {
                asm.emitStore(output, "r" + i, localOffset);
            } else {
                int callerOffset = frame.frameSize() + AsmEmitter.PUSH_BYTES + (i - 4) * AsmEmitter.WORD;
                asm.emitLoad(output, "r0", callerOffset);
                asm.emitStore(output, "r0", localOffset);
            }
        }
    }

    private void emitInstruction(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.Const constant -> {
                asm.emitLoadImmediate(output, "r0", constant.value());
                asm.store(output, frame, "r0", constant.target());
            }
            case IrInstruction.StringConst constant -> {
                asm.emitStringAddress(output, "r0", constant.value());
                asm.store(output, frame, "r0", constant.target());
            }
            case IrInstruction.LoadLocal load -> emitLoadLocal(output, frame, load);
            case IrInstruction.StoreLocal storeLocal -> emitStoreLocal(output, frame, storeLocal);
            case IrInstruction.Binary binary -> ints.emitBinary(output, frame, binary);
            case IrInstruction.Unary unary -> ints.emitUnary(output, frame, unary);
            case IrInstruction.Compare compare -> ints.emitCompare(output, frame, compare);
            case IrInstruction.Call call -> emitCall(output, frame, call);
            case IrInstruction.IntrinsicCall call -> intrinsics.emit(output, frame, call);
            case IrInstruction.NewArray newArray -> arrays.emitNewArray(output, frame, newArray);
            case IrInstruction.NewMultiArray array -> arrays.emitNewMultiArray(output, frame, array);
            case IrInstruction.ArrayLoad load -> arrays.emitArrayLoad(output, frame, load);
            case IrInstruction.ArrayStore store -> arrays.emitArrayStore(output, frame, store);
            case IrInstruction.BoundsCheck check -> arrays.emitBoundsCheck(output, frame, check);
            case IrInstruction.Panic ignored -> output.append("    bl juno_panic\n");
            case IrInstruction.NewObject object -> emitNewObject(output, frame, object);
            case IrInstruction.LoadField load -> {
                asm.load(output, frame, "r0", load.receiver());
                output.append("    ldr r1, [r0, #").append(layout.fieldOffset(load.field())).append("]\n");
                asm.store(output, frame, "r1", load.target());
            }
            case IrInstruction.StoreField storeField -> {
                asm.load(output, frame, "r0", storeField.receiver());
                asm.load(output, frame, "r1", storeField.value());
                output.append("    str r1, [r0, #").append(layout.fieldOffset(storeField.field())).append("]\n");
            }
            case IrInstruction.LoadStatic load -> {
                output.append("    ldr r0, =").append(layout.staticSymbol(load.field())).append('\n')
                        .append("    ldr r0, [r0]\n");
                asm.store(output, frame, "r0", load.target());
            }
            case IrInstruction.StoreStatic storeStatic -> {
                asm.load(output, frame, "r0", storeStatic.value());
                output.append("    ldr r1, =").append(layout.staticSymbol(storeStatic.field())).append('\n')
                        .append("    str r0, [r1]\n");
            }
            case IrInstruction.IntArrayConst array -> {
                output.append("    ldr r0, =").append(layout.intArraySymbol(array)).append('\n');
                asm.store(output, frame, "r0", array.target());
            }
            default -> wides.emit(output, frame, instruction);
        }
    }

    private void emitLoadLocal(StringBuilder output, FrameLayout frame, IrInstruction.LoadLocal load) {
        if (load.target().type() == JunoType.FLOAT64) {
            asm.emitLoad(output, "r0", frame.localOffset(load.local()));
            asm.emitLoad(output, "r1", frame.localOffset(load.local()) + AsmEmitter.WORD);
            asm.store64(output, frame, "r0", "r1", load.target());
        } else {
            asm.emitLoad(output, "r0", frame.localOffset(load.local()));
            asm.store(output, frame, "r0", load.target());
        }
    }

    private void emitStoreLocal(StringBuilder output, FrameLayout frame, IrInstruction.StoreLocal storeLocal) {
        if (storeLocal.value().type() == JunoType.FLOAT64) {
            asm.load64(output, frame, "r0", "r1", storeLocal.value());
            asm.emitStore(output, "r0", frame.localOffset(storeLocal.local()));
            asm.emitStore(output, "r1", frame.localOffset(storeLocal.local()) + AsmEmitter.WORD);
        } else {
            asm.load(output, frame, "r0", storeLocal.value());
            asm.emitStore(output, "r0", frame.localOffset(storeLocal.local()));
        }
    }

    private void emitNewObject(StringBuilder output, FrameLayout frame, IrInstruction.NewObject object) {
        asm.emitLoadImmediate(output, "r0", layout.objectSize(object.className()));
        asm.emitLoadImmediate(output, "r1", AsmEmitter.WORD);
        output.append("    bl juno_alloc\n");
        int classId = layout.throwableClasses().indexOf(object.className());
        if (classId >= 0) {
            // juno_alloc zero-fills, so the message word already starts out null.
            asm.emitLoadImmediate(output, "r1", classId);
            output.append("    str r1, [r0, #").append(ThrowableTypes.CLASS_ID_OFFSET).append("]\n");
        }
        asm.store(output, frame, "r0", object.target());
    }

    /** Loads/stores each argument, staging any beyond the first four onto a transient stack area (AAPCS). */
    private void emitCall(StringBuilder output, FrameLayout frame, IrInstruction.Call call) {
        String label = functionLabels.get(call.method());
        if (label == null) {
            throw unsupported("call to unresolved method " + call.method().displayName());
        }
        List<Value> arguments = call.arguments();
        int extra = Math.max(0, arguments.size() - 4);
        int reserved = AsmEmitter.roundUp(extra * AsmEmitter.WORD, 8);
        if (reserved > 0) {
            output.append("    sub sp, sp, #").append(reserved).append('\n');
            for (int i = 4; i < arguments.size(); i++) {
                asm.emitLoad(output, "r0", frame.valueOffset(arguments.get(i)) + reserved);
                asm.emitStore(output, "r0", (i - 4) * AsmEmitter.WORD);
            }
        }
        for (int i = 0; i < Math.min(4, arguments.size()); i++) {
            asm.emitLoad(output, "r" + i, frame.valueOffset(arguments.get(i)) + reserved);
        }
        output.append("    bl ").append(label).append('\n');
        if (reserved > 0) {
            output.append("    add sp, sp, #").append(reserved).append('\n');
        }
        call.target().ifPresent(target -> asm.store(output, frame, "r0", target));
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
                asm.load(output, frame, "r0", branch.condition());
                // A conditional branch (beq/bne/...) only has a short encoded range; the true/false
                // blocks can be arbitrarily far away in a large method. So the *conditional* hop only
                // ever jumps a few bytes, to a label right here, and the actual (possibly far) jumps
                // are unconditional `b`, which the assembler widens to whatever range it needs.
                String falseLabel = asm.newLabel(".Lbranchfalse");
                output.append("    cmp r0, #0\n")
                        .append("    beq ").append(falseLabel).append('\n')
                        .append("    b .L").append(label).append("block").append(branch.trueTarget()).append('\n')
                        .append(falseLabel).append(":\n")
                        .append("    b .L").append(label).append("block").append(branch.falseTarget()).append('\n');
            }
            case IrTerminator.Return returned -> {
                returned.value().ifPresent(value -> asm.load(output, frame, "r0", value));
                if (frame.frameSize() > 0) {
                    asm.emitLoadImmediate(output, "r12", frame.frameSize());
                    output.append("    add sp, sp, r12\n");
                }
                output.append("    pop {r4-r11, pc}\n");
            }
            case IrTerminator.Switch switched -> {
                for (int target : switched.targets()) {
                    emitYieldIfBackedge(output, block.start(), target);
                }
                emitYieldIfBackedge(output, block.start(), switched.defaultTarget());
                asm.load(output, frame, "r0", switched.selector());
                for (int i = 0; i < switched.keys().size(); i++) {
                    // Same short-conditional-hop/long-unconditional-jump idiom as Branch, chained:
                    // each case either jumps straight to its (possibly far) target, or falls through
                    // to the next case's check.
                    String nextCheckLabel = asm.newLabel(".Lswitchnext");
                    asm.emitLoadImmediate(output, "r1", switched.keys().get(i));
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

    /** Keeps the core's USB service polled on every loop backedge. */
    private void emitYieldIfBackedge(StringBuilder output, int blockStart, int target) {
        if (target <= blockStart) {
            output.append("    bl yield\n");
        }
    }

    private String asmFunctionName(IrMethod method) {
        String owner = method.reference().owner();
        String simpleName = owner.substring(owner.lastIndexOf('/') + 1);
        return "juno_" + simpleName + "_asm";
    }

    static CompileException unsupported(String detail) {
        return new CompileException(
                "The experimental Cortex-M4 assembly backend does not support this yet: " + detail);
    }
}
