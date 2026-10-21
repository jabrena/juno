package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.AsmBackend;
import io.github.jabrena.juno.backend.CompareFusion;
import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.GeneratedCode;
import io.github.jabrena.juno.backend.IntrinsicLowering;
import io.github.jabrena.juno.backend.ProgramLayout;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.backend.runtime.RuntimeEntryPoints;
import io.github.jabrena.juno.backend.runtime.RuntimeShim;
import io.github.jabrena.juno.backend.runtime.core.CoreRuntimes;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.backend.thumb2.Thumb2AsmBackend;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.WORD;

/**
 * Emits GNU Xtensa assembly (the Nano ESP32's ESP32-S3, an LX7 core) straight from Juno IR, with the storage model of
 * {@link Thumb2AsmBackend}: every IR value and JVM local lives in a fixed stack slot (see {@link FrameLayout#packed})
 * and registers are only scratch within one instruction. The C++ runtime shim is the same one Thumb-2 links against
 * ({@link RuntimeShim}): its code is plain C++ the ESP32 toolchain compiles as is.
 *
 * <h2>Calling convention</h2>
 * Generated functions follow the windowed-register ABI GCC uses on this core, so C code and generated code call each
 * other directly: a function opens with {@code entry a1, N}, reserving its slots at {@code a1+0} upwards plus the 32
 * bytes of register-window save areas GCC reserves, and returns with {@code retw} (the result in {@code a2}, or
 * {@code a2}/{@code a3}). A call passes up to six argument words in {@code a10}-{@code a15} through {@code call8};
 * a call needing more fails to compile, as do threads.
 *
 * <h2>Scope</h2>
 * This first cut lowers what GPIO, delay and serial programs need: {@code int}, {@code long} and {@code float}
 * arithmetic and comparisons, branches and {@code switch}, static and instance calls, interface dispatch, objects,
 * fields, statics and arrays. The {@code double} operations, lambdas and threads are not lowered yet; a program
 * using one fails with a {@link CompileException} rather than generating code that was never checked.
 */
public final class XtensaAsmBackend implements AsmBackend {
    /**
     * Bytes of stack the entry point zeroes below its frame before anything else runs, so stale pointer-shaped bytes
     * from a previous run are not mistaken for live references by the conservative collector (see
     * {@link Thumb2AsmBackend}). The 16 bytes right below the stack pointer are skipped: an interrupt that spills the
     * register windows stores the caller's {@code a0}-{@code a3} there.
     */
    private static final int STACK_ZERO_BYTES = 4096;
    private static final int WINDOW_SAVE_BYTES = 16;
    /** Bytes of register-window save areas GCC adds to every {@code call8} frame. */
    private static final int FRAME_OVERHEAD_BYTES = 32;
    private static final int STACK_ALIGNMENT = 16;

    private final CoreRuntimes coreRuntimes = new CoreRuntimes();
    private final RuntimeEntryPoints runtimeEntryPoints = new RuntimeEntryPoints();

    private final Map<MethodRef, String> functionLabels = new LinkedHashMap<>();
    private final Set<ShimFeature> features = EnumSet.noneOf(ShimFeature.class);
    private final Set<Intrinsic> usedMath = EnumSet.noneOf(Intrinsic.class);
    private final List<String> clinitLabels = new ArrayList<>();
    private final boolean gcLoggingEnabled;
    private final Board board;
    private final RuntimeConfig runtimeConfig;
    private MethodRef entryPoint;
    private ProgramLayout layout;
    private XtensaEmitter xtensa;
    private XtensaIntArithmeticLowering ints;
    private XtensaMemoryLowering memory;
    private XtensaWideArithmeticLowering wides;
    private IntrinsicLowering intrinsics;

