package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.Program;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative resource and runtime-risk analysis over the optimized closed-world IR. */
public final class RuntimeRiskAnalyzer {
    public RuntimeRiskReport analyze(Program linked, IrProgram program) {
        Map<MethodRef, Set<MethodRef>> calls = new HashMap<>();
        Map<MethodRef, List<MethodRef>> callSites = new HashMap<>();
        Map<MethodRef, Integer> directAllocation = new HashMap<>();
        Map<MethodRef, Integer> frames = new HashMap<>();
        Set<MethodRef> loopAllocators = new LinkedHashSet<>();
        Set<FieldRef> staticFields = new HashSet<>();
        List<RuntimeRisk> findings = new ArrayList<>();
        int boundsChecks = 0;
        int arrayAccesses = 0;
        int constantArrayBytes = 0;

        for (IrMethod method : program.methods()) {
            calls.put(method.reference(), new LinkedHashSet<>());
            callSites.put(method.reference(), new ArrayList<>());
            frames.put(method.reference(), estimatedFrameBytes(method));
            Set<Integer> cyclicBlocks = cyclicBlocks(method);
            Map<Value, Integer> integerConstants = new HashMap<>();
            int allocated = 0;
            int possibleDivisionByZero = 0;
            int definiteNullDereferences = 0;
            int oversizedStringBuilders = 0;
            for (IrBasicBlock block : method.blocks()) {
                int blockAllocation = 0;
                for (IrInstruction instruction : block.instructions()) {
                    int bytes = allocationBytes(instruction, linked.classes());
                    allocated += bytes;
                    blockAllocation += bytes;
                    if (instruction instanceof IrInstruction.IntrinsicCall call
                            && call.intrinsic() == Intrinsic.STRING_BUILDER_NEW) {
                        Integer capacity = integerConstants.get(call.arguments().get(0));
                        if (capacity == null || capacity >= RuntimeLimits.STRING_SLOT_CAPACITY_BYTES) {
                            oversizedStringBuilders++;
                        }
                    }
                    if (instruction instanceof IrInstruction.Call call) {
                        calls.get(method.reference()).add(call.method());
                        callSites.get(method.reference()).add(call.method());
                        if (call.arguments().size() == Descriptor.parse(call.method().descriptor()).parameters().size() + 1
                                && isDefinitelyNull(call.arguments().get(0), integerConstants)) {
                            definiteNullDereferences++;
                        }
                    } else if (instruction instanceof IrInstruction.LoadStatic load) {
                        staticFields.add(load.field());
                    } else if (instruction instanceof IrInstruction.StoreStatic store) {
                        staticFields.add(store.field());
                    } else if (instruction instanceof IrInstruction.BoundsCheck) {
                        boundsChecks++;
                    } else if (instruction instanceof IrInstruction.ArrayLoad load) {
                        arrayAccesses++;
                        if (isDefinitelyNull(load.array(), integerConstants)) {
                            definiteNullDereferences++;
                        }
                    } else if (instruction instanceof IrInstruction.ArrayStore store) {
                        arrayAccesses++;
                        if (isDefinitelyNull(store.array(), integerConstants)) {
                            definiteNullDereferences++;
                        }
                    } else if (instruction instanceof IrInstruction.LoadField load) {
                        if (isDefinitelyNull(load.receiver(), integerConstants)) {
                            definiteNullDereferences++;
                        }
                    } else if (instruction instanceof IrInstruction.StoreField store) {
                        if (isDefinitelyNull(store.receiver(), integerConstants)) {
                            definiteNullDereferences++;
                        }
                    } else if (instruction instanceof IrInstruction.Const constant) {
                        integerConstants.put(constant.target(), constant.value());
                    } else if (instruction instanceof IrInstruction.LongConst constant) {
                        integerConstants.put(constant.targetLow(), (int) constant.value());
                        integerConstants.put(constant.targetHigh(), (int) (constant.value() >>> 32));
                    }
                    if (instruction instanceof IrInstruction.IntArrayConst array) {
                        constantArrayBytes += array.values().size() * 4;
                    }
                    if (mayDivideByZero(instruction, integerConstants)) {
                        possibleDivisionByZero++;
                    }
                }
                if (blockAllocation > 0 && cyclicBlocks.contains(block.start())) {
                    loopAllocators.add(method.reference());
                }
            }
            directAllocation.put(method.reference(), allocated);
            if (possibleDivisionByZero > 0) {
                findings.add(new RuntimeRisk("JUNO-RISK-005", RiskSeverity.WARNING, method.reference(),
                        possibleDivisionByZero + " division/remainder operation(s) may receive a zero divisor"));
            }
            if (definiteNullDereferences > 0) {
                findings.add(new RuntimeRisk("JUNO-RISK-006", RiskSeverity.WARNING, method.reference(),
                        definiteNullDereferences + " reference dereference(s) use a compile-time null value"));
            }
            if (oversizedStringBuilders > 0) {
                findings.add(new RuntimeRisk("JUNO-RISK-007", RiskSeverity.WARNING, method.reference(),
                        oversizedStringBuilders + " StringBuilder(s) constructed with capacity >= "
                                + RuntimeLimits.STRING_SLOT_CAPACITY_BYTES + " (or not a compile-time constant); "
                                + "toString() panics instead of truncating once content reaches that length"));
            }
        }

        Set<MethodRef> recursive = recursiveMethods(calls);
        for (MethodRef method : recursive.stream()
                .sorted((left, right) -> left.displayName().compareTo(right.displayName())).toList()) {
            findings.add(new RuntimeRisk("JUNO-RISK-003", RiskSeverity.WARNING, method,
                    "recursive call cycle makes maximum call depth and stack usage unbounded"));
        }

        Set<MethodRef> transitiveAllocators = transitiveAllocators(calls, directAllocation);
        for (IrMethod method : program.methods()) {
            Set<Integer> cyclicBlocks = cyclicBlocks(method);
            for (IrBasicBlock block : method.blocks()) {
                if (!cyclicBlocks.contains(block.start())) {
                    continue;
                }
                boolean callsAllocator = block.instructions().stream()
                        .filter(IrInstruction.Call.class::isInstance)
                        .map(IrInstruction.Call.class::cast)
                        .anyMatch(call -> transitiveAllocators.contains(call.method()));
                if (callsAllocator) {
                    loopAllocators.add(method.reference());
                }
            }
        }

        boolean unboundedArena = !loopAllocators.isEmpty()
                || recursive.stream().anyMatch(transitiveAllocators::contains);
        for (MethodRef method : loopAllocators) {
            findings.add(new RuntimeRisk("JUNO-RISK-001", RiskSeverity.WARNING, method,
                    "allocation can repeat in a control-flow loop; the fixed arena may eventually "
                            + "exhaust assuming no intermediate garbage collection reclaims space "
                            + "(the runtime does collect, but this is a static, GC-oblivious estimate)"));
        }

        int arenaBytes = startupAllocationEstimate(program, callSites, directAllocation);
        if (arenaBytes > RuntimeLimits.ARENA_CAPACITY_BYTES) {
            findings.add(new RuntimeRisk("JUNO-RISK-002", RiskSeverity.WARNING, program.entryPoint(),
                    "conservative startup arena estimate (assuming no intermediate garbage collection "
                            + "reclaims space) is " + arenaBytes + " bytes, exceeding the "
                            + RuntimeLimits.ARENA_CAPACITY_BYTES + " byte capacity"));
        }

        int uncheckedArrayAccesses = Math.max(0, arrayAccesses - boundsChecks);
        if (uncheckedArrayAccesses > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-004", RiskSeverity.WARNING, program.entryPoint(),
                    uncheckedArrayAccesses + " array access(es) have no compile-time-known bounds check"));
        }

        int staticFieldBytes = staticFields.stream().mapToInt(field -> descriptorSize(field.descriptor())).sum();
        int estimatedStaticRam = RuntimeLimits.ARENA_CAPACITY_BYTES + staticFieldBytes + constantArrayBytes + 4;
        int maxDepth = recursive.isEmpty() ? maximumStartupCallDepth(program, calls) : -1;
        int maxStack = recursive.isEmpty() ? maximumStartupStack(program, calls, frames) : -1;
        findings.sort((left, right) -> {
            int severity = right.severity().compareTo(left.severity());
            if (severity != 0) return severity;
            int code = left.code().compareTo(right.code());
            if (code != 0) return code;
            return left.method().displayName().compareTo(right.method().displayName());
        });
        return new RuntimeRiskReport(RuntimeLimits.ARENA_CAPACITY_BYTES, arenaBytes, unboundedArena,
                estimatedStaticRam, maxStack, maxDepth, boundsChecks, uncheckedArrayAccesses, findings);
    }

    private int allocationBytes(IrInstruction instruction, Map<String, JavaClass> classes) {
        if (instruction instanceof IrInstruction.NewArray array) {
            int alignment = elementSize(array.elementType());
            return allocationUpperBound(array.length() * alignment, alignment);
        }
        if (instruction instanceof IrInstruction.NewObject object) {
            int size = objectSize(classes.get(object.className()));
            int alignment = objectAlignment(classes.get(object.className()));
            return allocationUpperBound(size, alignment);
        }
        if (instruction instanceof IrInstruction.NewMultiArray array) {
            return multiArrayBytes(array.leafType(), array.dimensions());
        }
        return 0;
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

    private int objectSize(JavaClass javaClass) {
        if (javaClass == null) {
            return 1;
        }
        int offset = 0;
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

    private int objectAlignment(JavaClass javaClass) {
        if (javaClass == null) return 1;
        return javaClass.fields().stream()
                .filter(field -> !field.isStatic())
                .mapToInt(field -> Math.min(descriptorSize(field.descriptor()), 8))
                .max().orElse(1);
    }

    private int descriptorSize(String descriptor) {
        if (Descriptor.isLong(descriptor) || Descriptor.isDouble(descriptor)) return 8;
        return 4;
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

    private int estimatedFrameBytes(IrMethod method) {
        Descriptor descriptor = Descriptor.parse(method.reference().descriptor());
        boolean typedSlots = method.values().stream().anyMatch(value -> value.type() != JunoType.INT32)
                || descriptor.parameters().stream().anyMatch(type -> Descriptor.isLong(type)
                        || Descriptor.isFloat(type) || Descriptor.isDouble(type));
        int bytes = Math.max(1, method.maxLocals()) * (typedSlots ? 8 : 4);
        for (Value value : method.values()) {
            bytes += switch (value.type()) {
                case INT32, FLOAT32 -> 4;
                case INT64, FLOAT64 -> 8;
            };
        }
        return bytes + maximumTemporaryBytes(method);
    }

    private int maximumTemporaryBytes(IrMethod method) {
        int maximum = 0;
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                int temporaryBytes = switch (instruction) {
                    case IrInstruction.IntrinsicCall call ->
                            (call.arguments().size() + (call.receiver().isPresent() ? 1 : 0)) * 4;
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

    private boolean mayDivideByZero(IrInstruction instruction, Map<Value, Integer> constants) {
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
        return false;
    }

    private boolean isDefinitelyNull(Value value, Map<Value, Integer> constants) {
        return constants.getOrDefault(value, 1) == 0;
    }

    private Set<Integer> cyclicBlocks(IrMethod method) {
        Map<Integer, List<Integer>> edges = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            edges.put(block.start(), successors(block.terminator()));
        }
        Set<Integer> cyclic = new HashSet<>();
        for (int block : edges.keySet()) {
            for (int successor : edges.get(block)) {
                if (reaches(successor, block, edges, new HashSet<>())) {
                    cyclic.add(block);
                    break;
                }
            }
        }
        return cyclic;
    }

    private List<Integer> successors(IrTerminator terminator) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> List.of(jump.target());
            case IrTerminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return ignored -> List.of();
            case IrTerminator.Switch switched -> {
                List<Integer> targets = new ArrayList<>(switched.targets());
                targets.add(switched.defaultTarget());
                yield List.copyOf(targets);
            }
        };
    }

    private boolean reaches(MethodRef start, MethodRef target, Map<MethodRef, Set<MethodRef>> edges,
                            Set<MethodRef> visited) {
        if (start.equals(target)) return true;
        if (!visited.add(start)) return false;
        for (MethodRef successor : edges.getOrDefault(start, Set.of())) {
            if (reaches(successor, target, edges, visited)) return true;
        }
        return false;
    }

    private boolean reaches(int start, int target, Map<Integer, List<Integer>> edges, Set<Integer> visited) {
        if (start == target) return true;
        if (!visited.add(start)) return false;
        for (int successor : edges.getOrDefault(start, List.of())) {
            if (reaches(successor, target, edges, visited)) return true;
        }
        return false;
    }

    private Set<MethodRef> recursiveMethods(Map<MethodRef, Set<MethodRef>> calls) {
        Set<MethodRef> recursive = new HashSet<>();
        for (MethodRef method : calls.keySet()) {
            for (MethodRef callee : calls.get(method)) {
                if (reaches(callee, method, calls, new HashSet<>())) {
                    recursive.add(method);
                    break;
                }
            }
        }
        return recursive;
    }

    private Set<MethodRef> transitiveAllocators(Map<MethodRef, Set<MethodRef>> calls,
                                                Map<MethodRef, Integer> allocations) {
        Set<MethodRef> allocators = new HashSet<>();
        for (MethodRef method : calls.keySet()) {
            if (allocations.getOrDefault(method, 0) > 0 || reachesAllocator(method, calls, allocations, new HashSet<>())) {
                allocators.add(method);
            }
        }
        return allocators;
    }

    private boolean reachesAllocator(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                     Map<MethodRef, Integer> allocations, Set<MethodRef> visited) {
        if (!visited.add(method)) return false;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            if (allocations.getOrDefault(callee, 0) > 0
                    || reachesAllocator(callee, calls, allocations, visited)) return true;
        }
        return false;
    }

    private int longestCallDepth(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                 Map<MethodRef, Integer> memo) {
        Integer cached = memo.get(method);
        if (cached != null) return cached;
        int depth = 1;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            depth = Math.max(depth, 1 + longestCallDepth(callee, calls, memo));
        }
        memo.put(method, depth);
        return depth;
    }

    private int longestStackPath(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                 Map<MethodRef, Integer> frames, Map<MethodRef, Integer> memo) {
        Integer cached = memo.get(method);
        if (cached != null) return cached;
        int child = 0;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            child = Math.max(child, longestStackPath(callee, calls, frames, memo));
        }
        int bytes = frames.getOrDefault(method, 0) + child;
        memo.put(method, bytes);
        return bytes;
    }

    private int maximumStartupCallDepth(IrProgram program, Map<MethodRef, Set<MethodRef>> calls) {
        Map<MethodRef, Integer> memo = new HashMap<>();
        int maximum = longestCallDepth(program.entryPoint(), calls, memo);
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                maximum = Math.max(maximum, longestCallDepth(method.reference(), calls, memo));
            }
        }
        return maximum;
    }

    private int maximumStartupStack(IrProgram program, Map<MethodRef, Set<MethodRef>> calls,
                                    Map<MethodRef, Integer> frames) {
        Map<MethodRef, Integer> memo = new HashMap<>();
        int maximum = longestStackPath(program.entryPoint(), calls, frames, memo);
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                maximum = Math.max(maximum, longestStackPath(method.reference(), calls, frames, memo));
            }
        }
        return maximum;
    }

    private int startupAllocationEstimate(IrProgram program, Map<MethodRef, List<MethodRef>> callSites,
                                          Map<MethodRef, Integer> directAllocation) {
        int estimate = allocationPerInvocation(program.entryPoint(), callSites, directAllocation, new HashSet<>());
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                estimate = Math.addExact(estimate,
                        allocationPerInvocation(method.reference(), callSites, directAllocation, new HashSet<>()));
            }
        }
        return estimate;
    }

    private int allocationPerInvocation(MethodRef method, Map<MethodRef, List<MethodRef>> callSites,
                                        Map<MethodRef, Integer> directAllocation, Set<MethodRef> active) {
        if (!active.add(method)) return 0;
        int estimate = directAllocation.getOrDefault(method, 0);
        for (MethodRef callee : callSites.getOrDefault(method, List.of())) {
            estimate = Math.addExact(estimate,
                    allocationPerInvocation(callee, callSites, directAllocation, new HashSet<>(active)));
        }
        return estimate;
    }
}
