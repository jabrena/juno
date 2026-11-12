package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.ResolvedRuntimeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares the {@link ResolvedRuntimeConfig} with what the program needs and suggests a better one. It reports only what
 * Juno can know exactly: the arena. The final RAM fit also depends on the core and on optional
 * libraries Juno cannot see, so the Arduino linker stays authoritative for that, and no suggestion here predicts it.
 *
 * <p>{@link RuntimeRiskAnalyzer} answers what could go wrong with the program itself; this class answers whether the
 * configuration suits it. A suggestion never goes above the board's maximum arena.
 */
public final class ConfigAnalyzer {
    private static final int ARENA_STEP_BYTES = 1024;

    private final ResolvedRuntimeConfig config;

    public ConfigAnalyzer(ResolvedRuntimeConfig config) {
        this.config = config;
    }

    public List<ConfigSuggestion> analyze(RuntimeRiskReport report) {
        List<ConfigSuggestion> suggestions = new ArrayList<>();
        arenaBelowStartupEstimate(report).ifPresent(suggestions::add);
        return suggestions;
    }

    /** The estimate is conservative and ignores collection, so it is a suggestion, not a failure. */
    private Optional<ConfigSuggestion> arenaBelowStartupEstimate(RuntimeRiskReport report) {
        if (report.estimatedArenaBytes() <= config.arenaBytes()) {
            return Optional.empty();
        }
        int maxArena = config.board().memoryProfile().maxArenaBytes();
        int rounded = (report.estimatedArenaBytes() + ARENA_STEP_BYTES - 1) / ARENA_STEP_BYTES * ARENA_STEP_BYTES;
        String reason = "the conservative startup estimate is " + report.estimatedArenaBytes() + " bytes, above the "
                + config.arenaBytes() + " byte arena";
        if (rounded > maxArena) {
            return Optional.of(new ConfigSuggestion("JUNO-CONFIG-002", "arenaBytes", config.arenaBytes(),
                    maxArena, maxArena - config.arenaBytes(), reason + "; the " + config.board().displayName()
                            + " accepts at most " + maxArena + " bytes, so this program's startup estimate cannot fit"
                            + " on this board"));
        }
        return Optional.of(new ConfigSuggestion("JUNO-CONFIG-002", "arenaBytes", config.arenaBytes(),
                rounded, rounded - config.arenaBytes(), reason + "; " + rounded + " bytes would cover it"));
    }
}
