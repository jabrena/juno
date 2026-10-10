package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.ConstantAndStackSupport.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.linker.AtomicSupport;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.ir.ArrayDeclaration;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ThrowableTypes;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.LockSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Array-load/store and object/array allocation opcode lowering, split out of {@link BytecodeToIr}. */
final class ArrayLowering {
    private ArrayLowering() {
    }
    static Lowered lowerArrayAccess(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                                     int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 46 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.INT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 47 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.LONG);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 48 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.FLOAT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 49 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.DOUBLE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 50 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.REFERENCE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 51 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.BYTE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 52 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.CHAR);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 53 -> {
                        Lowered lowered = lowerArrayLoad(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.SHORT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 79 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.INT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 80 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.LONG);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 81 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.FLOAT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 82 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.DOUBLE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 83 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.REFERENCE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 84 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.BYTE);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 85 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.CHAR);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }

                    case 86 -> {
                        Lowered lowered = lowerArrayStore(instructions, stackBase, depth, nextValueId,
                                tracking, ArrayElementType.SHORT);
                        nextValueId = lowered.nextValueId();
                        depth = lowered.depth();
                    }
            default -> throw new IllegalStateException("unreachable array-access opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }
    /** Classes whose {@code new} only reserves a placeholder: the constructor call builds the runtime handle. */
    /**
     * Whether another class of the closed world extends {@code className}; a class nothing extends
     * dispatches statically even when it is not declared {@code final}.
     */
    private static boolean hasSubclass(Map<String, JavaClass> classes, String className) {
        return classes.values().stream().anyMatch(candidate -> className.equals(candidate.superClassName()));
    }

    private static boolean isRuntimeHandleClass(String className) {
        return className.startsWith("java/lang/") || className.equals("java/util/Properties")
                || RandomAccessFileMethods.isOwner(className)
                || LockSupport.isReentrantLockClass(className)
                || AtomicSupport.isAtomicClass(className) || BigNumberSupport.isBigNumberOwner(className);
    }

    static Lowered lowerAllocation(LinkedMethod linked, Instruction instruction, int opcode,
                                    List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                    ValueTracking tracking, Map<String, JavaClass> classes,
                                    List<ArrayDeclaration> arrayDeclarations) {
        switch (opcode) {

                    case 187 -> {
                        String className = linked.owner().constantPool().className(instruction.operandA());
                        JavaClass allocatedClass = classes.get(className);
                        if (ThrowableTypes.isBuiltIn(className)) {
                            Value exception = Value.int32(nextValueId++);
                            instructions.add(new IrInstruction.NewObject(exception, className));
                            storeToStack(instructions, stackBase, depth, exception, tracking);
                            depth++;
                            break;
                        }
                        if (isRuntimeHandleClass(className)) {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId, 0, tracking);
                            depth++;
                            break;
                        }
                        if (allocatedClass == null) {
                            throw new CompileException(linked.method().reference().displayName()
                                    + " at bytecode offset " + instruction.offset()
                                    + ": object class is not available for closed-world allocation: " + className);
                        }
                        if (!allocatedClass.isFinal() && hasSubclass(classes, className)) {
                            throw new CompileException(linked.method().reference().displayName()
                                    + " at bytecode offset " + instruction.offset()
                                    + ": allocated classes must be final or have no subclass for statically resolved"
                                    + " dispatch: "
                                    + className.replace('/', '.'));
                        }
                        Value object = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewObject(object, className));
                        storeToStack(instructions, stackBase, depth, object, tracking);
                        depth++;
                    }

                    case 188 -> {
                        ArrayElementType elementType = ArrayElementType.fromAtype(instruction.operandA())
                                .orElseThrow(() -> new CompileException(linked.method().reference().displayName()
                                        + " at bytecode offset " + instruction.offset()
                                        + ": unsupported primitive array atype " + instruction.operandA()));
                        ConstPop length = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                        depth = length.depth();
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewArray(handle, elementType, length.value()));
                        arrayDeclarations.add(new ArrayDeclaration(handle, elementType, length.value()));
                        tracking.markKnownArray(handle, length.value());
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }

                    case 189 -> {
                        ConstPop length = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                        depth = length.depth();
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewArray(handle, ArrayElementType.REFERENCE, length.value()));
                        arrayDeclarations.add(new ArrayDeclaration(handle, ArrayElementType.REFERENCE, length.value()));
                        tracking.markKnownArray(handle, length.value());
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }

                    case 197 -> {
                        int dimensions = instruction.operandB();
                        List<Integer> sizes = new ArrayList<>();
                        for (int index = dimensions - 1; index >= 0; index--) {
                            ConstPop size = popKnownConstant(instructions, stackBase, depth, linked, instruction, tracking);
                            depth = size.depth();
                            sizes.add(0, size.value());
                        }
                        String descriptor = linked.owner().constantPool().className(instruction.operandA());
                        char leafDescriptor = descriptor.charAt(descriptor.length() - 1);
                        ArrayElementType leafType = ArrayElementType.fromDescriptor(leafDescriptor)
                                .orElseThrow(() -> new CompileException(linked.method().reference().displayName()
                                        + " at bytecode offset " + instruction.offset()
                                        + ": multidimensional object arrays are not supported yet: " + descriptor));
                        Value handle = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.NewMultiArray(handle, leafType, List.copyOf(sizes)));
                        tracking.markKnownArray(handle, sizes.get(0));
                        storeToStack(instructions, stackBase, depth, handle, tracking);
                        depth++;
                    }

                    case 190 -> {
                        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = array.nextValueId();
                        Integer knownLength = tracking.knownLength(array.value());
                        if (knownLength == null) {
                            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                                    + instruction.offset() + ": array length is not known at compile time here "
                                    + "(only supported on a local array created once in this method with a "
                                    + "compile-time-constant size)");
                        }
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, knownLength, tracking);
                        depth++;
                    }
            default -> throw new IllegalStateException("unreachable allocation opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerArrayLoad(List<IrInstruction> instructions, int stackBase, int depth,
                                    int nextValueId, ValueTracking tracking, ArrayElementType elementType) {
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        Value target = new Value(nextValueId++, elementType.valueType());
        instructions.add(new IrInstruction.ArrayLoad(target, elementType, array.value(), index.value()));
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
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerArrayStore(List<IrInstruction> instructions, int stackBase, int depth,
                                     int nextValueId, ValueTracking tracking, ArrayElementType elementType) {
        depth -= elementType.valueType().jvmSlots();
        Value storedValue;
        if (elementType.valueType() == JunoType.INT64) {
            WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
            nextValueId = value.nextValueId();
            storedValue = Value.int64(nextValueId++);
            instructions.add(new IrInstruction.PackLong(storedValue, value.low(), value.high()));
        } else {
            Popped value = switch (elementType.valueType()) {
                case INT32 -> pop(instructions, stackBase, depth, nextValueId, tracking);
                case FLOAT32 -> popFloat(instructions, stackBase, depth, nextValueId, tracking);
                case FLOAT64 -> popDouble(instructions, stackBase, depth, nextValueId, tracking);
                case INT64 -> throw new IllegalStateException("handled above");
            };
            nextValueId = value.nextValueId();
            storedValue = value.value();
        }
        Popped index = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = index.nextValueId();
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (knownLength != null) {
            instructions.add(new IrInstruction.BoundsCheck(index.value(), knownLength));
        }
        instructions.add(new IrInstruction.ArrayStore(elementType, array.value(), index.value(), storedValue));
        return new Lowered(nextValueId, depth);
    }}
