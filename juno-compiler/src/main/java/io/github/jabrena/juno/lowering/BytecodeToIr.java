package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.ConstantTables;
import io.github.jabrena.juno.linker.InterfaceCallSite;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.StringConcatResolver;
import io.github.jabrena.juno.linker.StructuredTaskSupport;
import io.github.jabrena.juno.linker.ThreadSupport;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static io.github.jabrena.juno.bytecode.Opcodes.IDIV;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEINTERFACE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;
import static io.github.jabrena.juno.bytecode.Opcodes.IREM;
import static io.github.jabrena.juno.bytecode.Opcodes.LDIV;
import static io.github.jabrena.juno.bytecode.Opcodes.LREM;
import static io.github.jabrena.juno.bytecode.Opcodes.NEW;

/**
 * Lowers each reachable method's JVM bytecode into Juno IR: a non-SSA, block-structured form where every
 * operand-stack push/pop becomes an explicit {@link io.github.jabrena.juno.ir.Value} produced or consumed by an
 * {@link io.github.jabrena.juno.ir.IrInstruction}.
 *
 * <p>The JVM operand stack is not single-assignment across blocks: two predecessor blocks can each push a value
 * that a common successor consumes (a ternary expression, say), and whichever branch actually ran is the one whose
 * value must be read. So each stack position is a synthetic local slot (see {@link OperandStack}) that every
 * predecessor writes and every successor reads. Each block's entry depth is computed up front
 * ({@link StackDepths}), since it cannot in general be inferred by reading blocks in textual order.
 *
 * <p>Arrays have no runtime length, so a length is known only where it is provable at compile time: for a local
 * assigned exactly once, right after an array creation with a constant size ({@link LocalSlots}). Every other array
 * reference still loads and stores, just unchecked, and {@code arraylength} on it is a compile error rather than a
 * silently wrong answer.
 *
 * <p>Each method is lowered by a {@link MethodLowering}; this class computes what they share about the program.
 */
public final class BytecodeToIr {
    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final StringConcatResolver stringConcatResolver = new StringConcatResolver();
    private final RuntimeRaisedExceptions runtimeRaised = new RuntimeRaisedExceptions();

    public IrProgram lower(Program program) {
        boolean divisionByZeroUnwinds = anyHandlerCatchesArithmetic(program);
        ConstantTables constantTables = ConstantTables.of(program);
        List<String> throwableClasses = throwableClasses(program.methods(), program.classes(), divisionByZeroUnwinds);
        Map<String, Integer> objectTypeIds = objectTypeIds(program.interfaceDispatches(), throwableClasses);
        ProgramContext context = new ProgramContext(program.classes(), throwableClasses,
                program.interfaceDispatches(), objectTypeIds, program.lambdaSites(), new ThrowingMethods(),
                constantTables, divisionByZeroUnwinds, decoder, stringConcatResolver);
        List<IrMethod> methods = lowerMethods(program.methods(), context);
        // A first pass without call guards shows which methods can unwind with an exception; only calls to
        // those get a pending-exception poll, so programs that never throw across a call lower unchanged.
        ThrowingMethods throwing = context.throwing().closureOver(methods);
        if (!throwing.isEmpty()) {
            methods = lowerMethods(program.methods(), context.withThrowing(throwing));
        }
        methods.addAll(entryPoints(program.interfaceDispatches(), objectTypeIds));
        return new IrProgram(program.entryPoint(), List.copyOf(methods), program.watchdogTimeoutMillis(),
                throwableClasses, objectTypeIds);
    }

    /** Lowers one method on its own, with no enum classes to resolve (e.g. a hand-built method in a test). */
    public IrMethod lower(LinkedMethod linked) {
        return lower(linked, Map.of());
    }

    /** Lowers one method on its own, as if it were the whole program. */
    public IrMethod lower(LinkedMethod linked, Map<String, JavaClass> classes) {
        ProgramContext context = new ProgramContext(classes, throwableClasses(List.of(linked), classes, false),
                Map.of(), Map.of(), Map.of(), new ThrowingMethods(), ConstantTables.none(), false, decoder,
                stringConcatResolver);
        return new MethodLowering(context, linked).lower();
    }

