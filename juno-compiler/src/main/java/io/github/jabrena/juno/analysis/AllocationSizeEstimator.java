package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.StackFrameSizing;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative worst-case byte-size estimates for heap allocations and stack frames. */
final class AllocationSizeEstimator {
    private final StackFrameSizing stackFrameSizing = new StackFrameSizing();

    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    /** The intrinsics that start a thread, each allocating one thread object. */
    private static final Set<Intrinsic> THREAD_LAUNCHES = Set.of(
            Intrinsic.THREAD_START_VIRTUAL, Intrinsic.TASK_SCOPE_FORK_CALLABLE, Intrinsic.TASK_SCOPE_FORK_RUNNABLE);
    private static final Set<Intrinsic> STRUCTURED_SCOPE_OPENS = Set.of(
            Intrinsic.TASK_SCOPE_OPEN_DEFAULT, Intrinsic.TASK_SCOPE_OPEN,
            Intrinsic.TASK_SCOPE_OPEN_CONFIG, Intrinsic.TASK_SCOPE_OPEN_WITH_CONFIG);
    private static final Set<Intrinsic> CONFIGURED_SCOPE_OPENS = Set.of(
            Intrinsic.TASK_SCOPE_OPEN_CONFIG, Intrinsic.TASK_SCOPE_OPEN_WITH_CONFIG);


    int allocationBytes(IrInstruction instruction, Map<String, JavaClass> classes,
                               Map<String, Integer> objectTypeIds) {
        if (instruction instanceof IrInstruction.NewArray array) {
            int alignment = elementSize(array.elementType());
            return allocationUpperBound(array.length() * alignment, alignment);
        }
        if (instruction instanceof IrInstruction.NewObject object) {
            int size = objectSize(object.className(), classes)
                    + (throwableTypes.isThrowable(object.className(), classes) ? ThrowableTypes.HEADER_BYTES
                    : objectTypeIds.containsKey(object.className()) ? Integer.BYTES : 0);
            int alignment = objectAlignment(object.className(), classes);
            return allocationUpperBound(size, alignment);
        }
        if (instruction instanceof IrInstruction.LambdaCreate lambda && lambda.site().isCapturing()) {
            return allocationUpperBound((1 + lambda.captures().size()) * Integer.BYTES, Integer.BYTES);
        }
        if (instruction instanceof IrInstruction.NewMultiArray array) {
            return multiArrayBytes(array.leafType(), array.dimensions());
        }
        if (instruction instanceof IrInstruction.IntrinsicCall call) {
            return intrinsicAllocationBytes(call.intrinsic());
        }
        return 0;
    }

    private int intrinsicAllocationBytes(Intrinsic intrinsic) {
        if (intrinsic == Intrinsic.PROPERTIES_NEW) {
            return allocationUpperBound(RuntimeLimits.PROPERTIES_STORAGE_BYTES, 4);
        }
        if (STRUCTURED_SCOPE_OPENS.contains(intrinsic)) {
            int scopeBytes = allocationUpperBound(RuntimeLimits.STRUCTURED_TASK_SCOPE_BYTES, 4);
            return scopeBytes + (CONFIGURED_SCOPE_OPENS.contains(intrinsic)
                    ? RuntimeLimits.STRUCTURED_TASK_CONFIGURATION_BYTES : 0);
        }
        if (THREAD_LAUNCHES.contains(intrinsic)) {
            return allocationUpperBound(RuntimeLimits.THREAD_OBJECT_BYTES, 4);
        }
        if (intrinsic == Intrinsic.REENTRANT_LOCK_NEW
                || intrinsic == Intrinsic.ATOMIC_INT_NEW_DEFAULT
                || intrinsic == Intrinsic.ATOMIC_INT_NEW) {
            return allocationUpperBound(Integer.BYTES, Integer.BYTES);
        }
        if (intrinsic == Intrinsic.ATOMIC_LONG_NEW_DEFAULT || intrinsic == Intrinsic.ATOMIC_LONG_NEW) {
            return allocationUpperBound(2 * Integer.BYTES, Integer.BYTES);
        }
        return 0;
    }

    int descriptorSize(String descriptor) {
        if (Descriptor.isLong(descriptor) || Descriptor.isDouble(descriptor)) return 8;
        return 4;
    }

    int estimatedFrameBytes(IrMethod method) {
        return stackFrameSizing.methodStackBytes(method) + maximumTemporaryBytes(method);
    }

    private int maximumTemporaryBytes(IrMethod method) {
        int maximum = 0;
        for (var block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                int temporaryBytes = switch (instruction) {
                    case IrInstruction.IntrinsicCall call ->
                            (call.arguments().stream().mapToInt(argument -> argument.type().jvmSlots()).sum()
                                    + (call.receiver().isPresent() ? 1 : 0)) * 4;
                    case IrInstruction.InterfaceCall call -> call.arguments().stream()
                            .mapToInt(argument -> argument.type().jvmSlots()).sum() * 4;
                    case IrInstruction.LambdaCall call -> (call.arguments().size()
                            + call.site().captureTypes().size()) * 4;
                    case IrInstruction.LongBinary ignored -> 24;
                    case IrInstruction.LongShift ignored -> 16;
                    case IrInstruction.LongNegate ignored -> 8;
                    case IrInstruction.DoubleToLong ignored -> 8;
                    case IrInstruction.FloatToLong ignored -> 8;
                    case IrInstruction.LongCompare ignored -> 16;
                    default -> 0;
                };
                maximum = Math.max(maximum, temporaryBytes);
            }
        }
        return maximum;
    }

    private int multiArrayBytes(ArrayElementType leafType, List<Integer> dimensions) {
        int total = 0;
        int arraysAtLevel = 1;
        for (int level = 0; level < dimensions.size(); level++) {
            int elementSize = level == dimensions.size() - 1 ? elementSize(leafType) : 4;
            total += arraysAtLevel * allocationUpperBound(dimensions.get(level) * elementSize, elementSize);
            arraysAtLevel *= dimensions.get(level);
        }
        return total;
    }

    private int allocationUpperBound(int size, int alignment) {
        return Math.addExact(size, alignment - 1);
    }

    private int objectSize(String className, Map<String, JavaClass> classes) {
        JavaClass javaClass = classes.get(className);
        if (javaClass == null) {
            return 1;
        }
        int offset = javaClass.superClassName() == null ? 0 : objectSize(javaClass.superClassName(), classes);
        int maximumAlignment = 1;
        for (FieldInfo field : javaClass.fields()) {
            if (field.isStatic()) continue;
            int size = descriptorSize(field.descriptor());
            int alignment = Math.min(size, 8);
            maximumAlignment = Math.max(maximumAlignment, alignment);
            offset = align(offset, alignment) + size;
        }
        return Math.max(1, align(offset, maximumAlignment));
    }

    private int objectAlignment(String className, Map<String, JavaClass> classes) {
        JavaClass javaClass = classes.get(className);
        if (javaClass == null) return 1;
        int own = javaClass.fields().stream()
                .filter(field -> !field.isStatic())
                .mapToInt(field -> Math.min(descriptorSize(field.descriptor()), 8))
                .max().orElse(1);
        return javaClass.superClassName() == null ? own : Math.max(own,
                objectAlignment(javaClass.superClassName(), classes));
    }

    private int elementSize(ArrayElementType type) {
        return switch (type) {
            case BYTE -> 1;
            case CHAR, SHORT -> 2;
            case INT, REFERENCE, FLOAT -> 4;
            case LONG, DOUBLE -> 8;
        };
    }

    private int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }
}
