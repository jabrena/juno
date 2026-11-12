package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.board.ArduinoCore;
import io.github.jabrena.juno.board.BoardMemoryProfile;
import io.github.jabrena.juno.board.MemoryAuthority;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.ConstantTables;
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
    private final IrCyclicBlocks irCyclicBlocks = new IrCyclicBlocks();

    private final InstructionRiskScanner instructionRiskScanner = new InstructionRiskScanner();

    private final AllocationSizeEstimator allocationSizeEstimator = new AllocationSizeEstimator();

    private final CallGraphMetrics callGraphMetrics = new CallGraphMetrics();

    private final ResolvedRuntimeConfig config;

    public RuntimeRiskAnalyzer(ResolvedRuntimeConfig config) {
        this.config = config;
    }

    public RuntimeRiskReport analyze(Program linked, IrProgram program) {
        return analyze(linked, program, program);
    }

    /**
     * Analyzes the optimized {@code program}, counting array accesses and their bounds checks on {@code lowered},
     * the IR before optimization. The optimizer removes a check it proves can never fail, and that access must not
     * then be reported as unchecked: the count is of accesses whose array length is known at compile time.
     */
    public RuntimeRiskReport analyze(Program linked, IrProgram program, IrProgram lowered) {
        Map<MethodRef, Set<MethodRef>> calls = new HashMap<>();
        Map<MethodRef, List<MethodRef>> callSites = new HashMap<>();
        Map<MethodRef, Integer> directAllocation = new HashMap<>();
        Map<MethodRef, Integer> frames = new HashMap<>();
        Set<MethodRef> loopAllocators = new LinkedHashSet<>();
        Set<FieldRef> staticFields = new HashSet<>();
        Set<MethodRef> threadEntries = new LinkedHashSet<>();
        Set<MethodRef> launchers = new HashSet<>();
        List<RuntimeRisk> findings = new ArrayList<>();
        List<MethodRef> pinnedThreadEntries = new ArrayList<>();
        int boundsChecks = 0;
        int arrayAccesses = 0;
        int constantArrayBytes = 0;

        for (IrMethod method : program.methods()) {
            calls.put(method.reference(), new LinkedHashSet<>());
            callSites.put(method.reference(), new ArrayList<>());
            frames.put(method.reference(), allocationSizeEstimator.estimatedFrameBytes(method));

            InstructionRiskScanner.MethodScan scan = instructionRiskScanner.scan(method, linked.classes(),
                    program.objectTypeIds(), irCyclicBlocks.cyclicBlocks(method), calls.get(method.reference()),
                    callSites.get(method.reference()), staticFields, threadEntries);

            directAllocation.put(method.reference(), scan.allocatedBytes());
            boundsChecks += scan.boundsChecks();
            arrayAccesses += scan.arrayAccesses();
            constantArrayBytes += scan.constantArrayBytes();
            if (scan.loopAllocation()) {
                loopAllocators.add(method.reference());
            }
            addScanFindings(findings, method.reference(), scan);
            if (scan.launchesThreads()) {
                launchers.add(method.reference());
            }
        }

        Set<MethodRef> recursive = callGraphMetrics.recursiveMethods(calls);
        for (MethodRef method : recursive.stream()
                .sorted((left, right) -> left.displayName().compareTo(right.displayName())).toList()) {
            findings.add(new RuntimeRisk("JUNO-RISK-003", RiskSeverity.WARNING, method,
                    "recursive call cycle makes maximum call depth and stack usage unbounded"));
        }

        addThreadBodyFindings(findings, threadEntries, launchers, recursive, calls, frames);
        int carrierCount = config.carrierCount();
        if (carrierCount > 1) {
            addCarrierFindings(linked, program, threadEntries, calls, findings, pinnedThreadEntries);
        }

        Set<MethodRef> transitiveAllocators = callGraphMetrics.transitiveAllocators(calls, directAllocation);
        loopAllocators.addAll(callGraphMetrics.loopAllocatorsCallingAllocator(program, transitiveAllocators));

        boolean unboundedArena = !loopAllocators.isEmpty()
                || recursive.stream().anyMatch(transitiveAllocators::contains);
        for (MethodRef method : loopAllocators) {
            findings.add(new RuntimeRisk("JUNO-RISK-001", RiskSeverity.WARNING, method,
                    "allocation can repeat in a control-flow loop; the fixed arena may eventually "
                            + "exhaust assuming no intermediate garbage collection reclaims space "
                            + "(the runtime does collect, but this is a static, GC-oblivious estimate)"));
        }

        int arenaBytes = callGraphMetrics.startupAllocationEstimate(program, callSites, directAllocation);
        if (arenaBytes > config.arenaBytes()) {
            findings.add(new RuntimeRisk("JUNO-RISK-002", RiskSeverity.WARNING, program.entryPoint(),
                    "conservative startup arena estimate (assuming no intermediate garbage collection "
                            + "reclaims space) is " + arenaBytes + " bytes, exceeding the "
                            + config.arenaBytes() + " byte capacity"));
        }

        for (ConstantTables.RamFallback fallback : ConstantTables.of(linked).ramFallbacks()) {
            findings.add(new RuntimeRisk("JUNO-RISK-012", RiskSeverity.INFO, fallback.method(),
                    "constant table " + fallback.field().displayName() + " stays in the arena instead of flash: it "
                            + fallback.reason()));
        }

        // Identity, not equality: skip the recount when the optimizer returned its input instance unchanged.
        @SuppressWarnings("ReferenceEquality")
        boolean loweredChanged = lowered != program;
        if (loweredChanged) {
            boundsChecks = count(lowered, IrInstruction.BoundsCheck.class);
            arrayAccesses = count(lowered, IrInstruction.ArrayLoad.class) + count(lowered, IrInstruction.ArrayStore.class);
        }
        int uncheckedArrayAccesses = Math.max(0, arrayAccesses - boundsChecks);
        if (uncheckedArrayAccesses > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-004", RiskSeverity.WARNING, program.entryPoint(),
                    uncheckedArrayAccesses + " array access(es) have no compile-time-known bounds check"));
        }

        int staticFieldBytes = staticFields.stream()
                .mapToInt(field -> allocationSizeEstimator.descriptorSize(field.descriptor())).sum();
        int estimatedStaticRam = config.arenaBytes() + staticFieldBytes + constantArrayBytes + 4;
        BoardMemoryProfile profile = config.board().memoryProfile();
        if (profile.authority() == MemoryAuthority.LOADER_POOL
                && (long) estimatedStaticRam * 10 > (long) profile.internalSramBytes() * 9) {
            findings.add(new RuntimeRisk("JUNO-RISK-014", RiskSeverity.WARNING, program.entryPoint(),
                    "estimated arena and static RAM of " + estimatedStaticRam + " bytes is above 90% of the "
                            + profile.internalSramBytes() + " byte " + config.board().displayName()
                            + " loader pool; the sketch's code shares that pool, so it may not load at boot"));
        }
        int maxDepth = recursive.isEmpty() ? callGraphMetrics.maximumStartupCallDepth(program, calls) : -1;
        int maxStack = recursive.isEmpty() ? callGraphMetrics.maximumStartupStack(program, calls, frames) : -1;
        findings.sort(this::compareFindings);

        return new RuntimeRiskReport(config.arenaBytes(), arenaBytes, unboundedArena,
                estimatedStaticRam, maxStack, maxDepth, boundsChecks, uncheckedArrayAccesses, findings,
                carrierCount, pinnedThreadEntries);
    }

    private void addCarrierFindings(Program linked, IrProgram program, Set<MethodRef> threadEntries,
                                    Map<MethodRef, Set<MethodRef>> calls, List<RuntimeRisk> findings,
                                    List<MethodRef> pinnedThreadEntries) {
        if (linked.board().core() != ArduinoCore.RP2350) return;
        for (MethodRef entry : threadEntries.stream().filter(calls::containsKey)
                .sorted((left, right) -> left.displayName().compareTo(right.displayName())).toList()) {
            Set<MethodRef> reachable = callGraphMetrics.reachableFrom(entry, calls);
            boolean reachesDevice = program.methods().stream()
                    .filter(method -> reachable.contains(method.reference()) || method.reference().equals(entry))
                    .flatMap(method -> method.blocks().stream())
                    .flatMap(block -> block.instructions().stream())
                    .filter(IrInstruction.IntrinsicCall.class::isInstance)
                    .map(IrInstruction.IntrinsicCall.class::cast)
                    .map(IrInstruction.IntrinsicCall::intrinsic)
                    .anyMatch(this::requiresCoreZero);
            if (reachesDevice) {
                pinnedThreadEntries.add(entry);
                findings.add(new RuntimeRisk("JUNO-RISK-013", RiskSeverity.INFO, entry,
                        "thread entry reaches a device intrinsic that is not confirmed safe on RP2350 core 1; "
                                + "pinned to carrier 0"));
            }
        }
    }

    private boolean requiresCoreZero(Intrinsic intrinsic) {
        String name = intrinsic.name();
        return name.startsWith("WIFI_") || name.startsWith("HTTP_") || name.startsWith("HTTPS_")
                || name.startsWith("TCP_") || name.startsWith("UDP_") || name.startsWith("BLUETOOTH_");
    }

    private void addScanFindings(List<RuntimeRisk> findings, MethodRef method,
                                        InstructionRiskScanner.MethodScan scan) {
        if (scan.possibleDivisionByZero() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-005", RiskSeverity.WARNING, method,
                    scan.possibleDivisionByZero() + " division/remainder operation(s) may receive a zero divisor"));
        }
        if (scan.definiteNullDereferences() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-006", RiskSeverity.WARNING, method,
                    scan.definiteNullDereferences() + " reference dereference(s) use a compile-time null value"));
        }
        if (scan.launchesInLoops() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-008", RiskSeverity.WARNING, method,
                    scan.launchesInLoops() + " virtual-thread start or fork call(s) sit in a control-flow loop; every live"
                            + " one keeps its frames in the arena while parked (on AVR boards at most "
                            + (RuntimeLimits.MAX_THREADS - 1) + " can be active beside the caller)"));
        }
        if (scan.peakUnjoinedLaunches() > RuntimeLimits.MAX_THREADS - 1) {
            findings.add(new RuntimeRisk("JUNO-RISK-009", RiskSeverity.WARNING, method,
                    scan.peakUnjoinedLaunches() + " threads or subtasks are started with no join in between; each"
                            + " keeps its frames in the arena while parked (on AVR boards at most "
                            + (RuntimeLimits.MAX_THREADS - 1) + " can be active beside the caller)"));
        }
        if (scan.oversizedStringBuilders() > 0) {
            findings.add(new RuntimeRisk("JUNO-RISK-007", RiskSeverity.WARNING, method,
                    scan.oversizedStringBuilders() + " StringBuilder(s) constructed with capacity >= "
                            + RuntimeLimits.STRING_SLOT_CAPACITY_BYTES + " (or not a compile-time constant); "
                            + "toString() panics instead of truncating once content reaches that length"));
        }
    }

    /** {@code JUNO-RISK-010}/{@code 011}: what a thread body does with its own small stack and the shared slots. */
    private void addThreadBodyFindings(List<RuntimeRisk> findings, Set<MethodRef> threadEntries,
                                              Set<MethodRef> launchers, Set<MethodRef> recursive,
                                              Map<MethodRef, Set<MethodRef>> calls,
                                              Map<MethodRef, Integer> frames) {
        for (MethodRef entry : threadEntries.stream().filter(calls::containsKey)
                .sorted((left, right) -> left.displayName().compareTo(right.displayName())).toList()) {
            Set<MethodRef> reachable = callGraphMetrics.reachableFrom(entry, calls);
            if (reachable.stream().anyMatch(launchers::contains)) {
                findings.add(new RuntimeRisk("JUNO-RISK-010", RiskSeverity.WARNING, entry,
                        "thread body starts a thread or opens a task scope; nested threads share the arena (on AVR"
                                + " boards, the " + RuntimeLimits.MAX_THREADS + " scheduler slots) with their parent"));
            }
            if (reachable.stream().anyMatch(recursive::contains)) {
                findings.add(new RuntimeRisk("JUNO-RISK-011", RiskSeverity.WARNING, entry,
                        "thread body reaches a recursive call cycle; the " + threadStackBytes()
                                + " byte carrier stack cannot be proven sufficient"));
                continue;
            }
            int stack = callGraphMetrics.maximumStack(entry, calls, frames);
            if (stack > threadStackBytes()) {
                findings.add(new RuntimeRisk("JUNO-RISK-011", RiskSeverity.WARNING, entry,
                        "estimated thread stack use of " + stack + " bytes exceeds the carrier stack of the "
                                + config.board().displayName() + " (" + threadStackBytes() + " bytes)"));
            }
        }
    }

    /** The carrier stack the analysis judges thread bodies against: the one the build resolved for its board. */
    private int threadStackBytes() {
        return config.threadStackBytes();
    }

    private int compareFindings(RuntimeRisk left, RuntimeRisk right) {
        int severity = right.severity().compareTo(left.severity());
        if (severity != 0) return severity;
        int code = left.code().compareTo(right.code());
        if (code != 0) return code;
        return left.method().displayName().compareTo(right.method().displayName());
    }

    private int count(IrProgram program, Class<? extends IrInstruction> kind) {
        return (int) program.methods().stream().flatMap(method -> method.blocks().stream())
                .flatMap(block -> block.instructions().stream()).filter(kind::isInstance).count();
    }
}
