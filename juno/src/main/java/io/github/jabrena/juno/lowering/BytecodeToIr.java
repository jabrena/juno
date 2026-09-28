package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.ArithmeticLowering.*;
import static io.github.jabrena.juno.lowering.ConstantAndStackSupport.*;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.*;
import static io.github.jabrena.juno.lowering.InvokeLowering.*;
import static io.github.jabrena.juno.lowering.LocalSlotAnalysis.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.linker.RecordSupport;
import io.github.jabrena.juno.ir.ArrayDeclaration;
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
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Lowers each reachable method's JVM bytecode into Juno IR: a non-SSA, block-structured form where every
 * operand-stack push/pop becomes an explicit {@link Value} produced or consumed by an {@link IrInstruction}.
 *
 * <p>The JVM operand stack is not single-assignment across blocks: two different predecessor blocks can each
 * push a value that a common successor consumes (e.g. a ternary expression), and whichever branch actually
 * ran at runtime is the one whose value must be read. So each stack position is represented as a synthetic
 * local slot (reusing {@link IrInstruction.StoreLocal}/{@link IrInstruction.LoadLocal}, sized using the
 * classfile's own {@code max_stack}) that every predecessor writes and every successor reads — exactly how
 * the pre-IR backend's single shared {@code stack[]} array behaved. Each block's true entry depth is computed
 * up front with a small forward pass over the {@link BasicBlock} partition from
 * {@link io.github.jabrena.juno.analysis.ControlFlowGraphBuilder}, since a block's depth cannot in general be
 * inferred just by reading blocks in textual order.
 *
 * <p><b>Arrays</b> (there is no heap, so every array is a fixed-size C array) are supported in a deliberately
 * narrow, always-sound way, tracked by {@link ValueTracking}:
 * <ul>
 *   <li>A JVM local slot is treated as a known-length array only when it is assigned via {@code astore}
 *       exactly once in the whole method (i.e. "effectively final"), immediately after {@code newarray} with
 *       a compile-time-constant count ({@link #computeSingleAssignmentArrayLocals}). Since that slot can then
 *       only ever hold that one array for its entire reachable lifetime, every load of it is safely
 *       known-length too, without needing a merge-aware, cross-block dataflow pass. Anything else holding an
 *       array reference (a parameter, a reassigned local) falls back to raw-pointer semantics: array
 *       load/store still compile, just unchecked, and {@code arraylength} is a compile error rather than a
 *       silently wrong answer.
 *   <li>Returning an array ({@code areturn}) is accepted only when the returned value directly traces to one
 *       of this method's own array-typed parameters — anything else (a locally {@code newarray}'d array, for
 *       instance) would return a pointer into this call's own stack frame, which dangles once it returns.
 * </ul>
 * Per-value knowledge ({@code arrayLength}, {@code parameterForwarded}) is tracked globally, since values are
 * never redefined. Propagating it back out of a stack-slot round trip needs one more piece of state — which
 * stack slot currently holds which known array — and that part is reset at the start of every block: unlike
 * JVM locals, stack slots are constantly reused as depth rises and falls, and two different branches could in
 * principle leave different arrays in the same slot before a merge.
 */
public final class BytecodeToIr {
    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final Map<String, List<FieldInfo>> validatedRecords = new HashMap<>();

    // Opcodes grouped by which lower* helper handles them, so the per-instruction dispatch in lower()
    // is a handful of set-membership checks instead of one huge switch spanning every opcode.
    private static final BitSet STACK_OP_OPCODES = bitSetOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
            40, 41, 42, 43, 44, 45, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67,
            68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 87, 88, 89);
    private static final BitSet ARITHMETIC_OPCODES = bitSetOf(
            96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115,
            116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135,
            136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152);
    private static final BitSet CONTROL_FLOW_OPCODES = bitSetOf(
            153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 170, 171, 172, 173, 174,
            175, 176, 177, 191, 198, 199);
    private static final BitSet ARRAY_ACCESS_OPCODES = bitSetOf(
            46, 47, 48, 49, 50, 51, 52, 53, 79, 80, 81, 82, 83, 84, 85, 86);
    private static final BitSet ALLOCATION_OPCODES = bitSetOf(
            187, 188, 189, 190, 197);

    static BitSet bitSetOf(int... codes) {
        BitSet set = new BitSet();
        for (int code : codes) {
            set.set(code);
        }
        return set;
    }

    // Net operand-stack effect (slots pushed minus slots popped) of every opcode whose effect depends
    // only on the opcode itself. The handful of opcodes whose effect also depends on a constant-pool
    // entry or operand (field/method descriptors, multianewarray's dimension count) are handled
    // explicitly in stackDelta instead of appearing here.


    public IrProgram lower(Program program) {
        List<String> throwableClasses = throwableClasses(program.methods(), program.classes());
        List<IrMethod> methods = new ArrayList<>();
        for (LinkedMethod linked : program.methods()) {
            methods.add(lower(linked, program.classes(), throwableClasses));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods), program.watchdogTimeoutMillis(),
                throwableClasses);
    }

    /**
     * Every throwable class the program allocates, sorted so class ids (their indexes) are deterministic.
     * An id is one bit of the caught-class mask {@code athrow} passes to the runtime, hence the limit.
     */
    private List<String> throwableClasses(List<LinkedMethod> methods, Map<String, JavaClass> classes) {
        Set<String> names = new TreeSet<>();
        for (LinkedMethod linked : methods) {
            for (Instruction instruction : linked.instructions()) {
                if (instruction.opcode() == 187) {
                    String className = linked.owner().constantPool().className(instruction.operandA());
                    if (ThrowableTypes.isThrowable(className, classes)) {
                        names.add(className);
                    }
                }
                if (isIntegerDivision(instruction.opcode())
                        && arithmeticHandler(linked, instruction.offset(), classes) != null) {
                    names.add(ARITHMETIC_EXCEPTION);
                }
            }
        }
        if (names.size() > Integer.SIZE) {
            throw new CompileException("Juno supports at most " + Integer.SIZE
                    + " distinct exception classes per program, found " + names.size());
        }
        return List.copyOf(names);
    }

    /** Convenience overload for callers with no enum classes to resolve (e.g. hand-built {@link LinkedMethod}s in tests). */
    public IrMethod lower(LinkedMethod linked) {
        return lower(linked, Map.of());
    }

    public IrMethod lower(LinkedMethod linked, Map<String, JavaClass> classes) {
        return lower(linked, classes, throwableClasses(List.of(linked), classes));
    }

    private IrMethod lower(LinkedMethod linked, Map<String, JavaClass> classes, List<String> throwableClasses) {
        int stackBase = linked.method().maxLocals();
        Descriptor methodDescriptor = Descriptor.parse(linked.method().descriptor());
        Map<Integer, Integer> entryDepths = computeEntryDepths(linked);
        Map<Integer, Integer> slotArrayLength = computeSingleAssignmentArrayLocals(linked);
        Set<Integer> arrayParameterSlots = arrayParameterSlots(methodDescriptor, linked.method().isStatic());
        Set<Integer> singleAssignmentLocals = computeSingleAssignmentLocals(linked);
        Map<Integer, RecordInstance> slotRecordInstance = new HashMap<>();
        Map<Integer, String> slotStringInstance = new HashMap<>();
        ValueTracking tracking = new ValueTracking();
        List<ArrayDeclaration> arrayDeclarations = new ArrayList<>();
        List<IrBasicBlock> blocks = new ArrayList<>();
        int nextValueId = 0;
        for (BasicBlock block : linked.controlFlowGraph().blocks()) {
            List<IrInstruction> instructions = new ArrayList<>();
            // Advances past a split when a guarded division (see guardZeroDivisor) ends the IR block early.
            int irBlockStart = block.start();
            int depth = entryDepths.getOrDefault(block.start(), 0);
            tracking.startBlock();
            IrTerminator terminator = null;
            for (Instruction instruction : block.instructions()) {
                int opcode = instruction.opcode();
                InstructionLowering lowered = lowerInstruction(linked, instruction, opcode, block, irBlockStart,
                        blocks, instructions, stackBase, depth, nextValueId, tracking, classes, throwableClasses,
                        slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance,
                        singleAssignmentLocals, arrayDeclarations);
                nextValueId = lowered.nextValueId();
                depth = lowered.depth();
                irBlockStart = lowered.irBlockStart();
                if (lowered.terminator() != null) {
                    terminator = lowered.terminator();
                }
            }
            if (terminator == null) {
                terminator = new IrTerminator.Jump(((Terminator.Fallthrough) block.terminator()).target());
            }
            blocks.add(new IrBasicBlock(irBlockStart, List.copyOf(instructions), terminator));
        }
        return IrMethod.withInferredValues(linked.method().reference(), linked.method().isStatic(),
                stackBase + linked.method().maxStack(),
                nextValueId, List.copyOf(arrayDeclarations), List.copyOf(blocks));
    }

    /** The result of lowering one bytecode instruction: {@link Lowered} plus the (rarely touched) IR
     * block start and terminator, so lowerInstruction has one return shape for every opcode group. */
    

    /**
     * Dispatches one bytecode instruction to the {@code lower*} helper for its opcode group. Opcodes are
     * grouped by which helper handles them (see the {@code *_OPCODES} sets) rather than switched on
     * individually, so this dispatch is a handful of set-membership checks instead of one huge switch.
     */
    private InstructionLowering lowerInstruction(LinkedMethod linked, Instruction instruction, int opcode,
                                                 BasicBlock block, int irBlockStart, List<IrBasicBlock> blocks,
                                                 List<IrInstruction> instructions, int stackBase, int depth,
                                                 int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes,
                                                 List<String> throwableClasses, Map<Integer, Integer> slotArrayLength,
                                                 Set<Integer> arrayParameterSlots,
                                                 Map<Integer, RecordInstance> slotRecordInstance,
                                                 Map<Integer, String> slotStringInstance,
                                                 Set<Integer> singleAssignmentLocals,
                                                 List<ArrayDeclaration> arrayDeclarations) {
        if (STACK_OP_OPCODES.get(opcode)) {
            return InstructionLowering.of(ConstAndLoadLowering.lowerStackOp(linked, instruction, opcode, instructions, stackBase, depth,
                    nextValueId, tracking, slotArrayLength, arrayParameterSlots, slotRecordInstance,
                    slotStringInstance, singleAssignmentLocals), irBlockStart);
        }
        if (ARITHMETIC_OPCODES.get(opcode)) {
            return InstructionLowering.of(lowerArithmetic(linked, instruction, opcode, irBlockStart, instructions,
                    blocks, stackBase, depth, nextValueId, classes, tracking));
        }
        if (CONTROL_FLOW_OPCODES.get(opcode)) {
            return InstructionLowering.of(lowerControlFlow(linked, instruction, opcode, block, instructions,
                    stackBase, depth, nextValueId, tracking, classes, throwableClasses), irBlockStart);
        }
        if (opcode == 178 || opcode == 179) {
            return InstructionLowering.of(FieldLowering.lowerStaticField(linked, instruction, opcode, instructions,
                    stackBase, depth, nextValueId, tracking, classes, decoder), irBlockStart);
        }
        if (opcode == 180) {
            return InstructionLowering.of(FieldLowering.lowerFieldLoad(linked, instruction, instructions, stackBase,
                    depth, nextValueId, tracking, classes, decoder), irBlockStart);
        }
        if (opcode == 181) {
            return InstructionLowering.of(FieldLowering.lowerFieldStore(linked, instruction, instructions, stackBase,
                    depth, nextValueId, tracking, classes), irBlockStart);
        }
        if (opcode == 182) {
            return InstructionLowering.of(lowerInvokeVirtual(linked, instruction, instructions, stackBase, depth,
                    nextValueId, tracking, classes), irBlockStart);
        }
        if (opcode == 183) {
            return InstructionLowering.of(lowerInvokeSpecial(linked, instruction, instructions, stackBase, depth,
                    nextValueId, tracking, classes), irBlockStart);
        }
        if (opcode == 184) {
            return InstructionLowering.of(lowerInvokeStatic(linked, instruction, instructions, stackBase, depth,
                    nextValueId, tracking, classes), irBlockStart);
        }
        if (ARRAY_ACCESS_OPCODES.get(opcode)) {
            return InstructionLowering.of(ArrayLowering.lowerArrayAccess(opcode, instructions, stackBase, depth,
                    nextValueId, tracking), irBlockStart);
        }
        if (ALLOCATION_OPCODES.get(opcode)) {
            return InstructionLowering.of(ArrayLowering.lowerAllocation(linked, instruction, opcode, instructions,
                    stackBase, depth, nextValueId, tracking, classes, arrayDeclarations), irBlockStart);
        }
        throw new CompileException("Juno IR lowering does not support opcode " + opcode);
    }





    /** Constant-push and local-load opcodes (0-45): {@code iconst_*}/{@code ldc}/{@code *load*}. */




    /** Constant-push opcodes (0-20): {@code iconst_*}/{@code lconst_*}/{@code fconst_*}/{@code dconst_*}/{@code *ipush}/{@code ldc*}. */




    /** {@code iconst_*}/{@code lconst_*}/{@code fconst_*}/{@code dconst_*} (0-15): value is the opcode itself. */


    /** {@code bipush}/{@code sipush}/{@code ldc}/{@code ldc_w}/{@code ldc2_w} (16-20): value from the operand or constant pool. */


    /** Local-load opcodes (21-45): {@code iload}/{@code lload}/{@code fload}/{@code dload}/{@code aload} and their {@code _N} forms. */




    /** {@code iload}/{@code lload}/{@code fload}/{@code dload}/{@code aload} with an explicit index (21-25). */


    /** {@code iload_N}/{@code lload_N}/{@code fload_N}/{@code dload_N}/{@code aload_N} (26-45). */




    /** Local-store and stack-shuffle opcodes (54-89): {@code *store*}, {@code pop}/{@code pop2}, {@code dup}. */


    /** {@code istore}/{@code lstore}/{@code fstore}/{@code dstore}/{@code astore} and {@code istore_N} (54-62). */


    /** {@code lstore_N}/{@code fstore_N}/{@code dstore_N}/{@code astore_N} and {@code pop}/{@code pop2}/{@code dup} (63-89). */






    /** Binary arithmetic, shifts, and bitwise operators (96-131): {@code iadd}..{@code lxor}. */




    /** {@code iadd}..{@code drem} (96-115): add/subtract/multiply/divide/remainder for int/long/float/double. */


    /** {@code ineg}..{@code lxor} (116-131): negate, shifts, and bitwise and/or/xor. */


    /** Numeric conversions (133-147), {@code iinc} (132), and long/float/double compare-to-int (148-152). */




    /** {@code i2l}/{@code i2f}/{@code i2d}/{@code l2i}/{@code l2f}/{@code l2d}/{@code f2i}/{@code f2l}/{@code f2d} (133-141). */


    /** {@code d2i}/{@code d2l}/{@code d2f}, {@code i2b}/{@code i2c}/{@code i2s}, {@code iinc}, and compares (142-152, 132). */






    /** {@code if<cond>}, {@code if_icmp<cond>}, {@code if_acmp<cond>}, {@code ifnull}/{@code ifnonnull}. */


    /** {@code goto}, {@code tableswitch}/{@code lookupswitch}, the return family, and {@code athrow}. */
















    /**
     * Finds every JVM local slot that is assigned via {@code astore} exactly once in the whole method,
     * immediately after {@code newarray} with a compile-time-constant count. Such a slot can only ever hold
     * that one array for its entire reachable lifetime (Java requires definite assignment before any read),
     * so every load of it is safely known-length without needing cross-block dataflow.
     */






    /**
     * Pops the top of stack, requiring it to be a compile-time-constant literal pushed immediately before
     * (nothing else observed it in between) — un-emits that push and returns its value. Used for
     * {@code newarray}'s count, since there is no heap and every array must be a fixed-size C array.
     */


    /**
     * Computes each reachable block's true operand-stack depth on entry, by walking the CFG from the method's
     * entry block (depth 0) and propagating each block's net push/pop effect to its successors. The JVM
     * verifier guarantees every predecessor of a block agrees on that block's entry depth, so a first-visit
     * BFS (no fixed-point iteration) is enough.
     */






    /** {@link #lowerCall}'s popped arguments: each slot is either a numeric {@link Value} or (for a parameter
     * an intrinsic requires/prefers as compile-time text) a {@code literalStrings} entry, never both. */
    

















    /**
     * A built-in throwable's {@code ()}/{@code (String)} constructor — on a fresh {@code new} or as a
     * program exception's {@code super(message)} — records the message in the object header.
     */




    

    /** IR-only block ids live above every bytecode offset (a method's code is at most 65535 bytes). */



    

    /**
     * The first handler (in exception-table order) that would catch an {@code ArithmeticException}
     * thrown at {@code offset}, or {@code null} when none does and a zero divisor should panic as usual.
     */




    /**
     * Java throws {@code ArithmeticException("/ by zero")} for an integer {@code /} or {@code %} by zero.
     * Where a handler in this method catches it, the current IR block ends here with a zero test: the
     * zero path builds that exception and jumps straight to the handler (known statically, since the
     * exception's class is), and the rest of the bytecode block continues in a new IR block. Elsewhere
     * the division is left alone and a zero divisor still panics.
     */


    /**
     * {@code athrow}: resolves, at compile time, which handler of this method (in exception-table order)
     * catches each of the program's throwable classes. At runtime the thrown object's class id picks the
     * handler, which starts with the exception as its only operand-stack value; a class no handler here
     * catches — including every exception thrown outside a {@code try} — panics with its name and message.
     */










    /**
     * {@code new StringBuilder(capacity)} — {@code new} (opcode 187) already pushed a placeholder
     * {@code 0} for any unrecognized {@code java/lang/*} allocation (see that opcode's handling
     * above), which {@code dup} then duplicated: one copy is consumed here as this constructor's
     * receiver, the other survives on the stack as the expression's result. Since {@code <init>}
     * is declared {@code void}, the normal call-lowering "push a return value" path never runs, so
     * the surviving placeholder would otherwise stay {@code 0} forever — this overwrites that
     * exact stack slot with the real arena-allocated handle instead.
     */


    /** Replaces {@code new Properties()} with Juno's bounded arena-backed properties handle. */








    /**
     * {@code System.getenv("NAME")} of a literal environment-variable name is evaluated by Juno itself,
     * at compile time, by calling the real {@code System.getenv} in Juno's own JVM process — reading
     * Juno's build-time environment (wherever {@code juno compile} runs), never the target device's.
     * The result becomes a compile-time string literal exactly like {@code ldc "..."} (see
     * {@link #pushStringConst}), used e.g. to keep WiFi credentials out of committed source.
     */




    /**
     * {@code LedCanvas.drawText(frame, "literal", x, y)} is unrolled entirely at compile time into one
     * {@code LedCanvas.drawChar} call per character of the literal, each character's x position
     * computed as {@code x + i * (LedMatrixFontAscii.GLYPH_WIDTH + 1)} — exactly what writing the
     * calls out by hand (as {@code LedMatrixScrollingText} used to) would produce. {@code drawChar} is
     * an entirely ordinary reachable Juno method (see {@link io.github.jabrena.juno.linker.Linker}'s
     * matching reachability special-case), so neither backend needs any new codegen for this at all.
     */


    /**
     * {@code new X} + {@code dup} + args + {@code invokespecial <init>} is the only object-construction
     * pattern Juno supports, and only for a validated simple record (see {@link #validateSimpleRecord}):
     * there is no heap, so a record is never actually allocated, just decomposed into its N argument
     * values. {@code dup} already duplicated the {@code new}-pushed placeholder (see the opcode 89 case) —
     * this pops the copy consumed as the receiver, then tags the slot the OTHER (surviving) copy occupies,
     * exactly mirroring {@link #lowerCall}'s pop order for an instance call.
     */


    /**
     * A record accessor call ({@code p.x()}) never actually calls anything: it resolves directly to the
     * field value captured at construction time (see {@link #lowerRecordConstruction}), reusing that
     * existing {@link Value} rather than emitting any new instruction.
     */


    /**
     * Validates that {@code recordClass} is a "simple" record Juno can safely decompose: every component is
     * an int-like primitive, its canonical constructor is exactly the compiler-generated shape (no compact
     * or custom constructor logic), and every accessor is exactly the compiler-generated trivial getter (no
     * override). Anything else risks silently using a raw constructor argument or field value where the
     * user's own code would have transformed it — a compile error here instead. Cached per class per
     * compile, since bytecode decoding is not free and the same record can be constructed many times.
     */


    /** Expected shape: {@code aload_0; invokespecial <super ctor>; (aload_0; iload_N; putfield)*; return}. */


    /** Expected shape: {@code aload_0; getfield <this component>; ireturn}. */




    /** The local slot an {@code iload}/{@code iload_0..3} instruction reads, or {@code null} otherwise. */


    /** Every JVM local slot assigned via {@code astore} exactly once in the whole method (see {@link #astoreSlot}). */


    /**
     * If {@code slot} is assigned exactly once in the whole method and the value being stored is a known
     * record instance, remembers that fact for {@code slot}'s entire remaining lifetime — safe because Java
     * requires definite assignment before any read, so a single-assignment slot can only ever hold that one
     * value (the same "effectively final" reasoning already used for arrays).
     */


    /** Same reasoning as {@link #trackRecordLocalIfSingleAssignment}, but for a known string literal. */




    /**
     * A string literal points directly at immutable generated storage while its text remains tracked at
     * compile time for intrinsics that require literal arguments.
     */
























    /** Emits a StoreLocal to a stack slot and keeps {@code tracking} consistent with it. */










    /**
     * A {@code long} occupies two consecutive stack/local slots; by convention the lower-depth (lower-index)
     * slot holds the low 32 bits and the next one holds the high 32 bits, both for synthetic stack slots and
     * for JVM local slots (e.g. {@code lload n} reads locals {@code n} and {@code n + 1}).
     */


    /** Stores a long's two halves to a pair of stack slots; arrays are never wide, so both slots are defensively cleared. */








    /** {@code lshl}/{@code lshr}/{@code lushr}: the shift amount is a plain int, popped before the long value. */






    /**
     * Resolves a {@code getstatic} target to an enum constant's ordinal. Juno never constructs a real enum
     * object; the field must belong to a class recognized as an enum (see {@link JavaClass#isEnum()}) and be
     * one of its constants ({@link JavaClass#enumConstantNames()}) — any other static field (mutable, or an
     * enum's own non-constant field, neither of which Juno supports) is a clear compile error.
     */


    /**
     * Resolves an enum's single constructor-associated integer field to one value per ordinal.
     * {@code javac} represents {@code KEY(123)} as a literal constructor argument in {@code <clinit>}
     * and a direct parameter-to-field assignment in the enum constructor. Juno keeps enum values as
     * ordinal ints, so a field read becomes an immutable lookup indexed by that ordinal instead of an
     * object dereference.
     */


    /**
     * Finds the {@code <init>} overload that assigns {@code field} directly from its single integer
     * constructor argument, and returns that constructor's descriptor.
     */


    /** Scans {@code <clinit>} for each constant's construction and reads back its associated field value. */














    

    

    

    /** Like {@link Lowered}, plus the possibly-split IR block start that a guarded division may advance. */
    

    /** Like {@link Lowered}, plus the block's terminator when the opcode ends it (branch/return/throw/switch). */
    

    

    /**
     * Per-method bookkeeping of facts about values that a real type system would normally carry: whether a
     * value is a known-length local array (safe to bounds-check), a direct array-parameter forward (safe to
     * return), or a known record instance (its field values, so an accessor call can resolve directly to one
     * without ever needing a real object). Every "known X" map is keyed by {@link Value} and never reset —
     * values are single-assignment, so a fact about one is true for its whole lifetime. The stack-slot views
     * are reset at the start of every block (see the class-level docs for why).
     */
    

    /** A record instance that was never actually constructed on any heap — just its component field values. */
    
}