    /**
     * @param board an Xtensa board; its core selects the shim's core glue
     * @param runtimeConfig the arena capacity of the generated runtime shim
     */
    // entryPoint, layout, xtensa and the lowering helpers are per-program state that generate() assigns before any use.
    @SuppressWarnings("NullAway.Init")
    public XtensaAsmBackend(boolean gcLoggingEnabled, Board board, RuntimeConfig runtimeConfig) {
        if (board.core().architecture() != Architecture.XTENSA) {
            throw new IllegalArgumentException(board.displayName() + " is not an Xtensa board");
        }
        this.gcLoggingEnabled = gcLoggingEnabled;
        this.board = board;
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public GeneratedCode generate(IrProgram program) {
        if (runtimeEntryPoints.usesThreads(program)) {
            throw Architecture.XTENSA.unsupported("threads");
        }
        if (program.watchdogTimeoutMillis().isPresent()) {
            throw Architecture.XTENSA.unsupported("@Watchdog");
        }
        entryPoint = program.entryPoint();
        for (int index = 0; index < program.methods().size(); index++) {
            MethodRef reference = program.methods().get(index).reference();
            String label = reference.equals(entryPoint) ? "juno_" + simpleName(reference) + "_asm"
                    : "juno_fn" + index;
            functionLabels.put(reference, label);
            if (reference.name().equals("<clinit>")) {
                clinitLabels.add(label);
            }
        }
        layout = new ProgramLayout(program);
        xtensa = new XtensaEmitter(layout.stringLiteralSymbols());
        ints = new XtensaIntArithmeticLowering(xtensa);
        memory = new XtensaMemoryLowering(xtensa, layout);
        wides = new XtensaWideArithmeticLowering(xtensa, features);
        CoreRuntime coreRuntime = coreRuntimes.of(board.core());
        intrinsics = new IntrinsicLowering(new XtensaIntrinsicTarget(xtensa), coreRuntime, board.generation(),
                features, usedMath, false);

        StringBuilder text = new StringBuilder();
        for (IrMethod method : program.methods()) {
            emitMethod(text, method);
        }
        StringBuilder output = new StringBuilder();
        output.append("# Generated by Juno's Xtensa assembly backend. Do not edit.\n")
                .append("# Author: Juan Antonio Brena Moral\n")
                .append("# Entry point: ").append(program.entryPoint().displayName()).append('\n');
        layout.emitDataSections(output, ProgramLayout.DataDirectives.XTENSA);
        output.append("    .text\n")
                .append("    .literal_position\n")
                .append(text);
        String runtimeShim = new RuntimeShim(coreRuntime, runtimeConfig, gcLoggingEnabled, features, usedMath,
                layout.throwableClasses(), program.watchdogTimeoutMillis()).generate();
        return new GeneratedCode(output.toString(), runtimeShim, Objects.requireNonNull(functionLabels.get(entryPoint)));
    }

    private String simpleName(MethodRef method) {
        String owner = method.owner();
        return owner.substring(owner.lastIndexOf('/') + 1);
    }

    private void emitMethod(StringBuilder output, IrMethod method) {
        String label = Objects.requireNonNull(functionLabels.get(method.reference()));
        boolean isEntryPoint = method.reference().equals(entryPoint);
        FrameLayout frame = FrameLayout.packed(method);
        int frameBytes = roundUp(frame.frameSize(), STACK_ALIGNMENT) + FRAME_OVERHEAD_BYTES;
        output.append('\n')
                .append("    .balign 4\n");
        if (isEntryPoint) {
            output.append("    .global ").append(label).append('\n');
        }
        output.append("    .type ").append(label).append(", @function\n")
                .append(label).append(":\n")
                .append("    entry a1, ").append(frameBytes).append('\n');
        if (isEntryPoint) {
            // Uses only a8-a10, so the incoming arguments in a2-a7 survive it for the spill below.
            emitEntryPrologue(output, frame, frameBytes);
        }
        emitParameterSpill(output, frame, method);
        if (isEntryPoint) {
            for (String clinitLabel : clinitLabels) {
                xtensa.call(output, clinitLabel);
                emitEscapeCheck(output);
            }
        }
        CompareFusion fusion = new CompareFusion(method);
        for (IrBasicBlock block : method.blocks()) {
            output.append(blockLabel(label, block.start())).append(":\n");
            IrInstruction.Compare fused = fusion.fusedCompare(block);
            List<IrInstruction> instructions = fused == null ? block.instructions()
                    : block.instructions().subList(0, block.instructions().size() - 1);
            for (IrInstruction instruction : instructions) {
                emitInstruction(output, frame, instruction);
            }
            emitTerminator(output, frame, label, block, fused, isEntryPoint);
        }
        output.append("    .size ").append(label).append(", .-").append(label).append('\n');
    }

    /**
     * Records the stack top the conservative collector scans up to (the caller's stack pointer, right above this
     * frame), then zeroes {@link #STACK_ZERO_BYTES} below the frame and the frame's own slots.
     */
    private void emitEntryPrologue(StringBuilder output, FrameLayout frame, int frameBytes) {
        xtensa.loadAddress(output, A, "juno_gc_stack_top");
        output.append("    addi a9, a1, ").append(frameBytes).append('\n');
        xtensa.storeWordAt(output, "a9", A, 0);
        output.append("    movi a10, 0\n");
        xtensa.loadImmediate(output, A, STACK_ZERO_BYTES);
        output.append("    sub a8, a1, a8\n")
                .append("    addi a9, a1, -").append(WINDOW_SAVE_BYTES).append('\n');
        emitZeroLoop(output, ".LjunoZeroStack");
        if (frame.frameSize() > 0) {
            output.append("    mov a8, a1\n");
            xtensa.loadImmediate(output, "a9", frame.frameSize());
            output.append("    add a9, a9, a1\n");
            emitZeroLoop(output, ".LjunoZeroFrame");
        }
    }

    /** Stores {@code a10} (zero) to every word from {@code a8} up to, not including, {@code a9}. */
    private void emitZeroLoop(StringBuilder output, String prefix) {
        String loop = xtensa.newLabel(prefix);
        String done = xtensa.newLabel(prefix + "Done");
        output.append(loop).append(":\n")
                .append("    bgeu a8, a9, ").append(done).append('\n')
                .append("    s32i a10, a8, 0\n")
                .append("    addi a8, a8, 4\n")
                .append("    j ").append(loop).append('\n')
                .append(done).append(":\n");
    }

    /**
     * Copies the incoming argument words this method reads (JVMS 2.6.1 slot order, a {@code long}/{@code double}
     * starting at an even word as the ABI pairs registers) from {@code a2}-{@code a7} into their local slots.
     */
    private void emitParameterSpill(StringBuilder output, FrameLayout frame, IrMethod method) {
        Set<Integer> readLocals = method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(IrInstruction.LoadLocal.class::isInstance)
                .map(instruction -> ((IrInstruction.LoadLocal) instruction).local())
                .collect(Collectors.toSet());
        List<String> types = new ArrayList<>();
        if (!method.isStatic()) {
            types.add("L");
        }
        types.addAll(Descriptor.parse(method.reference().descriptor()).parameters());
        int slot = 0;
        int word = 0;
        for (String type : types) {
            boolean wide = Descriptor.isLong(type) || Descriptor.isDouble(type);
            if (wide && word % 2 != 0) {
                word++;
            }
            int words = wide ? 2 : 1;
            if (word + words > XtensaEmitter.REGISTER_ARGUMENT_WORDS) {
                throw Architecture.XTENSA.unsupported("more than " + XtensaEmitter.REGISTER_ARGUMENT_WORDS
                        + " argument words in " + method.reference().displayName());
            }
            if (readLocals.contains(slot)) {
                for (int index = 0; index < words; index++) {
                    xtensa.spillSlot(output, XtensaEmitter.incomingRegister(word + index),
                            frame.localOffset(slot) + index * WORD);
                }
            }
            slot += words;
            word += words;
        }
    }

    private void emitInstruction(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.Const constant -> {
                if (frame.constant(constant.target()) == null) {
                    xtensa.loadImmediate(output, A, constant.value());
                    xtensa.store(output, frame, A, constant.target());
                }
            }
            case IrInstruction.StringConst constant -> {
                xtensa.loadStringAddress(output, A, constant.value());
                xtensa.store(output, frame, A, constant.target());
            }
            case IrInstruction.LoadLocal load -> {
                xtensa.loadSlot(output, A, frame.localOffset(load.local()));
                if (load.target().type().isWide()) {
                    xtensa.loadSlot(output, "a9", frame.localOffset(load.local()) + WORD);
                    xtensa.store64(output, frame, A, "a9", load.target());
                } else {
                    xtensa.store(output, frame, A, load.target());
                }
            }
            case IrInstruction.StoreLocal store -> {
                if (store.value().type().isWide()) {
                    xtensa.load64(output, frame, A, "a9", store.value());
                    xtensa.storeSlot(output, A, frame.localOffset(store.local()));
                    xtensa.storeSlot(output, "a9", frame.localOffset(store.local()) + WORD);
                } else {
                    xtensa.load(output, frame, A, store.value());
                    xtensa.storeSlot(output, A, frame.localOffset(store.local()));
                }
            }
            case IrInstruction.Binary binary -> ints.emitBinary(output, frame, binary);
            case IrInstruction.Unary unary -> ints.emitUnary(output, frame, unary);
            case IrInstruction.Compare compare -> ints.emitCompare(output, frame, compare);
            case IrInstruction.Call call -> emitResolvedCall(output, frame, call.method(), call.arguments(),
                    call.target());
            case IrInstruction.InterfaceCall call -> emitInterfaceCall(output, frame, call);
            case IrInstruction.IntrinsicCall call -> intrinsics.emit(output, frame, call);
            case IrInstruction.NewObject object -> memory.emitNewObject(output, frame, object);
            case IrInstruction.LoadField load -> memory.emitLoadField(output, frame, load);
            case IrInstruction.StoreField store -> memory.emitStoreField(output, frame, store);
            case IrInstruction.LoadStatic load -> memory.emitLoadStatic(output, frame, load);
            case IrInstruction.StoreStatic store -> memory.emitStoreStatic(output, frame, store);
            case IrInstruction.IntArrayConst array -> memory.emitIntArrayConst(output, frame, array);
            case IrInstruction.ConstantTableRef table -> memory.emitConstantTableRef(output, frame, table);
            case IrInstruction.NewArray newArray -> memory.emitNewArray(output, frame, newArray);
            case IrInstruction.NewMultiArray array -> memory.emitNewMultiArray(output, frame, array);
            case IrInstruction.ArrayLoad load -> memory.emitArrayLoad(output, frame, load);
            case IrInstruction.ArrayStore store -> memory.emitArrayStore(output, frame, store);
            case IrInstruction.BoundsCheck check -> memory.emitBoundsCheck(output, frame, check);
            case IrInstruction.ArrayBoundsCheck check -> memory.emitArrayBoundsCheck(output, frame, check);
            case IrInstruction.ArrayLength length -> memory.emitArrayLength(output, frame, length);
            case IrInstruction.Panic ignored -> xtensa.call(output, "juno_panic");
            case IrInstruction.NullCheck check -> {
                String nonNull = xtensa.newLabel(".LnonNull");
                xtensa.load(output, frame, A, check.value());
                output.append("    bnez a8, ").append(nonNull).append('\n');
                xtensa.call(output, "juno_panic");
                output.append(nonNull).append(":\n");
            }
            default -> wides.emit(output, frame, instruction);
        }
    }

