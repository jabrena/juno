package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
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
            frames.put(method.reference(), AllocationSizeEstimator.estimatedFrameBytes(method));

            InstructionRiskScanner.MethodScan scan = InstructionRiskScanner.scan(method, linked.classes(),
                    IrCyclicBlocks.cyclicBlocks(method), calls.get(method.reference()),
                    callSites.get(method.reference()), staticFields);

            directAllocation.put(method.reference(), scan.allocatedBytes());
            boundsChecks += scan.boundsChecks();
            arrayAccesses += scan.arrayAccesses();
            constantArrayBytes += scan.constantArrayBytes();
            if (scan.loopAllocation()) {
                loopAllocators.add(method.reference());
            }
            addScanFindings(findings, method.reference(), scan);
        }

        Set<MethodRef> recursive = CallGraphMetrics.recursiveMethods(calls);
        for (MethodRef method : recursive.stream()
                .sorted((left, right) -> left.displayName().compareTo(right.displayName())).toList()) {
            findings.add(new RuntimeRisk("JUNO-RISK-003", RiskSeverity.WARNING, method,
                    "recursive call cycle makes maximum call depth and stack usage unbounded"));
        }

        Set<MethodRef> transitiveAllocators = CallGraphMetrics.transitiveAllocators(calls, directAllocation);
        loopAllocators.addAll(CallGraphMetrics.loopAllocatorsCallingAllocator(program, transitiveAllocators));

        boolean unboundedArena = !loopAllocators.isEmpty()
                || recursive.stream().anyMatch(transitiveAllocators::contains);
        for (MethodRef method : loopAllocators) {
            findings.add(new RuntimeRisk("JUNO-RISK-001", RiskSeverity.WARNING, method,
                    "allocation can repeat in a control-flow loop; the fixed arena may eventually "
                            + "exhaust assuming no intermediate garbage collection reclaims space "
                            + "(the runtime does collect, but this is a static, GC-oblivious estimate)"));
        }

        int arenaBytes = CallGraphMetrics.startupAllocationEstimate(program, callSites, directAllocation);
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

        int staticFieldBytes = staticFields.stream()
                .mapToInt(field -> AllocationSizeEstimator.descriptorSize(field.descriptor())).sum();
        int estimatedStaticRam = RuntimeLimits.ARENA_CAPACITY_BYTES + staticFieldBytes + constantArrayBytes + 4;
        int maxDepth = recursive.isEmpty() ? CallGraphMetrics.maximumStartupCallDepth(program, calls) : -1;
        int maxStack = recursive.isEmpty() ? CallGraphMetrics.maximumStartupStack(program, calls, frames) : -1;
        findings.sort(RuntimeRiskAnalyzer::compareFindings);

        return new RuntimeRiskReport(RuntimeLimits.ARENA_CAPACITY_BYTES, arenaBytes, unboundedArena,
                estimatedStaticRam, maxStack, maxDepth, boundsChecks, uncheckedArrayAccesses, findings);
    }

    private static void addScanFindings(List<RuntimeRisk> findings, MethodRef method,
                                        InstructionRiskScanner.MethodScan scan) {
        if (scan.possibleDivisionByZero() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-005", RiskSeverity.WARNING, method,
                    scan.possibleDivisionByZero() + " division/remainder operation(s) may receive a zero divisor"));
        }
        if (scan.definiteNullDereferences() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-006", RiskSeverity.WARNING, method,
                    scan.definiteNullDereferences() + " reference dereference(s) use a compile-time null value"));
        }
        if (scan.oversizedStringBuilders() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-007", RiskSeverity.WARNING, method,
                    scan.oversizedStringBuilders() + " StringBuilder(s) constructed with capacity >= "
                            + RuntimeLimits.STRING_SLOT_CAPACITY_BYTES + " (or not a compile-time constant); "
                            + "toString() panics instead of truncating once content reaches that length"));
        }
    }

    private static int compareFindings(RuntimeRisk left, RuntimeRisk right) {
        int severity = right.severity().compareTo(left.severity());
        if (severity != 0) return severity;
        int code = left.code().compareTo(right.code());
        if (code != 0) return code;
        return left.method().displayName().compareTo(right.method().displayName());
    }
}
