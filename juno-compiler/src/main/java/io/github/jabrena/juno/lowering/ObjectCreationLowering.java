package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.AtomicSupport;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LockSupport;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code new} and the constructors that complete it. A program class or built-in throwable is allocated in the
 * arena. A JDK class Juno implements in its runtime ({@code StringBuilder}, {@code Properties},
 * {@code ReentrantLock}, {@code RandomAccessFile}, {@code BigInteger}, atomics…) only gets a placeholder from
 * {@code new}; its constructor call then builds the runtime handle and overwrites that placeholder, which
 * {@code dup} left on the stack as the expression's result.
 */
final class ObjectCreationLowering implements InstructionLowering {
    private final RandomAccessFileMethods randomAccessFileMethods = new RandomAccessFileMethods();

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private final AtomicSupport atomicSupport = new AtomicSupport();

    private final LockSupport lockSupport = new LockSupport();

    private final BigNumberSupport bigNumberSupport = new BigNumberSupport();

    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    private static final MethodRef STRING_BUILDER = new MethodRef("java/lang/StringBuilder", "<init>", "(I)V");
    private static final MethodRef PROPERTIES = new MethodRef("java/util/Properties", "<init>", "()V");

    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;
    private final CallLowering calls;

    ObjectCreationLowering(MethodContext context, CallLowering calls) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
        this.calls = calls;
    }

    /** {@code new}. */
    @Override
    public void lower(Instruction instruction) {
        String className = context.constantPool().className(instruction.operandA());
        if (throwableTypes.isBuiltIn(className)) {
            allocate(className);
        } else if (isRuntimeHandleClass(className)) {
            stack.pushInt(0);
        } else {
            JavaClass allocated = context.classes().get(className);
            if (allocated == null) {
                throw new CompileException(context.at(instruction)
                        + ": object class is not available for closed-world allocation: " + className);
            }
            if (!allocated.isFinal() && hasSubclass(className)) {
                throw new CompileException(context.at(instruction) + ": allocated classes must be final or have "
                        + "no subclass for statically resolved dispatch: " + className.replace('/', '.'));
            }
            allocate(className);
        }
    }

    /** Whether {@code called} is a constructor this class lowers to a runtime handle. */
    boolean buildsHandle(MethodRef called) {
        return bigNumberSupport.isConstruction(called) || called.equals(STRING_BUILDER)
                || called.equals(LockSupport.CONSTRUCTOR) || atomicSupport.isConstruction(called)
                || called.equals(PROPERTIES) || called.equals(RandomAccessFileMethods.CONSTRUCTOR);
    }

    /** Lowers a constructor {@link #buildsHandle} accepts. */
    void construct(Instruction instruction, MethodRef called) {
        if (bigNumberSupport.isConstruction(called) || atomicSupport.isConstruction(called)) {
            constructFromArguments(instruction, called);
        } else if (called.equals(STRING_BUILDER)) {
            Value capacity = stack.pop();
            replacePlaceholder(Intrinsic.STRING_BUILDER_NEW, List.of(capacity), List.of());
        } else if (called.equals(RandomAccessFileMethods.CONSTRUCTOR)) {
            openRandomAccessFile(instruction, called);
        } else if (called.equals(LockSupport.CONSTRUCTOR)) {
            replacePlaceholder(Intrinsic.REENTRANT_LOCK_NEW, List.of(), List.of());
        } else {
            replacePlaceholder(Intrinsic.PROPERTIES_NEW, List.of(), List.of());
        }
    }

    /**
     * A built-in throwable's {@code ()}/{@code (String)} constructor, on a fresh {@code new} or as a program
     * exception's {@code super(message)}, records the message in the object header.
     */
    void constructThrowable(Instruction instruction, MethodRef called) {
        if (!throwableTypes.isSupportedConstructor(called)) {
            throw new CompileException(context.at(instruction) + ": " + called.displayName() + " is not supported "
                    + "yet (Juno exceptions carry a message only; use the () or (String) constructor)");
        }
        Optional<Value> message = throwableTypes.takesMessage(called) ? Optional.of(stack.pop()) : Optional.empty();
        Value receiver = stack.pop();
        message.ifPresent(text -> context.emit(new IrInstruction.IntrinsicCall(Optional.empty(),
                Intrinsic.THROWABLE_SET_MESSAGE, Optional.of(receiver), List.of(text), List.of())));
    }

    private void allocate(String className) {
        Value object = values.int32();
        context.emit(new IrInstruction.NewObject(object, className));
        stack.push(object);
    }

    private void constructFromArguments(Instruction instruction, MethodRef called) {
        Intrinsic intrinsic = intrinsicRegistry.resolve(called).orElseThrow();
        CallArguments arguments = calls.popArguments(instruction, called, Descriptor.parse(called.descriptor()),
                Optional.of(intrinsic));
        replacePlaceholder(intrinsic, arguments.values(), arguments.literals());
    }

    /** {@code new RandomAccessFile(path, "r")}: SD card files open read-only, from a literal path. */
    private void openRandomAccessFile(Instruction instruction, MethodRef called) {
        CallArguments arguments = calls.popArguments(instruction, called, Descriptor.parse(called.descriptor()),
                Optional.of(Intrinsic.RAF_OPEN));
        String mode = arguments.literal(1);
        if (!RandomAccessFileMethods.READ_ONLY_MODE.equals(mode)) {
            throw new CompileException(context.at(instruction) + ": RandomAccessFile mode \"" + mode
                    + "\" is not supported (only \"r\": SD card files open read-only)");
        }
        replacePlaceholder(Intrinsic.RAF_OPEN, List.of(), List.of(Objects.requireNonNull(arguments.literal(0))));
    }

    /** Pops the receiver copy {@code dup} made, then overwrites the surviving placeholder with the new handle. */
    private void replacePlaceholder(Intrinsic constructor, List<Value> arguments, List<String> literals) {
        stack.pop();
        Value handle = values.int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(handle), constructor, Optional.empty(), arguments,
                literals));
        stack.replaceTop(handle);
    }

    /** Whether another class of the closed world extends {@code className}; one nothing extends dispatches statically. */
    private boolean hasSubclass(String className) {
        return context.classes().values().stream().anyMatch(candidate -> className.equals(candidate.superClassName()));
    }

    private boolean isRuntimeHandleClass(String className) {
        return className.startsWith("java/lang/") || className.equals("java/util/Properties")
                || randomAccessFileMethods.isOwner(className)
                || lockSupport.isReentrantLockClass(className)
                || atomicSupport.isAtomicClass(className) || bigNumberSupport.isBigNumberOwner(className);
    }
}
