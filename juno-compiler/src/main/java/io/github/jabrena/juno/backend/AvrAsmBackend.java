package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Descriptor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emits GNU AVR assembly (avrxmega3, the UNO WiFi Rev2's ATmega4809) straight from Juno IR — the 8-bit counterpart of
 * {@link Thumb2AsmBackend}, with the same storage model: every IR value and JVM local lives in a fixed stack slot
 * (see {@link FrameLayout#packed}) and registers are only scratch within one instruction, so a method can never run
 * out of registers.
 *
 * <h2>Java types on an 8-bit core</h2>
 * Java's types keep their JVM widths: an {@code int} is four bytes and every {@code int} operation works on all of
 * them (add/subtract/logic and constant shifts inline; multiply, divide, remainder and variable shifts in shim
 * helpers with the JVM's wrap-around, {@code MIN_VALUE / -1} and shift-masking rules). A {@code long} is a pair of
 * such words, and {@code float}/{@code double} are their raw IEEE-754 bits: avr-gcc's own {@code double} is only 32
 * bits wide, so every floating-point operation runs in the shim's software binary64 ({@link AvrRuntimeShim}), never
 * in the toolchain's float library. A reference is a 16-bit address in a 32-bit word (see {@link AvrEmitter}), so
 * objects and arrays keep the same four-byte field and element layout as on Thumb-2.
 *
 * <h2>Calling convention</h2>
 * Generated functions pass argument words through the global {@code juno_avr_args} area: a caller stores them and
 * calls, and the callee's first act is to copy the ones it reads into its local slots, so a nested call can reuse
 * the area. Results come back in avr-gcc's return registers (r22-r25, or r18-r25 for a {@code long}/{@code double}).
 * Runtime-shim calls use the same area through generated C++ thunks (see {@link AvrShimThunks}), which convert each
 * word to the function's real C parameter type — the AAPCS word layout of {@link IntrinsicLowering}'s table is kept,
 * so the table is shared with Thumb-2. Generated code clobbers any register but Y (r29:r28) and r1; the entry point,
 * the one function C code calls, saves r2-r17 for its caller.
 *
 * <p>Threads, structured concurrency and the double-precision transcendental {@code Math}
 * functions are capabilities the UNO WiFi Rev2 does not have (see {@code Board}), so the linker rejects them before
 * code generation; anything else this backend cannot lower fails with a {@link CompileException}.
 */
public final class AvrAsmBackend {
    private static final int A = AvrEmitter.A;
    private static final int B = AvrEmitter.B;

    /** The generated assembly, its runtime shim, and the entry point's symbol. */
    public record Output(String assembly, String runtimeShim, String entryPointSymbol) {
    }

    private final Map<MethodRef, String> functionLabels = new LinkedHashMap<>();
    private final Set<ShimFeature> features = EnumSet.noneOf(ShimFeature.class);
    private final Set<Intrinsic> usedMath = EnumSet.noneOf(Intrinsic.class);
    private final boolean gcLoggingEnabled;
    private final Board board;
    private final RuntimeConfig runtimeConfig;
    private final List<String> clinitLabels = new ArrayList<>();
    private MethodRef entryPoint;
    private ProgramLayout layout;
    private AvrEmitter avr;
    private IntrinsicLowering intrinsics;
    private AvrIntArithmeticLowering ints;
    private AvrWideArithmeticLowering wides;
    private AvrMemoryLowering memory;
    private AvrCallLowering calls;

    /**
     * @param board an AVR board; its core selects the shim's core glue
     * @param runtimeConfig the arena capacity of the generated runtime shim
     */
    // entryPoint, layout, avr and the lowering helpers are per-program state that generate() assigns before any use.
    @SuppressWarnings("NullAway.Init")
    public AvrAsmBackend(boolean gcLoggingEnabled, Board board, RuntimeConfig runtimeConfig) {
        if (board.core().architecture() != Architecture.AVR) {
            throw new IllegalArgumentException(board.displayName() + " is not an AVR board");
        }
        this.gcLoggingEnabled = gcLoggingEnabled;
        this.board = board;
        this.runtimeConfig = runtimeConfig;
    }

    public Output generate(IrProgram program) {
        entryPoint = program.entryPoint();
        for (int index = 0; index < program.methods().size(); index++) {
            IrMethod method = program.methods().get(index);
            MethodRef reference = method.reference();
            if (!reference.equals(entryPoint) && reference.owner().startsWith("java/lang/")
                    && reference.name().startsWith("$juno")) {
                throw unsupported("the runtime entry point " + reference.displayName() + " (threads)");
            }
            String label = reference.equals(entryPoint) ? "juno_" + simpleName(reference) + "_asm" : "juno_fn" + index;
            functionLabels.put(reference, label);
            if (reference.name().equals("<clinit>")) {
                clinitLabels.add(label);
            }
        }
        layout = new ProgramLayout(program);
        avr = new AvrEmitter(layout.stringLiteralSymbols());
        ints = new AvrIntArithmeticLowering(avr, features);
        wides = new AvrWideArithmeticLowering(avr, features);
        memory = new AvrMemoryLowering(avr, layout);
        calls = new AvrCallLowering(avr, layout, memory, functionLabels);
        CoreRuntime coreRuntime = CoreRuntime.of(board.core());
        intrinsics = new IntrinsicLowering(new AvrIntrinsicTarget(avr), coreRuntime, features, usedMath, false);

        StringBuilder text = new StringBuilder();
        for (IrMethod method : program.methods()) {
            emitMethod(text, method);
        }
        StringBuilder output = new StringBuilder();
        output.append("; Generated by Juno's AVR assembly backend. Do not edit.\n")
                .append("; Author: Juan Antonio Brena Moral\n")
                .append("; Entry point: ").append(program.entryPoint().displayName()).append('\n');
        layout.emitDataSections(output, ProgramLayout.DataDirectives.AVR);
        output.append("    .section .bss\n")
                .append("    .global ").append(AvrEmitter.ARGUMENTS).append('\n')
                .append(AvrEmitter.ARGUMENTS).append(":\n")
                .append("    .space ").append(Math.max(1, avr.argumentWords()) * AvrEmitter.WORD).append('\n')
                .append("    .text\n")
                .append(text);
        String runtimeShim = new AvrRuntimeShim(coreRuntime, runtimeConfig, gcLoggingEnabled, features, usedMath,
                layout.throwableClasses(), avr.thunkFunctions(), avr.argumentWords()).generate();
        return new Output(output.toString(), runtimeShim, Objects.requireNonNull(functionLabels.get(entryPoint)));
    }

    private static String simpleName(MethodRef method) {
        String owner = method.owner();
        return owner.substring(owner.lastIndexOf('/') + 1);
    }

    private void emitMethod(StringBuilder output, IrMethod method) {
        String label = Objects.requireNonNull(functionLabels.get(method.reference()));
        boolean isEntryPoint = method.reference().equals(entryPoint);
        FrameLayout frame = FrameLayout.packed(method);
        output.append('\n');
        if (isEntryPoint) {
            output.append("    .global ").append(label).append('\n');
        }
        output.append("    .type ").append(label).append(", @function\n").append(label).append(":\n");
        if (isEntryPoint) {
            emitEntryPrologue(output);
        }
        output.append("    push r28\n")
                .append("    push r29\n")
                .append("    in r28, 0x3d\n")
                .append("    in r29, 0x3e\n");
        if (frame.frameSize() > 0) {
            adjustFrame(output, -frame.frameSize());
        }
        emitParameterSpill(output, frame, method);
        if (isEntryPoint) {
            for (String clinitLabel : clinitLabels) {
                output.append("    call ").append(clinitLabel).append('\n');
                emitEscapeCheck(output);
            }
        }
        CompareFusion fusion = new CompareFusion(method);
        for (IrBasicBlock block : method.blocks()) {
            output.append(".L").append(label).append("block").append(block.start()).append(":\n");
            IrInstruction.Compare fused = fusion.fusedCompare(block);
            List<IrInstruction> instructions = fused == null ? block.instructions()
                    : block.instructions().subList(0, block.instructions().size() - 1);
            for (IrInstruction instruction : instructions) {
                emitInstruction(output, frame, instruction);
            }
            emitTerminator(output, frame, label, block, fused, isEntryPoint);
        }
    }

    /**
     * Before anything else: records the stack top the conservative collector scans up to, zeroes the free RAM
     * between the heap and the stack (a reset keeps the previous run's stack bytes, which would otherwise look
     * like live references), and saves the call-saved registers of the C caller.
     */
    private void emitEntryPrologue(StringBuilder output) {
        output.append("    in r24, 0x3d\n")
                .append("    in r25, 0x3e\n")
                .append("    sts juno_gc_stack_top, r24\n")
                .append("    sts juno_gc_stack_top+1, r25\n")
                // From malloc's break when a library already used the heap, else from the heap's start.
                .append("    lds r26, __brkval\n")
                .append("    lds r27, __brkval+1\n")
                .append("    mov r0, r26\n")
                .append("    or r0, r27\n")
                .append("    brne .LjunoZeroStack\n")
                .append("    ldi r26, lo8(__heap_start)\n")
                .append("    ldi r27, hi8(__heap_start)\n")
                .append(".LjunoZeroStack:\n")
                .append("    cp r26, r24\n")
                .append("    cpc r27, r25\n")
                .append("    brsh .LjunoZeroStackDone\n")
                .append("    st X+, r1\n")
                .append("    rjmp .LjunoZeroStack\n")
                .append(".LjunoZeroStackDone:\n");
        for (int register = 2; register <= 17; register++) {
            output.append("    push r").append(register).append('\n');
        }
    }

    /** SP = Y = Y + {@code delta}, with interrupts held off while SPH and SPL disagree. */
    private static void adjustFrame(StringBuilder output, int delta) {
        output.append("    subi r28, lo8(").append(-delta).append(")\n")
                .append("    sbci r29, hi8(").append(-delta).append(")\n")
                .append("    in r0, 0x3f\n")
                .append("    cli\n")
                .append("    out 0x3e, r29\n")
                .append("    out 0x3f, r0\n")
                .append("    out 0x3d, r28\n");
    }

    /** Copies the incoming argument words this method reads (JVMS 2.6.1 slot order) into their local slots. */
    private void emitParameterSpill(StringBuilder output, FrameLayout frame, IrMethod method) {
        Set<Integer> readLocals = method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrInstruction.LoadLocal.class::isInstance)
                .map(instruction -> ((IrInstruction.LoadLocal) instruction).local())
                .collect(Collectors.toSet());
        int slot = 0;
        if (!method.isStatic()) {
            spill(output, frame, readLocals, slot++);
        }
        for (String type : Descriptor.parse(method.reference().descriptor()).parameters()) {
            if (Descriptor.isDouble(type)) {
                if (readLocals.contains(slot)) {
                    avr.loadArgument(output, A, slot);
                    avr.storeSlot(output, A, frame.localOffset(slot), AvrEmitter.WORD);
                    avr.loadArgument(output, A, slot + 1);
                    avr.storeSlot(output, A, frame.localOffset(slot) + AvrEmitter.WORD, AvrEmitter.WORD);
                }
                slot += 2;
            } else if (Descriptor.isLong(type)) {
                spill(output, frame, readLocals, slot++);
                spill(output, frame, readLocals, slot++);
            } else {
                spill(output, frame, readLocals, slot++);
            }
        }
    }

    /** Argument word {@code slot} lands in local {@code slot}: every JVM slot is one word. */
    private void spill(StringBuilder output, FrameLayout frame, Set<Integer> readLocals, int slot) {
        if (readLocals.contains(slot)) {
            avr.loadArgument(output, A, slot);
            avr.storeSlot(output, A, frame.localOffset(slot), AvrEmitter.WORD);
        }
    }

    private void emitInstruction(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.Const constant -> {
                if (frame.constant(constant.target()) == null) {
                    avr.loadImmediate(output, A, constant.value());
                    avr.store(output, frame, A, constant.target());
                }
            }
            case IrInstruction.StringConst constant -> {
                avr.loadStringAddress(output, A, constant.value());
                avr.store(output, frame, A, constant.target());
            }
            case IrInstruction.LoadLocal load -> {
                avr.loadSlot(output, A, frame.localOffset(load.local()), AvrEmitter.WORD);
                if (FrameLayout.isWide(load.target().type())) {
                    avr.loadSlot(output, B, frame.localOffset(load.local()) + AvrEmitter.WORD, AvrEmitter.WORD);
                    avr.store64(output, frame, A, B, load.target());
                } else {
                    avr.store(output, frame, A, load.target());
                }
            }
            case IrInstruction.StoreLocal store -> {
                if (FrameLayout.isWide(store.value().type())) {
                    avr.load64(output, frame, A, B, store.value());
                    avr.storeSlot(output, A, frame.localOffset(store.local()), AvrEmitter.WORD);
                    avr.storeSlot(output, B, frame.localOffset(store.local()) + AvrEmitter.WORD, AvrEmitter.WORD);
                } else {
                    avr.load(output, frame, A, store.value());
                    avr.storeSlot(output, A, frame.localOffset(store.local()), AvrEmitter.WORD);
                }
            }
            case IrInstruction.Binary binary -> ints.emitBinary(output, frame, binary);
            case IrInstruction.Unary unary -> ints.emitUnary(output, frame, unary);
            case IrInstruction.Compare compare -> ints.emitCompare(output, frame, compare);
            case IrInstruction.Call call -> calls.emitResolvedCall(output, frame, call.method(),
                    CallConvention.argumentWords(call.arguments()), call.target());
            case IrInstruction.LambdaCreate lambda -> calls.emitLambdaCreate(output, frame, lambda);
            case IrInstruction.LambdaCall call -> calls.emitLambdaCall(output, frame, call);
            case IrInstruction.InterfaceCall call -> calls.emitInterfaceCall(output, frame, call);
            case IrInstruction.IntrinsicCall call -> intrinsics.emit(output, frame, call);
            case IrInstruction.NewArray newArray -> {
                memory.emitAllocate(output, AvrMemoryLowering.elementSize(newArray.elementType()) * newArray.length());
                avr.store(output, frame, A, newArray.target());
            }
            case IrInstruction.NewMultiArray array -> memory.emitNewMultiArray(output, frame, array);
            case IrInstruction.ArrayLoad load -> memory.emitArrayLoad(output, frame, load);
            case IrInstruction.ArrayStore store -> memory.emitArrayStore(output, frame, store);
            case IrInstruction.BoundsCheck check -> memory.emitBoundsCheck(output, frame, check);
            case IrInstruction.Panic ignored -> output.append("    call juno_panic\n");
            case IrInstruction.NullCheck check -> {
                avr.load(output, frame, A, check.value());
                AvrEmitter.testZero(output, A);
                String nonNull = avr.newLabel(".LnonNull");
                output.append("    brne ").append(nonNull).append('\n')
                        .append("    call juno_panic\n")
                        .append(nonNull).append(":\n");
            }
            case IrInstruction.NewObject object -> memory.emitNewObject(output, frame, object.className(), object.target());
            case IrInstruction.LoadField load -> memory.emitLoadField(output, frame, load);
            case IrInstruction.StoreField store -> memory.emitStoreField(output, frame, store);
            case IrInstruction.LoadStatic load -> memory.emitLoadStatic(output, frame, load);
            case IrInstruction.StoreStatic store -> memory.emitStoreStatic(output, frame, store);
            case IrInstruction.IntArrayConst array -> {
                avr.loadAddress(output, A, layout.intArraySymbol(array));
                avr.store(output, frame, A, array.target());
            }
            case IrInstruction.ConstantTableRef table -> {
                avr.loadAddress(output, A, layout.constantTableSymbol(table.table().field()));
                avr.store(output, frame, A, table.target());
            }
            default -> wides.emit(output, frame, instruction);
        }
    }

    private void emitTerminator(StringBuilder output, FrameLayout frame, String label, IrBasicBlock block,
                                IrInstruction.@Nullable Compare fused, boolean isEntryPoint) {
        switch (block.terminator()) {
            case IrTerminator.Jump jump -> {
                emitYieldIfBackedge(output, block.start(), jump.target());
                output.append("    jmp ").append(blockLabel(label, jump.target())).append('\n');
            }
            case IrTerminator.Branch branch -> {
                emitYieldIfBackedge(output, block.start(), branch.trueTarget());
                emitYieldIfBackedge(output, block.start(), branch.falseTarget());
                String toFalse;
                if (fused != null) {
                    toFalse = AvrIntArithmeticLowering.inverse(ints.emitFlags(output, frame, fused.condition(), fused.left(), fused.right()));
                } else {
                    avr.load(output, frame, A, branch.condition());
                    AvrEmitter.testZero(output, A);
                    toFalse = "breq";
                }
                // Conditional branches reach only 63 words: hop locally, jump far.
                String falseLabel = avr.newLabel(".Lbranchfalse");
                output.append("    ").append(toFalse).append(' ').append(falseLabel).append('\n')
                        .append("    jmp ").append(blockLabel(label, branch.trueTarget())).append('\n')
                        .append(falseLabel).append(":\n")
                        .append("    jmp ").append(blockLabel(label, branch.falseTarget())).append('\n');
            }
            case IrTerminator.Return returned -> {
                if (isEntryPoint) {
                    emitEscapeCheck(output);
                }
                returned.value().ifPresent(value -> {
                    if (FrameLayout.isWide(value.type())) {
                        avr.load64(output, frame, B, A, value);
                    } else {
                        avr.load(output, frame, A, value);
                    }
                });
                if (frame.frameSize() > 0) {
                    adjustFrame(output, frame.frameSize());
                }
                output.append("    pop r29\n    pop r28\n");
                if (isEntryPoint) {
                    for (int register = 17; register >= 2; register--) {
                        output.append("    pop r").append(register).append('\n');
                    }
                }
                output.append("    ret\n");
            }
            case IrTerminator.Switch switched -> {
                for (int target : switched.targets()) {
                    emitYieldIfBackedge(output, block.start(), target);
                }
                emitYieldIfBackedge(output, block.start(), switched.defaultTarget());
                avr.load(output, frame, A, switched.selector());
                for (int index = 0; index < switched.keys().size(); index++) {
                    String next = avr.newLabel(".Lswitchnext");
                    avr.loadImmediate(output, B, switched.keys().get(index));
                    AvrEmitter.compareQuads(output, A, B);
                    output.append("    brne ").append(next).append('\n')
                            .append("    jmp ").append(blockLabel(label, switched.targets().get(index))).append('\n')
                            .append(next).append(":\n");
                }
                output.append("    jmp ").append(blockLabel(label, switched.defaultTarget())).append('\n');
            }
            default -> throw unsupported(block.terminator().getClass().getSimpleName());
        }
    }

    private static String blockLabel(String label, int block) {
        return ".L" + label + "block" + block;
    }

    /** Lets the core run its background work (serial events) on every loop backedge. */
    private static void emitYieldIfBackedge(StringBuilder output, int blockStart, int target) {
        if (target <= blockStart) {
            output.append("    call yield\n");
        }
    }

    private void emitEscapeCheck(StringBuilder output) {
        if (!layout.throwableClasses().isEmpty()) {
            features.add(ShimFeature.EXCEPTIONS);
            output.append("    call juno_throw_check_escape\n");
        }
    }

    static CompileException unsupported(String detail) {
        return new CompileException("The AVR assembly backend (UNO WiFi Rev2) does not support this yet: " + detail);
    }
}