    private void emitResolvedCall(StringBuilder output, FrameLayout frame, MethodRef method, List<Value> arguments,
                                  Optional<Value> target) {
        String label = functionLabels.get(method);
        if (label == null) {
            throw Architecture.XTENSA.unsupported("call to unresolved method " + method.displayName());
        }
        xtensa.emitCall(output, frame, label, alignedWords(arguments));
        target.ifPresent(value -> {
            if (value.type().isWide()) {
                xtensa.store64(output, frame, "a10", "a11", value);
            } else {
                xtensa.store(output, frame, "a10", value);
            }
        });
    }

    /** The argument words, with a padding word before a {@code long}/{@code double} that would start at an odd word. */
    private List<WordSource> alignedWords(List<Value> arguments) {
        List<WordSource> words = new ArrayList<>();
        for (WordSource word : WordSource.argumentWords(arguments)) {
            if (word instanceof WordSource.FromValueLow && words.size() % 2 != 0) {
                words.add(new WordSource.Immediate(0));
            }
            words.add(word);
        }
        return words;
    }

    /** Dispatches on the receiver's type id to each possible target, panicking when none matches. */
    private void emitInterfaceCall(StringBuilder output, FrameLayout frame, IrInstruction.InterfaceCall call) {
        String done = xtensa.newLabel(".LinterfaceDone");
        for (var target : call.targets()) {
            if (target.isLambda()) {
                throw Architecture.XTENSA.unsupported("lambdas");
            }
            String next = xtensa.newLabel(".LinterfaceNext");
            String matches = xtensa.newLabel(".LinterfaceMatch");
            xtensa.load(output, frame, A, call.arguments().getFirst());
            xtensa.loadWordAt(output, A, A, 0);
            xtensa.loadImmediate(output, "a9", target.typeId());
            output.append("    beq a8, a9, ").append(matches).append('\n')
                    .append("    j ").append(next).append('\n')
                    .append(matches).append(":\n");
            emitResolvedCall(output, frame, target.method(), call.arguments(), call.target());
            output.append("    j ").append(done).append('\n')
                    .append(next).append(":\n");
        }
        xtensa.call(output, "juno_panic");
        output.append(done).append(":\n");
    }

