package io.github.jabrena.juno.analysis;

import java.util.ArrayList;
import java.util.List;

/** Renders a {@link RuntimeRiskReport} as human-readable lines, shared by the CLI and the Maven plugin. */
public final class RuntimeRiskReportFormatter {
    private RuntimeRiskReportFormatter() {
    }

    public static List<String> format(RuntimeRiskReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Runtime risk analysis:");
        String arenaQualifier = report.unboundedArenaAllocation()
                ? "repetition risk: allocation occurs in a loop or recursive path"
                : "conservative startup estimate";
        lines.add("  Arena: " + report.estimatedArenaBytes() + " / " + report.arenaCapacityBytes()
                + " bytes (" + arenaQualifier + ")");
        lines.add("  Estimated Juno static RAM: " + report.estimatedStaticRamBytes() + " bytes");
        lines.add("  Estimated generated locals on deepest call path: "
                + formatBound(report.estimatedMaxStackBytes(), "bytes"));
        lines.add("  Maximum generated call depth: " + formatBound(report.maxCallDepth(), "frames"));
        lines.add("  Generated bounds checks: " + report.boundsChecks());
        lines.add("  Unchecked array accesses: " + report.uncheckedArrayAccesses());
        if (report.findings().isEmpty()) {
            lines.add("  Findings: (none)");
        } else {
            lines.add("  Findings:");
            for (RuntimeRisk finding : report.findings()) {
                lines.add("    [" + finding.severity() + " " + finding.code() + "] "
                        + finding.method().displayName() + ": " + finding.message());
            }
        }
        lines.add("  Note: resource figures are conservative source-level estimates; the Arduino linker"
                + " remains authoritative for final RAM/flash use.");
        return lines;
    }

    private static String formatBound(int value, String unit) {
        return value < 0 ? "unbounded (recursion)" : value + " " + unit;
    }
}
