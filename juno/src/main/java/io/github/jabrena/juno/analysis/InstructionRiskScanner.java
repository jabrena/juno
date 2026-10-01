package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans one method's instructions for the direct-allocation byte count and the per-method risk
 * signals ({@code JUNO-RISK-005}..{@code 007}), and records its call edges and touched static fields.
 */
final class InstructionRiskScanner {
    /** {@code Math.floorDiv}/{@code floorMod} panic on a zero divisor exactly like {@code /} and {@code %}. */
    private static final Set<Intrinsic> FLOOR_DIVISIONS = Set.of(
            Intrinsic.MATH_FLOOR_DIV_INT, Intrinsic.MATH_FLOOR_DIV_LONG, Intrinsic.MATH_FLOOR_DIV_LONG_INT,
            Intrinsic.MATH_FLOOR_MOD_INT, Intrinsic.MATH_FLOOR_MOD_LONG, Intrinsic.MATH_FLOOR_MOD_LONG_INT);

    private InstructionRiskScanner() {
    }

    record MethodScan(int allocatedBytes, int possibleDivisionByZero, int definiteNullDereferences,
                       int oversizedStringBuilders, int boundsChecks, int arrayAccesses,
                       int constantArrayBytes, boolean loopAllocation) {
    }

    static MethodScan scan(IrMethod method, Map<String, JavaClass> classes, Map<String, Integer> objectTypeIds,
                            Set<Integer> cyclicBlocks, Set<MethodRef> calls, List<MethodRef> callSites,
                            Set<FieldRef> staticFields) {
        Map<Value, Integer> integerConstants = new HashMap<>();
        Counters counters = new Counters();
        int allocated = 0;
        int constantArrayBytes = 0;
        boolean loopAllocation = false;

        for (IrBasicBlock block : method.blocks()) {
            int blockAllocation = 0;
            for (IrInstruction instruction : block.instructions()) {
                int bytes = AllocationSizeEstimator.allocationBytes(instruction, classes, objectTypeIds);
                allocated += bytes;
                blockAllocation += bytes;
                if (instruction instanceof IrInstruction.IntrinsicCall call
                        && call.intrinsic() == Intrinsic.STRING_BUILDER_NEW) {
                    Integer capacity = integerConstants.get(call.arguments().get(0));
                    if (capacity == null || capacity >= RuntimeLimits.STRING_SLOT_CAPACITY_BYTES) {
                        counters.oversizedStringBuilders++;
                    }
                }
                classifyInstruction(instruction, integerConstants, calls, callSites, staticFields, counters);
                if (instruction instanceof IrInstruction.IntArrayConst array) {
                    constantArrayBytes += array.values().size() * 4;
                }
                if (mayDivideByZero(instruction, integerConstants)) {
                    counters.possibleDivisionByZero++;
                }
            }
            if (blockAllocation > 0 && cyclicBlocks.contains(block.start())) {
                loopAllocation = true;
            }
        }
        return new MethodScan(allocated, counters.possibleDivisionByZero, counters.definiteNullDereferences,
                counters.oversizedStringBuilders, counters.boundsChecks, counters.arrayAccesses,
                constantArrayBytes, loopAllocation);
    }

    /** Handles the instruction kinds that record call edges, static-field touches or a definite null dereference. */
    private static void classifyInstruction(IrInstruction instruction, Map<Value, Integer> integerConstants,
                                            Set<MethodRef> calls, List<MethodRef> callSites,
                                            Set<FieldRef> staticFields, Counters counters) {
        if (instruction instanceof IrInstruction.Call call) {
            calls.add(call.method());
            callSites.add(call.method());
            if (call.arguments().size() == Descriptor.parse(call.method().descriptor()).parameters().size() + 1
                    && isDefinitelyNull(call.arguments().get(0), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.InterfaceCall call) {
            for (var target : call.targets()) {
                calls.add(target.method());
                callSites.add(target.method());
            }
            if (isDefinitelyNull(call.arguments().getFirst(), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.LoadStatic load) {
            staticFields.add(load.field());
        } else if (instruction instanceof IrInstruction.StoreStatic store) {
            staticFields.add(store.field());
        } else if (instruction instanceof IrInstruction.BoundsCheck) {
            counters.boundsChecks++;
        } else if (instruction instanceof IrInstruction.ArrayLoad load) {
            counters.arrayAccesses++;
            if (isDefinitelyNull(load.array(), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.ArrayStore store) {
            counters.arrayAccesses++;
            if (isDefinitelyNull(store.array(), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.LoadField load) {
            if (isDefinitelyNull(load.receiver(), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.StoreField store) {
            if (isDefinitelyNull(store.receiver(), integerConstants)) {
                counters.definiteNullDereferences++;
            }
        } else if (instruction instanceof IrInstruction.Const constant) {
            integerConstants.put(constant.target(), constant.value());
        } else if (instruction instanceof IrInstruction.LongConst constant) {
            integerConstants.put(constant.targetLow(), (int) constant.value());
            integerConstants.put(constant.targetHigh(), (int) (constant.value() >>> 32));
        } else if (instruction instanceof IrInstruction.PackLong pack
                && integerConstants.containsKey(pack.valueLow())
                && integerConstants.containsKey(pack.valueHigh())) {
            // Only zero-ness matters for a packed long (see mayDivideByZero).
            boolean zero = integerConstants.get(pack.valueLow()) == 0
                    && integerConstants.get(pack.valueHigh()) == 0;
            integerConstants.put(pack.target(), zero ? 0 : 1);
        }
    }

    /** Mutable per-method counters threaded through {@link #classifyInstruction}. */
    private static final class Counters {
        int possibleDivisionByZero;
        int definiteNullDereferences;
        int oversizedStringBuilders;
        int boundsChecks;
        int arrayAccesses;
    }

    private static boolean mayDivideByZero(IrInstruction instruction, Map<Value, Integer> constants) {
        if (instruction instanceof IrInstruction.Binary binary
                && (binary.operation() == BinaryOp.DIVIDE || binary.operation() == BinaryOp.REMAINDER)) {
            return constants.getOrDefault(binary.right(), 0) == 0;
        }
        if (instruction instanceof IrInstruction.LongBinary binary
                && (binary.operation() == BinaryOp.DIVIDE || binary.operation() == BinaryOp.REMAINDER)) {
            Integer low = constants.get(binary.rightLow());
            Integer high = constants.get(binary.rightHigh());
            return low == null || high == null || (low == 0 && high == 0);
        }
        if (instruction instanceof IrInstruction.IntrinsicCall call && FLOOR_DIVISIONS.contains(call.intrinsic())) {
            return constants.getOrDefault(call.arguments().get(1), 0) == 0;
        }
        return false;
    }

    private static boolean isDefinitelyNull(Value value, Map<Value, Integer> constants) {
        return constants.getOrDefault(value, 1) == 0;
    }
}