    private void emitTerminator(StringBuilder output, FrameLayout frame, String label, IrBasicBlock block,
                                IrInstruction.@Nullable Compare fused, boolean isEntryPoint) {
        switch (block.terminator()) {
            case IrTerminator.Jump jump -> {
                emitYieldIfBackedge(output, block.start(), jump.target());
                output.append("    j ").append(blockLabel(label, jump.target())).append('\n');
            }
            case IrTerminator.Branch branch -> {
                emitYieldIfBackedge(output, block.start(), branch.trueTarget());
                emitYieldIfBackedge(output, block.start(), branch.falseTarget());
                // Conditional branches reach only about 127 bytes: hop locally, jump far.
                String falseLabel = xtensa.newLabel(".Lbranchfalse");
                if (fused != null) {
                    ints.emitBranchUnless(output, frame, fused.condition(), fused.left(), fused.right(), falseLabel);
                } else {
                    xtensa.load(output, frame, A, branch.condition());
                    output.append("    beqz a8, ").append(falseLabel).append('\n');
                }
                output.append("    j ").append(blockLabel(label, branch.trueTarget())).append('\n')
                        .append(falseLabel).append(":\n")
                        .append("    j ").append(blockLabel(label, branch.falseTarget())).append('\n');
            }
            case IrTerminator.Return returned -> {
                if (isEntryPoint) {
                    emitEscapeCheck(output);
                }
                returned.value().ifPresent(value -> {
                    if (value.type().isWide()) {
                        xtensa.load64(output, frame, "a2", "a3", value);
                    } else {
                        xtensa.load(output, frame, "a2", value);
                    }
                });
                output.append("    retw\n");
            }
            case IrTerminator.Switch switched -> {
                for (int target : switched.targets()) {
                    emitYieldIfBackedge(output, block.start(), target);
                }
                emitYieldIfBackedge(output, block.start(), switched.defaultTarget());
                for (int index = 0; index < switched.keys().size(); index++) {
                    String next = xtensa.newLabel(".Lswitchnext");
                    xtensa.load(output, frame, A, switched.selector());
                    xtensa.loadImmediate(output, "a9", switched.keys().get(index));
                    output.append("    bne a8, a9, ").append(next).append('\n')
                            .append("    j ").append(blockLabel(label, switched.targets().get(index))).append('\n')
                            .append(next).append(":\n");
                }
                output.append("    j ").append(blockLabel(label, switched.defaultTarget())).append('\n');
            }
        }
    }

    private String blockLabel(String label, int block) {
        return ".L" + label + "block" + block;
    }

    /** Lets FreeRTOS run other tasks on every loop backedge, through the core's {@code yield()}. */
    private void emitYieldIfBackedge(StringBuilder output, int blockStart, int target) {
        if (target <= blockStart) {
            xtensa.call(output, "yield");
        }
    }

    private void emitEscapeCheck(StringBuilder output) {
        if (!layout.throwableClasses().isEmpty()) {
            features.add(ShimFeature.EXCEPTIONS);
            xtensa.call(output, "juno_throw_check_escape");
        }
    }

    private static int roundUp(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }
}