    private List<IrMethod> lowerMethods(List<LinkedMethod> methods, ProgramContext context) {
        return methods.stream()
                .map(linked -> new MethodLowering(context, linked).lower())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** The generated thread, task and scoped-value entry functions the program's dispatches call for. */
    private List<IrMethod> entryPoints(Map<InterfaceCallSite, InterfaceDispatch> dispatches,
                                       Map<String, Integer> objectTypeIds) {
        EntryPointLowering entries = new EntryPointLowering(objectTypeIds);
        List<IrMethod> methods = new ArrayList<>();
        InterfaceDispatch threadEntry = dispatches.get(ThreadSupport.ENTRY_SITE);
        if (threadEntry != null) {
            methods.add(entries.threadEntry(threadEntry));
        }
        InterfaceDispatch taskEntry = dispatches.get(StructuredTaskSupport.ENTRY_SITE);
        if (taskEntry != null) {
            methods.add(entries.taskEntry(taskEntry, StructuredTaskSupport.ENTRY_METHOD));
        }
        InterfaceDispatch scopedEntry = dispatches.get(ScopedValueSupport.ENTRY_SITE);
        if (scopedEntry != null) {
            methods.add(entries.taskEntry(scopedEntry, ScopedValueSupport.ENTRY_METHOD));
        }
        return methods;
    }

    /**
     * Whether any {@code catch} or {@code finally} in the program can receive an {@code ArithmeticException}. Only
     * then does a division by zero outside a local handler raise one for callers; otherwise it keeps panicking and
     * costs no guard.
     */
    private boolean anyHandlerCatchesArithmetic(Program program) {
        return program.methods().stream().anyMatch(linked -> linked.method().exceptionHandlers().stream()
                .anyMatch(handler -> handler.catchesAny() || throwableTypes.isSubtype(
                        ExceptionRouting.ARITHMETIC_EXCEPTION, handler.catchType(), program.classes())));
    }

    /** The runtime type id of every class that takes part in polymorphic interface dispatch. */
    private Map<String, Integer> objectTypeIds(Map<InterfaceCallSite, InterfaceDispatch> dispatches,
                                               List<String> throwableClasses) {
        Set<String> polymorphicClasses = dispatches.values().stream()
                .filter(dispatch -> dispatch.targets().size() > 1)
                .flatMap(dispatch -> dispatch.targets().stream())
                .map(InterfaceDispatch.Target::className)
                .collect(Collectors.toCollection(TreeSet::new));
        polymorphicClasses.stream().filter(throwableClasses::contains).findFirst().ifPresent(className -> {
            throw new CompileException("Throwable interface implementations cannot use polymorphic dispatch yet: "
                    + className.replace('/', '.'));
        });
        Map<String, Integer> typeIds = new LinkedHashMap<>();
        for (String className : polymorphicClasses) {
            typeIds.put(className, typeIds.size() + 1);
        }
        return Map.copyOf(typeIds);
    }

    /**
     * Every throwable class the program allocates, sorted so class ids (their indexes) are deterministic.
     * An id is one bit of the caught-class mask {@code athrow} passes to the runtime, hence the limit.
     */
    private List<String> throwableClasses(List<LinkedMethod> methods, Map<String, JavaClass> classes,
                                          boolean divisionByZeroUnwinds) {
        Set<String> names = new TreeSet<>();
        for (LinkedMethod linked : methods) {
            ExceptionRouting exceptions = new ExceptionRouting(linked, classes);
            for (Instruction instruction : linked.instructions()) {
                int opcode = instruction.opcode();
                if (opcode == NEW) {
                    String className = linked.owner().constantPool().className(instruction.operandA());
                    if (throwableTypes.isThrowable(className, classes)) {
                        names.add(className);
                    }
                }
                if (isIntegerDivision(opcode) && (divisionByZeroUnwinds
                        || exceptions.arithmeticHandlerAt(instruction.offset()).isPresent())) {
                    names.add(ExceptionRouting.ARITHMETIC_EXCEPTION);
                }
                if (opcode >= INVOKEVIRTUAL && opcode <= INVOKEINTERFACE) {
                    MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                    names.addAll(runtimeRaised.raisedBy(called.owner()));
                }
            }
        }
        if (names.size() > Integer.SIZE) {
            throw new CompileException("Juno supports at most " + Integer.SIZE
                    + " distinct exception classes per program, found " + names.size());
        }
        return List.copyOf(names);
    }

    private boolean isIntegerDivision(int opcode) {
        return opcode == IDIV || opcode == LDIV || opcode == IREM || opcode == LREM;
    }
}
