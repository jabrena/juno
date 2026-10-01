package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.SupportedJdkMethods;
import io.github.jabrena.juno.linker.ThrowableTypes;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.InterfaceTarget;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code invokevirtual}/{@code invokespecial}/{@code invokestatic} opcode lowering, split out of {@link BytecodeToIr}. */
final class InvokeLowering {
    private InvokeLowering() {
    }

    static Lowered lowerInvokeVirtual(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                                      int stackBase, int depth, int nextValueId, ValueTracking tracking,
                                      Map<String, JavaClass> classes) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        return isEnumOrdinal(classes, called)
                ? lowerEnumOrdinal(instructions, stackBase, depth, nextValueId, tracking)
                : ThrowableTypes.isGetMessage(called, classes)
                        ? lowerGetMessage(instructions, stackBase, depth, nextValueId, tracking)
                        : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId, tracking);
    }

    static Lowered lowerInvokeSpecial(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                                      int stackBase, int depth, int nextValueId, ValueTracking tracking,
                                      Map<String, JavaClass> classes) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        return isStringBuilderConstruction(called)
                ? lowerStringBuilderConstruction(instructions, stackBase, depth, nextValueId, tracking)
                : isPropertiesConstruction(called)
                        ? lowerPropertiesConstruction(instructions, stackBase, depth, nextValueId, tracking)
                        : ThrowableTypes.isBuiltInConstructor(called)
                                ? lowerThrowableConstructor(linked, instruction, called, instructions, stackBase,
                                        depth, nextValueId, tracking)
                                : isRuntimeBaseConstructor(called)
                                        ? discardInstanceCall(called, instructions, stackBase, depth, nextValueId,
                                                tracking)
                                        : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId,
                                                tracking);
    }

    static Lowered lowerInvokeStatic(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                                     int stackBase, int depth, int nextValueId, ValueTracking tracking,
                                     Map<String, JavaClass> classes) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        return isEnumValues(classes, called)
                ? lowerEnumValues(called, instructions, stackBase, depth, nextValueId, tracking, classes)
                        : SupportedJdkMethods.isRequireNonNull(called)
                        ? JdkInvokeLowering.lowerRequireNonNull(instructions, stackBase, depth, nextValueId, tracking)
                        : MiscIntrinsicLowering.isCompileTimeGetenv(called)
                        ? MiscIntrinsicLowering.lowerCompileTimeGetenv(linked, instruction, instructions, stackBase,
                                depth, nextValueId, tracking)
                        : MiscIntrinsicLowering.isDrawTextCall(called)
                                ? MiscIntrinsicLowering.lowerDrawText(linked, instruction, instructions, stackBase,
                                        depth, nextValueId, tracking)
                                : lowerCall(linked, instruction, instructions, stackBase, depth, nextValueId,
                                        tracking);
    }

    static Lowered lowerInvokeInterface(LinkedMethod linked, Instruction instruction,
                                        List<IrInstruction> instructions, int stackBase, int depth,
                                        int nextValueId, ValueTracking tracking, InterfaceDispatch dispatch,
                                        Map<String, Integer> objectTypeIds) {
        if (dispatch.targets().size() == 1) {
            InterfaceDispatch.Target target = dispatch.targets().getFirst();
            return target.isLambda()
                    ? LambdaLowering.lowerCall(linked, instruction, target.lambda(), instructions, stackBase, depth,
                            nextValueId, tracking)
                    : lowerResolvedCall(linked, instruction, target.method(), true, List.of(),
                            instructions, stackBase, depth, nextValueId, tracking);
        }
        List<InterfaceTarget> targets = dispatch.targets().stream()
                .map(target -> new InterfaceTarget(objectTypeIds.get(target.className()), target.method(),
                        target.lambda()))
                .toList();
        return lowerResolvedCall(linked, instruction, dispatch.interfaceMethod(), true, targets, instructions,
                stackBase, depth, nextValueId, tracking);
    }

    static CallArguments popCallArguments(LinkedMethod linked, Instruction instruction, MethodRef called,
                                          Descriptor descriptor, Optional<Intrinsic> intrinsic,
                                          List<IrInstruction> instructions, int stackBase, int depth,
                                          int nextValueId, ValueTracking tracking) {
        Value[] arguments = new Value[descriptor.parameters().size()];
        String[] literalStrings = new String[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            String parameterType = descriptor.parameters().get(index);
            depth -= Descriptor.jvmSlots(parameterType);
            if (Descriptor.isLong(parameterType)) {
                WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                Value packed = Value.int64(nextValueId++);
                instructions.add(new IrInstruction.PackLong(packed, popped.low(), popped.high()));
                arguments[index] = packed;
            } else if (Descriptor.isString(parameterType)) {
                Popped popped = pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                if (intrinsic.isPresent() && IntrinsicRegistry.requiresLiteralStringArgument(intrinsic.get(), index)) {
                    String literal = tracking.knownString(popped.value());
                    if (literal == null) {
                        throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                                + instruction.offset() + ": " + called.displayName() + " requires a compile-time "
                                + "string literal argument");
                    }
                    literalStrings[index] = literal;
                } else if (intrinsic.isPresent()
                        && IntrinsicRegistry.prefersLiteralStringArgument(intrinsic.get(), index)
                        && tracking.knownString(popped.value()) != null) {
                    literalStrings[index] = tracking.knownString(popped.value());
                } else {
                    arguments[index] = popped.value();
                }
            } else {
                Popped popped = Descriptor.isDouble(parameterType)
                        ? popDouble(instructions, stackBase, depth, nextValueId, tracking)
                        : Descriptor.isFloat(parameterType)
                                ? popFloat(instructions, stackBase, depth, nextValueId, tracking)
                                : pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = popped.nextValueId();
                arguments[index] = popped.value();
            }
        }
        return new CallArguments(arguments, literalStrings, nextValueId, depth);
    }

    static Lowered lowerCall(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                             int stackBase, int depth, int nextValueId, ValueTracking tracking) {
        MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
        boolean hasReceiver = instruction.opcode() == 182 || instruction.opcode() == 183;
        return lowerResolvedCall(linked, instruction, called, hasReceiver, List.of(), instructions, stackBase,
                depth, nextValueId, tracking);
    }

    private static Lowered lowerResolvedCall(LinkedMethod linked, Instruction instruction, MethodRef called,
                                             boolean hasReceiver, List<InterfaceTarget> interfaceTargets,
                                             List<IrInstruction> instructions, int stackBase, int depth,
                                             int nextValueId, ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Optional<Intrinsic> intrinsic = IntrinsicRegistry.resolve(called);
        CallArguments popped = popCallArguments(linked, instruction, called, descriptor, intrinsic, instructions,
                stackBase, depth, nextValueId, tracking);
        Value[] arguments = popped.arguments();
        String[] literalStrings = popped.literalStrings();
        nextValueId = popped.nextValueId();
        depth = popped.depth();
        List<Value> numericArguments = new ArrayList<>();
        List<String> literalArguments = new ArrayList<>();
        for (int index = 0; index < arguments.length; index++) {
            if (literalStrings[index] != null) {
                literalArguments.add(literalStrings[index]);
            } else {
                numericArguments.add(arguments[index]);
            }
        }
        Optional<Value> receiver = Optional.empty();
        if (hasReceiver) {
            Popped poppedReceiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = poppedReceiver.nextValueId();
            receiver = Optional.of(poppedReceiver.value());
        }

        Value target = null;
        if (!descriptor.returnsVoid()) {
            target = Descriptor.isLong(descriptor.returnType())
                    ? Value.int64(nextValueId++)
                    : Descriptor.isDouble(descriptor.returnType())
                    ? Value.float64(nextValueId++)
                    : Descriptor.isFloat(descriptor.returnType())
                            ? Value.float32(nextValueId++)
                            : Value.int32(nextValueId++);
        }

        if (!interfaceTargets.isEmpty()) {
            List<Value> callArguments = new ArrayList<>();
            receiver.ifPresent(callArguments::add);
            callArguments.addAll(numericArguments);
            instructions.add(new IrInstruction.InterfaceCall(Optional.ofNullable(target),
                    List.copyOf(callArguments), interfaceTargets));
        } else if (intrinsic.isPresent()) {
            instructions.add(new IrInstruction.IntrinsicCall(
                    Optional.ofNullable(target), intrinsic.get(), receiver, numericArguments, literalArguments));
        } else {
            List<Value> callArguments = new ArrayList<>();
            receiver.ifPresent(callArguments::add);
            callArguments.addAll(numericArguments);
            instructions.add(new IrInstruction.Call(Optional.ofNullable(target), called, List.copyOf(callArguments)));
        }
        if (target != null) {
            if (target.type() == JunoType.INT64) {
                Value low = Value.int32(nextValueId++);
                Value high = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.UnpackLong(low, high, target));
                storeWideToStack(instructions, stackBase, depth, low, high, tracking);
            } else if (target.type() == JunoType.FLOAT64) {
                storeDoubleToStack(instructions, stackBase, depth, target, tracking);
            } else {
                storeToStack(instructions, stackBase, depth, target, tracking);
            }
            depth += target.type().jvmSlots();
        }
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerEnumOrdinal(List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking) {
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        storeToStack(instructions, stackBase, depth, receiver.value(), tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    static Lowered lowerEnumValues(MethodRef called, List<IrInstruction> instructions, int stackBase, int depth,
                                   int nextValueId, ValueTracking tracking, Map<String, JavaClass> classes) {
        JavaClass enumClass = classes.get(called.owner());
        List<Integer> ordinals = new ArrayList<>();
        for (int index = 0; index < enumClass.enumConstantNames().size(); index++) {
            ordinals.add(index);
        }
        Value target = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntArrayConst(target, List.copyOf(ordinals)));
        tracking.markKnownArray(target, ordinals.size());
        storeToStack(instructions, stackBase, depth, target, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    static Lowered lowerThrowableConstructor(LinkedMethod linked, Instruction instruction, MethodRef called,
                                             List<IrInstruction> instructions, int stackBase, int depth,
                                             int nextValueId, ValueTracking tracking) {
        if (!ThrowableTypes.isSupportedConstructor(called)) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": " + called.displayName() + " is not supported yet "
                    + "(Juno exceptions carry a message only; use the () or (String) constructor)");
        }
        Value message = null;
        if (ThrowableTypes.takesMessage(called)) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            message = popped.value();
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        if (message != null) {
            instructions.add(new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.THROWABLE_SET_MESSAGE,
                    Optional.of(receiver.value()), List.of(message), List.of()));
        }
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerGetMessage(List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                   ValueTracking tracking) {
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        Value message = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(message), Intrinsic.THROWABLE_GET_MESSAGE,
                Optional.of(receiver.value()), List.of(), List.of()));
        storeToStack(instructions, stackBase, depth, message, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    static Lowered discardInstanceCall(MethodRef called, List<IrInstruction> instructions, int stackBase,
                                       int depth, int nextValueId, ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        for (int index = descriptor.parameters().size() - 1; index >= 0; index--) {
            int slots = Descriptor.jvmSlots(descriptor.parameters().get(index));
            depth -= slots;
            if (slots == 2) {
                WidePopped ignored = popWide(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = ignored.nextValueId();
            } else {
                Popped ignored = pop(instructions, stackBase, depth, nextValueId, tracking);
                nextValueId = ignored.nextValueId();
            }
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        return new Lowered(receiver.nextValueId(), depth);
    }

    static boolean isRuntimeBaseConstructor(MethodRef called) {
        return called.name().equals("<init>")
                && called.owner().startsWith("java/lang/");
    }

    static boolean isStringBuilderConstruction(MethodRef called) {
        return called.owner().equals("java/lang/StringBuilder") && called.name().equals("<init>")
                && called.descriptor().equals("(I)V");
    }

    static boolean isPropertiesConstruction(MethodRef called) {
        return called.owner().equals("java/util/Properties") && called.name().equals("<init>")
                && called.descriptor().equals("()V");
    }

    static Lowered lowerStringBuilderConstruction(List<IrInstruction> instructions, int stackBase, int depth,
                                                   int nextValueId, ValueTracking tracking) {
        depth -= 1;
        Popped capacity = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = capacity.nextValueId();
        depth -= 1;
        Popped discardedReceiver = pop(instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), Intrinsic.STRING_BUILDER_NEW,
                Optional.empty(), List.of(capacity.value()), List.of()));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerPropertiesConstruction(List<IrInstruction> instructions, int stackBase, int depth,
                                               int nextValueId, ValueTracking tracking) {
        Popped discardedReceiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), Intrinsic.PROPERTIES_NEW,
                Optional.empty(), List.of(), List.of()));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }

    static boolean isEnumOrdinal(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        return called.name().equals("ordinal") && called.descriptor().equals("()I")
                && ((owner != null && owner.isEnum()) || called.owner().equals("java/lang/Enum"));
    }

    static boolean isEnumValues(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        return owner != null && owner.isEnum() && called.name().equals("values")
                && called.descriptor().startsWith("()[L");
    }
}
