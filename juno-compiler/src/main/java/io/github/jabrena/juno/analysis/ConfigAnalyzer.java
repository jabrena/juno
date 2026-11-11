package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.ir.IrProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares the {@link RuntimeConfig} with what the program needs and suggests a better one. It reports only what
 * Juno can know exactly: the arena. The final RAM fit also depends on the core and on optional
 * libraries Juno cannot see, so the Arduino linker stays authoritative for that, and no suggestion here predicts it.
 *
 * <p>{@link RuntimeRiskAnalyzer} answers what could go wrong with the program itself; this class answers whether the
 * configuration suits it.
 */
public final class ConfigAnalyzer {
    private static final int ARENA_STEP_BYTES = 1024;

    private final RuntimeConfig config;

    public ConfigAnalyzer(RuntimeConfig config) {
        this.config = config;
    }

    public List<ConfigSuggestion> analyze(IrProgram program, RuntimeRiskReport report, Board board) {
        List<ConfigSuggestion> suggestions = new ArrayList<>();
        arenaBelowStartupEstimate(report).ifPresent(suggestions::add);
        return suggestions;
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
}
