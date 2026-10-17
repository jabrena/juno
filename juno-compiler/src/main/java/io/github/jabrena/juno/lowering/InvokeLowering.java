package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.ir.InterfaceTarget;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ClojureSupport;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.InterfaceCallSite;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.StructuredTaskSupport;
import io.github.jabrena.juno.linker.SupportedJdkMethods;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEINTERFACE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESPECIAL;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;

/**
 * {@code invokevirtual}/{@code invokespecial}/{@code invokestatic}/{@code invokeinterface}. Most calls become an
 * intrinsic or a direct call (see {@link CallLowering}); the JDK methods Juno resolves itself are recognized first.
 */
final class InvokeLowering implements InstructionLowering {
    private final RandomAccessFileMethods randomAccessFileMethods = new RandomAccessFileMethods();

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private final SupportedJdkMethods supportedJdkMethods = new SupportedJdkMethods();

    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();

    private final MethodContext context;
    private final OperandStack stack;
    private final CallLowering calls;
    private final ObjectCreationLowering objects;
    private final LambdaLowering lambdas;
    private final CompileTimeCalls compileTime;
    private final RandomAccessFileReads fileReads;
    private final ClojureLowering clojure;

    InvokeLowering(MethodContext context, CallLowering calls, ObjectCreationLowering objects, LambdaLowering lambdas) {
        this.context = context;
        this.stack = context.stack();
        this.calls = calls;
        this.objects = objects;
        this.lambdas = lambdas;
        this.compileTime = new CompileTimeCalls(context);
        this.fileReads = new RandomAccessFileReads(context);
        this.clojure = new ClojureLowering(context);
    }

    @Override
    public void lower(Instruction instruction) {
        int opcode = instruction.opcode();
        MethodRef called = context.constantPool().methodRef(instruction.operandA());
        if (opcode == INVOKESTATIC && structuredTaskSupport.joinerPolicy(called) != null) {
            pushJoiner(called);
            return;
        }
        if (ClojureSupport.isLowered(called)) {
            clojure.lower(instruction, called);
            return;
        }
        if (opcode == INVOKEINTERFACE && intrinsicRegistry.isIntrinsic(called)) {
            calls.call(instruction, called, true, List.of());
            return;
        }
        switch (opcode) {
            case INVOKEVIRTUAL -> invokeVirtual(instruction, called);
            case INVOKESPECIAL -> invokeSpecial(instruction, called);
            case INVOKESTATIC -> invokeStatic(instruction, called);
            case INVOKEINTERFACE -> invokeInterface(instruction);
            default -> throw new IllegalStateException("Not an invoke opcode: " + opcode);
        }
    }

    private void invokeVirtual(Instruction instruction, MethodRef called) {
        if (randomAccessFileMethods.isBufferRead(called)) {
            fileReads.read(instruction, called);
        } else if (isEnumOrdinal(called)) {
            // An enum constant is its ordinal already.
            stack.push(stack.pop());
        } else if (throwableTypes.isGetMessage(called, context.classes())) {
            Value receiver = stack.pop();
            Value message = context.values().int32();
            context.emit(new IrInstruction.IntrinsicCall(Optional.of(message), Intrinsic.THROWABLE_GET_MESSAGE,
                    Optional.of(receiver), List.of(), List.of()));
            stack.push(message);
        } else if (throwableTypes.isAddSuppressed(called, context.classes())) {
            discardCall(called);
        } else {
            calls.call(instruction, called, true, List.of());
        }
    }

    private void invokeSpecial(Instruction instruction, MethodRef called) {
        if (objects.buildsHandle(called)) {
            objects.construct(instruction, called);
        } else if (throwableTypes.isBuiltInConstructor(called)) {
            objects.constructThrowable(instruction, called);
        } else if (called.name().equals("<init>") && called.owner().startsWith("java/lang/")) {
            // A JDK base constructor such as Object.<init> has nothing to initialize in Juno's runtime.
            discardCall(called);
        } else {
            calls.call(instruction, called, true, List.of());
        }
    }

    private void invokeStatic(Instruction instruction, MethodRef called) {
        if (isEnumValues(called)) {
            pushEnumValues(called);
        } else if (supportedJdkMethods.isRequireNonNull(called)) {
            Value reference = stack.pop();
            context.emit(new IrInstruction.NullCheck(reference));
            stack.push(reference);
        } else if (compileTime.isGetenv(called)) {
            compileTime.getenv(instruction);
        } else if (compileTime.isDrawText(called)) {
            compileTime.drawText(instruction);
        } else {
            calls.call(instruction, called, false, List.of());
        }
    }

    /** Calls through the implementations the linker found for this call site; a single one is called directly. */
    private void invokeInterface(Instruction instruction) {
        InterfaceCallSite site = new InterfaceCallSite(context.method().method().reference(), instruction.offset());
        InterfaceDispatch dispatch = context.program().interfaceDispatches().get(site);
        if (dispatch == null) {
            throw new CompileException("Missing linked interface dispatch for " + context.at(instruction));
        }
        if (dispatch.targets().size() == 1) {
            InterfaceDispatch.Target target = dispatch.targets().getFirst();
            if (target.isLambda()) {
                lambdas.call(instruction, Objects.requireNonNull(target.lambda()));
            } else {
                calls.call(instruction, target.method(), true, List.of());
            }
            return;
        }
        List<InterfaceTarget> targets = dispatch.targets().stream()
                .map(target -> new InterfaceTarget(
                        Objects.requireNonNull(context.program().objectTypeIds().get(target.className())),
                        target.method(), target.lambda()))
                .toList();
        calls.call(instruction, dispatch.interfaceMethod(), true, targets);
    }

    /** Materializes a built-in {@code Joiner} as a small runtime policy token. */
    private void pushJoiner(MethodRef called) {
        Value joiner = context.values().int32();
        context.emit(new IrInstruction.Const(joiner, Objects.requireNonNull(structuredTaskSupport.joinerPolicy(called))));
        stack.push(joiner);
    }

    /** {@code values()} of an enum is the constant array of its ordinals. */
    private void pushEnumValues(MethodRef called) {
        JavaClass enumClass = Objects.requireNonNull(context.classes().get(called.owner()), called.owner());
        List<Integer> ordinals = IntStream.range(0, enumClass.enumConstantNames().size()).boxed().toList();
        Value array = context.values().int32();
        context.emit(new IrInstruction.IntArrayConst(array, ordinals));
        context.facts().markKnownArray(array, ordinals.size());
        stack.push(array);
    }

    /** Pops a call's arguments and receiver without calling anything. */
    private void discardCall(MethodRef called) {
        List<String> parameters = Descriptor.parse(called.descriptor()).parameters();
        for (String parameter : parameters.reversed()) {
            if (Descriptor.jvmSlots(parameter) == 2) {
                stack.popHalves();
            } else {
                stack.pop();
            }
        }
        stack.pop();
    }

    private boolean isEnumOrdinal(MethodRef called) {
        JavaClass owner = context.classes().get(called.owner());
        return called.name().equals("ordinal") && called.descriptor().equals("()I")
                && ((owner != null && owner.isEnum()) || called.owner().equals("java/lang/Enum"));
    }

    private boolean isEnumValues(MethodRef called) {
        JavaClass owner = context.classes().get(called.owner());
        return owner != null && owner.isEnum() && called.name().equals("values")
                && called.descriptor().startsWith("()[L");
    }
}
