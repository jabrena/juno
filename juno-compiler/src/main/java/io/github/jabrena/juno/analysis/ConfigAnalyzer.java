package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Compares the {@link RuntimeConfig} with what the program needs and suggests a better one. It reports only what
 * Juno can know exactly: the arena and the task slots. The final RAM fit also depends on the core and on optional
 * libraries Juno cannot see, so the Arduino linker stays authoritative for that, and no suggestion here predicts it.
 *
 * <p>{@link RuntimeRiskAnalyzer} answers what could go wrong with the program itself; this class answers whether the
 * configuration suits it.
 */
public final class ConfigAnalyzer {
    private static final Set<Intrinsic> FORKS = Set.of(
            Intrinsic.TASK_SCOPE_FORK_CALLABLE, Intrinsic.TASK_SCOPE_FORK_RUNNABLE);
    /** Findings after which the number of live tasks cannot be bounded from the fork sites alone. */
    private static final Set<String> UNBOUNDED_TASK_FINDINGS = Set.of("JUNO-RISK-003", "JUNO-RISK-008",
            "JUNO-RISK-009", "JUNO-RISK-010");
    private static final int ARENA_STEP_BYTES = 1024;

    private final RuntimeConfig config;

    public ConfigAnalyzer(RuntimeConfig config) {
        this.config = config;
    }

    public List<ConfigSuggestion> analyze(IrProgram program, RuntimeRiskReport report, Board board) {
        List<ConfigSuggestion> suggestions = new ArrayList<>();
        unusedTaskStacks(program, report, board).ifPresent(suggestions::add);
        arenaBelowStartupEstimate(report).ifPresent(suggestions::add);
        return suggestions;
    }

    /**
     * Every fork site can have at most one task live, so a program with no fork in a loop, in a recursive method or
     * in a subtask body needs one slot for the main thread plus one per fork site. Slots beyond that each hold a stack
     * for nothing.
     */
    private Optional<ConfigSuggestion> unusedTaskStacks(IrProgram program, RuntimeRiskReport report,
                                                                  Board board) {
        int forkSites = forkSites(program);
        boolean unbounded = report.findings().stream()
                .anyMatch(finding -> UNBOUNDED_TASK_FINDINGS.contains(finding.code()));
        if (forkSites == 0 || unbounded) {
            return Optional.empty();
        }
        int needed = Math.max(2, 1 + forkSites);
        int unused = config.maxThreads() - needed;
        if (unused <= 0) {
            return Optional.empty();
        }
        int stackBytes = config.threadStackBytes(board.core().defaultThreadStackBytes());
        int freed = unused * stackBytes;
        return Optional.of(new ConfigSuggestion("JUNO-CONFIG-001", "maxThreads", config.maxThreads(),
                needed, -freed, "the program forks " + forkSites + " task(s) at most, so " + unused
                        + " of the " + (config.maxThreads() - 1) + " task stack(s) of " + stackBytes
                        + " bytes are never used; " + needed + " scheduler slots would free " + freed + " bytes"));
    }

    /** The estimate is conservative and ignores collection, so it is a suggestion, not a failure. */
    private Optional<ConfigSuggestion> arenaBelowStartupEstimate(RuntimeRiskReport report) {
        if (report.estimatedArenaBytes() <= config.arenaBytes()) {
            return Optional.empty();
        }
        int suggested = (report.estimatedArenaBytes() + ARENA_STEP_BYTES - 1) / ARENA_STEP_BYTES * ARENA_STEP_BYTES;
        return Optional.of(new ConfigSuggestion("JUNO-CONFIG-002", "arenaBytes", config.arenaBytes(),
                suggested, suggested - config.arenaBytes(), "the conservative startup estimate is "
                        + report.estimatedArenaBytes() + " bytes, above the " + config.arenaBytes()
                        + " byte arena; " + suggested + " bytes would cover it"));
    }

    private static int forkSites(IrProgram program) {
        int sites = 0;
        for (IrMethod method : program.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrInstruction.IntrinsicCall call && FORKS.contains(call.intrinsic())) {
                        sites++;
                    }
                }
            }
        }
        return sites;
    }
}
