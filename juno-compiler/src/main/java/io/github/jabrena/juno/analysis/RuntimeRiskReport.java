package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/** Resource estimates and runtime-risk findings for one closed-world compilation. */
public record RuntimeRiskReport(
        int arenaCapacityBytes,
        int estimatedArenaBytes,
        boolean unboundedArenaAllocation,
        int estimatedStaticRamBytes,
        int estimatedMaxStackBytes,
        int maxCallDepth,
        int boundsChecks,
        int uncheckedArrayAccesses,
        List<RuntimeRisk> findings,
        int carrierCount,
        List<MethodRef> pinnedThreadEntries) {
    public RuntimeRiskReport(int arenaCapacityBytes, int estimatedArenaBytes, boolean unboundedArenaAllocation,
                             int estimatedStaticRamBytes, int estimatedMaxStackBytes, int maxCallDepth,
                             int boundsChecks, int uncheckedArrayAccesses, List<RuntimeRisk> findings) {
        this(arenaCapacityBytes, estimatedArenaBytes, unboundedArenaAllocation, estimatedStaticRamBytes,
                estimatedMaxStackBytes, maxCallDepth, boundsChecks, uncheckedArrayAccesses, findings, 1, List.of());
    }

    public RuntimeRiskReport {
        findings = List.copyOf(findings);
        pinnedThreadEntries = List.copyOf(pinnedThreadEntries);
    }
}
