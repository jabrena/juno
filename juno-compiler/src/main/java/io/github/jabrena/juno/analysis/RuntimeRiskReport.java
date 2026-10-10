package io.github.jabrena.juno.analysis;

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
        List<RuntimeRisk> findings) {
    public RuntimeRiskReport {
        findings = List.copyOf(findings);
    }
}
